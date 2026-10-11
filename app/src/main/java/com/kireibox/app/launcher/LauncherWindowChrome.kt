package com.kireibox.app.launcher

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.SystemBarVisibilityPolicy

internal fun LauncherActivity.configureWindow() {
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes =
            window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
    }
    applySystemBarTone(LauncherActivity.isLauncherDarkMode(this))
}

/**
 * Alpha 8 V3 system-bar contract: only non-Pad phone landscape hides the status bar.
 * The transient top-swipe reveal never triggers a layout remeasure, and the navigation
 * bar stays visible in every mode. Reapplied on resume and configuration changes so a
 * rotation or split-screen resize restores the bar immediately.
 */
internal fun LauncherActivity.applySystemBars() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    val config = resources.configuration
    val visibility =
        SystemBarVisibilityPolicy.visibilitySpec(
            widthDp = config.screenWidthDp.toFloat(),
            heightDp = config.screenHeightDp.toFloat(),
            smallestScreenWidthDp = config.smallestScreenWidthDp,
            screenLayoutSize = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
            orientation = config.orientation,
        )
    if (visibility.hideStatusBar) {
        controller.hide(WindowInsetsCompat.Type.statusBars())
    } else {
        controller.show(WindowInsetsCompat.Type.statusBars())
    }
    if (visibility.hideNavigationBar) {
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    } else {
        controller.show(WindowInsetsCompat.Type.navigationBars())
    }
}

internal fun LauncherActivity.configureEdgeToEdge(dark: Boolean) {
    val style =
        if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
}

internal fun LauncherActivity.applySystemBarTone(dark: Boolean) {
    configureEdgeToEdge(dark)
    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
    }
}

/**
 * Applies the system-bar icon tone requested by the composed page. [lightIcons] is
 * true when the page background is dark and icons must be white (light); it maps to
 * [WindowInsetsControllerCompat.isAppearanceLightStatusBars] == false, never inverted.
 * The immersive home always requests light icons because its backdrop is dark in both
 * themes; the initial theme tone is applied before composition and this corrects it
 * on the first frame, on theme changes and on configuration changes.
 */
internal fun LauncherActivity.applyStatusBarTone(lightIcons: Boolean) {
    val style =
        if (lightIcons) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = !lightIcons
        isAppearanceLightNavigationBars = !lightIcons
    }
}
