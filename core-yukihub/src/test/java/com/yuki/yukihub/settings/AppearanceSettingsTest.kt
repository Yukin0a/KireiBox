package com.yuki.yukihub.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceSettingsTest {
    @Test
    fun `fresh install follows the system`() {
        val prefs = InMemoryPrefs()

        assertEquals(ThemeMode.SYSTEM, AppearanceSettings.readThemeMode(prefs))
        assertEquals(ThemeMode.SYSTEM, YukiSettingsStore(prefs).snapshot().themeMode)
    }

    @Test
    fun `legacy light choice migrates to LIGHT`() {
        val prefs = InMemoryPrefs(mapOf(YukiSettingsStore.KEY_DARK_MODE to false))

        assertEquals(ThemeMode.LIGHT, YukiSettingsStore(prefs).snapshot().themeMode)
    }

    @Test
    fun `legacy dark choice migrates to DARK`() {
        val prefs = InMemoryPrefs(mapOf(YukiSettingsStore.KEY_DARK_MODE to true))

        assertEquals(ThemeMode.DARK, YukiSettingsStore(prefs).snapshot().themeMode)
    }

    @Test
    fun `explicit theme key wins over the legacy boolean`() {
        val prefs =
            InMemoryPrefs(
                mapOf(
                    YukiSettingsStore.KEY_DARK_MODE to true,
                    AppearanceSettings.KEY_THEME_MODE to "light",
                ),
            )
        assertEquals(ThemeMode.LIGHT, YukiSettingsStore(prefs).snapshot().themeMode)

        val system =
            InMemoryPrefs(
                mapOf(
                    YukiSettingsStore.KEY_DARK_MODE to false,
                    AppearanceSettings.KEY_THEME_MODE to "system",
                ),
            )
        assertEquals(ThemeMode.SYSTEM, YukiSettingsStore(system).snapshot().themeMode)
    }

    @Test
    fun `resolver covers presence, legacy fallback and unknown values`() {
        assertEquals(ThemeMode.SYSTEM, ThemeModePolicy.resolveStoredThemeMode(null, legacyDarkPresent = false, legacyDark = true))
        assertEquals(ThemeMode.DARK, ThemeModePolicy.resolveStoredThemeMode(null, legacyDarkPresent = true, legacyDark = true))
        assertEquals(ThemeMode.LIGHT, ThemeModePolicy.resolveStoredThemeMode(null, legacyDarkPresent = true, legacyDark = false))
        assertEquals(ThemeMode.DARK, ThemeModePolicy.resolveStoredThemeMode("DARK", legacyDarkPresent = true, legacyDark = false))
        assertEquals(ThemeMode.SYSTEM, ThemeModePolicy.resolveStoredThemeMode(" system ", legacyDarkPresent = true, legacyDark = true))
        // An unreadable value (e.g. written by a newer build) keeps the user's explicit legacy choice.
        assertEquals(ThemeMode.LIGHT, ThemeModePolicy.resolveStoredThemeMode("amoled", legacyDarkPresent = true, legacyDark = false))
        assertEquals(ThemeMode.SYSTEM, ThemeModePolicy.resolveStoredThemeMode("", legacyDarkPresent = false, legacyDark = true))
        assertNull(ThemeMode.fromStoredOrNull("unknown"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("unknown"))
    }

    @Test
    fun `isDark follows the system only in SYSTEM mode`() {
        assertTrue(ThemeModePolicy.isDark(ThemeMode.SYSTEM, systemNight = true))
        assertFalse(ThemeModePolicy.isDark(ThemeMode.SYSTEM, systemNight = false))
        assertTrue(ThemeModePolicy.isDark(ThemeMode.DARK, systemNight = false))
        assertFalse(ThemeModePolicy.isDark(ThemeMode.LIGHT, systemNight = true))
    }

    @Test
    fun `setThemeMode round-trips and mirrors explicit modes into the legacy key`() {
        val prefs = InMemoryPrefs()
        val store = YukiSettingsStore(prefs)

        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, YukiSettingsStore(prefs).snapshot().themeMode)
        assertEquals("light", prefs.getString(AppearanceSettings.KEY_THEME_MODE, null))
        assertEquals(false, prefs.getBoolean(YukiSettingsStore.KEY_DARK_MODE, true))
        assertFalse(YukiSettingsStore(prefs).snapshot().darkMode)

        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, YukiSettingsStore(prefs).snapshot().themeMode)
        assertEquals(true, prefs.getBoolean(YukiSettingsStore.KEY_DARK_MODE, false))

        store.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(ThemeMode.SYSTEM, YukiSettingsStore(prefs).snapshot().themeMode)
        // SYSTEM leaves the last explicit legacy value for older builds untouched.
        assertEquals(true, prefs.getBoolean(YukiSettingsStore.KEY_DARK_MODE, false))
    }

    @Test
    fun `accent defaults to purple, round-trips and falls back on unknown values`() {
        val prefs = InMemoryPrefs()
        val store = YukiSettingsStore(prefs)

        // r4 (user decision): purple is the default for everyone without a stored accent.
        assertEquals(AccentChoice.CLASSIC, store.snapshot().accentChoice)
        assertEquals(AccentChoice.CLASSIC, AccentChoice.DEFAULT)

        AccentChoice.entries.forEach { choice ->
            store.setAccentChoice(choice)
            assertEquals(choice, YukiSettingsStore(prefs).snapshot().accentChoice)
            assertEquals(choice.storedValue, prefs.getString(AppearanceSettings.KEY_ACCENT_CHOICE, null))
        }

        prefs.edit().putString(AppearanceSettings.KEY_ACCENT_CHOICE, "neon").apply()
        assertEquals(AccentChoice.CLASSIC, YukiSettingsStore(prefs).snapshot().accentChoice)
        assertEquals(AccentChoice.OCEAN, AccentChoice.fromStored(" OCEAN "))
        assertEquals(AccentChoice.CLASSIC, AccentChoice.fromStored(null))
        // An explicit 樱粉 (the old default, now a stored choice) keeps working.
        prefs.edit().putString(AppearanceSettings.KEY_ACCENT_CHOICE, "sakura").apply()
        assertEquals(AccentChoice.SAKURA, YukiSettingsStore(prefs).snapshot().accentChoice)
    }

    @Test
    fun `persisted keys and labels are stable`() {
        assertEquals("launcher_theme_mode", AppearanceSettings.KEY_THEME_MODE)
        assertEquals("kireibox_accent_choice", AppearanceSettings.KEY_ACCENT_CHOICE)
        assertEquals("launcher_dark_mode", AppearanceSettings.KEY_LEGACY_DARK_MODE)
        assertEquals(listOf("system", "light", "dark"), ThemeMode.entries.map { it.storedValue })
        // The default (purple) is listed first; stored values are unchanged since alpha.17.
        assertEquals(listOf("紫色", "樱粉", "海蓝", "琥珀", "薄荷"), AccentChoice.entries.map { it.displayName })
        assertEquals(listOf("classic", "sakura", "ocean", "amber", "mint"), AccentChoice.entries.map { it.storedValue })
        assertEquals(AccentChoice.DEFAULT, AccentChoice.entries.first())
    }

    @Test
    fun `hand-built snapshots derive the theme mode from the legacy dark flag`() {
        assertEquals(ThemeMode.DARK, handBuiltSnapshot(darkMode = true).themeMode)
        assertEquals(ThemeMode.LIGHT, handBuiltSnapshot(darkMode = false).themeMode)
        assertEquals(AccentChoice.CLASSIC, handBuiltSnapshot(darkMode = true).accentChoice)
        assertFalse("a hand-built snapshot still suggests the quick setup", handBuiltSnapshot(darkMode = true).quickSetupDone)
    }

    @Test
    fun `quick setup is suggested until it is done and the flag round-trips`() {
        val prefs = InMemoryPrefs()
        val store = YukiSettingsStore(prefs)

        // Fresh installs and every upgrade (no key yet) see the suggestion once.
        assertFalse(store.snapshot().quickSetupDone)
        assertFalse(AppearanceSettings.readQuickSetupDone(prefs))

        store.setQuickSetupDone(true)
        assertTrue(YukiSettingsStore(prefs).snapshot().quickSetupDone)
        assertEquals(true, prefs.getBoolean(AppearanceSettings.KEY_QUICK_SETUP_DONE, false))

        store.setQuickSetupDone(false)
        assertFalse(YukiSettingsStore(prefs).snapshot().quickSetupDone)
    }

    @Test
    fun `quick setup flag tolerates a malformed value and keeps the other appearance keys`() {
        // A non-boolean value (e.g. from a broken backup restore) reads as "not done"; the real
        // SharedPreferences throws ClassCastException there, which readQuickSetupDone catches.
        val malformed = InMemoryPrefs(mapOf(AppearanceSettings.KEY_QUICK_SETUP_DONE to "yes"))
        assertFalse(YukiSettingsStore(malformed).snapshot().quickSetupDone)

        val prefs = InMemoryPrefs(mapOf(AppearanceSettings.KEY_ACCENT_CHOICE to "mint", AppearanceSettings.KEY_THEME_MODE to "light"))
        YukiSettingsStore(prefs).setQuickSetupDone(true)
        val snapshot = YukiSettingsStore(prefs).snapshot()
        assertEquals(AccentChoice.MINT, snapshot.accentChoice)
        assertEquals(ThemeMode.LIGHT, snapshot.themeMode)
        assertEquals("kireibox_quick_setup_done", AppearanceSettings.KEY_QUICK_SETUP_DONE)
    }

    private fun handBuiltSnapshot(darkMode: Boolean) =
        YukiSettingsStore.Snapshot(
            scanRoots = emptyList(),
            scanRootEnabled = emptyList(),
            useBuiltinFileChooser = false,
            startupScanDepth = 1,
            autoScanOnStartup = false,
            sortMode = "",
            engineLabelPosition = "",
            metadataSource = "",
            bangumiToken = "",
            krEngineVersion = "",
            krCompatMode = false,
            krSkipStartupInfoDialog = false,
            artemisScopedSaveDir = false,
            tyranoScopedSaveDir = false,
            darkMode = darkMode,
            motionMode = MotionMode.AUTO,
            reducedMotion = false,
            reducedTransparency = false,
            increasedContrast = false,
            dashboardCompact = DashboardLayouts.default(DashboardProfile.COMPACT),
            dashboardWide = DashboardLayouts.default(DashboardProfile.WIDE),
        )
}

/** Minimal in-memory [SharedPreferences] for JVM tests; removals really drop keys. */
private class InMemoryPrefs(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    private val store = initial.toMutableMap()

    override fun getAll(): Map<String, *> = store.toMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = store[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = store[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = store[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = store[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = store[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        private fun put(
            key: String?,
            value: Any?,
        ): SharedPreferences.Editor {
            if (key != null) puts[key] = value
            return this
        }

        override fun putString(
            key: String?,
            value: String?,
        ) = put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ) = put(key, values)

        override fun putInt(
            key: String?,
            value: Int,
        ) = put(key, value)

        override fun putLong(
            key: String?,
            value: Long,
        ) = put(key, value)

        override fun putFloat(
            key: String?,
            value: Float,
        ) = put(key, value)

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ) = put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            return this
        }

        override fun commit(): Boolean {
            if (clearAll) store.clear()
            removals.forEach(store::remove)
            store.putAll(puts)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
