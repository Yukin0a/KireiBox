package com.yuki.yukihub.launcher

import androidx.compose.ui.graphics.toArgb
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engines receive the app's token palette (launch ring, KRKR in-game dialogs). Purple is the
 * default accent again (r4 user decision); every other accent keeps its own hue and never falls
 * back to a purple.
 */
class KireiEngineThemeTest {
    /** The alpha.17 purples: the app accent pair (now the CLASSIC tokens) and the immersive pair. */
    private val purples = listOf(0xFFB3ACFF.toInt(), 0xFF5B45C7.toInt(), 0xFF9B8CFF.toInt(), 0xFF6C5CE7.toInt())
    private val retiredDarkBackground = 0xFF000000.toInt()

    @Test
    fun defaultAccentIsPurpleInBothThemes() {
        listOf(true, false).forEach { dark ->
            val palette = KireiEngineTheme.palette(dark, AccentChoice.DEFAULT)
            assertEquals(KireiTokens.accent(AccentChoice.CLASSIC, dark).accent.toArgb(), palette.primary)
            assertEquals(KireiTokens.accent(AccentChoice.CLASSIC, dark).onAccent.toArgb(), palette.onPrimary)
            assertTrue("the default engine accent is a purple", palette.primary in purples)
        }
    }

    @Test
    fun neutralsComeFromTheGraphiteTokens() {
        val dark = KireiEngineTheme.palette(true, AccentChoice.OCEAN)
        assertEquals(KireiTokens.DarkNeutrals.background.toArgb(), dark.background)
        assertNotEquals("dark engine screens are graphite, not pure black", retiredDarkBackground, dark.background)
        assertEquals(KireiTokens.DarkNeutrals.surface.toArgb(), dark.card)
        assertEquals(KireiTokens.DarkNeutrals.text.toArgb(), dark.text)
        assertEquals(KireiTokens.DarkNeutrals.secondary.toArgb(), dark.textMuted)
        val light = KireiEngineTheme.palette(false, AccentChoice.OCEAN)
        assertEquals(KireiTokens.LightNeutrals.background.toArgb(), light.background)
        assertEquals(KireiTokens.accent(AccentChoice.OCEAN, false).accent.toArgb(), light.primary)
    }

    @Test
    fun systemModeFollowsTheDeviceNightStateLikeTheDarkModeExtra() {
        assertEquals(KireiEngineTheme.palette(true, AccentChoice.MINT), KireiEngineTheme.palette(ThemeMode.SYSTEM, systemNight = true, accent = AccentChoice.MINT))
        assertEquals(KireiEngineTheme.palette(false, AccentChoice.MINT), KireiEngineTheme.palette(ThemeMode.SYSTEM, systemNight = false, accent = AccentChoice.MINT))
        assertEquals(KireiEngineTheme.palette(false, AccentChoice.MINT), KireiEngineTheme.palette(ThemeMode.LIGHT, systemNight = true, accent = AccentChoice.MINT))
        assertEquals(KireiEngineTheme.palette(true, AccentChoice.MINT), KireiEngineTheme.palette(ThemeMode.DARK, systemNight = false, accent = AccentChoice.MINT))
    }

    @Test
    fun purpleIsTheDefaultAndOtherAccentsNeverFallBackToPurple() {
        // The default (CLASSIC) maps to the purple tokens...
        listOf(true, false).forEach { dark ->
            assertEquals(KireiTokens.accent(AccentChoice.CLASSIC, dark).accent.toArgb(), KireiEngineTheme.palette(dark, AccentChoice.CLASSIC).primary)
            assertEquals(KireiEngineTheme.palette(dark, AccentChoice.CLASSIC), KireiEngineTheme.palette(dark, AccentChoice.DEFAULT))
        }
        // ...and an explicitly chosen other accent never resolves to any of the purples.
        AccentChoice.entries.filter { it != AccentChoice.CLASSIC }.forEach { accent ->
            listOf(true, false).forEach { dark ->
                purples.forEach { assertNotEquals("$accent dark=$dark", it, KireiEngineTheme.palette(dark, accent).primary) }
            }
        }
    }
}
