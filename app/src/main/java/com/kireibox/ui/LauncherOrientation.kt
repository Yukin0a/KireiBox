package com.kireibox.ui

import android.content.Context
import android.content.pm.ActivityInfo
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.YukiSettingsStore

/** Activity orientation for the 设置 → 外观 → 屏幕方向 choice. */
internal fun OrientationMode.activityOrientation(): Int =
    when (this) {
        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

/**
 * Stored orientation read synchronously, so [com.kireibox.app.LauncherActivity] can lock it in
 * onCreate before its first frame instead of rotating once the settings flow loads.
 */
internal fun storedLauncherOrientation(context: Context): Int =
    OrientationMode
        .fromStored(
            context
                .getSharedPreferences(YukiSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(YukiSettingsStore.KEY_ORIENTATION_MODE, null),
        ).activityOrientation()
