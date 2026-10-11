package com.yuki.yukihub.launcherbridge

import android.content.Context
import android.content.SharedPreferences

/**
 * KRKR 引擎设置桥接：负责读取/保存主项目 yukihub_prefs 中的 KRKR 引擎相关配置。
 * 涉及键值与 MainActivity / SyncManager 完全一致，保证 Launcher 修改后主项目立即可见。
 */
object LauncherKrkrBridge {
    private const val APP_PREFS = "yukihub_prefs"
    private const val KEY_KR_ENGINE_VERSION = "kr_engine_version"
    private const val KEY_KR_STARTUP_DIALOG_POLICY = "kr_startup_dialog_policy"
    private const val KEY_KR_COMPAT_MODE = "kr_compat_mode"
    private const val KEY_ARTEMIS_SCOPED_SAVE_DIR = "artemis_scoped_save_dir"
    private const val KEY_TYRANO_SCOPED_SAVE_DIR = "tyrano_scoped_save_dir"

    const val ENGINE_VERSION_AUTO = "auto"
    const val ENGINE_VERSION_139 = "1.3.9"
    const val ENGINE_VERSION_134 = "1.3.4"
    const val ENGINE_VERSION_126 = "1.2.6"
    const val DIALOG_POLICY_SHOW_ALL = "SHOW_ALL"
    const val DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO = "SKIP_SINGLE_BUTTON_STARTUP_INFO"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun getEngineVersion(context: Context?): String {
        if (context == null) return ENGINE_VERSION_AUTO
        val v = prefs(context).getString(KEY_KR_ENGINE_VERSION, ENGINE_VERSION_AUTO)
        return normalizeEngineVersion(v)
    }

    @JvmStatic
    fun setEngineVersion(
        context: Context?,
        version: String?
    ) {
        if (context == null) return
        prefs(context).edit()
            .putString(KEY_KR_ENGINE_VERSION, normalizeEngineVersion(version))
            .apply()
    }

    @JvmStatic
    fun getStartupDialogPolicy(context: Context?): String {
        if (context == null) return DIALOG_POLICY_SHOW_ALL
        return normalizeStartupDialogPolicy(
            prefs(context).getString(KEY_KR_STARTUP_DIALOG_POLICY, DIALOG_POLICY_SHOW_ALL)
        )
    }

    @JvmStatic
    fun setStartupDialogPolicy(
        context: Context?,
        policy: String?
    ) {
        if (context == null) return
        prefs(context).edit()
            .putString(KEY_KR_STARTUP_DIALOG_POLICY, normalizeStartupDialogPolicy(policy))
            .apply()
    }

    @JvmStatic
    fun isCompatMode(context: Context?): Boolean =
        context != null &&
            prefs(context).getBoolean(KEY_KR_COMPAT_MODE, false)

    @JvmStatic
    fun setCompatMode(
        context: Context?,
        enabled: Boolean
    ) {
        if (context == null) return
        prefs(context).edit().putBoolean(KEY_KR_COMPAT_MODE, enabled).apply()
    }

    @JvmStatic
    fun normalizeStartupDialogPolicy(value: String?): String =
        if (value.equals(DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO, ignoreCase = true)) {
            DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO
        } else {
            DIALOG_POLICY_SHOW_ALL
        }

    @JvmStatic
    fun isArtemisScopedSaveDir(context: Context?): Boolean {
        if (context == null) return true
        // Keep the current safe default for new installs, but honour an
        // explicit user choice to run Artemis against its original directory.
        return prefs(context).getBoolean(KEY_ARTEMIS_SCOPED_SAVE_DIR, true)
    }

    @JvmStatic
    fun setArtemisScopedSaveDir(
        context: Context?,
        enabled: Boolean
    ) {
        if (context == null) return
        prefs(context).edit().putBoolean(KEY_ARTEMIS_SCOPED_SAVE_DIR, enabled).apply()
    }

    @JvmStatic
    fun isTyranoScopedSaveDir(context: Context?): Boolean {
        if (context == null) return true
        return prefs(context).getBoolean(KEY_TYRANO_SCOPED_SAVE_DIR, true)
    }

    @JvmStatic
    fun setTyranoScopedSaveDir(
        context: Context?,
        enabled: Boolean
    ) {
        if (context == null) return
        prefs(context).edit().putBoolean(KEY_TYRANO_SCOPED_SAVE_DIR, enabled).apply()
    }

    @JvmStatic
    fun normalizeEngineVersion(value: String?): String {
        val v = value?.trim()?.lowercase() ?: ENGINE_VERSION_AUTO
        return when (v) {
            ENGINE_VERSION_139 -> ENGINE_VERSION_139
            ENGINE_VERSION_134 -> ENGINE_VERSION_134
            ENGINE_VERSION_126 -> ENGINE_VERSION_126
            else -> ENGINE_VERSION_AUTO
        }
    }

    @JvmStatic
    fun engineVersionLabel(value: String?): String {
        return when (normalizeEngineVersion(value)) {
            ENGINE_VERSION_139 -> "1.3.9"
            ENGINE_VERSION_134 -> "1.3.4"
            ENGINE_VERSION_126 -> "1.2.6"
            else -> "自动"
        }
    }
}
