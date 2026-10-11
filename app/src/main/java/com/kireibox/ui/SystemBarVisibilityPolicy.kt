package com.kireibox.ui

import android.content.res.Configuration
import android.view.WindowManager

data class SystemBarVisibilitySpec(
    val windowClass: ResponsiveWindowClass,
    val orientation: HomeOrientation,
    val hideStatusBar: Boolean,
    val hideNavigationBar: Boolean,
    val cutoutMode: Int,
) {
    val statusBarHidden: Boolean
        get() = hideStatusBar

    val navigationBarHidden: Boolean
        get() = hideNavigationBar
}

/**
 * Home system-bar contract: only compact phone landscape hides the status bar so the
 * immersive carousel can use the short phone canvas. Pad landscape keeps the status bar
 * visible. Navigation bars are never hidden, edge-to-edge rendering stays transparent,
 * and the display cutout uses SHORT_EDGES.
 */
object SystemBarVisibilityPolicy {
    /** Explicit device/orientation matrix. Navigation bars remain visible in every cell. */
    fun resolve(
        windowClass: ResponsiveWindowClass,
        orientation: HomeOrientation,
    ): SystemBarVisibilitySpec =
        SystemBarVisibilitySpec(
            windowClass = windowClass,
            orientation = orientation,
            hideStatusBar = shouldHideStatusBar(windowClass),
            hideNavigationBar = shouldHideNavigationBars(windowClass, orientation),
            cutoutMode = cutoutMode(),
        )

    /** Only the compact phone landscape class is status-bar immersive. */
    fun shouldHideStatusBar(windowClass: ResponsiveWindowClass): Boolean = windowClass == ResponsiveWindowClass.PHONE_LANDSCAPE

    /** Compatibility overload for callers that already carry the four-way class. */
    fun shouldHideStatusBar(
        windowClass: ResponsiveWindowClass,
        @Suppress("UNUSED_PARAMETER") orientation: HomeOrientation,
    ): Boolean = shouldHideStatusBar(windowClass)

    fun shouldHideNavigationBars(
        @Suppress("UNUSED_PARAMETER") windowClass: ResponsiveWindowClass,
        @Suppress("UNUSED_PARAMETER") orientation: HomeOrientation,
    ): Boolean = false

    fun resolvePolicy(
        safeWidthDp: Float,
        safeHeightDp: Float,
        sizeClass: HomeSizeClass,
    ): SystemBarVisibilitySpec {
        val windowClass =
            ResponsiveLayoutPolicy.resolveWindowClass(
                safeWidthDp = safeWidthDp,
                safeHeightDp = safeHeightDp,
                sizeClass = sizeClass,
            )
        val orientation =
            if (safeWidthDp > safeHeightDp) {
                HomeOrientation.LANDSCAPE
            } else {
                HomeOrientation.PORTRAIT
            }
        return resolve(windowClass, orientation)
    }

    /** Configuration-based policy used by a future Activity wiring pass. */
    fun resolvePolicy(
        widthDp: Float,
        heightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
        orientation: Int,
    ): SystemBarVisibilitySpec {
        val windowClass =
            ResponsiveLayoutPolicy.resolveWindowClass(
                safeWidthDp = widthDp,
                safeHeightDp = heightDp,
                smallestScreenWidthDp = smallestScreenWidthDp,
                screenLayoutSize = screenLayoutSize,
            )
        val resolvedOrientation =
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                HomeOrientation.LANDSCAPE
            } else {
                HomeOrientation.PORTRAIT
            }
        return resolve(windowClass, resolvedOrientation)
    }

    /** Navigation bars stay visible in every layout mode and orientation. */
    fun shouldHideNavigationBars(): Boolean = false

    /**
     * System-bar icon tone: dark backgrounds need light icons and light backgrounds need dark
     * icons. The immersive palette follows the selected theme, so it uses the same contrast rule
     * as the rest of the app; [immersiveHome] remains in the signature for existing call sites.
     */
    fun statusBarIconsLight(
        darkTheme: Boolean,
        @Suppress("UNUSED_PARAMETER") immersiveHome: Boolean = false,
    ): Boolean = darkTheme

    /** Immersive content extends into the short-edge cutout where the platform supports it. */
    fun cutoutMode(): Int = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

    /** Full classification shared by tests and the Activity, using the layout-mode inputs. */
    fun resolve(
        widthDp: Float,
        heightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
        orientation: Int,
    ): Boolean =
        resolvePolicy(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            screenLayoutSize = screenLayoutSize,
            orientation = orientation,
        ).hideStatusBar

    /** Same matrix as [resolvePolicy], exposed without the legacy Boolean return type. */
    fun visibilitySpec(
        widthDp: Float,
        heightDp: Float,
        smallestScreenWidthDp: Int,
        screenLayoutSize: Int,
        orientation: Int,
    ): SystemBarVisibilitySpec =
        resolvePolicy(
            widthDp = widthDp,
            heightDp = heightDp,
            smallestScreenWidthDp = smallestScreenWidthDp,
            screenLayoutSize = screenLayoutSize,
            orientation = orientation,
        )

    /** Device/orientation overload for code that has already classified the window. */
    fun visibilitySpec(
        windowClass: ResponsiveWindowClass,
        orientation: HomeOrientation,
    ): SystemBarVisibilitySpec = resolve(windowClass, orientation)
}
