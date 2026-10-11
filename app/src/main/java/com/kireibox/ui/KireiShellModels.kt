package com.kireibox.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density
import com.yuki.yukihub.model.Game

// Shell-level models, composition locals and window helpers shared by the app root and its pages.

internal val spotlightProcessSeed = System.nanoTime()

/** Reads the platform cutout rectangles in window coordinates for the header policy. */
internal fun displayCutoutBoundsDp(
    view: View,
    density: Density
): HeaderRectDp? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val rects = view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
    if (rects.isEmpty()) return null
    val left = rects.minOf { it.left }
    val top = rects.minOf { it.top }
    val right = rects.maxOf { it.right }
    val bottom = rects.maxOf { it.bottom }
    return with(density) {
        HeaderRectDp(
            left.toDp().value,
            top.toDp().value,
            right.toDp().value,
            bottom.toDp().value,
        )
    }
}

data class KireiActions(
    val chooseDirectory: () -> Unit,
    /** Opens the folder picker of the 添加游戏 flow; the selection comes back through the view model. */
    val pickAddGame: (AddGamePick) -> Unit,
    val chooseAndroidApp: () -> Unit,
    val chooseCoverFromAlbum: () -> Unit,
    val chooseCoverFromFiles: () -> Unit,
    val chooseExternalShortcuts: () -> Unit,
    val launchGame: (Game) -> Unit,
    val pinGameShortcut: (Game) -> Unit,
    val selectedGame: (Game) -> Unit,
    val launchKrkrNative: () -> Unit,
    val checkPermissions: () -> Unit,
    /** Asks for KRKR's native file access (all-files access on Android 11+, storage before it). */
    val requestNativeAccess: () -> Unit = {},
    val repairPermission: (String?) -> Unit,
    val restoreRemovedGame: (Game) -> Unit,
    val copyLogs: () -> Unit,
    val exportLogs: () -> Unit,
    val setDarkMode: (Boolean) -> Unit,
    val installUpdate: (String) -> Unit,
)

data class KireiPrompt(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val dismissLabel: String? = null,
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)

data class BuiltinFileBrowserRequest(
    val startPath: String,
    val appPath: String,
    /** The add-game step this pick feeds; null = a scan root. */
    val pick: AddGamePick? = null,
)

internal fun ResponsiveWindowClass.homeSizeClass(): HomeSizeClass =
    when (this) {
        ResponsiveWindowClass.PHONE_PORTRAIT,
        ResponsiveWindowClass.PHONE_LANDSCAPE,
        -> HomeSizeClass.COMPACT
        ResponsiveWindowClass.PAD_PORTRAIT,
        ResponsiveWindowClass.PAD_LANDSCAPE,
        -> HomeSizeClass.WIDE
    }

/** Bridges the four-way runtime classifier to legacy two-value task/presentation APIs. */
internal fun ResponsiveWindowClass.legacyLayoutMode(): KireiLayoutMode =
    when (homeSizeClass()) {
        HomeSizeClass.COMPACT -> KireiLayoutMode.PHONE_PORTRAIT
        HomeSizeClass.WIDE -> KireiLayoutMode.WIDE
    }

internal val LocalNsfwBlurEnabled = staticCompositionLocalOf { true }

/**
 * True when system-bar icons must be light (white) for the currently composed page:
 * the immersive home always paints a dark backdrop, so sheets and menus keep white icons
 * even in light theme. Provided at the app root and consumed by overlays that would
 * otherwise reset the window appearance from their own theme.
 */
internal val LocalSystemBarIconTone = staticCompositionLocalOf { false }

internal fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
