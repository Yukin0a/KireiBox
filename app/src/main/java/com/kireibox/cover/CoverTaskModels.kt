package com.kireibox.cover

import com.yuki.yukihub.metadata.model.CoverSourceMode

enum class CoverTaskStatus {
    QUEUED,
    SEARCHING,
    DOWNLOADING,
    NEEDS_REVIEW,
    FAILED,
    COMPLETED,
    CANCELLED,
}

enum class CoverQueueStatus { RUNNING, PAUSED }

data class CoverTaskUi(
    val gameId: Long,
    val title: String,
    val source: CoverSourceMode,
    val status: CoverTaskStatus,
    val bytesRead: Long,
    val totalBytes: Long?,
    val candidateCount: Int,
    val matchScore: Double?,
    val error: String?,
)

data class CoverQueueUiState(
    val tasks: List<CoverTaskUi>,
    val queueStatus: CoverQueueStatus,
) {
    val aggregateProgress: Float get() = CoverTaskPresentationPolicy.aggregateProgress(this)
    val pendingCount: Int get() = CoverTaskPresentationPolicy.badge(tasks).pendingCount
    val hasActiveTasks: Boolean get() = tasks.any { it.status in ACTIVE_STATUSES }

    private companion object {
        val ACTIVE_STATUSES = setOf(CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.DOWNLOADING)
    }
}

enum class CoverTaskContainer { BOTTOM_SHEET, ANCHORED_POPOVER }

data class CoverTaskBadge(val pendingCount: Int, val hasFailure: Boolean)

enum class CoverTaskRowAction { SELECT, RETRY, CANCEL, UNDO, NONE }
