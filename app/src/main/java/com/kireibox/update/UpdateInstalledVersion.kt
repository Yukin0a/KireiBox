package com.kireibox.update

import com.yuki.yukihub.BuildConfig

/**
 * The "installed version" used by the update check.
 *
 * Debug builds may override it from `local.properties` so the updater can be exercised end to end
 * without editing (or regressing) the shipped version:
 *
 * ```
 * # local.properties  (not committed)
 * KIREIBOX_DEBUG_INSTALLED_VERSION_NAME=0.5.0-alpha.14
 * KIREIBOX_DEBUG_INSTALLED_VERSION_CODE=21
 * ```
 *
 * Release builds always report the real [BuildConfig] values; the override fields are empty in
 * the generated `BuildConfig` for non-debug variants, so this is a no-op there.
 */
object UpdateInstalledVersion {
    val versionName: String
        get() =
            BuildConfig.DEBUG_OVERRIDE_VERSION_NAME
                .takeIf { it.isNotBlank() }
                ?: BuildConfig.VERSION_NAME

    val versionCode: Int
        get() =
            BuildConfig.DEBUG_OVERRIDE_VERSION_CODE
                .takeIf { it > 0 }
                ?: BuildConfig.VERSION_CODE
}
