package com.kireibox.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationCrossfadeTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
            File("../app/src/main/java/com/kireibox/ui/KireiBoxApp.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("KireiBoxApp.kt not found from ${File("").absolutePath}")
        return file.readText()
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
