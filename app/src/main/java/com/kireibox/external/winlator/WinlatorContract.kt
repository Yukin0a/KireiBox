package com.kireibox.external.winlator

/**
 * Winlator-CN external launch protocol (the single place that spells its literals).
 *
 * Source: winlator-cn `docs/external-launch.md` and `ExternalLaunchActivity`. The activity is
 * exported as `com.winlator.ExternalLaunchActivity` whatever the applicationId of a repackaged
 * build is. `dir_path` is mounted by Winlator on a free drive letter (counting down from `W:`) for
 * this session only while `save` is false, so nothing is written into the user's container.
 */
object WinlatorContract {
    const val DEFAULT_PACKAGE = "com.winlator"
    const val ACTIVITY_NAME = "com.winlator.ExternalLaunchActivity"
    const val DEEPLINK_SCHEME = "winlator"
    const val DEEPLINK_HOST = "launch"

    const val EXTRA_DIR_PATH = "dir_path"
    const val EXTRA_EXE_PATH = "exe_path"
    const val EXTRA_CONFIRM = "confirm"
    const val EXTRA_LAUNCH_ID = "launch_id"
    const val EXTRA_CONTAINER_ID = "container_id"
    const val EXTRA_CONTAINER_NAME = "container_name"
    const val EXTRA_GRAPHICS_DRIVER = "graphics_driver"
    const val EXTRA_DXWRAPPER = "dxwrapper"
    const val EXTRA_SCREEN_SIZE = "screen_size"
    const val EXTRA_LC_ALL = "lc_all"
    const val EXTRA_TZ = "tz"
    const val EXTRA_EXEC_ARGS = "exec_args"
    const val EXTRA_OVERRIDES = "overrides"
    const val EXTRA_SAVE = "save"

    const val OVERRIDE_BOX64_PRESET = "box64Preset"
    const val OVERRIDE_ENV_VARS = "envVars"

    /**
     * What a settings object adds to the intent. An empty value is never sent: Winlator then falls
     * back to the container's own configuration, so a setting left on "跟随容器" cannot break a launch.
     * Ints and booleans keep their type, everything else is a string.
     */
    fun extras(settings: WinlatorSettings): Map<String, Any> =
        buildMap {
            if (settings.containerId > 0) put(EXTRA_CONTAINER_ID, settings.containerId)
            sendable(settings.containerName)?.let { put(EXTRA_CONTAINER_NAME, it) }
            sendable(WinlatorChoices.graphicsDriver(settings.graphicsDriver))?.let { put(EXTRA_GRAPHICS_DRIVER, it) }
            sendable(WinlatorChoices.dxwrapper(settings.dxwrapper))?.let { put(EXTRA_DXWRAPPER, it) }
            sendable(WinlatorChoices.screenSize(settings.screenSize))?.let { put(EXTRA_SCREEN_SIZE, it) }
            sendable(WinlatorChoices.lcAll(settings.lcAll))?.let { put(EXTRA_LC_ALL, it) }
            sendable(WinlatorChoices.timezone(settings.tz))?.let { put(EXTRA_TZ, it) }
            sendable(settings.execArgs)?.let { put(EXTRA_EXEC_ARGS, it) }
            overrides(settings).takeIf(Map<String, String>::isNotEmpty)?.let { put(EXTRA_OVERRIDES, it) }
            if (settings.save) put(EXTRA_SAVE, true)
        }

    /** The keys that have no dedicated extra and travel in the `overrides` JSON object. */
    fun overrides(settings: WinlatorSettings): Map<String, String> =
        buildMap {
            sendable(WinlatorChoices.box64Preset(settings.box64Preset))?.let { put(OVERRIDE_BOX64_PRESET, it) }
            sendable(WinlatorChoices.envVars(settings.envVars))?.let { put(OVERRIDE_ENV_VARS, it) }
        }

    private fun sendable(value: String): String? = value.trim().takeIf(String::isNotEmpty)
}
