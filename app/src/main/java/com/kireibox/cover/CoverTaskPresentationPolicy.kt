package com.kireibox.cover

import com.kireibox.ui.KireiLayoutMode

internal object CoverTaskPresentationPolicy {
    data class Section(val title: String, val tasks: List<CoverTaskUi>)
    data class TaskButtonState(
        val visible: Boolean,
        val aggregateProgress: Float,
        val completedCount: Int,
        val totalCount: Int,
        val pendingCount: Int,
        val hasFailure: Boolean,
        val showCompletionCheck: Boolean,
        val contentDescription: String,
    )

    data class AnchoredContainerMetrics(
        val widthDp: Float,
        val maxHeightDp: Float,
    )

    fun aggregateProgress(state: CoverQueueUiState): Float {
        if (state.tasks.isEmpty()) return 0f
        val completed = state.tasks.sumOf { task ->
            when (task.status) {
                CoverTaskStatus.COMPLETED, CoverTaskStatus.NEEDS_REVIEW,
                CoverTaskStatus.FAILED, CoverTaskStatus.CANCELLED -> 1.0
                CoverTaskStatus.DOWNLOADING -> task.downloadFraction()
                CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING -> 0.0
            }
        }
        return (completed / state.tasks.size).toFloat()
    }

    fun displayOrder(tasks: List<CoverTaskUi>): List<CoverTaskUi> =
        tasks.filter { it.status in ATTENTION_STATUSES } +
            tasks.filter { it.status in ACTIVE_STATUSES } +
            tasks.filter { it.status in TERMINAL_STATUSES }

    fun badge(tasks: List<CoverTaskUi>): CoverTaskBadge = CoverTaskBadge(
        pendingCount = tasks.count { it.status in ATTENTION_STATUSES },
        hasFailure = tasks.any { it.status == CoverTaskStatus.FAILED },
    )

    fun containerFor(layoutMode: KireiLayoutMode): CoverTaskContainer = when (layoutMode) {
        KireiLayoutMode.PHONE_PORTRAIT -> CoverTaskContainer.BOTTOM_SHEET
        KireiLayoutMode.WIDE -> CoverTaskContainer.ANCHORED_POPOVER
    }

    fun buttonState(tasks: List<CoverTaskUi>): TaskButtonState {
        val badge = badge(tasks)
        val completedCount = tasks.count { it.status == CoverTaskStatus.COMPLETED }
        val totalCount = tasks.size
        return TaskButtonState(
            visible = tasks.isNotEmpty(),
            aggregateProgress = aggregateProgress(CoverQueueUiState(tasks, CoverQueueStatus.RUNNING)),
            completedCount = completedCount,
            totalCount = totalCount,
            pendingCount = badge.pendingCount,
            hasFailure = badge.hasFailure,
            showCompletionCheck = shouldShowCompletionCheck(tasks),
            contentDescription = "封面任务，已完成 $completedCount / $totalCount，待处理 ${badge.pendingCount}",
        )
    }

    fun anchoredContainerMetrics(
        windowWidthDp: Float,
        windowHeightDp: Float,
        safeTopDp: Float,
        safeRightDp: Float,
        safeBottomDp: Float,
    ): AnchoredContainerMetrics = AnchoredContainerMetrics(
        widthDp = (windowWidthDp - safeRightDp - POPOVER_MARGIN_DP)
            .coerceAtLeast(0f)
            .coerceAtMost(POPOVER_WIDTH_DP),
        maxHeightDp = (windowHeightDp - safeTopDp - safeBottomDp - POPOVER_MARGIN_DP)
            .coerceAtLeast(0f),
    )

    fun shouldShowTaskButton(tasks: List<CoverTaskUi>): Boolean = tasks.isNotEmpty()

    fun shouldDismissCompletedRows(tasks: List<CoverTaskUi>, panelOpen: Boolean): Boolean =
        !panelOpen && tasks.isNotEmpty() && tasks.all { it.status == CoverTaskStatus.COMPLETED }

    fun rowActionFor(status: CoverTaskStatus): CoverTaskRowAction = when (status) {
        CoverTaskStatus.NEEDS_REVIEW -> CoverTaskRowAction.SELECT
        CoverTaskStatus.FAILED -> CoverTaskRowAction.RETRY
        CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.DOWNLOADING -> CoverTaskRowAction.CANCEL
        CoverTaskStatus.CANCELLED -> CoverTaskRowAction.UNDO
        CoverTaskStatus.COMPLETED -> CoverTaskRowAction.NONE
    }

    fun sections(tasks: List<CoverTaskUi>): List<Section> = listOf(
        Section("需要处理", tasks.filter { it.status in ATTENTION_STATUSES }),
        Section("正在处理", tasks.filter { it.status in ACTIVE_STATUSES }),
        Section("已完成", tasks.filter { it.status in TERMINAL_STATUSES }),
    ).filter { it.tasks.isNotEmpty() }

    fun actionLabel(action: CoverTaskRowAction): String = when (action) {
        CoverTaskRowAction.SELECT -> "选择"
        CoverTaskRowAction.RETRY -> "重试"
        CoverTaskRowAction.CANCEL -> "取消"
        CoverTaskRowAction.UNDO -> "撤销"
        CoverTaskRowAction.NONE -> ""
    }

    fun phaseLabel(task: CoverTaskUi): String = when (task.status) {
        CoverTaskStatus.QUEUED -> "等待处理"
        CoverTaskStatus.SEARCHING -> "正在匹配"
        CoverTaskStatus.DOWNLOADING -> task.totalBytes?.takeIf { it > 0L }?.let { "正在下载 ${((task.bytesRead * 100) / it).coerceIn(0, 100)}%" } ?: "正在下载"
        CoverTaskStatus.NEEDS_REVIEW -> "等待确认"
        CoverTaskStatus.FAILED -> task.error ?: "匹配失败"
        CoverTaskStatus.COMPLETED -> "已完成"
        CoverTaskStatus.CANCELLED -> "已取消"
    }

    fun shouldShowCompletionCheck(tasks: List<CoverTaskUi>): Boolean =
        tasks.isNotEmpty() && tasks.all { it.status in TERMINAL_STATUSES }

    fun downloadBytesLabel(task: CoverTaskUi): String = task.totalBytes?.let { "${task.bytesRead} B / $it B" } ?: "已下载 ${task.bytesRead} B"

    private fun CoverTaskUi.downloadFraction(): Double {
        val total = totalBytes ?: return 0.0
        if (total <= 0L) return 0.0
        return (bytesRead.toDouble() / total).coerceIn(0.0, 1.0)
    }

    private val ATTENTION_STATUSES = setOf(CoverTaskStatus.NEEDS_REVIEW, CoverTaskStatus.FAILED)
    private val ACTIVE_STATUSES = setOf(CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.DOWNLOADING)
    private val TERMINAL_STATUSES = setOf(CoverTaskStatus.COMPLETED, CoverTaskStatus.CANCELLED)
    private const val POPOVER_WIDTH_DP = 360f
    private const val POPOVER_MARGIN_DP = 24f
}
