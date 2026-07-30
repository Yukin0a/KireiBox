package com.yuki.yukihub.launcher

import android.content.Context
import android.graphics.Color

/** Minimal engine handoff palette, independent from the removed legacy view theme layer. */
internal object KireiEngineTheme {
    private fun dark(context: Context): Boolean =
        context.getSharedPreferences("yukihub_prefs", Context.MODE_PRIVATE)
            .getBoolean("launcher_dark_mode", true)

    fun primary(context: Context): Int = Color.parseColor(if (dark(context)) "#B3ACFF" else "#5B45C7")
    fun onPrimary(context: Context): Int = Color.parseColor(if (dark(context)) "#211A4A" else "#FFFFFF")
    fun background(context: Context): Int = Color.parseColor(if (dark(context)) "#000000" else "#F5F4F8")
    fun card(context: Context): Int = Color.parseColor(if (dark(context)) "#1C1C1E" else "#FFFFFF")
    fun text(context: Context): Int = Color.parseColor(if (dark(context)) "#F5F5F7" else "#1C1C1E")
    fun textMuted(context: Context): Int = Color.parseColor(if (dark(context)) "#B7B4C0" else "#6E6E73")
}
