package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun noChangesStillGivesVisibleFeedback() {
        assertEquals(
            "自动更新：未发现新游戏",
            DirectoryAutoRefreshPolicy.notice(ScanImportSummary(), 0),
        )
        assertEquals(
            "自动更新：未发现新游戏",
            DirectoryAutoRefreshPolicy.notice(ScanImportSummary(), 0, 0),
        )
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
    fun prunedOnlyNoticeUsesCleanupWording() {
        assertEquals(
            "自动更新：已清理 3 张失效卡片",
            DirectoryAutoRefreshPolicy.notice(ScanImportSummary(), 0, 3),
        )
    }

    @Test
    fun addedAndPrunedShareOneNotice() {
        assertEquals(
            "自动更新：添加 2 个新游戏，已清理 1 张失效卡片",
            DirectoryAutoRefreshPolicy.notice(
                ScanImportSummary(addedGames = listOf(Game(id = 1L), Game(id = 2L))),
                failedDirectoryCount = 0,
                prunedCount = 1,
            ),
        )
    }

    @Test
    fun prunedAndFailedShareOneNotice() {
        assertEquals(
            "自动更新：已清理 1 张失效卡片，2 个目录扫描失败",
            DirectoryAutoRefreshPolicy.notice(ScanImportSummary(), 2, 1),
        )
    }

    @Test
    fun foregroundGateRejectsOverlapThenAllowsImmediateRestart() {
        val gate = ForegroundRefreshGate()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        gate.finish()
        assertTrue(gate.tryStart())
        gate.finish()
    }
}
