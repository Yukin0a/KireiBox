package com.yuki.yukihub.settings

import android.content.SharedPreferences

/** App-wide light/dark preference. [SYSTEM] follows the device night mode. */
enum class ThemeMode(val storedValue: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        /** Parses a persisted value; unknown or blank values return null so callers can fall back. */
        fun fromStoredOrNull(value: String?): ThemeMode? {
            val normalized = value?.trim()
            if (normalized.isNullOrEmpty()) return null
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true)
            }
        }

        fun fromStored(value: String?): ThemeMode = fromStoredOrNull(value) ?: SYSTEM
    }
}

/**
 * User-selectable accent color. The concrete color values live in the app design tokens.
 *
 * Purple ([CLASSIC], stored as "classic" since alpha.17) is the default and therefore listed first;
 * the stored values never change, so an explicit choice (including an explicit 樱粉) is kept and
 * only users without a stored accent follow the default.
 */
enum class AccentChoice(val storedValue: String, val displayName: String) {
    CLASSIC("classic", "紫色"),
    SAKURA("sakura", "樱粉"),
    OCEAN("ocean", "海蓝"),
    AMBER("amber", "琥珀"),
    MINT("mint", "薄荷"),
    ;

    companion object {
        val DEFAULT: AccentChoice = CLASSIC

        fun fromStored(value: String?): AccentChoice {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true)
            } ?: DEFAULT
        }
    }
}

/** Pure theme-mode rules shared by the settings store, the launcher window and the Compose root. */
object ThemeModePolicy {
    /**
     * Resolves the persisted theme mode.
     *
     * A parseable [storedTheme] is authoritative. Otherwise an explicit legacy dark-mode boolean
     * (written by every build before the theme mode existed) is preserved as DARK/LIGHT so existing
     * users keep their choice. A fresh install has neither and follows the system.
     */
    fun resolveStoredThemeMode(
        storedTheme: String?,
        legacyDarkPresent: Boolean,
        legacyDark: Boolean,
    ): ThemeMode =
        ThemeMode.fromStoredOrNull(storedTheme)
            ?: when {
                !legacyDarkPresent -> ThemeMode.SYSTEM
                legacyDark -> ThemeMode.DARK
                else -> ThemeMode.LIGHT
            }

    fun isDark(
        mode: ThemeMode,
        systemNight: Boolean,
    ): Boolean =
        when (mode) {
            ThemeMode.SYSTEM -> systemNight
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    /** Legacy boolean mirrored for explicit modes; SYSTEM leaves the legacy key untouched. */
    fun legacyDarkFor(mode: ThemeMode): Boolean? =
        when (mode) {
            ThemeMode.SYSTEM -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
}

/**
 * Persistence for appearance preferences. Kept out of [YukiSettingsStore] so the store file stays
 * within the size budget; the store delegates to these helpers. Static helpers take the raw
 * [SharedPreferences] so the launcher can resolve the theme before any store exists.
 */
object AppearanceSettings {
    const val KEY_THEME_MODE = "launcher_theme_mode"
    const val KEY_ACCENT_CHOICE = "kireibox_accent_choice"

    /**
     * Set once the 快速设置 guide has been finished, skipped or its suggestion dismissed: the settings
     * root then stops suggesting it (the guide stays reachable from the overview card).
     */
    const val KEY_QUICK_SETUP_DONE = "kireibox_quick_setup_done"

    /** Legacy boolean ("launcher_dark_mode"); still mirrored for explicit modes. */
    const val KEY_LEGACY_DARK_MODE = YukiSettingsStore.KEY_DARK_MODE

    fun readThemeMode(prefs: SharedPreferences): ThemeMode =
        ThemeModePolicy.resolveStoredThemeMode(
            storedTheme = runCatching { prefs.getString(KEY_THEME_MODE, null) }.getOrNull(),
            legacyDarkPresent = prefs.contains(KEY_LEGACY_DARK_MODE),
            legacyDark = runCatching { prefs.getBoolean(KEY_LEGACY_DARK_MODE, true) }.getOrDefault(true),
        )

    fun readAccentChoice(prefs: SharedPreferences): AccentChoice = AccentChoice.fromStored(runCatching { prefs.getString(KEY_ACCENT_CHOICE, null) }.getOrNull())

    fun readQuickSetupDone(prefs: SharedPreferences): Boolean = runCatching { prefs.getBoolean(KEY_QUICK_SETUP_DONE, false) }.getOrDefault(false)

    fun writeQuickSetupDone(
        prefs: SharedPreferences,
        done: Boolean,
    ) {
        prefs.edit().putBoolean(KEY_QUICK_SETUP_DONE, done).apply()
    }

    fun writeThemeMode(
        prefs: SharedPreferences,
        mode: ThemeMode,
    ) {
        val editor = prefs.edit().putString(KEY_THEME_MODE, mode.storedValue)
        ThemeModePolicy.legacyDarkFor(mode)?.let { editor.putBoolean(KEY_LEGACY_DARK_MODE, it) }
        editor.apply()
    }

    fun writeAccentChoice(
        prefs: SharedPreferences,
        choice: AccentChoice,
    ) {
        prefs.edit().putString(KEY_ACCENT_CHOICE, choice.storedValue).apply()
    }
}
