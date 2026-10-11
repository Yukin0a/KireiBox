package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationCrossfadeTest {
    private fun source(): String {
        return UiSourceFiles.all()
    }

    @Test
    fun `outer page crossfade is instant when reduced motion is enabled`() {
        val src = source()
        val marker = src.indexOf("mainNavigation")
        assertTrue("mainNavigation Crossfade missing", marker >= 0)
        val block = src.substring((marker - 400).coerceAtLeast(0), marker)
        assertTrue("expected tween(if (reducedMotion) 0 else 190), got: $block", block.contains("tween(if (reducedMotion) 0 else 190)"))
        assertFalse("reduced-motion duration must be 0, got: $block", block.contains("tween(if (reducedMotion) 80 else 190)"))
    }
}
