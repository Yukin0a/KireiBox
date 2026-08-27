package com.yuki.yukihub.settings

import android.content.Context
import android.content.SharedPreferences

enum class OrientationMode(val storedValue: String, val displayName: String) {
    AUTO("auto", "自动"),
    PORTRAIT("portrait", "竖屏"),
    LANDSCAPE("landscape", "横屏");

    companion object {
        fun fromStored(value: String?): OrientationMode = entries.firstOrNull {
            it.storedValue.equals(value?.trim(), ignoreCase = true) ||
                it.name.equals(value?.trim(), ignoreCase = true)
        } ?: AUTO
    }
}

/** YukiHub preference keys are the public settings contract for KireiBox. */
class YukiSettingsStore internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    data class Snapshot(
        val scanRoots: List<String>,
        val scanRootEnabled: List<Boolean>,
        val useBuiltinFileChooser: Boolean,
        val startupScanDepth: Int,
        val autoScanOnStartup: Boolean,
        val sortMode: String,
        val engineLabelPosition: String,
        val metadataSource: String,
        val bangumiToken: String,
        val krEngineVersion: String,
        val krCompatMode: Boolean,
        val krSkipStartupInfoDialog: Boolean,
        val artemisScopedSaveDir: Boolean,
        val tyranoScopedSaveDir: Boolean,
        val darkMode: Boolean,
        val motionMode: MotionMode,
        /** Compatibility mirror for callers not yet migrated to [motionMode]. */
        val reducedMotion: Boolean,
        val reducedTransparency: Boolean,
        val increasedContrast: Boolean,
        val dashboardCompact: DashboardLayout,
        val dashboardWide: DashboardLayout,
        val homePresentationMode: HomePresentationMode = HomePresentationMode.IMMERSIVE,
        val orientationMode: OrientationMode = OrientationMode.AUTO,
        /** Immersive home gallery display selection (AUTOMATIC = recent-play order). */
        val immersiveGalleryMode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
        /** Ordered manual ids shown in MANUAL mode; at most five, stable order. */
        val immersiveGalleryIds: List<Long> = emptyList(),
        /** Automatic recommendation source for the immersive gallery (default RECENT_FIVE). */
        val immersiveGalleryAutoSource: ImmersiveGalleryAutoSource = ImmersiveGalleryAutoSource.RECENT_FIVE,
        /** Canonical immersive source. FIXED is never returned for this field. */
        val immersiveGallerySource: SpotlightSource = SpotlightSource.RECENT,
        val immersiveGalleryStrategy: SpotlightStrategy = SpotlightStrategy.FIRST,
        val immersiveGalleryCount: Int = ImmersiveGallerySettings.DEFAULT_COUNT,
        val ignoredUpdateVersionCode: Int = 0,
        val updateEtag: String = "",
        val lastUpdateCheckAt: Long = 0L,
        /** Keeps user-removed cards hidden when a scan sees the same game again. */
        val keepRemovedGamesHiddenOnRefresh: Boolean = true
    ) {
        val immersiveGallerySettings: ImmersiveGallerySettings
            get() = ImmersiveGallerySettings(
                selectionMode = immersiveGalleryMode,
                manualIds = immersiveGalleryIds,
                source = immersiveGallerySource,
                strategy = immersiveGalleryStrategy,
                count = immersiveGalleryCount,
            )

        val immersiveGalleryManualIds: List<Long> get() = immersiveGalleryIds
    }

    private fun hasCanonicalImmersiveGalleryConfig(): Boolean =
        prefs.contains(KEY_IMMERSIVE_GALLERY_SOURCE) ||
            prefs.contains(KEY_IMMERSIVE_GALLERY_STRATEGY) ||
            prefs.contains(KEY_IMMERSIVE_GALLERY_COUNT)

    private fun readImmersiveGallerySettings(): ImmersiveGallerySettings {
        val legacy = ImmersiveGallerySettingsPolicy.fromLegacyAutoSource(
            prefs.getString(KEY_IMMERSIVE_GALLERY_AUTO, null),
        )
        val canonical = hasCanonicalImmersiveGalleryConfig()
        return ImmersiveGallerySettings(
            selectionMode = ImmersiveGallerySettingsPolicy.normalizeMode(
                prefs.getString(KEY_IMMERSIVE_GALLERY_MODE, null),
            ),
            manualIds = ImmersiveGallerySettingsPolicy.decodeIds(
                prefs.getString(KEY_IMMERSIVE_GALLERY_IDS, null),
            ),
            source = if (canonical) {
                ImmersiveGallerySettingsPolicy.normalizeSource(
                    prefs.getString(KEY_IMMERSIVE_GALLERY_SOURCE, null),
                )
            } else {
                legacy.source
            },
            strategy = if (canonical) {
                ImmersiveGallerySettingsPolicy.normalizeStrategy(
                    prefs.getString(KEY_IMMERSIVE_GALLERY_STRATEGY, null),
                )
            } else {
                legacy.strategy
            },
            count = if (canonical) {
                val rawCount = runCatching {
                    prefs.getString(KEY_IMMERSIVE_GALLERY_COUNT, null)
                }.getOrNull()
                ImmersiveGallerySettingsPolicy.normalizeCount(
                    rawCount ?: prefs.getInt(KEY_IMMERSIVE_GALLERY_COUNT, ImmersiveGallerySettings.DEFAULT_COUNT),
                )
            } else {
                legacy.count
            },
        ).normalized()
    }

    private fun writeCanonicalImmersiveGallerySettings(
        settings: ImmersiveGallerySettings,
        legacyOverride: ImmersiveGalleryAutoSource? = null,
    ) {
        val normalized = settings.normalized()
        prefs.edit()
            .putString(KEY_IMMERSIVE_GALLERY_SOURCE, normalized.source.name)
            .putString(KEY_IMMERSIVE_GALLERY_STRATEGY, normalized.strategy.name)
            .putInt(KEY_IMMERSIVE_GALLERY_COUNT, normalized.count)
            .putString(
                KEY_IMMERSIVE_GALLERY_AUTO,
                (legacyOverride ?: ImmersiveGallerySettingsPolicy.toLegacyAutoSource(normalized)).name,
            )
            .apply()
    }

    fun snapshot(): Snapshot {
        val legacyReduced = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        val motionMode = if (prefs.contains(KEY_MOTION_MODE)) {
            MotionMode.fromStored(prefs.getString(KEY_MOTION_MODE, MotionMode.AUTO.name))
        } else if (legacyReduced) {
            MotionMode.REDUCED
        } else {
            MotionMode.AUTO
        }
        val immersiveGallery = readImmersiveGallerySettings()
        val orientationMode = OrientationMode.fromStored(
            prefs.getString(KEY_ORIENTATION_MODE, OrientationMode.AUTO.storedValue),
        )
        val legacyAutoSource = prefs.getString(KEY_IMMERSIVE_GALLERY_AUTO, null)
            ?.let(ImmersiveGallerySettingsPolicy::normalizeAutoSource)
            ?: ImmersiveGallerySettingsPolicy.toLegacyAutoSource(immersiveGallery)
        return Snapshot(
        scanRoots = scanRoots(),
        scanRootEnabled = scanRootEnabled(),
        useBuiltinFileChooser = prefs.getBoolean(KEY_USE_BUILTIN_FILE_CHOOSER, true),
        startupScanDepth = prefs.getInt(KEY_STARTUP_SCAN_DEPTH, 2).coerceIn(1, 4),
        autoScanOnStartup = prefs.getBoolean(KEY_AUTO_SCAN_ON_STARTUP, true),
        sortMode = prefs.getString(KEY_SORT_MODE, SORT_RECENT) ?: SORT_RECENT,
        engineLabelPosition = prefs.getString(KEY_ENGINE_LABEL_POSITION, "title") ?: "title",
        metadataSource = normalizeMetadataSource(prefs.getString(KEY_METADATA_SOURCE, SOURCE_VNDB)),
        bangumiToken = prefs.getString(KEY_BANGUMI_TOKEN, "") ?: "",
        krEngineVersion = prefs.getString(KEY_KR_ENGINE_VERSION, "auto") ?: "auto",
        krCompatMode = prefs.getBoolean(KEY_KR_COMPAT_MODE, false),
        krSkipStartupInfoDialog = prefs.getString(KEY_KR_STARTUP_DIALOG_POLICY, DIALOG_POLICY_SHOW_ALL) ==
            DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO,
        artemisScopedSaveDir = prefs.getBoolean(KEY_ARTEMIS_SCOPED_SAVE_DIR, true),
        tyranoScopedSaveDir = prefs.getBoolean(KEY_TYRANO_SCOPED_SAVE_DIR, true),
        darkMode = prefs.getBoolean(KEY_DARK_MODE, true),
        motionMode = motionMode,
        reducedMotion = motionMode == MotionMode.REDUCED,
        reducedTransparency = prefs.getBoolean(KEY_REDUCED_TRANSPARENCY, false),
        increasedContrast = prefs.getBoolean(KEY_INCREASED_CONTRAST, false),
        dashboardCompact = DashboardLayouts.decode(
            prefs.getString(KEY_DASHBOARD_COMPACT, null), DashboardProfile.COMPACT
        ),
            dashboardWide = DashboardLayouts.decode(
            prefs.getString(KEY_DASHBOARD_WIDE, null), DashboardProfile.WIDE
        ),
            homePresentationMode = resolveHomePresentationMode(),
            orientationMode = orientationMode,
            immersiveGalleryMode = ImmersiveGallerySettingsPolicy.normalizeMode(
                prefs.getString(KEY_IMMERSIVE_GALLERY_MODE, null),
            ),
            immersiveGalleryIds = ImmersiveGallerySettingsPolicy.decodeIds(
                prefs.getString(KEY_IMMERSIVE_GALLERY_IDS, null),
            ),
            immersiveGalleryAutoSource = legacyAutoSource,
            immersiveGallerySource = immersiveGallery.source,
            immersiveGalleryStrategy = immersiveGallery.strategy,
            immersiveGalleryCount = immersiveGallery.count,
            ignoredUpdateVersionCode = prefs.getInt(KEY_IGNORED_UPDATE_VERSION_CODE, 0).coerceAtLeast(0),
            updateEtag = prefs.getString(KEY_UPDATE_ETAG, "").orEmpty(),
            lastUpdateCheckAt = prefs.getLong(KEY_LAST_UPDATE_CHECK_AT, 0L).coerceAtLeast(0L),
            keepRemovedGamesHiddenOnRefresh = prefs.getBoolean(
                KEY_KEEP_REMOVED_GAMES_HIDDEN_ON_REFRESH,
                true,
            )
        )
    }

    fun scanRoots(): List<String> = prefs.getString(KEY_SCAN_ROOT_URIS, "")
        .orEmpty().lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

    fun scanRootEnabled(): List<Boolean> = prefs.getString(KEY_SCAN_ROOT_ENABLED, "")
        .orEmpty().split(',').filter(String::isNotBlank).map { it == "1" }

    fun replaceScanRoots(roots: List<String>, enabled: List<Boolean> = emptyList()) {
        val clean = roots.map(String::trim).filter(String::isNotEmpty).distinct()
        val states = clean.indices.map { enabled.getOrNull(it) ?: true }
        prefs.edit()
            .putString(KEY_SCAN_ROOT_URIS, clean.joinToString("\n"))
            .putString(KEY_SCAN_ROOT_ENABLED, states.joinToString(",") { if (it) "1" else "0" })
            .apply {
                if (clean.isEmpty()) remove(KEY_LAST_SCAN_ROOT_URI)
                else putString(KEY_LAST_SCAN_ROOT_URI, clean.first())
            }
            .apply()
    }

    fun addScanRoot(uri: String) {
        if (uri.isBlank()) return
        val roots = scanRoots().toMutableList()
        val states = scanRootEnabled().toMutableList()
        val index = roots.indexOf(uri)
        if (index >= 0) {
            while (states.size <= index) states += true
            states[index] = true
        } else {
            roots += uri
            states += true
        }
        replaceScanRoots(roots, states)
    }

    fun putBoolean(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun isCoverQueuePaused(): Boolean = prefs.getBoolean(KEY_COVER_QUEUE_PAUSED, false)
    fun setCoverQueuePaused(paused: Boolean) { prefs.edit().putBoolean(KEY_COVER_QUEUE_PAUSED, paused).apply() }
    fun setMotionMode(mode: MotionMode) {
        prefs.edit()
            .putString(KEY_MOTION_MODE, mode.name)
            .putBoolean(KEY_REDUCED_MOTION, mode == MotionMode.REDUCED)
            .apply()
    }
    fun setHomePresentationMode(mode: HomePresentationMode) {
        prefs.edit().putString(KEY_HOME_PRESENTATION_MODE, mode.storedValue).apply()
    }
    fun setOrientationMode(mode: OrientationMode) {
        prefs.edit().putString(KEY_ORIENTATION_MODE, mode.storedValue).apply()
    }
    fun setIgnoredUpdateVersionCode(versionCode: Int) {
        prefs.edit().putInt(KEY_IGNORED_UPDATE_VERSION_CODE, versionCode.coerceAtLeast(0)).apply()
    }
    fun setUpdateEtag(etag: String?) {
        prefs.edit().putString(KEY_UPDATE_ETAG, etag.orEmpty()).apply()
    }
    fun setLastUpdateCheckAt(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_AT, timestampMillis.coerceAtLeast(0L)).apply()
    }
    fun setImmersiveGalleryMode(mode: SpotlightSelectionMode) {
        prefs.edit().putString(KEY_IMMERSIVE_GALLERY_MODE, mode.name).apply()
    }
    fun setImmersiveGalleryIds(ids: List<Long>) {
        prefs.edit().putString(
            KEY_IMMERSIVE_GALLERY_IDS,
            ImmersiveGallerySettingsPolicy.encodeIds(ids),
        ).apply()
    }
    fun setImmersiveGalleryIds(ids: List<Long>, validGameIds: Set<Long>) {
        prefs.edit().putString(
            KEY_IMMERSIVE_GALLERY_IDS,
            ImmersiveGallerySettingsPolicy.encodeIds(ids, validGameIds),
        ).apply()
    }
    fun setImmersiveGalleryAutoSource(source: ImmersiveGalleryAutoSource) {
        writeCanonicalImmersiveGallerySettings(
            ImmersiveGallerySettingsPolicy.fromLegacyAutoSource(source),
            legacyOverride = source,
        )
    }
    fun setImmersiveGallerySource(source: SpotlightSource) {
        writeCanonicalImmersiveGallerySettings(
            readImmersiveGallerySettings().copy(source = source),
        )
    }
    fun setImmersiveGalleryStrategy(strategy: SpotlightStrategy) {
        writeCanonicalImmersiveGallerySettings(
            readImmersiveGallerySettings().copy(strategy = strategy),
        )
    }
    fun setImmersiveGalleryCount(count: Int) {
        writeCanonicalImmersiveGallerySettings(
            readImmersiveGallerySettings().copy(count = count),
        )
    }
    fun setImmersiveGallerySettings(settings: ImmersiveGallerySettings) {
        val normalized = settings.normalized()
        prefs.edit()
            .putString(KEY_IMMERSIVE_GALLERY_MODE, normalized.selectionMode.name)
            .putString(KEY_IMMERSIVE_GALLERY_IDS, ImmersiveGallerySettingsPolicy.encodeIds(normalized.manualIds))
            .putString(KEY_IMMERSIVE_GALLERY_SOURCE, normalized.source.name)
            .putString(KEY_IMMERSIVE_GALLERY_STRATEGY, normalized.strategy.name)
            .putInt(KEY_IMMERSIVE_GALLERY_COUNT, normalized.count)
            .putString(KEY_IMMERSIVE_GALLERY_AUTO, ImmersiveGallerySettingsPolicy.toLegacyAutoSource(normalized).name)
            .apply()
    }
    fun setImmersiveGallerySelectionMode(mode: SpotlightSelectionMode) =
        setImmersiveGalleryMode(mode)

    fun setImmersiveGalleryManualIds(ids: List<Long>) = setImmersiveGalleryIds(ids)

    /** Drops ids that no longer exist in the current library and persists the stable result. */
    fun normalizeImmersiveGalleryIds(validGameIds: Set<Long>): List<Long> {
        val current = ImmersiveGallerySettingsPolicy.decodeIds(
            prefs.getString(KEY_IMMERSIVE_GALLERY_IDS, null),
        )
        val normalized = ImmersiveGallerySettingsPolicy.normalizeIds(current, validGameIds)
        if (normalized != current) setImmersiveGalleryIds(normalized)
        return normalized
    }

    /** Materializes legacy immersive values into canonical fields without touching dashboard drafts. */
    fun migrateImmersiveGallerySettingsIfNeeded() {
        val normalized = readImmersiveGallerySettings()
        writeCanonicalImmersiveGallerySettings(normalized)
        val editor = prefs.edit()
        if (prefs.contains(KEY_IMMERSIVE_GALLERY_MODE)) {
            editor.putString(KEY_IMMERSIVE_GALLERY_MODE, normalized.selectionMode.name)
        }
        if (prefs.contains(KEY_IMMERSIVE_GALLERY_IDS)) {
            editor.putString(KEY_IMMERSIVE_GALLERY_IDS, ImmersiveGallerySettingsPolicy.encodeIds(normalized.manualIds))
        }
        editor.apply()
    }

    fun migrateImmersiveGallerySettings() = migrateImmersiveGallerySettingsIfNeeded()

    /**
     * Resolves the home presentation mode with new-key precedence.
     *
     * If [KEY_HOME_PRESENTATION_MODE] exists, its value is authoritative: blank and illegal
     * values resolve to [HomePresentationMode.IMMERSIVE] without consulting the legacy key.
     * The legacy landscape-style key is only consulted when the new key is completely absent,
     * where "components" maps to [HomePresentationMode.COMPONENTS] and anything else
     * (including legacy C/D styles) maps to [HomePresentationMode.IMMERSIVE].
     */
    fun resolveHomePresentationMode(): HomePresentationMode {
        if (prefs.contains(KEY_HOME_PRESENTATION_MODE)) {
            return HomePresentationMode.fromStored(prefs.getString(KEY_HOME_PRESENTATION_MODE, null))
        }
        return HomePresentationMode.fromStored(prefs.getString(LEGACY_LANDSCAPE_HOME_STYLE, null))
    }

    /** Canonicalizes the home-presentation key from the legacy landscape-style key when needed. */
    fun migrateHomePresentationModeIfNeeded() {
        val stored = prefs.getString(KEY_HOME_PRESENTATION_MODE, null)
        val canonical = resolveHomePresentationMode().storedValue
        if (stored == null || stored.trim() != canonical) {
            prefs.edit().putString(KEY_HOME_PRESENTATION_MODE, canonical).apply()
        }
    }
    fun saveDashboardLayout(profile: DashboardProfile, layout: DashboardLayout) {
        val key = if (profile == DashboardProfile.COMPACT) KEY_DASHBOARD_COMPACT else KEY_DASHBOARD_WIDE
        prefs.edit().putString(key, DashboardLayouts.encode(layout, profile)).apply()
    }
    fun rawPreferences(): SharedPreferences = prefs

    companion object {
        const val PREFS_NAME = "yukihub_prefs"
        const val KEY_LAST_SCAN_ROOT_URI = "last_scan_root_uri"
        const val KEY_SCAN_ROOT_URIS = "scan_root_uris"
        const val KEY_SCAN_ROOT_ENABLED = "scan_root_enabled"
        const val KEY_USE_BUILTIN_FILE_CHOOSER = "use_builtin_file_chooser"
        const val KEY_STARTUP_SCAN_DEPTH = "startup_scan_depth"
        const val KEY_AUTO_SCAN_ON_STARTUP = "auto_scan_on_startup"
        const val KEY_KEEP_REMOVED_GAMES_HIDDEN_ON_REFRESH = "keep_removed_games_hidden_on_refresh"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_ENGINE_LABEL_POSITION = "engine_label_position"
        const val KEY_METADATA_SOURCE = "metadata_source"
        const val KEY_BANGUMI_TOKEN = "bangumi_token"
        const val KEY_KR_ENGINE_VERSION = "kr_engine_version"
        const val KEY_KR_STARTUP_DIALOG_POLICY = "kr_startup_dialog_policy"
        const val KEY_KR_COMPAT_MODE = "kr_compat_mode"
        const val KEY_ARTEMIS_SCOPED_SAVE_DIR = "artemis_scoped_save_dir"
        const val KEY_TYRANO_SCOPED_SAVE_DIR = "tyrano_scoped_save_dir"
        const val KEY_DARK_MODE = "launcher_dark_mode"
        const val KEY_MOTION_MODE = "kireibox_motion_mode"
        const val KEY_REDUCED_MOTION = "kireibox_reduced_motion"
        const val KEY_REDUCED_TRANSPARENCY = "kireibox_reduced_transparency"
        const val KEY_INCREASED_CONTRAST = "kireibox_increased_contrast"
        private const val LEGACY_LANDSCAPE_HOME_STYLE = "kireibox_landscape_home_style"
        const val KEY_DASHBOARD_COMPACT = "kireibox_dashboard_compact_v1"
        const val KEY_DASHBOARD_WIDE = "kireibox_dashboard_wide_v1"
        const val KEY_COVER_QUEUE_PAUSED = "kireibox_cover_queue_paused"
        const val KEY_HOME_PRESENTATION_MODE = "kireibox_home_presentation_mode"
        const val KEY_ORIENTATION_MODE = "kireibox_orientation_mode"
        const val KEY_IGNORED_UPDATE_VERSION_CODE = "kireibox_ignored_update_version_code"
        const val KEY_UPDATE_ETAG = "kireibox_update_etag"
        const val KEY_LAST_UPDATE_CHECK_AT = "kireibox_last_update_check_at"
        const val KEY_IMMERSIVE_GALLERY_MODE = "kireibox_immersive_gallery_mode"
        const val KEY_IMMERSIVE_GALLERY_IDS = "kireibox_immersive_gallery_ids"
        const val KEY_IMMERSIVE_GALLERY_AUTO = "kireibox_immersive_gallery_auto"
        const val KEY_IMMERSIVE_GALLERY_SOURCE = "kireibox_immersive_gallery_source"
        const val KEY_IMMERSIVE_GALLERY_STRATEGY = "kireibox_immersive_gallery_strategy"
        const val KEY_IMMERSIVE_GALLERY_COUNT = "kireibox_immersive_gallery_count"
        const val KEY_IMMERSIVE_GALLERY_SELECTION_MODE = KEY_IMMERSIVE_GALLERY_MODE
        const val KEY_IMMERSIVE_GALLERY_MANUAL_IDS = KEY_IMMERSIVE_GALLERY_IDS
        const val KEY_IMMERSIVE_GALLERY_MAX_COUNT = KEY_IMMERSIVE_GALLERY_COUNT
        const val KEY_MIGRATION_V18 = "single_core_migration_v18"

        const val SORT_RECENT = "recent"
        const val SORT_NEWEST = "newest"
        const val SORT_NAME = "name"
        const val SOURCE_VNDB = "vndb"
        const val SOURCE_BANGUMI = "bangumi"
        const val SOURCE_BANGUMI_MIRROR = "bangumi_mirror"
        const val SOURCE_YMGAL = "ymgal"
        const val SOURCE_HIKARINAGI = "hikarinagi"
        const val DIALOG_POLICY_SHOW_ALL = "SHOW_ALL"
        const val DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO = "SKIP_SINGLE_BUTTON_STARTUP_INFO"

        fun normalizeMetadataSource(value: String?): String = when (value?.trim()?.lowercase()) {
            SOURCE_BANGUMI -> SOURCE_BANGUMI
            SOURCE_BANGUMI_MIRROR -> SOURCE_BANGUMI_MIRROR
            SOURCE_YMGAL -> SOURCE_YMGAL
            SOURCE_HIKARINAGI -> SOURCE_HIKARINAGI
            else -> SOURCE_VNDB
        }
    }
}
