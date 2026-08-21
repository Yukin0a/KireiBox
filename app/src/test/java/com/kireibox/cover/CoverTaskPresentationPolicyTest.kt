package com.kireibox.cover

import com.kireibox.ui.KireiLayoutMode
import com.yuki.yukihub.metadata.CoverSourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTaskPresentationPolicyTest {
    @Test
    fun taskButtonVisibilityCleanupAndRowActionsFollowQueueState() {
        val active = task(1, CoverTaskStatus.DOWNLOADING)
        val review = task(2, CoverTaskStatus.NEEDS_REVIEW)
        val completed = task(3, CoverTaskStatus.COMPLETED)

        assertTrue(CoverTaskPresentationPolicy.shouldShowTaskButton(listOf(active)))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(completed), panelOpen = true))
        assertTrue(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(completed), panelOpen = false))
        assertEquals(CoverTaskRowAction.SELECT, CoverTaskPresentationPolicy.rowActionFor(review.status))
        assertEquals(CoverTaskRowAction.CANCEL, CoverTaskPresentationPolicy.rowActionFor(active.status))
        assertEquals(CoverTaskRowAction.UNDO, CoverTaskPresentationPolicy.rowActionFor(CoverTaskStatus.CANCELLED))
    }

    @Test
    fun taskCenterSectionsAndChineseActionsAreExplicit() {
        val tasks = listOf(
            task(1, CoverTaskStatus.NEEDS_REVIEW), task(2, CoverTaskStatus.DOWNLOADING), task(3, CoverTaskStatus.CANCELLED)
        )
        assertEquals(listOf("需要处理", "正在处理", "已完成"), CoverTaskPresentationPolicy.sections(tasks).map { it.title })
        assertEquals("撤销", CoverTaskPresentationPolicy.actionLabel(CoverTaskRowAction.UNDO))
        assertEquals("正在下载 50%", CoverTaskPresentationPolicy.phaseLabel(task(2, CoverTaskStatus.DOWNLOADING, 5, 10)))
    }

    @Test
    fun terminalBatchShowsCheckButOnlyCompletedBatchAllowsCleanup() {
        assertTrue(CoverTaskPresentationPolicy.shouldShowCompletionCheck(listOf(task(1, CoverTaskStatus.COMPLETED))))
        assertTrue(CoverTaskPresentationPolicy.shouldShowCompletionCheck(listOf(task(1, CoverTaskStatus.CANCELLED))))
        assertTrue(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(task(1, CoverTaskStatus.COMPLETED)), panelOpen = false))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(task(1, CoverTaskStatus.COMPLETED)), panelOpen = true))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(task(1, CoverTaskStatus.CANCELLED)), panelOpen = false))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(task(1, CoverTaskStatus.FAILED)), panelOpen = false))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(listOf(task(1, CoverTaskStatus.NEEDS_REVIEW)), panelOpen = false))
        assertFalse(CoverTaskPresentationPolicy.shouldDismissCompletedRows(emptyList(), panelOpen = false))
    }

    @Test
    fun taskButtonStateKeepsTerminalRowsVisibleAndExposesDynamicCounts() {
        val state = CoverTaskPresentationPolicy.buttonState(
            listOf(
                task(1, CoverTaskStatus.COMPLETED),
                task(2, CoverTaskStatus.CANCELLED),
                task(3, CoverTaskStatus.NEEDS_REVIEW),
                task(4, CoverTaskStatus.FAILED),
                task(5, CoverTaskStatus.DOWNLOADING, bytesRead = 5, totalBytes = 10),
            ),
        )

        assertTrue(state.visible)
        assertEquals(1, state.completedCount)
        assertEquals(5, state.totalCount)
        assertEquals(2, state.pendingCount)
        assertTrue(state.hasFailure)
        assertFalse(state.showCompletionCheck)
        assertEquals(0.9f, state.aggregateProgress, 0.0001f)
        assertEquals("封面任务，已完成 1 / 5，待处理 2", state.contentDescription)

        val terminal = CoverTaskPresentationPolicy.buttonState(
            listOf(task(1, CoverTaskStatus.COMPLETED), task(2, CoverTaskStatus.CANCELLED)),
        )
        assertTrue(terminal.visible)
        assertTrue(terminal.showCompletionCheck)
        assertFalse(CoverTaskPresentationPolicy.buttonState(emptyList()).visible)
    }

    @Test
    fun anchoredContainerMetricsDeductFullSafeDrawingArea() {
        val compact = CoverTaskPresentationPolicy.anchoredContainerMetrics(
            windowWidthDp = 914f,
            windowHeightDp = 411f,
            safeTopDp = 24f,
            safeRightDp = 48f,
            safeBottomDp = 24f,
        )

        assertEquals(360f, compact.widthDp, 0.001f)
        assertEquals(339f, compact.maxHeightDp, 0.001f)

        val narrow = CoverTaskPresentationPolicy.anchoredContainerMetrics(
            windowWidthDp = 320f,
            windowHeightDp = 400f,
            safeTopDp = 20f,
            safeRightDp = 20f,
            safeBottomDp = 30f,
        )
        assertEquals(276f, narrow.widthDp, 0.001f)
        assertEquals(326f, narrow.maxHeightDp, 0.001f)
    }

    @Test
    fun downloadByteLabelShowsKnownAndUnknownLengths() {
        assertEquals("5 B / 10 B", CoverTaskPresentationPolicy.downloadBytesLabel(task(1, CoverTaskStatus.DOWNLOADING, 5, 10)))
        assertEquals("已下载 5 B", CoverTaskPresentationPolicy.downloadBytesLabel(task(1, CoverTaskStatus.DOWNLOADING, 5)))
    }
    @Test
    fun aggregateProgressCountsTerminalTasksAndKnownDownloadBytes() {
        val state = CoverQueueUiState(
            tasks = listOf(
                task(1, CoverTaskStatus.COMPLETED),
                task(2, CoverTaskStatus.NEEDS_REVIEW),
                task(3, CoverTaskStatus.DOWNLOADING, bytesRead = 40, totalBytes = 80),
                task(4, CoverTaskStatus.SEARCHING),
            ),
            queueStatus = CoverQueueStatus.RUNNING,
        )

        assertEquals(0.625f, CoverTaskPresentationPolicy.aggregateProgress(state), 0.0001f)
    }

    @Test
    fun aggregateProgressReturnsZeroForAnEmptyQueueAndUnknownDownloads() {
        assertEquals(0f, CoverTaskPresentationPolicy.aggregateProgress(CoverQueueUiState(emptyList(), CoverQueueStatus.RUNNING)))
        assertEquals(
            0f,
            CoverTaskPresentationPolicy.aggregateProgress(
                CoverQueueUiState(listOf(task(1, CoverTaskStatus.DOWNLOADING, bytesRead = 50)), CoverQueueStatus.RUNNING)
            ),
        )
    }

    @Test
    fun displayOrderPutsAttentionThenActiveThenTerminalWhileKeepingInputOrderInEachGroup() {
        val ordered = CoverTaskPresentationPolicy.displayOrder(
            listOf(
                task(1, CoverTaskStatus.COMPLETED),
                task(2, CoverTaskStatus.SEARCHING),
                task(3, CoverTaskStatus.FAILED),
                task(4, CoverTaskStatus.QUEUED),
                task(5, CoverTaskStatus.NEEDS_REVIEW),
                task(6, CoverTaskStatus.CANCELLED),
                task(7, CoverTaskStatus.DOWNLOADING),
            )
        )

        assertEquals(listOf(3L, 5L, 2L, 4L, 7L, 1L, 6L), ordered.map { it.gameId })
    }

    @Test
    fun badgeSummarizesReviewAndFailureTasks() {
        val badge = CoverTaskPresentationPolicy.badge(
            listOf(task(1, CoverTaskStatus.NEEDS_REVIEW), task(2, CoverTaskStatus.FAILED), task(3, CoverTaskStatus.SEARCHING))
        )

        assertEquals(2, badge.pendingCount)
        assertTrue(badge.hasFailure)
        assertFalse(CoverTaskPresentationPolicy.badge(listOf(task(4, CoverTaskStatus.COMPLETED))).hasFailure)
    }

    @Test
    fun containerUsesBottomSheetOnlyForPhonePortrait() {
        assertEquals(CoverTaskContainer.BOTTOM_SHEET, CoverTaskPresentationPolicy.containerFor(KireiLayoutMode.PHONE_PORTRAIT))
        assertEquals(CoverTaskContainer.ANCHORED_POPOVER, CoverTaskPresentationPolicy.containerFor(KireiLayoutMode.WIDE))
    }

    private fun task(
        gameId: Long,
        status: CoverTaskStatus,
        bytesRead: Long = 0,
        totalBytes: Long? = null,
    ) = CoverTaskUi(
        gameId = gameId,
        title = "Game $gameId",
        source = CoverSourceMode.VNDB,
        status = status,
        bytesRead = bytesRead,
        totalBytes = totalBytes,
        candidateCount = 0,
        matchScore = null,
        error = null,
    )
}
