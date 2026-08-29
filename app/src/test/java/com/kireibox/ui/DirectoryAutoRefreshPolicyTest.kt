package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryAutoRefreshPolicyTest {
    @Test
    fun enabledRootsDefaultMissingStatesToEnabled() {
        assertEquals(
            listOf("one", "three"),
            DirectoryAutoRefreshPolicy.enabledRoots(
                roots = listOf("one", "two", "three"),
                enabled = listOf(true, false),
            ),
        )
    }

    @Test
    fun noChangesAndNoFailuresStaySilent() {
        assertNull(DirectoryAutoRefreshPolicy.notice(ScanImportSummary(), 0))
    }

    @Test
    fun singleNewGameNoticeIncludesItsTitle() {
        val notice = DirectoryAutoRefreshPolicy.notice(
            ScanImportSummary(addedGames = listOf(Game(id = 7L, title = "星空列车"))),
            failedDirectoryCount = 0,
        )
        assertEquals("自动更新：发现并添加 1 个新游戏：星空列车", notice)
    }

    @Test
    fun partialFailureNoticeRetainsSuccessfulAddedCount() {
        val notice = DirectoryAutoRefreshPolicy.notice(
            ScanImportSummary(addedGames = listOf(Game(id = 1L), Game(id = 2L))),
            failedDirectoryCount = 1,
        )
        assertEquals("自动更新：添加 2 个新游戏，1 个目录扫描失败", notice)
    }

    @Test
    fun foregroundGateRejectsOverlapAndDebounceThenAllowsLaterRefresh() {
        val gate = ForegroundRefreshGate(minIntervalMs = 1_500L)
        assertTrue(gate.tryStart(1_000L))
        assertFalse(gate.tryStart(1_100L))
        gate.finish()
        assertFalse(gate.tryStart(2_000L))
        assertTrue(gate.tryStart(2_500L))
    }
}
