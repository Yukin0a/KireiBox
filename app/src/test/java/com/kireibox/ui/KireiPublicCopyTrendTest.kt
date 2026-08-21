package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KireiPublicCopyTrendTest {
    @Test fun trendAndLogCopyContainsOnlyProductLanguageWithoutRemovedTechnicalExplanations() {
        val copy = KireiPublicCopy.dashboardAndLogLabels()

        assertTrue(copy.contains("游玩时长"))
        assertTrue(copy.contains("打开游戏数"))
        assertTrue(copy.contains("最近 7 天 · 结果"))
        assertFalse(copy.any { it.contains("仅统计引擎前台时长") || it.contains("日内去重") })
        assertFalse(copy.any { it.contains("记录诊断日志") || it.contains("导出日志") })
    }
}
