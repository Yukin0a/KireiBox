package com.kireibox.ui

import dev.chrisbanes.haze.HazeInputScale
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazeLiquidActionButtonPolicyTest {
    @Test
    fun availableDockHazeUsesFixedHalfInputScale() {
        assertEquals(
            HazeInputScale.Fixed(.5f),
            DockHazePolicy.inputScale(
                reducedTransparency = false,
                hazeAvailable = true,
                apiSupportsHaze = true,
            ),
        )
    }

    @Test
    fun dockConfigurationKeepsStableColorsAndUsesOpaqueFallbacks() {
        val darkGlass = DockHazePolicy.configuration(
            isDark = true,
            reducedTransparency = false,
            hazeAvailable = true,
            apiSupportsHaze = true,
        )
        assertFalse(darkGlass.useOpaqueFallback)
        assertEquals(HazeInputScale.Fixed(.5f), darkGlass.inputScale)
        assertEquals(Color.Transparent, darkGlass.backgroundColor)
        assertEquals(Color(0x59141620), darkGlass.tint)

        val lightGlass = DockHazePolicy.configuration(
            isDark = false,
            reducedTransparency = false,
            hazeAvailable = true,
            apiSupportsHaze = true,
        )
        assertEquals(Color.Transparent, lightGlass.backgroundColor)
        assertEquals(Color(0x45FFFFFF), lightGlass.tint)

        listOf(
            DockHazePolicy.configuration(true, reducedTransparency = true, hazeAvailable = true, apiSupportsHaze = true),
            DockHazePolicy.configuration(true, reducedTransparency = false, hazeAvailable = false, apiSupportsHaze = true),
            DockHazePolicy.configuration(true, reducedTransparency = false, hazeAvailable = true, apiSupportsHaze = false),
        ).forEach { fallback ->
            assertTrue(fallback.useOpaqueFallback)
            assertNull(fallback.inputScale)
            assertEquals(Color(0xF20C101C), fallback.backgroundColor)
            assertNull(fallback.tint)
        }
    }
}
