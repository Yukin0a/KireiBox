package com.kireibox.designsystem

import android.app.UiModeManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.yuki.yukihub.settings.ThemeMode
import com.yuki.yukihub.settings.ThemeModePolicy

/**
 * Theme-mode resolution for the launcher window and the Compose root. The system night state is
 * always read from [Resources.getSystem] because the launcher activity's own configuration carries
 * the app's overridden night bits (attachBaseContext / AppCompat), not the device setting.
 */
object KireiThemeResolver {
    fun isNightUiMode(uiMode: Int): Boolean = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Device-wide night state, independent of any per-activity override. */
    fun systemNight(): Boolean = isNightUiMode(Resources.getSystem().configuration.uiMode)

    /** Returns [uiMode] with its night bits replaced by [dark]. */
    fun withNight(
        uiMode: Int,
        dark: Boolean,
    ): Int = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO

    /** Process-wide AppCompat night mode for a stored theme mode. */
    fun appCompatNightMode(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }

    /**
     * Per-app night mode for `UiModeManager.setApplicationNightMode` (API 31+). The system draws
     * the splash screen before any app code runs, from resources resolved with this mode, so a
     * forced 浅色/深色 must be handed to the system or a cold start flashes the other theme.
     */
    fun applicationNightMode(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }

    /**
     * Compose-root dark decision. Loaded settings resolve [themeMode] against the device night
     * state. Before settings load, the activity configuration is used: it was resolved from the same
     * stored mode when the activity attached, so a fresh install follows the system and an explicit
     * light/dark choice does not flash the other theme on cold start.
     */
    fun resolveDark(
        themeMode: ThemeMode?,
        systemNight: Boolean,
        activityNight: Boolean,
    ): Boolean = if (themeMode == null) activityNight else ThemeModePolicy.isDark(themeMode, systemNight)
}

/** Device night state, re-read whenever the composition's configuration changes. */
@Composable
fun rememberSystemNight(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration) { KireiThemeResolver.systemNight() }
}
