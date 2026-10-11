package com.kireibox.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.darkKireiColors
import com.kireibox.designsystem.kireiColorsFromTokens
import com.yuki.yukihub.settings.AccentChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePaletteTest {
    private val dark = ImmersivePaletteResolver.resolve(isDark = true)
    private val light = ImmersivePaletteResolver.resolve(isDark = false)

    @Test
    fun darkTokensUseANearBlackBaseInTheAccentHue() {
        assertTrue(dark.isDark)
        // Spec §2.4 / §10 r4 · V1: as dark as the graphite #0A0A0C token, but carrying the accent hue —
        // alpha.17's #05070D was blue-black, r4's neutral base read as a grey room. Purple by default.
        assertEquals(immersiveDarkBase(dark.accent), dark.base)
        assertEquals("within one 8-bit step", KireiTokens.Immersive.DarkBase.luminance(), dark.base.luminance(), .0004f)
        assertEquals(ImmersivePaletteResolver.resolve(isDark = true, accent = AccentChoice.MINT).base, immersiveDarkBase(KireiTokens.accent(AccentChoice.MINT, isDark = true).accent))
        assertEquals(KireiTokens.DarkNeutrals.text, dark.primary)
        assertEquals(dark.primary.copy(alpha = .70f), dark.secondary)
        assertEquals(KireiTokens.Immersive.DarkGlass, dark.glass)
        assertEquals(Color.White.copy(alpha = .28f), dark.glassBorder)
    }

    @Test
    fun lightTokensUseTheNeutralGraphiteBase() {
        assertFalse(light.isDark)
        assertEquals(Color(0xFFF4F4F6), light.base)
        assertEquals(KireiTokens.LightNeutrals.text, light.primary)
        assertEquals(KireiTokens.LightNeutrals.secondary, light.secondary)
        assertEquals(KireiTokens.Immersive.LightGlass, light.glass)
        assertEquals(KireiTokens.LightNeutrals.strokeStrong, light.glassBorder)
    }

    @Test
    fun accentInkFollowsTheAppAccentInsteadOfAHardcodedPurple() {
        // Default token palette = the default accent, purple (spec §2.2, r4), taken from the app
        // tokens — never the hardcoded alpha.17 immersive #9B8CFF / #6C5CE7.
        assertEquals(KireiTokens.accent(AccentChoice.DEFAULT, isDark = true).accent, dark.accent)
        assertEquals(Color(0xFFB3ACFF), dark.accent)
        assertEquals(Color(0xFF211E47), dark.playInk)
        assertEquals(Color(0xFF5B45C7), light.accent)
        assertEquals(Color.White, light.playInk)
        listOf(dark, light).forEach { palette ->
            assertNotEquals(Color(0xFF9B8CFF), palette.accent)
            assertNotEquals(Color(0xFF6C5CE7), palette.accent)
        }

        // The resolved app accent drives accent, highlight and play ink.
        val ocean = kireiColorsFromTokens(isDark = true, accent = AccentChoice.OCEAN)
        val fromApp = ImmersivePaletteResolver.resolve(ocean.isDark, ocean.accent, ocean.onAccent)
        assertEquals(ocean.accent, fromApp.accent)
        assertEquals(ocean.onAccent, fromApp.playInk)
        assertNotEquals(dark.accentBright, fromApp.accentBright)
        assertEquals(fromApp, ImmersivePaletteResolver.resolve(isDark = true, accent = AccentChoice.OCEAN))

        AccentChoice.entries.forEach { choice ->
            val palette = ImmersivePaletteResolver.resolve(isDark = true, accent = choice)
            assertEquals(KireiTokens.accent(choice, isDark = true).accent, palette.accent)
            assertNotEquals("highlight is a lighter accent, not the accent itself", palette.accent, palette.accentBright)
        }
    }

    @Test
    fun lightAndDarkTokensAreFullyDistinct() {
        assertNotEquals(dark.base, light.base)
        assertNotEquals(dark.primary, light.primary)
        assertNotEquals(dark.secondary, light.secondary)
        assertNotEquals(dark.glass, light.glass)
        assertNotEquals(dark.glassBorder, light.glassBorder)
        assertNotEquals(dark.accent, light.accent)
        assertNotEquals(dark.accentBright, light.accentBright)
        assertNotEquals(dark.scrimStart, light.scrimStart)
        assertNotEquals(dark.scrimMid, light.scrimMid)
        assertNotEquals(dark.blendAlpha, light.blendAlpha, .001f)
        assertNotEquals(dark.glowAlpha, light.glowAlpha, .001f)
    }

    @Test
    fun coverInternalOverlayStaysDarkInBothThemes() {
        assertEquals(dark.cardOverlayBottom, light.cardOverlayBottom)
        assertEquals(KireiTokens.Immersive.DarkBase.copy(alpha = .94f), dark.cardOverlayBottom)
    }

    @Test
    fun immersiveDockUsesGraphiteDarkPaletteInDarkThemeAndAppPaletteInLightTheme() {
        val lightApp = kireiColorsFromTokens(isDark = false, accent = AccentChoice.MINT)
        val dockLight = immersiveDockKireiColors(lightApp)
        assertFalse("light theme must not force the dark chrome Dock", dockLight.isDark)
        assertEquals("light theme Dock reuses the app palette", lightApp, dockLight)

        val darkApp = kireiColorsFromTokens(isDark = true, accent = AccentChoice.MINT)
        val dockDark = immersiveDockKireiColors(darkApp)
        assertTrue("dark theme Dock keeps the chrome-dark palette", dockDark.isDark)
        assertNotEquals("chrome-dark palette differs from the app palette", darkApp, dockDark)
        assertEquals("Dock sits on the immersive graphite base", KireiTokens.Immersive.DarkBase, dockDark.background)
        assertEquals(darkKireiColors().surface, dockDark.surface)
        assertEquals("Dock keeps the user's accent", darkApp.accent, dockDark.accent)
        assertEquals(darkApp.onAccent, dockDark.onAccent)
    }
}
