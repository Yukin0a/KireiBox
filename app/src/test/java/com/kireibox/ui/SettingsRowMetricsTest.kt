package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 屏幕方向 row lines up with its neighbours. Until r4 it sat among the root's category rows and
 * had to share their adaptive metrics (tile size, padding); r4 S1 moved it into 外观 → 显示, where
 * every row is a plain titled row without a tile, so it must be built exactly like 动画效果 above
 * it — no category header, no tile — and the root category rows keep their own adaptive metrics.
 */
class SettingsRowMetricsTest {
    private val components: String by lazy { UiSourceFiles.file("SettingsComponents.kt") }

    @Test
    fun orientationRowIsAPlainSegmentedRowLikeTheMotionRow() {
        val orientation = body(components, "fun OrientationModeRow(")
        val motion = body(components, "fun MotionModeRow(")
        listOf(orientation, motion).forEach { row ->
            assertTrue(row.contains("SettingsSegmentedRow("))
            assertTrue(row.contains("SettingsSegmentedRowSpec("))
            assertFalse("no category header on a 显示 row", row.contains("header ="))
            assertFalse("no category tile on a 显示 row", row.contains("KireiTileColor"))
            assertFalse("no root category metrics on a 显示 row", row.contains("withCategoryMetrics"))
        }
        assertFalse("屏幕方向's options say it all: no description line", orientation.contains("subtitle ="))
        assertTrue("动画效果 only says what 自动 resolved to", motion.contains("subtitle = if (requested == MotionMode.AUTO) \"当前：\${motionModeLabel(resolved)}\" else null"))
    }

    @Test
    fun rootCategoryRowsKeepTheirAdaptiveMetrics() {
        val root = UiSourceFiles.file("SettingsRootPage.kt")
        assertTrue(root.contains("withCategoryMetrics(resolveSettingsCategoryRowMetrics(context.orientation))"))
        // Pinned per orientation: the orientation row no longer mirrors these, so nothing else holds them.
        assertEquals(
            SettingsCategoryRowMetrics(verticalPaddingDp = 12f, minimumHeightDp = 64f, iconSizeDp = 34f),
            resolveSettingsCategoryRowMetrics(HomeOrientation.PORTRAIT),
        )
        assertEquals(
            "landscape rows stay compact so the root's first groups clear the dock",
            SettingsCategoryRowMetrics(verticalPaddingDp = 4f, minimumHeightDp = 40f, iconSizeDp = 28f),
            resolveSettingsCategoryRowMetrics(HomeOrientation.LANDSCAPE),
        )
    }

    private fun body(
        source: String,
        marker: String,
    ): String {
        val start = source.indexOf(marker)
        assertTrue("$marker missing", start >= 0)
        // Up to the next top-level declaration (annotation, keyword or KDoc at column zero).
        val end = Regex("""\n(?=[@A-Za-z/])""").find(source, start)?.range?.first ?: source.length
        return source.substring(start, end)
    }
}
