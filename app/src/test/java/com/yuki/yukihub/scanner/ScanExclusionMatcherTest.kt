package com.yuki.yukihub.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanExclusionMatcherTest {
    private val patterns =
        listOf(
            "backup",
            "backups",
            "bak",
            "\u5907\u4efd",
            "*\u5907\u4efd",
            "\u5907\u4efd*",
            "*\u526f\u672c",
            "ai\u7ffb\u8bd1\u5907\u4efd",
            "*.bak"
        )

    @Test
    fun prunesCommonBackupDirectoriesCaseInsensitively() {
        assertTrue(ScanExclusionMatcher.matchesName("BACKUP", patterns))
        assertTrue(ScanExclusionMatcher.matchesName("\u9b54\u5973\u7684\u591c\u5bb4\u5907\u4efd", patterns))
        assertTrue(ScanExclusionMatcher.matchesName("AI\u7ffb\u8bd1\u5907\u4efd", patterns))
        assertTrue(ScanExclusionMatcher.matchesName("data.bak", patterns))
    }

    @Test
    fun keepsNormalGameDirectories() {
        assertFalse(ScanExclusionMatcher.matchesName("\u9b54\u5973\u7684\u591c\u5bb4", patterns))
        assertFalse(ScanExclusionMatcher.matchesName("9nine-1", patterns))
    }
}
