package com.kireibox.ui

import android.content.res.Configuration
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.Game
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Everything a real page needs from KireiBoxApp's root scope, resolved exactly like the root does
 * (safe area, window class, adaptive metrics, dock clearance), plus fixture state and inert actions.
 */
internal class ShowcaseHostEnv(
    val state: KireiUiState,
    val colors: KireiColors,
    val haze: HazeState?,
    val viewModel: KireiViewModel,
    val actions: KireiActions,
    val layoutMode: KireiLayoutMode,
    val windowClass: ResponsiveWindowClass,
    val safeDrawingPadding: PaddingValues,
    val statusBarBottomDp: Dp,
    val navigationBottom: Dp,
    val displayCutout: HeaderRectDp?,
    val adaptiveMetrics: AdaptiveUiMetrics,
    val dockContentPadding: Dp,
    val libraryDockContentPadding: Dp,
    val games: List<Game>,
)

/** Actions for fixture pages: every launcher / picker entry point is a no-op. */
internal fun showcaseInertActions(): KireiActions =
    KireiActions(
        chooseDirectory = {},
        pickAddGame = {},
        chooseAndroidApp = {},
        chooseCoverFromAlbum = {},
        chooseCoverFromFiles = {},
        chooseExternalShortcuts = {},
        launchGame = {},
        pinGameShortcut = {},
        selectedGame = {},
        launchKrkrNative = {},
        checkPermissions = {},
        repairPermission = {},
        restoreRemovedGame = {},
        copyLogs = {},
        exportLogs = {},
        setDarkMode = {},
        installUpdate = {},
    )

/**
 * Hosts a real page composable under the same root scope KireiBoxApp provides. The library is the
 * fixture library, not the emulator's: [transform] overrides the real view model state (settings,
 * theme and so on stay the emulator's). The real [KireiViewModel] is only touched when a button on the
 * page is pressed, so screenshots never write anything.
 *
 * @param dockTab draws the real main dock with this tab selected; null hides it (full-screen pages).
 */
@Composable
internal fun ShowcasePageHost(
    activity: ComponentActivity,
    colors: KireiColors,
    dockTab: KireiTab?,
    games: List<Game> = emptyList(),
    transform: (KireiUiState) -> KireiUiState = { it },
    content: @Composable BoxScope.(ShowcaseHostEnv) -> Unit,
) {
    val viewModel = remember { ViewModelProvider(activity)[KireiViewModel::class.java] }
    val base by viewModel.state.collectAsState()
    val state =
        remember(base, games) {
            transform(
                base.copy(
                    games = games,
                    gameSearchIndex = ShowcaseFixtures.searchIndex(games),
                    homeLoadState = HomeLoadState.LOADED,
                    homeLoadError = null,
                    notice = null,
                    undoRemoval = null,
                    undoScanRootRemoval = null,
                    selectedGame = null,
                    krkrEditorGame = null,
                    coverSearch = null,
                    xp3Picker = null,
                    androidAppPicker = null,
                    externalShortcutPicker = null,
                    busy = false,
                    updateState = com.kireibox.update.UpdateUiState.Idle,
                ),
            )
        }
    val actions = remember { showcaseInertActions() }
    val haze = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !colors.highContrast) HazeState() else null }
    Box(Modifier.fillMaxSize().background(colors.background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
            val layoutDirection = LocalLayoutDirection.current
            val configuration = LocalConfiguration.current
            val safeWidth =
                maxWidth - safeDrawingPadding.calculateLeftPadding(layoutDirection) -
                    safeDrawingPadding.calculateRightPadding(layoutDirection)
            val safeHeight = maxHeight - safeDrawingPadding.calculateTopPadding() - safeDrawingPadding.calculateBottomPadding()
            val windowClass =
                ResponsiveLayoutPolicy.resolveWindowClass(
                    safeWidthDp = safeWidth.value,
                    safeHeightDp = safeHeight.value,
                    smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                    screenLayoutSize = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
                )
            val adaptiveMetrics =
                resolveAdaptiveUiMetrics(
                    windowWidthDp = maxWidth.value,
                    windowHeightDp = maxHeight.value,
                    safeLeftDp = safeDrawingPadding.calculateLeftPadding(layoutDirection).value,
                    safeTopDp = safeDrawingPadding.calculateTopPadding().value,
                    safeRightDp = safeDrawingPadding.calculateRightPadding(layoutDirection).value,
                    safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                )
            val env =
                ShowcaseHostEnv(
                    state = state,
                    colors = colors,
                    haze = haze,
                    viewModel = viewModel,
                    actions = actions,
                    layoutMode = windowClass.legacyLayoutMode(),
                    windowClass = windowClass,
                    safeDrawingPadding = safeDrawingPadding,
                    statusBarBottomDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    displayCutout = displayCutoutBoundsDp(LocalView.current, LocalDensity.current),
                    adaptiveMetrics = adaptiveMetrics,
                    dockContentPadding = adaptiveMetrics.bottomContentPaddingDp.dp,
                    libraryDockContentPadding =
                        resolveLibraryGridBottomContentPaddingDp(
                            windowHeightDp = maxHeight.value,
                            dockRect = adaptiveMetrics.dockRect,
                            safeBottomDp = safeDrawingPadding.calculateBottomPadding().value,
                        ).dp,
                    games = games,
                )
            Box(Modifier.fillMaxSize().then(if (haze != null) Modifier.hazeSource(haze) else Modifier)) {
                content(env)
            }
            if (dockTab != null) {
                KireiMainDock(
                    spec = MainDockSpec(dockTab, DockTabBadge.None, adaptiveMetrics, reducedTransparency = false),
                    colors = colors,
                    haze = haze,
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
