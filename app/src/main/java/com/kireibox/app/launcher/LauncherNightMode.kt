package com.kireibox.app.launcher

import android.app.ActivityManager
import android.app.UiModeManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kireibox.app.LauncherActivity
import com.kireibox.designsystem.KireiThemeResolver
import com.yuki.yukihub.settings.ThemeMode

/**
 * API 31+: hands the stored theme mode to the system as the app's own night mode. The system splash
 * (`windowSplashScreenBackground` = `@color/yh_bg`) is resolved by the system before any app code
 * runs, so without this a forced 浅色/深色 cold-starts through the other theme's splash. Older APIs
 * draw the app's own window background as the splash and are unchanged.
 *
 * A night-mode change relaunches the activity, so it is applied only once the launcher is no longer
 * visible (ON_STOP): switching themes in 设置 → 外观 stays a seamless in-place recolor, and the next
 * cold start still gets the matching splash.
 */
internal fun LauncherActivity.syncApplicationNightModeWhenHidden() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !isChangingConfigurations) {
                viewModel.state.value.settings
                    ?.themeMode
                    ?.let { mode -> LauncherNightModeSync.sync(this, mode) }
            }
        },
    )
}

internal object LauncherNightModeSync {
    /** Mode last handed to the system by this process; null until the first sync. */
    @Volatile
    private var applied: ThemeMode? = null

    /**
     * Applies [mode] unless it is already applied, or another task of this app (a game engine in
     * its own singleInstance task) is still running: a night-mode change relaunches activities that
     * do not handle uiMode, which would restart that game. A skipped sync is retried at the
     * launcher's next ON_STOP (only then can it relaunch without a visible flash).
     */
    fun sync(
        activity: LauncherActivity,
        mode: ThemeMode,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || applied == mode || hasOtherRunningTask(activity)) return
        activity.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(KireiThemeResolver.applicationNightMode(mode))
        applied = mode
    }

    private fun hasOtherRunningTask(activity: LauncherActivity): Boolean {
        val manager = activity.getSystemService(ActivityManager::class.java) ?: return false
        // Unknown means "assume a game may be running": skipping only defers the sync.
        return runCatching { manager.appTasks.any { it.taskInfo.taskId != activity.taskId && it.taskInfo.isRunning } }.getOrDefault(true)
    }
}
