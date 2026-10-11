package com.kireibox.ui

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.kireibox.designsystem.AppleColorPalette
import com.kireibox.designsystem.KireiThemeResolver
import com.kireibox.designsystem.kireiColorsFromTokens
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Theme-mode / accent wiring from settings to the launcher window and the Compose palette. */
class ThemeModeWiringTest {
    @Test
    fun nightBitsAreReadAndRewrittenWithoutTouchingOtherUiModeBits() {
        val typeBits = Configuration.UI_MODE_TYPE_NORMAL
        assertTrue(KireiThemeResolver.isNightUiMode(typeBits or Configuration.UI_MODE_NIGHT_YES))
        assertFalse(KireiThemeResolver.isNightUiMode(typeBits or Configuration.UI_MODE_NIGHT_NO))
        assertFalse(KireiThemeResolver.isNightUiMode(typeBits or Configuration.UI_MODE_NIGHT_UNDEFINED))

        val night = KireiThemeResolver.withNight(typeBits or Configuration.UI_MODE_NIGHT_NO, dark = true)
        assertEquals(typeBits or Configuration.UI_MODE_NIGHT_YES, night)
        assertEquals(typeBits or Configuration.UI_MODE_NIGHT_NO, KireiThemeResolver.withNight(night, dark = false))
    }

    @Test
    fun appCompatFollowsTheSystemOnlyInSystemMode() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, KireiThemeResolver.appCompatNightMode(ThemeMode.SYSTEM))
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, KireiThemeResolver.appCompatNightMode(ThemeMode.LIGHT))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, KireiThemeResolver.appCompatNightMode(ThemeMode.DARK))
    }

    @Test
    fun composeRootResolvesDarkFromThemeModeAndSystemNight() {
        assertTrue(KireiThemeResolver.resolveDark(ThemeMode.SYSTEM, systemNight = true, activityNight = false))
        assertFalse(KireiThemeResolver.resolveDark(ThemeMode.SYSTEM, systemNight = false, activityNight = true))
        assertTrue(KireiThemeResolver.resolveDark(ThemeMode.DARK, systemNight = false, activityNight = false))
        assertFalse(KireiThemeResolver.resolveDark(ThemeMode.LIGHT, systemNight = true, activityNight = true))
        // Before settings load, the launcher configuration (resolved at attach) decides.
        assertTrue(KireiThemeResolver.resolveDark(null, systemNight = false, activityNight = true))
        assertFalse(KireiThemeResolver.resolveDark(null, systemNight = true, activityNight = false))
    }

    @Test
    fun appPaletteConstructionAgreesWithTheTokenPaletteForEveryThemeAccentAndContrast() {
        listOf(true, false).forEach { dark ->
            AccentChoice.entries.forEach { accent ->
                listOf(false, true).forEach { contrast ->
                    val fromTheme = kireiColorsOf(AppleColorPalette.resolve(dark, accent, contrast))
                    assertEquals("$dark/$accent/$contrast", kireiColorsFromTokens(dark, accent, contrast), fromTheme)
                }
            }
        }
    }

    @Test
    fun rootWiresThemeModeAccentAndContrastIntoTheTheme() {
        val root = UiSourceFiles.file("KireiBoxApp.kt")
        assertTrue(root.contains("themeMode = settings?.themeMode"))
        assertTrue(root.contains("systemNight = rememberSystemNight()"))
        assertTrue(root.contains("accent = settings?.accentChoice ?: AccentChoice.DEFAULT"))
        assertTrue(root.contains("highContrast = settings?.increasedContrast == true"))
        assertTrue(root.contains("animateKireiColorsAsState("))
        assertTrue(root.contains("AppleMotion.Duration.MEDIUM"))
        assertFalse("hardcoded semantic colors moved to the tokens", root.contains("Color(0xFF78E7A0)"))
        assertFalse(root.contains("settings?.darkMode"))
        assertEquals("KireiBoxApp.kt stays the single app-palette construction site", 1, Regex("""\bKireiColors\(""").findAll(root).count())
    }

    @Test
    fun launcherWindowResolvesThemeModeAgainstTheDeviceNightState() {
        val launcher = launcherSource("app/LauncherActivity.kt")
        assertTrue(launcher.contains("AppearanceSettings.readThemeMode("))
        assertTrue(launcher.contains("ThemeModePolicy.isDark(launcherThemeMode(context), KireiThemeResolver.systemNight())"))
        assertTrue(launcher.contains("syncDefaultNightMode(newBase)"))
        assertTrue(launcher.contains("super.attachBaseContext(wrapLauncherUiMode(newBase) ?: newBase)"))
        val app = File(launcherSourceRoot(), "../yuki/yukihub/YukiHubApp.kt").readText()
        assertTrue(app.contains("KireiThemeResolver.appCompatNightMode(LauncherActivity.launcherThemeMode(this))"))
    }

    private fun launcherSourceRoot(): File =
        listOf(File("src/main/java/com/kireibox"), File("../app/src/main/java/com/kireibox")).firstOrNull { it.isDirectory }
            ?: error("kireibox source dir not found")

    private fun launcherSource(relative: String): String = File(launcherSourceRoot(), relative).readText()
}
