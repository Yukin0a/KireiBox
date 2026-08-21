package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTaskBadgePolicyTest {
    @Test
    fun badgeLabelsKeepExactSmallCountsAndCapVisibleLabelAt99Plus() {
        val expected = mapOf(
            0 to "",
            1 to "1",
            9 to "9",
            10 to "10",
            99 to "99",
            100 to "99+",
            1000 to "99+",
        )

        expected.forEach { (count, label) ->
            val badge = CoverTaskBadgePolicy.presentation(count)
            assertEquals(label, badge.label)
            assertEquals(count, badge.accessibilityCount)
            if (count == 0) assertFalse(badge.visible) else assertTrue(badge.visible)
        }
    }

    @Test
    fun cappedLabelsKeepExactCapsuleMetricsAndAccessibilityCounts() {
        listOf(100, 1000).forEach { count ->
            val badge = CoverTaskBadgePolicy.presentation(count)

            assertTrue(badge.visible)
            assertEquals("99+", badge.label)
            assertEquals(count, badge.accessibilityCount)
            assertEquals(22f, badge.heightDp, .001f)
            assertEquals(22f, badge.minWidthDp, .001f)
            assertEquals(6f, badge.horizontalPaddingDp, .001f)
            assertTrue(badge.minWidthDp + badge.horizontalPaddingDp * 2f > badge.minWidthDp)
        }
    }
}
