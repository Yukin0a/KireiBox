package com.kireibox.ui
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.LandscapeHomeStyle
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.SpotlightSelector
import dev.chrisbanes.haze.HazeState
import java.time.LocalDate

// Dashboard grid and its home widget host.

/** True inside the dashboard editor's widget previews: the corner controls sit over a widget's header action. */
internal val LocalDashboardEditing = compositionLocalOf { false }

@Composable
internal fun DashboardGrid(
    layout: DashboardLayout,
    profile: DashboardProfile,
    density: DashboardDensity,
    modifier: Modifier = Modifier,
    content: @Composable (DashboardWidgetSpec) -> Unit
) {
    val placements = remember(layout, profile) { DashboardLayouts.pack(layout, profile) }
    val compactHeight = density == DashboardDensity.COMPACT_HEIGHT
    val horizontalGap = if (compactHeight) 8.dp else 10.dp
    val verticalStep = if (compactHeight) 6.dp else DashboardLayouts.HEIGHT_STEP_DP.dp
    val polished = LocalResolvedMotionMode.current == MotionMode.POLISHED
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cellWidth = (maxWidth - horizontalGap * (profile.columns - 1)) / profile.columns
        val rows = placements.maxOfOrNull { it.row + it.spec.heightSteps } ?: 0
        // The height animates without animateContentSize's clip: the editor's corner controls ride the widgets' edges.
        val gridHeight by animateDpAsState(verticalStep * rows, if (polished) spring(.82f, Spring.StiffnessMediumLow) else tween(90), label = "dashboardGridHeight")
        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            placements.forEach { placement ->
                key(placement.spec.instanceId) {
                    val spec = placement.spec
                    val animation = if (polished) spring<Dp>(.82f, Spring.StiffnessMediumLow) else tween(90)
                    val x by animateDpAsState((cellWidth + horizontalGap) * placement.column, animation, label = "dashboardX")
                    val y by animateDpAsState(verticalStep * placement.row, animation, label = "dashboardY")
                    val width by animateDpAsState(
                        cellWidth * spec.size.columns + horizontalGap * (spec.size.columns - 1),
                        animation,
                        label = "dashboardWidth"
                    )
                    val height by animateDpAsState(verticalStep * spec.heightSteps, animation, label = "dashboardHeight")
                    Box(Modifier.offset(x, y).size(width, height)) { content(spec) }
                }
            }
        }
    }
}

@Composable
internal fun HomeDashboardWidget(
    spec: DashboardWidgetSpec,
    profile: DashboardProfile,
    games: List<Game>,
    recent: List<Game>,
    playTrend: List<PlayTrendPoint>,
    colors: KireiColors,
    haze: HazeState?,
    onOpenOverview: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit,
    onChooseDirectory: () -> Unit,
    onOpenSpotlightEditor: () -> Unit = {},
    interactiveTrend: Boolean = true,
    spotlightInitialPage: Int = 0,
    onSpotlightPageChanged: (Int) -> Unit = {},
    landscapeStyle: LandscapeHomeStyle? = null,
    safeContentPadding: Dp = 0.dp,
    constrainRecentRow: Boolean = false,
) {
    when (spec.type) {
        DashboardWidgetType.GAME_SPOTLIGHT -> {
            val pages =
                SpotlightSelector.selectPages(
                    spec = spec,
                    games = games,
                    sessionSeed = spotlightProcessSeed,
                    epochDay = LocalDate.now().toEpochDay(),
                )
            if (pages.isNotEmpty()) {
                HomeSpotlight(
                    pages = pages,
                    colors = colors,
                    modifier = Modifier.fillMaxSize(),
                    styleKey = spec.styleKey,
                    onOpen = onOpenGame,
                    onLaunch = onLaunchGame,
                    landscapeStyle = landscapeStyle,
                    safeContentPadding = safeContentPadding,
                    initialPage = spotlightInitialPage,
                    onPageChanged = onSpotlightPageChanged,
                )
            } else {
                HomeEmptySpotlight(
                    colors = colors,
                    emptyState =
                        ImmersiveGalleryEditorPolicy.emptyState(
                            games = games,
                            mode = spec.spotlightSelectionMode,
                            manualIds = spec.spotlightGameIds,
                            source = spec.spotlightSource,
                        ),
                    onChooseDirectory = onChooseDirectory,
                    onOpenSpotlightEditor = onOpenSpotlightEditor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        DashboardWidgetType.LIBRARY_OVERVIEW ->
            HomeInsightRail(
                gameCount = games.size,
                favoriteCount = games.count { it.favorite },
                playTime = games.sumOf { it.totalPlayTime },
                colors = colors,
                modifier = Modifier.fillMaxSize(),
                compact = spec.size.columns == 1,
                styleKey = spec.styleKey,
                onOpenLibrary = onOpenOverview
            )
        DashboardWidgetType.RECENT_GAMES ->
            DashboardRecentWidget(
                games = recent.take(3),
                showThreeAcross = profile == DashboardProfile.WIDE && spec.size.columns == 4,
                constrainToInnerBounds = landscapeStyle != null || constrainRecentRow,
                compact = spec.size.columns == 1,
                styleKey = spec.styleKey,
                colors = colors,
                haze = haze,
                onOpenLibrary = onOpenLibrary,
                onOpenGame = onOpenGame,
                onLaunchGame = onLaunchGame
            )
        DashboardWidgetType.FAVORITES ->
            DashboardFavoritesWidget(
                games.filter { it.favorite },
                spec.styleKey,
                colors,
                haze,
                onOpenLibrary
            )
        DashboardWidgetType.LIBRARY_SHORTCUT ->
            DashboardLibraryShortcut(
                games.size,
                games.count { it.lastPlayedAt > 0L },
                spec.styleKey,
                colors,
                haze,
                onOpenLibrary
            )
        DashboardWidgetType.PLAY_TREND -> DashboardTrendWidget(playTrend, spec, colors, haze, interactiveTrend)
    }
}
