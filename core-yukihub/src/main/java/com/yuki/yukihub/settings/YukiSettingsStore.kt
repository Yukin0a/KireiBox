package com.yuki.yukihub.settings

import android.content.Context
import android.content.SharedPreferences

/** YukiHub preference keys are the public settings contract for KireiBox. */
class YukiSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        val dashboardWide: DashboardLayout
    )

    fun snapshot(): Snapshot {
        val legacyReduced = prefs.getBoolean(KEY_REDUCED_MOTION, false)
        val motionMode = if (prefs.contains(KEY_MOTION_MODE)) {
            MotionMode.fromStored(prefs.getString(KEY_MOTION_MODE, MotionMode.AUTO.name))
        } else if (legacyReduced) {
            MotionMode.REDUCED
        } else {
            MotionMode.AUTO
        }
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
    fun setMotionMode(mode: MotionMode) {
        prefs.edit()
            .putString(KEY_MOTION_MODE, mode.name)
            .putBoolean(KEY_REDUCED_MOTION, mode == MotionMode.REDUCED)
            .apply()
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
        const val KEY_DASHBOARD_COMPACT = "kireibox_dashboard_compact_v1"
        const val KEY_DASHBOARD_WIDE = "kireibox_dashboard_wide_v1"
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
