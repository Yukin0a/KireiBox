package com.yuki.yukihub.sync

import android.content.Context
import android.content.SharedPreferences
import com.yuki.yukihub.auth.CoverAccountStatus
import com.yuki.yukihub.auth.CoverAccounts
import com.yuki.yukihub.metadata.model.CoverSourceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

/** Why a sync could not run or finish; the settings status line words each one. */
enum class PlaytimeSyncProblem { NOT_CONNECTED, NEEDS_LOGIN, QUOTA, RATE_LIMITED, NETWORK }

data class PlaytimeSyncStatus(
    val enabled: Boolean = true,
    val running: Boolean = false,
    val lastSyncAt: Long = 0L,
    val summary: PlaytimeLedgerSummary = PlaytimeLedgerSummary(),
    val problem: PlaytimeSyncProblem? = null,
    /** Games whose local total the last finished sync raised from NextMoe (a new device, a reinstall). */
    val pulled: Int = 0,
)

/**
 * Mirrors local playtime to the user's NextMoe account (doc §5). The local total is the truth and is
 * the only number ever written; a work is written only when the local value is larger than what
 * NextMoe already has, so a reinstall, a new device or a reset never lowers the remote value.
 * The other way, a NextMoe total larger than the local one is brought onto this device (§5.7), so a
 * new phone picks up the time played elsewhere. Both directions only ever raise: the result is the
 * larger value of all devices, never their sum.
 *
 * Runs in-process: [schedule] after the launcher comes back (sessions settle there), [syncNow] for
 * 立即同步 and right after login. A killed process simply catches up on the next launcher resume.
 */
class NextmoePlaytimeSync(
    private val nextmoeAccount: () -> CoverAccountStatus?,
    private val ledger: NextmoePlaytimeStore,
    private val api: NextmoePlaytimeRemote,
    private val prefs: SharedPreferences,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pending: Job? = null
    private var pulled = 0
    private val mutableStatus = MutableStateFlow(PlaytimeSyncStatus(enabled = enabled(), lastSyncAt = prefs.getLong(KEY_LAST, 0L)))
    val status: StateFlow<PlaytimeSyncStatus> = mutableStatus.asStateFlow()

    fun enabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        if (!value) pending?.cancel()
        mutableStatus.value = mutableStatus.value.copy(enabled = value, problem = null)
        if (value) syncNow()
    }

    /** Coalesces triggers: a sync already waiting keeps its slot. */
    @Synchronized
    fun schedule(delayMillis: Long = DEFAULT_DELAY_MS) {
        if (pending?.isActive == true) return
        pending =
            scope.launch {
                delay(delayMillis)
                runWithRetry()
            }
    }

    @Synchronized
    fun syncNow() {
        pending?.cancel()
        pending = scope.launch { runWithRetry() }
    }

    /** One retry after a network failure while the process lives; otherwise the next launcher resume catches up. */
    private suspend fun runWithRetry() {
        if (runOnce() == PlaytimeSyncProblem.NETWORK) {
            delay(RETRY_DELAY_MS)
            runOnce()
        }
    }

    /** Re-reads the ledger for the status line without any request (after a login, a confirmation, a reload). */
    fun refreshSummary() {
        scope.launch { runCatching { mutableStatus.value = mutableStatus.value.copy(summary = NextmoePlaytimeLedger.summarize(ledger.games(account()))) } }
    }

    /** Games whose work the user still has to confirm or pick, and games NextMoe refused. */
    fun attention(): List<PlaytimeGame> = ledger.games(account()).filter { it.row != null && (it.row.workId == null || it.row.rejected != null) }

    /** The user confirmed [workId] for [gameId]: bind it and upload on the next sync. */
    fun confirm(
        gameId: Long,
        workId: String,
    ) {
        val account = account()
        val row = ledger.games(account).firstOrNull { it.gameId == gameId }?.row ?: PlaytimeLedgerRow()
        ledger.save(gameId, account, rebind(row, workId))
        syncNow()
    }

    /** The user is about to pick another work in the cover picker: decide this game again afterwards. */
    fun forget(gameId: Long) = ledger.forgetResolution(gameId, account())

    /** Runs one sync unless one is already running; returns what stopped it, or null when it finished. */
    suspend fun runOnce(): PlaytimeSyncProblem? {
        if (!mutex.tryLock()) return null
        var problem: PlaytimeSyncProblem? = PlaytimeSyncProblem.NETWORK
        try {
            pulled = 0
            mutableStatus.value = mutableStatus.value.copy(running = true)
            problem = blocker() ?: syncOrProblem()
            if (problem == null) prefs.edit().putLong(KEY_LAST, clock()).apply()
        } finally {
            mutableStatus.value =
                mutableStatus.value.copy(
                    running = false,
                    problem = problem,
                    pulled = pulled,
                    lastSyncAt = prefs.getLong(KEY_LAST, 0L),
                    summary = runCatching { NextmoePlaytimeLedger.summarize(ledger.games(account())) }.getOrDefault(mutableStatus.value.summary),
                )
            mutex.unlock()
        }
        return problem
    }

    private fun blocker(): PlaytimeSyncProblem? {
        val account = nextmoeAccount()
        return when {
            !enabled() || account?.connected != true -> PlaytimeSyncProblem.NOT_CONNECTED
            account.needsReauth -> PlaytimeSyncProblem.NEEDS_LOGIN
            clock() < prefs.getLong(KEY_BLOCKED_UNTIL, 0L) -> if (prefs.getBoolean(KEY_BLOCKED_DAILY, false)) PlaytimeSyncProblem.QUOTA else PlaytimeSyncProblem.RATE_LIMITED
            else -> null
        }
    }

    private fun syncOrProblem(): PlaytimeSyncProblem? =
        try {
            sync(account())
            null
        } catch (limit: NextmoeLimitException) {
            prefs.edit().putLong(KEY_BLOCKED_UNTIL, limit.untilMillis).putBoolean(KEY_BLOCKED_DAILY, limit.dailyQuota).apply()
            if (limit.dailyQuota) PlaytimeSyncProblem.QUOTA else PlaytimeSyncProblem.RATE_LIMITED
        } catch (_: NextmoeAuthException) {
            PlaytimeSyncProblem.NEEDS_LOGIN
        } catch (_: IOException) {
            PlaytimeSyncProblem.NETWORK
        } catch (_: java.util.concurrent.CancellationException) {
            // The account was disconnected mid-sync (CoverAccounts generation changed).
            PlaytimeSyncProblem.NOT_CONNECTED
        } catch (_: Exception) {
            // An unexpected answer (malformed JSON, …): report it like a failed request and retry later.
            PlaytimeSyncProblem.NETWORK
        }

    private fun sync(account: String) {
        resolve(account, ledger.games(account))
        deleteStaleRows(account)
        pulled = pull(account)
        upload(account)
    }

    /**
     * Raises local totals that are below NextMoe's; returns how many games changed. Games not played
     * here yet (a fresh install) are resolved only while NextMoe holds works no local game is bound to,
     * and only a sure binding is kept for them, so an unplayed game never lands in 待确认 and is tried
     * again on a later sync (e.g. once its cover has been matched).
     */
    private fun pull(account: String): Int {
        val remote = api.allPlaytimes().filterValues { it > 0 }
        if (remote.isEmpty()) return 0
        var library = ledger.libraryGames(account)
        val bound = library.mapNotNullTo(mutableSetOf()) { it.row?.workId }
        if (!bound.containsAll(remote.keys)) {
            resolve(account, library.filter { it.localMinutes == 0 }, keepUnbound = false)
            library = ledger.libraryGames(account)
        }
        var raised = 0
        library.forEach { game ->
            val row = game.row?.takeIf { it.rejected == null } ?: return@forEach
            val work = row.workId ?: return@forEach
            val minutes = remote[work] ?: return@forEach
            if (minutes > game.localMinutes) {
                ledger.raiseLocal(game.gameId, minutes)
                raised++
            }
            // NextMoe already has this much: the upload step has nothing to write for it.
            if (minutes > row.uploadedMinutes) ledger.save(game.gameId, account, row.copy(uploadedMinutes = minutes, uploadedWorkId = work))
        }
        return raised
    }

    /** Rows this app wrote on a work the game is no longer bound to are removed (only this app's row). */
    private fun deleteStaleRows(account: String) {
        ledger.games(account).forEach { game ->
            val row = game.row ?: return@forEach
            val stale = row.staleWorkId ?: return@forEach
            api.delete(stale)
            ledger.save(game.gameId, account, row.copy(staleWorkId = null, uploadedWorkId = row.uploadedWorkId?.takeIf { it != stale }))
        }
    }

    /** Finds [games]' works; with [keepUnbound] false only a sure binding is saved (a candidate or no match is asked again later). */
    private fun resolve(
        account: String,
        games: List<PlaytimeGame>,
        keepUnbound: Boolean = true,
    ) {
        val unresolved = mutableListOf<GameBinding>()
        games.forEach { game ->
            val binding = ledger.binding(game.gameId)
            val manual = NextmoeWorkPlan.manualWork(binding)
            val row = game.row
            when {
                // A NextMoe cover the user newly picked wins over any earlier answer (a merged id still counts as the same pick).
                manual != null && manual != (row?.boundFrom ?: row?.workId) -> ledger.save(game.gameId, account, rebind(row ?: PlaytimeLedgerRow(), manual).copy(boundFrom = manual))
                row == null -> unresolved += binding
            }
        }
        if (unresolved.isEmpty()) return
        val works = unresolved.flatMap(NextmoeWorkPlan::refs).distinct().chunked(BATCH).flatMap(api::resolveRefs)
        val byRef = works.flatMap { work -> work.externalRefs.split(',').map { it.trim() to work.id } }.toMap()
        val titles = works.associate { it.id to it.chineseTitle.ifBlank { it.originalTitle } }
        unresolved.forEach { binding ->
            val decision = NextmoeWorkPlan.decide(binding, byRef)
            if (!keepUnbound && decision !is WorkDecision.Bound) return@forEach
            val row =
                when (decision) {
                    is WorkDecision.Bound -> PlaytimeLedgerRow(workId = decision.workId)
                    is WorkDecision.Candidate -> PlaytimeLedgerRow(candidateId = decision.workId, candidateTitle = titles[decision.workId])
                    WorkDecision.Unmatched -> PlaytimeLedgerRow()
                }
            ledger.save(binding.gameId, account, row)
        }
    }

    private fun upload(account: String) {
        val games = ledger.games(account).filter { it.row?.workId != null && it.row.rejected == null }
        // Two library entries of the same work write once, with the larger total.
        val byWork = games.groupBy { it.row!!.workId!! }.filterValues { group -> group.any { it.localMinutes > it.row!!.uploadedMinutes } }
        byWork.keys.chunked(BATCH).forEach { workIds ->
            val remote = api.playtimes(workIds)
            val writes = workIds.map { it to byWork.getValue(it).maxOf(PlaytimeGame::localMinutes) }.filter { (work, minutes) -> minutes > (remote[work] ?: 0) }
            val results = if (writes.isEmpty()) emptyMap() else api.write(writes).associateBy(PlaytimeWriteResult::workId)
            workIds.forEach { work ->
                val local = writes.firstOrNull { it.first == work }?.second
                byWork.getValue(work).forEach { game -> record(account, game, UploadOutcome(work, local, remote[work] ?: 0, results[work])) }
            }
        }
    }

    /** What happened to one work in a batch: [written] is null when NextMoe already had at least as much. */
    private data class UploadOutcome(
        val work: String,
        val written: Int?,
        val remote: Int,
        val result: PlaytimeWriteResult?,
    )

    private fun record(
        account: String,
        game: PlaytimeGame,
        outcome: UploadOutcome,
    ) {
        val row = game.row ?: return
        val result = outcome.result
        val next =
            when {
                // NextMoe already has at least as much: nothing to write, never lower it.
                outcome.written == null -> row.copy(uploadedMinutes = maxOf(outcome.remote, game.localMinutes), uploadedWorkId = outcome.work)
                result?.ok == true -> row.copy(uploadedMinutes = outcome.written, uploadedWorkId = outcome.work)
                result?.code == "ENTITY_MERGED" && result.currentId.isNotBlank() -> row.copy(workId = result.currentId)
                else -> row.copy(rejected = result?.code ?: "MISSING")
            }
        ledger.save(game.gameId, account, next)
    }

    private fun account(): String = nextmoeAccount()?.id.orEmpty().ifBlank { "nextmoe" }

    companion object {
        private const val PREFS = "kireibox_nextmoe_playtime"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last_sync_at"
        private const val KEY_BLOCKED_UNTIL = "blocked_until"
        private const val KEY_BLOCKED_DAILY = "blocked_daily"
        private const val BATCH = 100
        const val DEFAULT_DELAY_MS = 2 * 60 * 1000L
        private const val RETRY_DELAY_MS = 10 * 60 * 1000L

        /** Binds [workId]; time already written to a different work is queued for removal from it. */
        fun rebind(
            row: PlaytimeLedgerRow,
            workId: String,
        ): PlaytimeLedgerRow =
            PlaytimeLedgerRow(
                workId = workId,
                uploadedMinutes = if (row.uploadedWorkId == workId) row.uploadedMinutes else 0,
                uploadedWorkId = row.uploadedWorkId,
                staleWorkId = row.uploadedWorkId?.takeIf { it != workId } ?: row.staleWorkId,
            )

        @Volatile private var instance: NextmoePlaytimeSync? = null

        fun get(context: Context): NextmoePlaytimeSync =
            instance ?: synchronized(this) {
                instance ?: run {
                    val app = context.applicationContext
                    val accounts = CoverAccounts.get(app)
                    NextmoePlaytimeSync(
                        { accounts.status.value[CoverSourceMode.NEXTMOE] },
                        NextmoePlaytimeLedger(app),
                        NextmoeUserApi(accounts),
                        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                    )
                }.also { instance = it }
            }
    }
}
