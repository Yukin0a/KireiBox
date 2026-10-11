package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.kireibox.cover.CoverBindingCompletion
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskStatus
import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.model.CoverSourceHealth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class KireiCoverTaskController(host: KireiViewModel) : KireiViewModelController(host) {
    private var bindingRefreshJob: Job? = null

    internal fun applyCoverQueueState(snapshot: CoverQueueUiState) {
        mutableState.value =
            mutableState.value.copy(
                coverQueue = snapshot,
                coverTaskGameIds =
                    snapshot.tasks
                        .filter { it.status in ACTIVE_COVER_QUEUE_STATUSES }
                        .mapTo(linkedSetOf()) { it.gameId },
                pendingCoverCount = snapshot.pendingCount,
                pendingCoverReviewCount = pendingCoverReviewCount(mutableState.value.games, snapshot),
                coverAttentionCount = coverAttentionCount(mutableState.value.games, snapshot),
            )
    }

    internal fun scheduleBindingRefresh(completion: CoverBindingCompletion) {
        if (!completion.success) return
        bindingRefreshJob?.cancel()
        bindingRefreshJob =
            viewModelScope.launch {
                delay(BINDING_REFRESH_DEBOUNCE_MS)
                reloadInternal()
            }
    }

    fun suspendForEngineHandoff() = coverQueue.suspendForGame()

    fun resumeAfterEngineHandoff() = coverQueue.resumeAfterGame()

    fun pauseCoverQueue() {
        core.settings.setCoverQueuePaused(true)
        coverQueue.pause()
    }

    fun resumeCoverQueue() {
        core.settings.setCoverQueuePaused(false)
        coverQueue.resume()
    }

    fun cancelCoverTask(gameId: Long) = coverQueue.cancel(gameId)

    fun retryCoverTask(gameId: Long) {
        // An explicit retry must probe every source again instead of honouring the health cooldown
        // that parked a previously-unreachable source — otherwise 重试 fails instantly for 5 minutes.
        CoverSourceHealth.reset()
        coverQueue.retry(gameId)
    }

    fun dismissCoverTaskSessionRows() = coverQueue.dismissSessionRows()

    fun openCoverTaskCenter() {
        mutableState.value = mutableState.value.copy(taskCenterOpen = true)
    }

    fun closeCoverTaskCenter() {
        mutableState.value = mutableState.value.copy(taskCenterOpen = false)
    }

    fun openCoverReview(gameId: Long) {
        val task = mutableState.value.coverQueue.tasks.firstOrNull { it.gameId == gameId } ?: return
        if (task.status != CoverTaskStatus.NEEDS_REVIEW) return
        val game = mutableState.value.games.firstOrNull { it.id == gameId } ?: return
        val order = CoverSourcePriority.searchOrder { accounts.canSearch(it) }
        val candidates =
            CoverCandidateEvidence.decorate(
                query = game.title.orEmpty(),
                candidates =
                    CoverCandidateAggregator.aggregate(
                        coverQueue.candidates(gameId).map { it.toUi(task.source) },
                        order,
                    ),
                order = order,
            )
        mutableState.value =
            mutableState.value.copy(
                taskCenterOpen = false,
                coverSearch =
                    CoverSearchUi(
                        game = game, source = task.source, keyword = game.title.orEmpty(), candidates = candidates,
                        selectedKey = candidates.firstOrNull()?.key, origin = CoverPickerOrigin.TASK_CENTER,
                        originalBindingMode = game.vndbBindingMode,
                        hadCoverOnEntry = !game.coverUri.isNullOrBlank(),
                    )
            )
    }
}

// ---------------------------------------------------------------------------------------------
// Scan roots
// ---------------------------------------------------------------------------------------------
