package com.yuki.yukihub.launcherbridge

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo

/**
 * Global defaults shared by the built-in engines: screen orientation (every engine) and the Tyrano
 * window flags. They live in the app's own prefs; the launchers copy them into the engine intent.
 */
object LauncherEngineOptions {
    private const val PREF_NAME = "kirei_engine_options"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_TYRANO_KEEP_SCREEN_ON = "tyrano_keep_screen_on"
    private const val KEY_TYRANO_IGNORE_CUTOUT = "tyrano_ignore_cutout"

    /** Sensor landscape (either way up), or locked to one landscape side. */
    const val ORIENTATION_SENSOR_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    const val ORIENTATION_LEFT_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    const val ORIENTATION_RIGHT_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    val ORIENTATIONS = listOf(ORIENTATION_SENSOR_LANDSCAPE, ORIENTATION_LEFT_LANDSCAPE, ORIENTATION_RIGHT_LANDSCAPE)

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @JvmStatic
    fun orientation(context: Context?): Int {
        if (context == null) return ORIENTATION_SENSOR_LANDSCAPE
        return prefs(context).getInt(KEY_ORIENTATION, ORIENTATION_SENSOR_LANDSCAPE).takeIf { it in ORIENTATIONS } ?: ORIENTATION_SENSOR_LANDSCAPE
    }

    @JvmStatic
    fun setOrientation(
        context: Context,
        value: Int,
    ) {
        prefs(context).edit().putInt(KEY_ORIENTATION, value.takeIf { it in ORIENTATIONS } ?: ORIENTATION_SENSOR_LANDSCAPE).apply()
    }

    @JvmStatic
    fun tyranoKeepScreenOn(context: Context?): Boolean = context == null || prefs(context).getBoolean(KEY_TYRANO_KEEP_SCREEN_ON, true)

    @JvmStatic
    fun setTyranoKeepScreenOn(
        context: Context,
        value: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_TYRANO_KEEP_SCREEN_ON, value).apply()
    }

    @JvmStatic
    fun tyranoIgnoreCutout(context: Context?): Boolean = context != null && prefs(context).getBoolean(KEY_TYRANO_IGNORE_CUTOUT, false)

    @JvmStatic
    fun setTyranoIgnoreCutout(
        context: Context,
        value: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_TYRANO_IGNORE_CUTOUT, value).apply()
    }
}
