package com.yuki.yukihub.launcher

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.kireibox.app.LauncherActivity
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.AppearanceSettings
import com.yuki.yukihub.settings.ThemeMode
import com.yuki.yukihub.settings.ThemeModePolicy

/**
 * Engine handoff palette: the colors the launchers pass as intent extras (`primaryColor`,
 * `themeColor*`), used by the engines for the launch progress ring and the KRKR in-game dialogs.
 * It is derived from the same design tokens and the same resolved light/dark state as the app
 * (stored theme mode, SYSTEM following the device night mode), so the `darkMode` extra and the
 * colors always agree and games no longer launch with the retired purple palette.
 */
internal object KireiEngineTheme {
    /** ARGB colors for one resolved theme and accent. */
    data class Palette(
        val primary: Int,
        val onPrimary: Int,
        val background: Int,
        val card: Int,
        val text: Int,
        val textMuted: Int,
    )

    fun palette(
        dark: Boolean,
        accent: AccentChoice,
    ): Palette {
        val neutrals = KireiTokens.neutrals(dark)
        val tone = KireiTokens.accent(accent, dark)
        return Palette(
            primary = tone.accent.toArgb(),
            onPrimary = tone.onAccent.toArgb(),
            background = neutrals.background.toArgb(),
            card = neutrals.surface.toArgb(),
            text = neutrals.text.toArgb(),
            textMuted = neutrals.secondary.toArgb(),
        )
    }

    /** Same resolution as LauncherActivity.isLauncherDarkMode: SYSTEM follows [systemNight]. */
    fun palette(
        themeMode: ThemeMode,
        systemNight: Boolean,
        accent: AccentChoice,
    ): Palette = palette(ThemeModePolicy.isDark(themeMode, systemNight), accent)

    private fun palette(context: Context): Palette =
        palette(
            dark = LauncherActivity.isLauncherDarkMode(context),
            accent = AppearanceSettings.readAccentChoice(context.getSharedPreferences(LauncherActivity.APP_PREFS, Context.MODE_PRIVATE)),
        )

    fun primary(context: Context): Int = palette(context).primary

    fun onPrimary(context: Context): Int = palette(context).onPrimary

    fun background(context: Context): Int = palette(context).background

    fun card(context: Context): Int = palette(context).card

    fun text(context: Context): Int = palette(context).text

    fun textMuted(context: Context): Int = palette(context).textMuted
}
