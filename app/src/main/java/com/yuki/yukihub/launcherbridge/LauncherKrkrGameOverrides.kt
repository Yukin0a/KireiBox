package com.yuki.yukihub.launcherbridge

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-game KRKR launch overrides kept in the app's own prefs (a game folder may be read-only, and the
 * engine never reads these). A game without an entry follows the global setting.
 */
object LauncherKrkrGameOverrides {
    private const val PREF_NAME = "kr_game_overrides"
    private const val KEY_STARTUP_DIALOG = "startup_dialog_"
    private const val KEY_FONT_SCALE = "font_scale_"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** The game's own startup-dialog policy, or null when it follows the global one. */
    @JvmStatic
    fun startupDialogPolicy(
        context: Context?,
        gameId: Long
    ): String? {
        if (context == null || gameId <= 0) return null
        return prefs(context).getString(KEY_STARTUP_DIALOG + gameId, null)?.let(LauncherKrkrBridge::normalizeStartupDialogPolicy)
    }

    /** Stores [policy] for the game; null (or a game id that is not saved yet) goes back to following the global setting. */
    @JvmStatic
    fun setStartupDialogPolicy(
        context: Context?,
        gameId: Long,
        policy: String?
    ) {
        if (context == null || gameId <= 0) return
        val editor = prefs(context).edit()
        if (policy == null) {
            editor.remove(KEY_STARTUP_DIALOG + gameId)
        } else {
            editor.putString(KEY_STARTUP_DIALOG + gameId, LauncherKrkrBridge.normalizeStartupDialogPolicy(policy))
        }
        editor.apply()
    }

    /** The game's font size multiplier, or null when none is set. */
    @JvmStatic
    fun fontScale(
        context: Context?,
        gameId: Long
    ): Float? {
        if (context == null || gameId <= 0) return null
        return prefs(context).takeIf { it.contains(KEY_FONT_SCALE + gameId) }?.getFloat(KEY_FONT_SCALE + gameId, 1f)
    }

    /** Stores [scale] for the game; null goes back to no font patch. */
    @JvmStatic
    fun setFontScale(
        context: Context?,
        gameId: Long,
        scale: Float?
    ) {
        if (context == null || gameId <= 0) return
        val editor = prefs(context).edit()
        if (scale == null) editor.remove(KEY_FONT_SCALE + gameId) else editor.putFloat(KEY_FONT_SCALE + gameId, scale)
        editor.apply()
    }
}
