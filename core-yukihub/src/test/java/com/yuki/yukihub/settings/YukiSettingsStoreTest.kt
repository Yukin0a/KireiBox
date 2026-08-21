package com.yuki.yukihub.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class YukiSettingsStoreTest {
    // --- In-memory fake SharedPreferences for JVM round-trip tests ---

    private fun fakePrefs(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        return object : SharedPreferences {
            override fun getAll(): Map<String, *> = store.toMap()
            override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue
            @Suppress("UNCHECKED_CAST")
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
                store[key] as? MutableSet<String> ?: defValues
            override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue
            override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue
            override fun contains(key: String?): Boolean = store.containsKey(key)
            override fun edit(): SharedPreferences.Editor {
                val edits = mutableMapOf<String, Any?>()
                return object : SharedPreferences.Editor {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        if (key != null) edits[key] = value; return this
                    }
                    override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                        if (key != null) edits[key] = values; return this
                    }
                    override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                        if (key != null) edits[key] = value; return this
                    }
                    override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                        if (key != null) edits[key] = value; return this
                    }
                    override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                        if (key != null) edits[key] = value; return this
                    }
                    override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                        if (key != null) edits[key] = value; return this
                    }
                    override fun remove(key: String?): SharedPreferences.Editor {
                        if (key != null) edits[key] = null; return this
                    }
                    override fun clear(): SharedPreferences.Editor { edits.clear(); return this }
                    override fun commit(): Boolean { store.putAll(edits); return true }
                    override fun apply() { store.putAll(edits) }
                }
            }
            override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
    }

    // --- Snapshot data class default-value tests ---

    @Test
    fun `snapshot homePresentationMode defaults to immersive`() {
        val snap = YukiSettingsStore.Snapshot(
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
            darkMode = false,
            motionMode = MotionMode.AUTO,
            reducedMotion = false,
            reducedTransparency = false,
            increasedContrast = false,
            dashboardCompact = DashboardLayouts.default(DashboardProfile.COMPACT),
            dashboardWide = DashboardLayouts.default(DashboardProfile.WIDE),
        )
        assertEquals(HomePresentationMode.IMMERSIVE, snap.homePresentationMode)
    }

    @Test
    fun `snapshot explicit homePresentationMode is stored`() {
        val snap = YukiSettingsStore.Snapshot(
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
            darkMode = false,
            motionMode = MotionMode.AUTO,
            reducedMotion = false,
            reducedTransparency = false,
            increasedContrast = false,
            dashboardCompact = DashboardLayouts.default(DashboardProfile.COMPACT),
            dashboardWide = DashboardLayouts.default(DashboardProfile.WIDE),
            homePresentationMode = HomePresentationMode.COMPONENTS,
        )
        assertEquals(HomePresentationMode.COMPONENTS, snap.homePresentationMode)
    }

    // --- KEY constant tests ---

    @Test
    fun `settings key is the persisted contract`() {
        assertEquals("kireibox_home_presentation_mode", YukiSettingsStore.KEY_HOME_PRESENTATION_MODE)
        assertEquals("kireibox_immersive_gallery_auto", YukiSettingsStore.KEY_IMMERSIVE_GALLERY_AUTO)
    }

    @Test
    fun `immersive gallery auto source defaults to recent five and round-trips`() {
        val prefs = fakePrefs()
        val store = YukiSettingsStore(prefs)

        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, store.snapshot().immersiveGalleryAutoSource)

        store.setImmersiveGalleryAutoSource(ImmersiveGalleryAutoSource.FAVORITES)
        assertEquals(ImmersiveGalleryAutoSource.FAVORITES, store.snapshot().immersiveGalleryAutoSource)
        assertEquals(
            "FAVORITES",
            prefs.getString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_AUTO, null),
        )

        store.setImmersiveGalleryAutoSource(ImmersiveGalleryAutoSource.RECENT_ALL)
        assertEquals(ImmersiveGalleryAutoSource.RECENT_ALL, store.snapshot().immersiveGalleryAutoSource)

        prefs.edit().putString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_AUTO, "garbage").apply()
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, store.snapshot().immersiveGalleryAutoSource)
    }

    // --- Real round-trip: setHomePresentationMode -> prefs -> snapshot ---

    @Test
    fun `setHomePresentationMode round-trip via fake prefs`() {
        val prefs = fakePrefs()
        val store = YukiSettingsStore(prefs)

        // Default: no key set -> IMMERSIVE
        val snap1 = store.snapshot()
        assertEquals(HomePresentationMode.IMMERSIVE, snap1.homePresentationMode)

        // Set to COMPONENTS
        store.setHomePresentationMode(HomePresentationMode.COMPONENTS)
        val snap2 = store.snapshot()
        assertEquals(HomePresentationMode.COMPONENTS, snap2.homePresentationMode)

        // Set back to IMMERSIVE
        store.setHomePresentationMode(HomePresentationMode.IMMERSIVE)
        val snap3 = store.snapshot()
        assertEquals(HomePresentationMode.IMMERSIVE, snap3.homePresentationMode)
    }

    @Test
    fun `setHomePresentationMode persists across snapshot calls`() {
        val prefs = fakePrefs()
        val store = YukiSettingsStore(prefs)

        store.setHomePresentationMode(HomePresentationMode.COMPONENTS)

        // Multiple snapshot calls should all return COMPONENTS
        repeat(3) {
            assertEquals(HomePresentationMode.COMPONENTS, store.snapshot().homePresentationMode)
        }

        // Change mid-stream
        store.setHomePresentationMode(HomePresentationMode.IMMERSIVE)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
    }

    // --- R1 correction: legacy key migration ---

    @Test
    fun `legacy immersive_cover alone migrates to immersive`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_landscape_home_style", "immersive_cover").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("immersive", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `legacy focus_poster alone migrates to immersive`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_landscape_home_style", "focus_poster").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("immersive", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `missing or unknown legacy style defaults to immersive`() {
        val prefs = fakePrefs()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("immersive", prefs.getString("kireibox_home_presentation_mode", null))

        val prefs2 = fakePrefs()
        prefs2.edit().putString("kireibox_landscape_home_style", "something_else").apply()
        val store2 = YukiSettingsStore(prefs2)
        assertEquals(HomePresentationMode.IMMERSIVE, store2.snapshot().homePresentationMode)
    }

    @Test
    fun `valid new components wins over legacy style`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_home_presentation_mode", "components").apply()
        prefs.edit().putString("kireibox_landscape_home_style", "immersive_cover").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.COMPONENTS, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("components", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `blank new key resolves immersive without consulting legacy`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_home_presentation_mode", "  ").apply()
        prefs.edit().putString("kireibox_landscape_home_style", "components").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("immersive", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `illegal new key resolves immersive without consulting legacy`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_home_presentation_mode", "banana").apply()
        prefs.edit().putString("kireibox_landscape_home_style", "components").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.IMMERSIVE, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("immersive", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `legacy components migrates to components when new key is absent`() {
        val prefs = fakePrefs()
        prefs.edit().putString("kireibox_landscape_home_style", "components").apply()
        val store = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.COMPONENTS, store.snapshot().homePresentationMode)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("components", prefs.getString("kireibox_home_presentation_mode", null))
    }

    @Test
    fun `migration preserves dashboard layouts`() {
        val prefs = fakePrefs()
        // Opaque payloads: core JVM tests use the Android org.json stub, so avoid JSON round-trips here.
        prefs.edit().putString(YukiSettingsStore.KEY_DASHBOARD_COMPACT, "compact-payload").apply()
        prefs.edit().putString(YukiSettingsStore.KEY_DASHBOARD_WIDE, "wide-payload").apply()
        prefs.edit().putString("kireibox_landscape_home_style", "immersive_cover").apply()
        val store = YukiSettingsStore(prefs)
        store.migrateHomePresentationModeIfNeeded()
        assertEquals("compact-payload", prefs.getString(YukiSettingsStore.KEY_DASHBOARD_COMPACT, null))
        assertEquals("wide-payload", prefs.getString(YukiSettingsStore.KEY_DASHBOARD_WIDE, null))
        assertEquals(DashboardLayouts.default(DashboardProfile.COMPACT), store.snapshot().dashboardCompact)
        assertEquals(DashboardLayouts.default(DashboardProfile.WIDE), store.snapshot().dashboardWide)
    }

    @Test
    fun `setHomePresentationMode survives store recreation`() {
        val prefs = fakePrefs()
        YukiSettingsStore(prefs).setHomePresentationMode(HomePresentationMode.COMPONENTS)
        val recreated = YukiSettingsStore(prefs)
        assertEquals(HomePresentationMode.COMPONENTS, recreated.snapshot().homePresentationMode)
    }

    // --- Immersive gallery selection persistence ---

    @Test
    fun `snapshot immersive gallery defaults are backward compatible`() {
        val store = YukiSettingsStore(fakePrefs())
        val snap = store.snapshot()
        assertEquals(SpotlightSelectionMode.AUTOMATIC, snap.immersiveGalleryMode)
        assertEquals(emptyList<Long>(), snap.immersiveGalleryIds)
    }

    @Test
    fun `immersive gallery keys are the persisted contract`() {
        assertEquals("kireibox_immersive_gallery_mode", YukiSettingsStore.KEY_IMMERSIVE_GALLERY_MODE)
        assertEquals("kireibox_immersive_gallery_ids", YukiSettingsStore.KEY_IMMERSIVE_GALLERY_IDS)
    }

    @Test
    fun `immersive gallery selection survives store recreation`() {
        val prefs = fakePrefs()
        YukiSettingsStore(prefs).setImmersiveGalleryMode(SpotlightSelectionMode.MANUAL)
        YukiSettingsStore(prefs).setImmersiveGalleryIds(listOf(30L, 10L, 20L))
        val recreated = YukiSettingsStore(prefs)
        assertEquals(SpotlightSelectionMode.MANUAL, recreated.snapshot().immersiveGalleryMode)
        assertEquals(listOf(30L, 10L, 20L), recreated.snapshot().immersiveGalleryIds)
    }

    @Test
    fun `immersive gallery write normalizes invalid duplicates and cap`() {
        val store = YukiSettingsStore(fakePrefs())
        store.setImmersiveGalleryIds(listOf(0L, -3L, 7L, 7L, 1L, 2L, 3L, 4L, 5L, 6L))
        assertEquals(listOf(7L, 1L, 2L, 3L, 4L), store.snapshot().immersiveGalleryIds)
    }

    @Test
    fun `illegal stored immersive gallery values resolve to defaults`() {
        val prefs = fakePrefs()
        prefs.edit().putString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_MODE, "garbage").apply()
        prefs.edit().putString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_IDS, "x,,0,-1").apply()
        val snap = YukiSettingsStore(prefs).snapshot()
        assertEquals(SpotlightSelectionMode.AUTOMATIC, snap.immersiveGalleryMode)
        assertEquals(emptyList<Long>(), snap.immersiveGalleryIds)
    }

    @Test
    fun `legacy gallery settings migrate and canonical setters survive recreation`() {
        val prefs = fakePrefs()
        prefs.edit()
            .putString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_AUTO, "RECENT_ALL")
            .putString(YukiSettingsStore.KEY_IMMERSIVE_GALLERY_IDS, "9,2,9,8,7,6,5")
            .apply()

        YukiSettingsStore(prefs).migrateImmersiveGallerySettingsIfNeeded()
        val migrated = YukiSettingsStore(prefs).snapshot()
        assertEquals(SpotlightSource.RECENT, migrated.immersiveGallerySource)
        assertEquals(SpotlightStrategy.FIRST, migrated.immersiveGalleryStrategy)
        assertEquals(5, migrated.immersiveGalleryCount)
        assertEquals(listOf(9L, 2L, 8L, 7L, 6L), migrated.immersiveGalleryIds)

        YukiSettingsStore(prefs).setImmersiveGallerySource(SpotlightSource.ALL)
        YukiSettingsStore(prefs).setImmersiveGalleryStrategy(SpotlightStrategy.DAILY_RANDOM)
        YukiSettingsStore(prefs).setImmersiveGalleryCount(2)
        val recreated = YukiSettingsStore(prefs).snapshot()
        assertEquals(SpotlightSource.ALL, recreated.immersiveGallerySource)
        assertEquals(SpotlightStrategy.DAILY_RANDOM, recreated.immersiveGalleryStrategy)
        assertEquals(2, recreated.immersiveGalleryCount)
        assertEquals(ImmersiveGalleryAutoSource.RECENT_ALL, recreated.immersiveGalleryAutoSource)
    }

    @Test
    fun `update metadata defaults are backward compatible`() {
        val snapshot = YukiSettingsStore(fakePrefs()).snapshot()

        assertEquals(0, snapshot.ignoredUpdateVersionCode)
        assertEquals("", snapshot.updateEtag)
        assertEquals(0L, snapshot.lastUpdateCheckAt)
    }

    @Test
    fun `update metadata survives store recreation`() {
        val prefs = fakePrefs()
        val store = YukiSettingsStore(prefs)
        store.setIgnoredUpdateVersionCode(20)
        store.setUpdateEtag("release-etag")
        store.setLastUpdateCheckAt(123456789L)

        val recreated = YukiSettingsStore(prefs).snapshot()

        assertEquals(20, recreated.ignoredUpdateVersionCode)
        assertEquals("release-etag", recreated.updateEtag)
        assertEquals(123456789L, recreated.lastUpdateCheckAt)
    }
}
