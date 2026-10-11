package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import com.kireibox.designsystem.KireiTokens
import dev.chrisbanes.haze.HazeInputScale
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
        val darkGlass =
            DockHazePolicy.configuration(
                isDark = true,
                reducedTransparency = false,
                hazeAvailable = true,
                apiSupportsHaze = true,
            )
        assertFalse(darkGlass.useOpaqueFallback)
        assertEquals(HazeInputScale.Fixed(.5f), darkGlass.inputScale)
        assertEquals(Color.Transparent, darkGlass.backgroundColor)
        // Spec §5 e3 glass: dark #16161B α .62, light #FFFFFF α .72 (token values).
        assertEquals(Color(0xFF16161B).copy(alpha = .62f), darkGlass.tint)
        assertEquals(KireiTokens.DarkNeutrals.glass, darkGlass.tint)

        val lightGlass =
            DockHazePolicy.configuration(
                isDark = false,
                reducedTransparency = false,
                hazeAvailable = true,
                apiSupportsHaze = true,
            )
        assertEquals(Color.Transparent, lightGlass.backgroundColor)
        assertEquals(Color.White.copy(alpha = .72f), lightGlass.tint)
        assertEquals(KireiTokens.LightNeutrals.glass, lightGlass.tint)

        listOf(
            DockHazePolicy.configuration(true, reducedTransparency = true, hazeAvailable = true, apiSupportsHaze = true),
            DockHazePolicy.configuration(true, reducedTransparency = false, hazeAvailable = false, apiSupportsHaze = true),
            DockHazePolicy.configuration(true, reducedTransparency = false, hazeAvailable = true, apiSupportsHaze = false),
        ).forEach { fallback ->
            assertTrue(fallback.useOpaqueFallback)
            assertNull(fallback.inputScale)
            // Opaque fallback is the dark surfaceOverlay token (#1B1B20).
            assertEquals(Color(0xFF1B1B20), fallback.backgroundColor)
            assertEquals(KireiTokens.DarkNeutrals.surfaceOverlay, fallback.backgroundColor)
            assertNull(fallback.tint)
        }
        assertEquals(
            KireiTokens.LightNeutrals.surfaceOverlay,
            DockHazePolicy.configuration(false, reducedTransparency = true, hazeAvailable = true, apiSupportsHaze = true).backgroundColor,
        )
    }
}
