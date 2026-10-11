package com.kireibox.cover

import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.RankedCoverSearchResult
import com.yuki.yukihub.metadata.model.CoverSourceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class CoverTaskRequest(
    val gameId: Long,
    val title: String,
    val source: CoverSourceMode,
    /**
     * True when the request came from the background automatic scheduler. Only these requests feed
     * the persistent no-match store; a user retry or manual pick must never be deduplicated.
     */
    val automaticSearch: Boolean = false,
)

internal data class CoverBindingCompletion(
    val gameId: Long,
    val mode: String,
    val success: Boolean,
)

internal interface CoverTaskBackend {
    suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult>

    suspend fun save(
        request: CoverTaskRequest,
        candidate: RankedCoverCandidate,
        onProgress: (CoverDownloadProgress) -> Unit,
    ): Result<String>

    suspend fun markBinding(
        gameId: Long,
        mode: String,
        score: Double
    )
}

/**
 * In-memory, lifecycle-owned cover work queue.
 *
 * [lock] owns every mutable record and StateFlow snapshot. It never starts a coroutine or calls
 * a backend: locked code only reserves work by registering a parent-linked placeholder job and
 * returns actions for [dispatch] to run after the monitor has been released.
 */
internal class CoverTaskQueue(
    private val scope: CoroutineScope,
    private val backend: CoverTaskBackend,
    /** Invoked after a background automatic search ends without an auto-bindable candidate. */
    private val onAutomaticNoMatch: (CoverTaskRequest) -> Unit = {},
) {
    private val lock = Any()
    private val records = LinkedHashMap<Long, TaskRecord>()
    private val mutableState = MutableStateFlow(CoverQueueUiState(emptyList(), CoverQueueStatus.RUNNING))
    private val mutableBindingCompletions = MutableSharedFlow<CoverBindingCompletion>()
    private var queueStatus = CoverQueueStatus.RUNNING
    private var gameRunningSuspended = false
    private var ownerClosed = false

    val state: StateFlow<CoverQueueUiState> = mutableState.asStateFlow()
    val bindingCompletions: SharedFlow<CoverBindingCompletion> = mutableBindingCompletions.asSharedFlow()

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { closeForOwnerCancellation() }
    }

    fun enqueue(request: CoverTaskRequest) {
        dispatch(
            mutate { actions ->
                if (ownerClosed || records.containsKey(request.gameId)) return@mutate
                records[request.gameId] = TaskRecord(request)
                publishLocked()
                drainLocked(actions)
            }
        )
    }

    /** Account disconnection cancels work without changing the user's saved cover choice. */
    fun removeSource(source: CoverSourceMode) {
        dispatch(
            mutate { actions ->
                records.values.filter { it.request.source == source }.toList().forEach { record ->
                    record.generation += 1
                    record.job?.let { actions += QueueAction.Cancel(it) }
                    records.remove(record.request.gameId)
                }
                publishLocked()
                drainLocked(actions)
            }
        )
    }

    fun pause() {
        dispatch(
            mutate {
                if (ownerClosed || queueStatus == CoverQueueStatus.PAUSED) return@mutate
                queueStatus = CoverQueueStatus.PAUSED
                publishLocked()
            }
        )
    }

    fun resume() {
        dispatch(
            mutate { actions ->
                if (ownerClosed || queueStatus == CoverQueueStatus.RUNNING) return@mutate
                queueStatus = CoverQueueStatus.RUNNING
                publishLocked()
                drainLocked(actions)
            }
        )
    }

    fun suspendForGame() {
        dispatch(
            mutate { actions ->
                if (ownerClosed || gameRunningSuspended) return@mutate
                gameRunningSuspended = true
                records.values.filter { it.status == CoverTaskStatus.SEARCHING || it.status == CoverTaskStatus.DOWNLOADING }
                    .forEach { record ->
                        record.generation += 1
                        record.job?.let { actions += QueueAction.Cancel(it) }
                        record.job = null
                        record.status = CoverTaskStatus.QUEUED
                        record.bytesRead = 0
                        record.totalBytes = null
                        record.error = null
                    }
                publishLocked()
            }
        )
    }

    fun resumeAfterGame() {
        dispatch(
            mutate { actions ->
                if (ownerClosed || !gameRunningSuspended) return@mutate
                gameRunningSuspended = false
                publishLocked()
                if (queueStatus == CoverQueueStatus.RUNNING) drainLocked(actions)
            }
        )
    }

    fun cancel(gameId: Long) {
        dispatch(
            mutate { actions ->
                val record = records[gameId] ?: return@mutate
                if (record.status in TERMINAL_STATUSES) return@mutate
                record.generation += 1
                record.job?.let { actions += QueueAction.Cancel(it) }
                record.job = null
                record.status = CoverTaskStatus.CANCELLED
                record.error = null
                publishLocked()
                actions += QueueAction.Binding(record, "DISABLED", 0.0)
                drainLocked(actions)
            }
        )
    }

    fun retry(gameId: Long) {
        dispatch(
            mutate { actions ->
                if (ownerClosed) return@mutate
                val record = records[gameId] ?: return@mutate
                if (record.status !in RETRYABLE_STATUSES) return@mutate
                record.generation += 1
                record.job?.let { actions += QueueAction.Cancel(it) }
                record.job = null
                record.stage = Stage.SEARCH
                record.status = CoverTaskStatus.QUEUED
                record.candidates = emptyList()
                record.bytesRead = 0
                record.totalBytes = null
                record.error = null
                publishLocked()
                drainLocked(actions)
            }
        )
    }

    /** Resolves any stale task after a manual/default cover has been durably persisted. */
    fun resolvePersistedCover(
        gameId: Long,
        keepCompletedRow: Boolean
    ) {
        dispatch(
            mutate { actions ->
                val record = records[gameId] ?: return@mutate
                record.generation += 1
                record.job?.let { actions += QueueAction.Cancel(it) }
                record.job = null
                record.error = null
                if (keepCompletedRow) {
                    record.status = CoverTaskStatus.COMPLETED
                } else {
                    records.remove(gameId)
                }
                publishLocked()
                drainLocked(actions)
            }
        )
    }

    /** Backward-compatible helper retained for task-center selection call sites and tests. */
    fun resolveReview(gameId: Long) = resolvePersistedCover(gameId, keepCompletedRow = true)

    fun candidates(gameId: Long): List<RankedCoverCandidate> =
        synchronized(lock) {
            records[gameId]?.candidates?.toList().orEmpty()
        }

    fun dismissSessionRows() {
        dispatch(
            mutate { actions ->
                val iterator = records.entries.iterator()
                while (iterator.hasNext()) {
                    val record = iterator.next().value
                    if (record.status in DISMISSIBLE_STATUSES) {
                        record.generation += 1
                        record.job?.let { actions += QueueAction.Cancel(it) }
                        iterator.remove()
                    }
                }
                publishLocked()
            }
        )
    }

    private fun drainLocked(actions: MutableList<QueueAction>) {
        if (ownerClosed || queueStatus == CoverQueueStatus.PAUSED || gameRunningSuspended) return
        while (true) {
            val nextSearch =
                records.values.firstOrNull {
                    it.status == CoverTaskStatus.QUEUED && it.stage == Stage.SEARCH
                }
            if (nextSearch != null && activeCountLocked(CoverTaskStatus.SEARCHING) < MAX_SEARCHES) {
                reserveSearchLocked(nextSearch, actions)
                continue
            }
            val nextDownload =
                records.values.firstOrNull {
                    it.status == CoverTaskStatus.QUEUED && it.stage == Stage.DOWNLOAD
                }
            if (nextDownload != null && activeCountLocked(CoverTaskStatus.DOWNLOADING) < MAX_DOWNLOADS) {
                reserveDownloadLocked(nextDownload, actions)
                continue
            }
            return
        }
    }

    private fun reserveSearchLocked(
        record: TaskRecord,
        actions: MutableList<QueueAction>
    ) {
        record.status = CoverTaskStatus.SEARCHING
        val job = newWorkJobLocked()
        record.job = job
        publishLocked()
        actions += QueueAction.Search(record, record.generation, job)
    }

    private fun reserveDownloadLocked(
        record: TaskRecord,
        actions: MutableList<QueueAction>
    ) {
        record.status = CoverTaskStatus.DOWNLOADING
        val job = newWorkJobLocked()
        record.job = job
        val candidate = record.candidates.first()
        publishLocked()
        actions += QueueAction.Download(record, record.generation, candidate, job)
    }

    private fun onSearchFinished(
        record: TaskRecord,
        generation: Long,
        result: Result<RankedCoverSearchResult>
    ) {
        dispatch(
            mutate { actions ->
                if (!isCurrentLocked(record, generation)) return@mutate
                record.job?.let { actions += QueueAction.Finish(it) }
                record.job = null
                result.fold(
                    onSuccess = { search -> onSearchResultLocked(record, generation, search, actions) },
                    onFailure = { error -> failLocked(record, generation, error, actions) },
                )
            }
        )
    }

    private fun onSearchResultLocked(
        record: TaskRecord,
        generation: Long,
        result: RankedCoverSearchResult,
        actions: MutableList<QueueAction>,
    ) {
        if (!isCurrentLocked(record, generation)) return
        record.candidates = result.candidates.toList()
        if (record.candidates.isEmpty()) {
            if (record.request.automaticSearch) actions += QueueAction.NoMatch(record.request)
            failLocked(record, generation, IllegalStateException("未找到匹配的封面，可手动搜索或重试"), actions)
            return
        }
        // A confident automatic result always applies, even over a stale REVIEW binding: the user
        // asked for high scores to set the cover instead of stopping at a match percentage and
        // demanding a manual pick. Only a non-automatic result waits for confirmation.
        if (!result.automatic) {
            if (record.request.automaticSearch) {
                actions += QueueAction.NoMatch(record.request)
            }
            record.status = CoverTaskStatus.NEEDS_REVIEW
            publishLocked()
            actions += QueueAction.Binding(record, "REVIEW", record.candidates.first().score)
            drainLocked(actions)
            return
        }
        record.stage = Stage.DOWNLOAD
        record.status = CoverTaskStatus.QUEUED
        publishLocked()
        if (activeCountLocked(CoverTaskStatus.DOWNLOADING) < MAX_DOWNLOADS) {
            // Pause blocks new game tasks. A game already searching may finish its automatic
            // search-to-download flow, while unrelated queued games remain undispatched.
            reserveDownloadLocked(record, actions)
        } else {
            drainLocked(actions)
        }
    }

    private fun onSaveProgress(
        record: TaskRecord,
        generation: Long,
        progress: CoverDownloadProgress
    ) {
        dispatch(
            mutate {
                if (isCurrentLocked(record, generation) && record.status == CoverTaskStatus.DOWNLOADING) {
                    record.bytesRead = progress.bytesRead
                    record.totalBytes = progress.totalBytes
                    publishLocked()
                }
            }
        )
    }

    private fun onSaveFinished(
        record: TaskRecord,
        generation: Long,
        candidate: RankedCoverCandidate,
        result: Result<String>
    ) {
        dispatch(
            mutate { actions ->
                if (!isCurrentLocked(record, generation)) return@mutate
                record.job?.let { actions += QueueAction.Finish(it) }
                record.job = null
                result.fold(
                    onSuccess = {
                        record.status = CoverTaskStatus.COMPLETED
                        publishLocked()
                        actions += QueueAction.Binding(record, "AUTO", candidate.score)
                        drainLocked(actions)
                    },
                    onFailure = { error -> failLocked(record, generation, error, actions) },
                )
            }
        )
    }

    private fun failLocked(
        record: TaskRecord,
        generation: Long,
        error: Throwable,
        actions: MutableList<QueueAction>,
    ) {
        if (!isCurrentLocked(record, generation)) return
        record.job = null
        record.status = CoverTaskStatus.FAILED
        record.error = error.message ?: "Cover task failed"
        publishLocked()
        actions += QueueAction.Binding(record, "RETRY", record.candidates.firstOrNull()?.score ?: 0.0)
        drainLocked(actions)
    }

    private fun closeForOwnerCancellation() {
        dispatch(
            mutate {
                if (ownerClosed) return@mutate
                ownerClosed = true
                records.values.filter { it.status !in TERMINAL_STATUSES }.forEach { record ->
                    record.generation += 1
                    record.job = null
                    record.status = CoverTaskStatus.CANCELLED
                    record.error = null
                }
                publishLocked()
            }
        )
    }

    private fun newWorkJobLocked(): CompletableJob = Job(scope.coroutineContext[Job])

    private fun activeCountLocked(status: CoverTaskStatus): Int = records.values.count { it.status == status }

    private fun isCurrentLocked(
        record: TaskRecord,
        generation: Long
    ): Boolean = !ownerClosed && records[record.request.gameId] === record && record.generation == generation

    private fun publishLocked() {
        mutableState.value =
            CoverQueueUiState(
                tasks =
                    records.values.map { record ->
                        CoverTaskUi(
                            gameId = record.request.gameId,
                            title = record.request.title,
                            source = record.request.source,
                            status = record.status,
                            bytesRead = record.bytesRead,
                            totalBytes = record.totalBytes,
                            candidateCount = record.candidates.size,
                            matchScore = record.candidates.firstOrNull()?.score,
                            error = record.error,
                        )
                    },
                queueStatus = if (gameRunningSuspended) CoverQueueStatus.PAUSED else queueStatus,
            )
    }

    private inline fun mutate(block: (MutableList<QueueAction>) -> Unit): List<QueueAction> =
        synchronized(lock) {
            buildList {
                block(this)
            }
        }

    private fun dispatch(actions: List<QueueAction>) {
        actions.forEach { action ->
            when (action) {
                is QueueAction.Search ->
                    scope.launch(action.job) {
                        val result =
                            try {
                                backend.search(action.record.request)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        onSearchFinished(action.record, action.generation, result)
                    }

                is QueueAction.Download ->
                    scope.launch(action.job) {
                        val result =
                            try {
                                backend.save(action.record.request, action.candidate) { progress ->
                                    onSaveProgress(action.record, action.generation, progress)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Throwable) {
                                Result.failure(error)
                            }
                        onSaveFinished(action.record, action.generation, action.candidate, result)
                    }

                is QueueAction.Binding ->
                    scope.launch {
                        val success =
                            try {
                                action.record.bindingMutex.withLock {
                                    backend.markBinding(action.record.request.gameId, action.mode, action.score)
                                }
                                true
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                false
                            }
                        mutableBindingCompletions.emit(
                            CoverBindingCompletion(action.record.request.gameId, action.mode, success)
                        )
                    }

                is QueueAction.Cancel -> action.job.cancel()
                is QueueAction.Finish -> action.job.complete()
                is QueueAction.NoMatch -> onAutomaticNoMatch(action.request)
            }
        }
    }

    private data class TaskRecord(
        val request: CoverTaskRequest,
        var status: CoverTaskStatus = CoverTaskStatus.QUEUED,
        var stage: Stage = Stage.SEARCH,
        var candidates: List<RankedCoverCandidate> = emptyList(),
        var bytesRead: Long = 0,
        var totalBytes: Long? = null,
        var error: String? = null,
        var generation: Long = 0,
        var job: CompletableJob? = null,
        val bindingMutex: Mutex = Mutex(),
    )

    private sealed interface QueueAction {
        data class Search(val record: TaskRecord, val generation: Long, val job: CompletableJob) : QueueAction

        data class Download(
            val record: TaskRecord,
            val generation: Long,
            val candidate: RankedCoverCandidate,
            val job: CompletableJob,
        ) : QueueAction

        data class Binding(val record: TaskRecord, val mode: String, val score: Double) : QueueAction

        data class Cancel(val job: CompletableJob) : QueueAction

        data class Finish(val job: CompletableJob) : QueueAction

        data class NoMatch(val request: CoverTaskRequest) : QueueAction
    }

    private enum class Stage { SEARCH, DOWNLOAD }

    private companion object {
        const val MAX_SEARCHES = 3
        const val MAX_DOWNLOADS = 2
        val TERMINAL_STATUSES = setOf(CoverTaskStatus.FAILED, CoverTaskStatus.COMPLETED, CoverTaskStatus.CANCELLED)
        val RETRYABLE_STATUSES = setOf(CoverTaskStatus.FAILED, CoverTaskStatus.CANCELLED)
        val DISMISSIBLE_STATUSES = setOf(CoverTaskStatus.COMPLETED, CoverTaskStatus.CANCELLED)
    }
}
