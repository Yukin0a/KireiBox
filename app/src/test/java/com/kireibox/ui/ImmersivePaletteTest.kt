package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePaletteTest {
    private val dark = ImmersivePaletteResolver.resolve(isDark = true)
    private val light = ImmersivePaletteResolver.resolve(isDark = false)

    @Test
    fun darkTokensMatchTheChromeContract() {
        assertTrue(dark.isDark)
        assertEquals(Color(0xFF05070D), dark.base)
        assertEquals(Color.White, dark.primary)
        assertEquals(Color.White.copy(alpha = .70f), dark.secondary)
        assertEquals(Color(0x66121720), dark.glass)
        assertEquals(Color.White.copy(alpha = .28f), dark.glassBorder)
        assertEquals(Color(0xFF9B8CFF), dark.accent)
        assertEquals(Color(0xFF21164E), dark.playInk)
    }

    @Test
    fun lightTokensMatchTheLightContract() {
        assertFalse(light.isDark)
        assertEquals(Color(0xFFF4F2F9), light.base)
        assertEquals(Color(0xFF18151F), light.primary)
        assertEquals(Color(0xFF6E6778), light.secondary)
        assertEquals(Color(0xB3FFFFFF), light.glass)
        assertEquals(Color(0x996750D8), light.glassBorder)
        assertEquals(Color(0xFF6C5CE7), light.accent)
        assertEquals(Color.White, light.playInk)
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
        assertEquals(Color(0xF005070D), dark.cardOverlayBottom)
    }

    @Test
    fun immersiveDockKeepsDarkChromeInDarkThemeAndAppPaletteInLightTheme() {
        val lightApp = KireiColors(
            background = Color(0xFFF4F2F9),
            surface = Color(0xFFFFFFFF),
            surfaceRaised = Color(0xFFFFFFFF),
            text = Color(0xFF18151F),
            secondary = Color(0xFF6E6778),
            accent = Color(0xFF6C5CE7),
            success = Color(0xFF4FCC7A),
            outline = Color(0xFFD9D5E4),
            onAccent = Color.White,
            danger = Color(0xFFE5484D),
            isDark = false,
        )
        val dockLight = immersiveDockKireiColors(lightApp)
        assertFalse("light theme must not force the dark chrome Dock", dockLight.isDark)
        assertEquals("light theme Dock reuses the app palette", lightApp, dockLight)

        val darkApp = lightApp.copy(isDark = true)
        val dockDark = immersiveDockKireiColors(darkApp)
        assertTrue("dark theme Dock keeps the chrome-dark palette", dockDark.isDark)
        assertNotEquals("chrome-dark palette differs from the app palette", darkApp, dockDark)
        assertEquals(Color(0xFF05070D), dockDark.background)
    }
}
