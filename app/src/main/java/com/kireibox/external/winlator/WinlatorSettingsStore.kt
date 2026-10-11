package com.kireibox.external.winlator

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Winlator choices kept in the app's own prefs: the global defaults, each game's overrides and the
 * first-use guide's progress. The container itself lives in Winlator's private data, so none of
 * this can be read back from it.
 */
class WinlatorSettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadGlobal(): WinlatorSettings =
        WinlatorSettings(
            containerId = prefs.getInt(KEY_CONTAINER_ID, 0).coerceAtLeast(0),
            containerName = prefs.getString(KEY_CONTAINER_NAME, "").orEmpty().trim(),
            graphicsDriver = WinlatorChoices.graphicsDriver(prefs.getString(KEY_GRAPHICS_DRIVER, "")),
            dxwrapper = WinlatorChoices.dxwrapper(prefs.getString(KEY_DXWRAPPER, "")),
            screenSize = WinlatorChoices.screenSize(prefs.getString(KEY_SCREEN_SIZE, "")),
            lcAll = WinlatorChoices.lcAll(prefs.getString(KEY_LC_ALL, "")),
            tz = WinlatorChoices.timezone(prefs.getString(KEY_TZ, "")),
            box64Preset = WinlatorChoices.box64Preset(prefs.getString(KEY_BOX64_PRESET, "")),
            envVars = prefs.getString(KEY_ENV_VARS, "").orEmpty().trim(),
            save = prefs.getBoolean(KEY_SAVE, false),
        )

    fun saveGlobal(settings: WinlatorSettings) {
        prefs.edit()
            .putInt(KEY_CONTAINER_ID, settings.containerId.coerceAtLeast(0))
            .putString(KEY_CONTAINER_NAME, settings.containerName.trim())
            .putString(KEY_GRAPHICS_DRIVER, WinlatorChoices.graphicsDriver(settings.graphicsDriver))
            .putString(KEY_DXWRAPPER, WinlatorChoices.dxwrapper(settings.dxwrapper))
            .putString(KEY_SCREEN_SIZE, WinlatorChoices.screenSize(settings.screenSize))
            .putString(KEY_LC_ALL, WinlatorChoices.lcAll(settings.lcAll))
            .putString(KEY_TZ, WinlatorChoices.timezone(settings.tz))
            .putString(KEY_BOX64_PRESET, WinlatorChoices.box64Preset(settings.box64Preset))
            .putString(KEY_ENV_VARS, settings.envVars.trim())
            .putBoolean(KEY_SAVE, settings.save)
            .apply()
    }

    fun loadGame(gameId: Long): WinlatorGameOverride {
        val json = prefs.getString(GAME_PREFIX + gameId, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (gameId <= 0 || json == null) return WinlatorGameOverride()

        fun text(key: String): String? = if (json.has(key)) json.optString(key) else null
        return WinlatorGameOverride(
            containerId = if (json.has(KEY_CONTAINER_ID)) json.optInt(KEY_CONTAINER_ID, 0).coerceAtLeast(0) else null,
            containerName = text(KEY_CONTAINER_NAME)?.trim(),
            graphicsDriver = text(KEY_GRAPHICS_DRIVER)?.let(WinlatorChoices::graphicsDriver),
            dxwrapper = text(KEY_DXWRAPPER)?.let(WinlatorChoices::dxwrapper),
            screenSize = text(KEY_SCREEN_SIZE)?.let(WinlatorChoices::screenSize),
            lcAll = text(KEY_LC_ALL)?.let(WinlatorChoices::lcAll),
            tz = text(KEY_TZ)?.let(WinlatorChoices::timezone),
            box64Preset = text(KEY_BOX64_PRESET)?.let(WinlatorChoices::box64Preset),
            envVars = text(KEY_ENV_VARS)?.trim(),
            execArgs = text(KEY_EXEC_ARGS)?.trim(),
            save = if (json.has(KEY_SAVE)) json.optBoolean(KEY_SAVE) else null,
        )
    }

    /** Stores [override]; an override that follows everything is removed so the game tracks the defaults. */
    fun saveGame(
        gameId: Long,
        override: WinlatorGameOverride,
    ) {
        if (gameId <= 0) return
        val editor = prefs.edit()
        if (override.isEmpty) {
            editor.remove(GAME_PREFIX + gameId)
        } else {
            val json = JSONObject()
            override.containerId?.let { json.put(KEY_CONTAINER_ID, it) }
            override.containerName?.let { json.put(KEY_CONTAINER_NAME, it) }
            override.graphicsDriver?.let { json.put(KEY_GRAPHICS_DRIVER, it) }
            override.dxwrapper?.let { json.put(KEY_DXWRAPPER, it) }
            override.screenSize?.let { json.put(KEY_SCREEN_SIZE, it) }
            override.lcAll?.let { json.put(KEY_LC_ALL, it) }
            override.tz?.let { json.put(KEY_TZ, it) }
            override.box64Preset?.let { json.put(KEY_BOX64_PRESET, it) }
            override.envVars?.let { json.put(KEY_ENV_VARS, it) }
            override.execArgs?.let { json.put(KEY_EXEC_ARGS, it) }
            override.save?.let { json.put(KEY_SAVE, it) }
            editor.putString(GAME_PREFIX + gameId, json.toString())
        }
        editor.apply()
    }

    /** The settings a launch of [gameId] sends: the global defaults with the game's own choices on top. */
    fun resolve(gameId: Long): WinlatorSettings = loadGame(gameId).mergedOver(loadGlobal())

    /** The user has finished (or waved off) the first-use guide; nothing asks about the container again. */
    var guideDone: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GUIDE_DONE, value).apply()
        }

    /** The first-use notice has been raised once; closing it must not bring it back on the next added game. */
    var guideNoticeShown: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_NOTICE_SHOWN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GUIDE_NOTICE_SHOWN, value).apply()
        }

    private companion object {
        const val PREF_NAME = "kirei_winlator"
        const val GAME_PREFIX = "game_"
        const val KEY_CONTAINER_ID = "container_id"
        const val KEY_CONTAINER_NAME = "container_name"
        const val KEY_GRAPHICS_DRIVER = "graphics_driver"
        const val KEY_DXWRAPPER = "dxwrapper"
        const val KEY_SCREEN_SIZE = "screen_size"
        const val KEY_LC_ALL = "lc_all"
        const val KEY_TZ = "tz"
        const val KEY_BOX64_PRESET = "box64_preset"
        const val KEY_ENV_VARS = "env_vars"
        const val KEY_EXEC_ARGS = "exec_args"
        const val KEY_SAVE = "save"
        const val KEY_GUIDE_DONE = "guide_done"
        const val KEY_GUIDE_NOTICE_SHOWN = "guide_notice_shown"
    }
}
