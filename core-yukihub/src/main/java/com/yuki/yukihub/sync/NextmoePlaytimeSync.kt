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
import kotlinx.coroutines.sync.withLock
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
    val uploaded: Int = 0,
    val failed: Int = 0,
)

/**
 * Account-scoped cloud snapshots, reversible imports and absolute local uploads.
 * Only an explicit manual reduction may overwrite this app's remote row with a lower value.
 * Runs in-process after library/cover changes, login and launcher resume.
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
    private var trigger = "手动"
    private var pulled = 0
    private var uploaded = 0
    private var failed = 0
    private var remoteSnapshot: Map<String, Int> = emptyMap()
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
        if (mutableStatus.value.running) return
        if (pending?.isActive == true) {
            if(delayMillis >= DEFAULT_DELAY_MS) return
            pending?.cancel()
        }
        pending =
            scope.launch {
                delay(delayMillis)
                trigger = if(delayMillis < DEFAULT_DELAY_MS) "游戏库或封面更新" else "回到前台"
                runWithRetry()
            }
    }

    @Synchronized
    fun syncNow() {
        if(mutableStatus.value.running) {
            scope.launch { while(mutableStatus.value.running) delay(100); syncNow() }
            return
        }
        pending?.cancel()
        pending = scope.launch { trigger = "立即同步"; runWithRetry() }
    }

    /** One retry after a network failure while the process lives; otherwise the next launcher resume catches up. */
    private suspend fun runWithRetry() {
        if (runOnce() == PlaytimeSyncProblem.NETWORK || failed > 0) {
            delay(RETRY_DELAY_MS)
            runOnce()
        }
    }

    /** Re-reads the ledger for the status line without any request (after a login, a confirmation, a reload). */
    fun refreshSummary() {
        scope.launch { runCatching { mutableStatus.value = mutableStatus.value.copy(summary = NextmoePlaytimeLedger.summarize(ledger.libraryGames(account()))) } }
    }

    /** Games whose work the user still has to confirm or pick, and games NextMoe refused. */
    fun attention(): List<PlaytimeGame> = ledger.libraryGames(account()).filter { it.row != null && (it.row.workId == null || it.row.rejected != null || it.row.lastResult?.startsWith("FAILED:") == true) }

    /** The user confirmed [workId] for [gameId]: bind it and upload on the next sync. */
    suspend fun confirm(
        gameId: Long,
        workId: String,
    ) = mutex.withLock {
        val account = account()
        val row = ledger.libraryGames(account).firstOrNull { it.gameId == gameId }?.row ?: PlaytimeLedgerRow()
        if(row.workId != null && row.workId != workId) ledger.undoImport(gameId)
        ledger.save(gameId, account, rebind(row, workId).copy(boundFrom = NextmoeWorkPlan.manualWork(ledger.binding(gameId)) ?: "confirmed:$workId"))
        syncNow()
    }

    /** The user is about to pick another work in the cover picker: decide this game again afterwards. */
    suspend fun forget(gameId: Long) = mutex.withLock { ledger.undoImport(gameId); ledger.forgetResolution(gameId, account()) }

    /** Runs one sync unless one is already running; returns what stopped it, or null when it finished. */
    suspend fun runOnce(): PlaytimeSyncProblem? {
        val startRequests = api.requestCount
        mutex.lock()
        var problem: PlaytimeSyncProblem? = PlaytimeSyncProblem.NETWORK
        try {
            pulled = 0
            uploaded = 0
            failed = 0
            mutableStatus.value = mutableStatus.value.copy(running = true)
            problem = blocker() ?: syncOrProblem()
            if (problem == null) prefs.edit().putLong(KEY_LAST, clock()).apply()
        } finally {
            mutableStatus.value =
                mutableStatus.value.copy(
                    running = false,
                    problem = problem,
                    pulled = pulled,
                    uploaded = uploaded, failed = failed,
                    lastSyncAt = prefs.getLong(KEY_LAST, 0L),
                    summary = runCatching { NextmoePlaytimeLedger.summarize(ledger.libraryGames(account())) }.getOrDefault(mutableStatus.value.summary),
                )
            if (problem != PlaytimeSyncProblem.NOT_CONNECTED) {
                val errors = runCatching { ledger.libraryGames(account()).mapNotNull { it.row?.lastResult?.takeIf { value -> value.startsWith("FAILED:") } }.distinct().joinToString() }.getOrDefault("")
                val entry = "${clock()} · $trigger · 云端 ${remoteSnapshot.size} · 上传 $uploaded · 拉取 $pulled · 失败 $failed · 请求 ${api.requestCount - startRequests} · ${problem ?: "完成"} $errors"
                prefs.edit().putString("history_${account()}", (listOf(entry) + history()).take(20).joinToString("\n")).apply()
            }
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

    fun privacy(): Boolean? = api.privacy()
    fun setPrivacy(value: Boolean) = api.setPrivacy(value)

    fun cloud(): List<CloudPlaytimeWork> = ledger.cloud(account())
    fun library(): List<PlaytimeGame> = ledger.libraryGames(account())
    fun history(): List<String> = prefs.getString("history_${account()}", "").orEmpty().lines().filter { it.isNotBlank() }

    private fun ignoreCloudUnlocked(workId: String) {
        val account = account()
        ledger.snapshot(account, ledger.cloud(account).map { if(it.workId == workId) it.copy(match = "IGNORED", gameId = null) else it })
    }

    suspend fun ignoreCloud(workId: String) = mutex.withLock {
        val account = account()
        ledger.libraryGames(account).filter { it.row?.workId == workId }.forEach { game ->
            ledger.undoImport(game.gameId)
            ledger.save(game.gameId, account, game.row!!.copy(workId = null, candidateId = null, lastResult = "IMPORT_UNDONE"))
        }
        ignoreCloudUnlocked(workId)
        refreshSummary()
    }

    suspend fun undo(gameId: Long) = mutex.withLock {
        val account = account()
        ledger.undoImport(gameId)
        val row = ledger.libraryGames(account).firstOrNull { it.gameId == gameId }?.row ?: PlaytimeLedgerRow()
        row.workId?.let(::ignoreCloudUnlocked)
        ledger.save(gameId, account, row.copy(workId = null, candidateId = null, lastResult = "IMPORT_UNDONE"))
        refreshSummary()
    }

    suspend fun editLocal(gameId: Long, change: () -> Boolean) = mutex.withLock {
        val lower = change()
        val account = account()
        val row = ledger.libraryGames(account).firstOrNull { it.gameId == gameId }?.row ?: PlaytimeLedgerRow()
        ledger.save(gameId, account, row.copy(forceWrite = lower || row.forceWrite, ignoreRemoteAbove = if(lower) Int.MAX_VALUE else row.ignoreRemoteAbove, rejected = null, retryAfter = 0))
        syncNow()
    }

    private fun sync(account: String) {
        remoteSnapshot = api.allPlaytimes()
        val previous = ledger.cloud(account).associateBy { it.workId }
        val expired = remoteSnapshot.keys.filter { previous[it]?.metadata == null || clock() - previous.getValue(it).fetchedAt > 7 * 86_400_000L }
        val catalog = expired.chunked(BATCH).flatMap(api::catalog).associateBy { it.id }
        val works = remoteSnapshot.map { (id, minutes) ->
            val old = previous[id]
            CloudPlaytimeWork(id, minutes, catalog[id] ?: old?.metadata, match = if(old?.match == "IGNORED") "IGNORED" else "NONE", fetchedAt = if(id in catalog) clock() else old?.fetchedAt ?: 0)
        }
        // Keep the previous snapshot on network/JSON failures; replace only after all pages succeed.
        ledger.snapshot(account, works)
        resolve(account, ledger.libraryGames(account))
        matchCloud(account, works)
        deleteStaleRows(account)
        pulled = pull(account)
        upload(account)
        val bound = ledger.libraryGames(account).filter { it.row?.workId != null }.groupBy { it.row!!.workId }
        val cloud = ledger.cloud(account)
        val added = bound.filterKeys { id -> cloud.none { it.workId == id } }.mapNotNull { (id, group) ->
            val game = group.first()
            val minutes = game.row!!.remoteMinutes
            if(minutes <= 0) null else CloudPlaytimeWork(id!!, minutes, gameId = game.gameId, match = "BOUND")
        }
        ledger.snapshot(account, cloud.map { work ->
            val game = bound[work.workId]?.firstOrNull()
            if(game != null) work.copy(gameId = game.gameId, match = "BOUND", minutes = game.row!!.remoteMinutes) else work
        } + added)
    }

    private fun matchCloud(account: String, works: List<CloudPlaytimeWork>) {
        val initial = ledger.libraryGames(account)
        initial.filter { it.row?.workId != null && it.row.boundFrom == null }.forEach { game ->
            val binding = ledger.binding(game.gameId)
            val matches = works.filter { work -> work.match != "IGNORED" && (binding.nextmoeId == work.workId || NextmoeWorkPlan.refs(binding).any { it in work.metadata?.externalRefs.orEmpty().split(',').map(String::trim) }) }
            if(matches.size > 1) {
                ledger.undoImport(game.gameId)
                ledger.save(game.gameId, account, game.row!!.copy(workId = null, candidateId = matches.first().workId, lastResult = "AMBIGUOUS"))
            }
        }
        val library = ledger.libraryGames(account)
        val proposals = mutableMapOf<Long, MutableList<CloudPlaytimeWork>>()
        works.filter { it.match != "IGNORED" }.forEach { work ->
            val refs = work.metadata?.externalRefs.orEmpty().split(',').map { it.trim() }.toSet()
            library.filter { game ->
                val binding = ledger.binding(game.gameId)
                game.row?.workId == null && game.row?.lastResult != "IMPORT_UNDONE" &&
                    (binding.nextmoeId == work.workId || NextmoeWorkPlan.refs(binding).any { it in refs })
            }.forEach { proposals.getOrPut(it.gameId) { mutableListOf() }.add(work) }
        }
        proposals.filterValues { it.size == 1 }.forEach { (id, matches) ->
            val game = library.first { it.gameId == id }
            ledger.save(id, account, rebind(game.row ?: PlaytimeLedgerRow(), matches.single().workId))
        }
        val snapshots = works.map { work ->
            if(work.match == "IGNORED") return@map work
            val titles = work.metadata?.let { listOf(it.chineseTitle, it.originalTitle, it.romanTitle) + it.titleAliases }.orEmpty().map(::normalizedTitle).filter { it.isNotBlank() }
            val candidates = library.filter { it.row?.workId == null && it.row?.lastResult != "IMPORT_UNDONE" && normalizedTitle(it.title) in titles }
            candidates.forEach { game ->
                if(proposals[game.gameId].isNullOrEmpty()) ledger.save(game.gameId, account, (game.row ?: PlaytimeLedgerRow()).copy(candidateId = work.workId, candidateTitle = work.metadata?.chineseTitle?.ifBlank { work.metadata.originalTitle }, lastResult = "TITLE_SUGGESTED"))
            }
            if(candidates.isNotEmpty()) work.copy(match = "SUGGESTED", gameId = candidates.singleOrNull()?.gameId) else work
        }
        ledger.snapshot(account, snapshots)
    }

    private fun pull(account: String): Int {
        var raised = 0
        ledger.libraryGames(account).forEach { game ->
            val row = game.row?.takeIf { it.rejected == null } ?: return@forEach
            val work = row.workId ?: return@forEach
            val minutes = remoteSnapshot[work] ?: 0
            if(row.forceWrite) return@forEach
            val ignored = row.ignoreRemoteAbove?.let { minutes <= it } == true
            val result = when {
                ignored -> "SKIPPED_REMOTE_HIGHER"
                minutes > game.localMinutes -> { ledger.raiseLocal(game.gameId, minutes); raised++; "PULLED" }
                minutes == game.localMinutes -> "IN_SYNC"
                row.retryAfter > clock() && row.lastResult?.startsWith("FAILED:") == true -> row.lastResult
                else -> "WAITING_UPLOAD"
            }
            ledger.save(game.gameId, account, row.copy(remoteMinutes = minutes, uploadedMinutes = if(ignored) row.uploadedMinutes else maxOf(row.uploadedMinutes, minutes), lastResult = result, lastResultAt = clock()))
        }
        return raised
    }

    /** Rows this app wrote on a work the game is no longer bound to are removed (only this app's row). */
    private fun deleteStaleRows(account: String) {
        val library = ledger.libraryGames(account)
        library.forEach { game ->
            val row = game.row ?: return@forEach
            val stale = row.staleWorkId ?: return@forEach
            if(library.none { it.row?.workId == stale }) api.delete(stale)
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
                manual != null && manual != (row?.boundFrom ?: row?.workId) -> {
                    if(row?.workId != null && row.workId != manual) ledger.undoImport(game.gameId)
                    ledger.save(game.gameId, account, rebind(row ?: PlaytimeLedgerRow(), manual).copy(boundFrom = manual, forceWrite = row?.forceWrite ?: false, ignoreRemoteAbove = row?.ignoreRemoteAbove))
                }
                row == null || (row.workId == null && row.lastResult != "IMPORT_UNDONE") -> unresolved += binding
            }
        }
        if (unresolved.isEmpty()) return
        val works = unresolved.flatMap(NextmoeWorkPlan::refs).distinct().chunked(BATCH).flatMap(api::resolveRefs)
        val refAnswers = works.flatMap { work -> work.externalRefs.split(',').map { it.trim() to work.id } }.groupBy({ it.first }, { it.second })
        val byRef = refAnswers.filterValues { it.distinct().size == 1 }.mapValues { it.value.first() }
        val ambiguousRefs = refAnswers.filterValues { it.distinct().size > 1 }.keys
        val cachedIds = unresolved.mapNotNull { it.nextmoeId }.filter { id -> works.none { it.id == id } }.distinct()
        val details = (works + cachedIds.chunked(BATCH).flatMap(api::catalog)).distinctBy { it.id }
        val titles = details.associate { it.id to it.chineseTitle.ifBlank { it.originalTitle } }
        unresolved.forEach { binding ->
            val localKey = normalizedTitle(games.firstOrNull { it.gameId == binding.gameId }?.title.orEmpty())
            val exactTitles = details.filter { localKey.length >= 4 && localKey in NextmoeTitleIdentity.aliases(it) }
            val titleConfirmed = exactTitles.size == 1 && NextmoeWorkPlan.refs(binding).none { it in ambiguousRefs } &&
                (binding.nextmoeId == exactTitles.single().id || NextmoeWorkPlan.refs(binding).any { byRef[it] == exactTitles.single().id })
            val proposed = NextmoeWorkPlan.decide(binding, byRef, titleConfirmed)
            val decision = if(proposed is WorkDecision.Bound && NextmoeWorkPlan.manualWork(binding) == null &&
                NextmoeWorkPlan.manualRef(binding) == null && NextmoeWorkPlan.refs(binding).any { it in ambiguousRefs })
                WorkDecision.Candidate(proposed.workId) else proposed
            if (!keepUnbound && decision !is WorkDecision.Bound) return@forEach
            val row =
                when (decision) {
                    is WorkDecision.Bound -> PlaytimeLedgerRow(workId = decision.workId)
                    is WorkDecision.Candidate -> PlaytimeLedgerRow(candidateId = decision.workId, candidateTitle = titles[decision.workId])
                    WorkDecision.Unmatched -> PlaytimeLedgerRow()
                }
            val prior = games.firstOrNull { it.gameId == binding.gameId }?.row
            ledger.save(binding.gameId, account, row.copy(forceWrite = prior?.forceWrite ?: false, ignoreRemoteAbove = prior?.ignoreRemoteAbove))
        }
    }

    private fun upload(account: String) {
        val games = ledger.libraryGames(account).filter { it.row?.workId != null && it.row.rejected == null && it.row.retryAfter <= clock() }
        games.groupBy { it.row!!.workId!! }.entries.chunked(BATCH).forEach { batch ->
            val force = batch.filter { (_, group) -> group.any { it.row!!.forceWrite } }
            force.forEach { (work, group) ->
                val target = group.first { it.row!!.forceWrite }.localMinutes
                val result = api.put(work, target)
                val observed = if(result.ok) api.playtimes(listOf(work))[work] ?: target else remoteSnapshot[work] ?: 0
                if(result.ok) uploaded++
                group.forEach { record(account, it, target, observed, result, true) }
            }
            val writes = batch.filter { it !in force }.map { (work, group) -> work to group.maxOf { it.localMinutes } }
                .filter { (work, minutes) -> minutes > (remoteSnapshot[work] ?: 0) }
            val results = if(writes.isEmpty()) emptyMap() else api.write(writes).associateBy { it.workId }
            uploaded += results.values.count { it.ok }
            writes.forEach { (work, minutes) -> batch.first { it.key == work }.value.forEach { record(account, it, minutes, if(results[work]?.ok == true) minutes else remoteSnapshot[work] ?: 0, results[work], false) } }
        }
    }

    private fun record(account: String, game: PlaytimeGame, written: Int, remote: Int, result: PlaytimeWriteResult?, force: Boolean) {
        val row = game.row ?: return
        val next = when {
            result?.ok == true -> {
                row.copy(uploadedMinutes = written, uploadedWorkId = row.workId, remoteMinutes = remote, forceWrite = false, retryCount = 0, retryAfter = 0,
                    ignoreRemoteAbove = if(force && remote > written) remote else if(force) null else row.ignoreRemoteAbove,
                    lastResult = if(force && remote > written) "SKIPPED_REMOTE_HIGHER" else "UPLOADED")
            }
            result?.code == "ENTITY_MERGED" && result.currentId.isNotBlank() -> row.copy(workId = result.currentId, lastResult = "MERGED")
            else -> {
                failed++
                val code = result?.code ?: "MISSING"
                val permanent = code in setOf("NOT_FOUND", "VALIDATION_FAILED", "HTTP_404", "HTTP_422", "HTTP_403", "FORBIDDEN")
                row.copy(rejected = code.takeIf { permanent }, retryCount = row.retryCount + 1, retryAfter = clock() + (30_000L * (1L shl row.retryCount.coerceAtMost(10))), lastResult = "FAILED:$code")
            }
        }
        ledger.save(game.gameId, account, next.copy(lastResultAt = clock()))
    }

    private fun account(): String = nextmoeAccount()?.id.orEmpty().ifBlank { "nextmoe" }

    companion object {
        private const val PREFS = "kireibox_nextmoe_playtime"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last_sync_at"
        private const val KEY_BLOCKED_UNTIL = "blocked_until"
        private const val KEY_BLOCKED_DAILY = "blocked_daily"
        private const val BATCH = 100
        fun normalizedTitle(title: String): String = NextmoeTitleIdentity.key(title)

        const val DEFAULT_DELAY_MS = 2 * 60 * 1000L
        private const val RETRY_DELAY_MS = 10 * 60 * 1000L

        /** Binds [workId]; time already written to a different work is queued for removal from it. */
        fun rebind(
            row: PlaytimeLedgerRow,
            workId: String,
        ): PlaytimeLedgerRow =
            PlaytimeLedgerRow(
                workId = workId,
                forceWrite = row.forceWrite,
                ignoreRemoteAbove = row.ignoreRemoteAbove.takeIf { row.forceWrite || row.workId == workId },
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
