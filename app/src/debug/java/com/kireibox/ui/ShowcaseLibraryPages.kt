package com.kireibox.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.kireibox.designsystem.KireiColors
import kotlinx.coroutines.delay

/** The real 游戏库 tab (LibraryPage) on the fixture library, with the main dock like the app draws it. */
@Composable
internal fun ShowcaseLibraryContent(env: ShowcaseHostEnv) {
    LibraryPage(
        state = env.state,
        colors = env.colors,
        haze = env.haze,
        viewModel = env.viewModel,
        actions = env.actions,
        removingGameId = null,
        layoutMode = env.layoutMode,
        safeDrawingPadding = env.safeDrawingPadding,
        bottomContentPadding = env.libraryDockContentPadding,
        adaptiveMetrics = env.adaptiveMetrics,
        windowClass = env.windowClass,
        onRequestRemove = {},
    )
}

internal enum class ShowcaseLibraryVariant { BROWSE, EDIT, EMPTY, TASKS }

/**
 * `library`, `library-edit`, `library-empty` and `task-center`. Edit mode is local state inside
 * LibraryPage (no parameter reaches it), so `library-edit` replays the real gesture: a synthetic long
 * press on the first card, dispatched to the window like a finger.
 */
@Composable
internal fun ShowcaseLibraryPage(
    activity: ComponentActivity,
    colors: KireiColors,
    variant: ShowcaseLibraryVariant,
) {
    val context = activity
    val games = remember(variant) { if (variant == ShowcaseLibraryVariant.EMPTY) emptyList() else ShowcaseFixtures.libraryGames(context) }
    ShowcasePageHost(
        activity = activity,
        colors = colors,
        dockTab = KireiTab.GAMES,
        games = games,
        transform = { s ->
            when (variant) {
                ShowcaseLibraryVariant.TASKS -> s.copy(coverQueue = ShowcaseFixtures.coverQueue())
                else -> s.copy(coverQueue = CoverQueueIdle)
            }
        },
    ) { env ->
        ShowcaseLibraryContent(env)
        if (variant == ShowcaseLibraryVariant.TASKS) {
            LaunchedEffect(Unit) { env.viewModel.openCoverTaskCenter() }
        }
        if (variant == ShowcaseLibraryVariant.EDIT) {
            ShowcaseAutoLongPress(activity, xFraction = .25f, yFraction = .45f)
        }
    }
}

private val CoverQueueIdle = com.kireibox.cover.CoverQueueUiState(emptyList(), com.kireibox.cover.CoverQueueStatus.RUNNING)

/** Presses and holds at a fraction of the window after the page has settled, then lifts the finger. */
@Composable
internal fun ShowcaseAutoLongPress(
    activity: ComponentActivity,
    xFraction: Float,
    yFraction: Float,
    settleMs: Long = 2_500L,
    holdMs: Long = 900L,
) {
    LaunchedEffect(Unit) {
        delay(settleMs)
        val view = activity.window.decorView
        val x = view.width * xFraction
        val y = view.height * yFraction
        val down = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        delay(holdMs)
        val up = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x, y, 0))
    }
}
