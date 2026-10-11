package com.kireibox.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiProgressBar
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.TrendVisualStyle
import dev.chrisbanes.haze.HazeState
import java.util.Date

// Home dashboard insight rail and widgets. Every widget is the e1 card: surface + stroke hairline, KireiShapes.lg.

@Composable
internal fun DashboardRecentWidget(
    games: List<Game>,
    showThreeAcross: Boolean,
    constrainToInnerBounds: Boolean,
    compact: Boolean,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit
) {
    val showAction = !LocalDashboardEditing.current
    GlassSurface(colors, haze, Modifier.fillMaxSize(), KireiShapes.lg, KireiGlassRole.CONTENT) {
        Column(
            Modifier.fillMaxSize().padding(
                start = KireiSpacing.md,
                top = KireiSpacing.xxs,
                end = KireiSpacing.md,
                bottom = KireiSpacing.xs,
            ),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("最近打开", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1)
                if (showAction) {
                    KireiButton(
                        label = "查看全部",
                        colors = colors,
                        options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                        onClick = onOpenLibrary,
                    )
                }
            }
            if (games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text("还没有游玩记录", color = colors.secondary, style = AppleTypography.bodyMedium)
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val maxItems =
                        when {
                            showThreeAcross -> 3
                            constrainToInnerBounds -> 2
                            else -> 3
                        }
                    val itemCount = RecentGamesRowPolicy.itemCount(maxWidth.value, maxItems)
                    val visibleGames = games.take(itemCount)
                    val cardWidth = RecentGamesRowPolicy.cardWidthDp(maxWidth.value, itemCount)
                    if (constrainToInnerBounds) {
                        // Weighted row: items always divide the real viewport, so every
                        // recent item stays complete and no second card can be clipped.
                        Row(
                            Modifier.fillMaxWidth().fillMaxHeight(),
                            horizontalArrangement = Arrangement.spacedBy(RecentGamesRowPolicy.ITEM_GAP_DP.dp),
                        ) {
                            visibleGames.forEach { game ->
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    RecentGamesItem(
                                        game = game,
                                        modifier = Modifier.fillMaxSize(),
                                        compact = compact,
                                        styleKey = styleKey,
                                        colors = colors,
                                        onOpenGame = onOpenGame,
                                        onLaunchGame = onLaunchGame,
                                    )
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            Modifier.fillMaxSize().clipToBounds(),
                            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm),
                            contentPadding =
                                PaddingValues(
                                    end = if (showThreeAcross) 0.dp else KireiSpacing.xl,
                                ),
                            userScrollEnabled = true,
                        ) {
                            items(visibleGames, key = { it.id }) { game ->
                                RecentGamesItem(
                                    game = game,
                                    modifier = Modifier.width(cardWidth.dp).fillMaxHeight(),
                                    compact = compact,
                                    styleKey = styleKey,
                                    colors = colors,
                                    onOpenGame = onOpenGame,
                                    onLaunchGame = onLaunchGame,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecentGamesItem(
    game: Game,
    modifier: Modifier = Modifier,
    compact: Boolean,
    styleKey: String,
    colors: KireiColors,
    onOpenGame: (Game) -> Unit,
    onLaunchGame: (Game) -> Unit,
) {
    PressScaleBox(
        modifier,
        KireiShapes.md,
        { onOpenGame(game) },
        pressedScale = .98f,
    ) {
        if (styleKey == DashboardStyles.RECENT_COVERS) {
            Box(Modifier.fillMaxSize().background(colors.surfaceRaised)) {
                GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop, KireiShapes.Radius.md)
                Box(Modifier.fillMaxSize().background(coverScrim(.82f)))
                Text(
                    game.title.orEmpty(),
                    color = KireiTokens.OnImage.ink,
                    style = AppleTypography.bodyLargeEmphasis,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(KireiSpacing.sm),
                )
            }
        } else {
            Row(
                Modifier.fillMaxSize().background(colors.surfaceRaised).padding(KireiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameCoverArtwork(
                    game,
                    Modifier.size(if (compact) 42.dp else 52.dp, if (compact) 52.dp else 66.dp),
                    ContentScale.Crop,
                    KireiShapes.Radius.sm,
                )
                Column(Modifier.weight(1f).padding(horizontal = if (compact) KireiSpacing.xs else KireiSpacing.sm)) {
                    Text(
                        game.title.orEmpty(),
                        color = colors.text,
                        style = AppleTypography.bodyLargeEmphasis,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        game.engine?.displayName.orEmpty(),
                        color = colors.secondary,
                        style = AppleTypography.labelLarge,
                        maxLines = 1,
                    )
                }
                PlayTriangleButton(colors, "启动 ${game.title.orEmpty()}", 48.dp, onClick = { onLaunchGame(game) })
            }
        }
    }
}

/** Dark ramp that keeps white titles readable over any cover art, in both themes. */
private fun coverScrim(alpha: Float): Brush = Brush.verticalGradient(listOf(Color.Transparent, KireiTokens.Immersive.DarkBase.copy(alpha = alpha)))

@Composable
internal fun DashboardFavoritesWidget(
    games: List<Game>,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit
) {
    GlassSurface(
        colors,
        haze,
        Modifier.fillMaxSize(),
        KireiShapes.lg,
        role = KireiGlassRole.CONTENT,
        onClick = onOpenLibrary
    ) {
        if (styleKey == DashboardStyles.FAVORITES_COUNT) {
            Column(
                Modifier.fillMaxSize().padding(KireiSpacing.md),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${games.size}", color = colors.danger, style = AppleTypography.numericLarge)
                Text("款收藏", color = colors.secondary, style = AppleTypography.labelLarge)
            }
            return@GlassSurface
        }
        if (styleKey == DashboardStyles.FAVORITES_FEATURED && games.isNotEmpty()) {
            Box(Modifier.fillMaxSize()) {
                GameCoverArtwork(games.first(), Modifier.fillMaxSize(), ContentScale.Crop, KireiShapes.Radius.lg)
                Box(Modifier.fillMaxSize().background(coverScrim(.82f)))
                Column(Modifier.align(Alignment.BottomStart).padding(KireiSpacing.md)) {
                    Text("收藏主封面", color = KireiTokens.OnImage.ink.copy(alpha = .72f), style = AppleTypography.labelMedium)
                    Text(
                        games.first().title.orEmpty(),
                        color = KireiTokens.OnImage.ink,
                        style = AppleTypography.bodyLargeEmphasis,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            return@GlassSurface
        }
        Column(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, tint = colors.danger, modifier = Modifier.size(24.dp))
                Text("收藏", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(start = KireiSpacing.xs))
            }
            Text("${games.size} 部游戏", color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(top = KireiSpacing.xxs))
            if (games.isNotEmpty()) {
                Row(
                    Modifier.padding(top = KireiSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(if (styleKey == DashboardStyles.FAVORITES_MOSAIC) KireiSpacing.xs else (-KireiSpacing.xs))
                ) {
                    games.take(3).forEach { game ->
                        Box(
                            if (styleKey == DashboardStyles.FAVORITES_MOSAIC) {
                                Modifier.size(40.dp, 52.dp).clip(KireiShapes.sm).background(colors.surfaceRaised)
                            } else {
                                Modifier.size(40.dp).clip(KireiShapes.full).background(colors.surfaceRaised)
                                    .border(2.dp, colors.surface, KireiShapes.full)
                            }
                        ) {
                            GameCoverArtwork(game, Modifier.fillMaxSize(), ContentScale.Crop, KireiShapes.Radius.sm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardTrendWidget(
    points: List<PlayTrendPoint>,
    spec: DashboardWidgetSpec,
    colors: KireiColors,
    haze: HazeState?,
    interactive: Boolean = true,
) {
    val compact = spec.size.columns == 1
    val model =
        remember(points, spec.trendMetric, spec.trendRange, compact) {
            TrendChartPolicy.chart(points, spec.trendMetric, spec.trendRange, compact)
        }
    val dateLabel = remember { java.text.SimpleDateFormat("M/d", java.util.Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    var chartWidth by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    fun selectAt(x: Float) {
        val next = TrendChartPolicy.nearestIndex(x, chartWidth, model.values.size)
        if (next != selectedIndex) {
            val previous = selectedIndex
            selectedIndex = next.takeIf { it >= 0 }
            if (TrendChartPolicy.shouldHaptic(previous, next)) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
    GlassSurface(colors, haze, Modifier.fillMaxSize(), KireiShapes.lg) {
        Column(Modifier.fillMaxSize().padding(horizontal = KireiSpacing.md, vertical = KireiSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        model.title,
                        color = colors.text,
                        style = AppleTypography.bodyLargeEmphasis
                    )
                    Text(
                        model.summary,
                        color = colors.secondary,
                        style = AppleTypography.labelLarge
                    )
                }
                Icon(Icons.Rounded.ShowChart, null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(top = KireiSpacing.xs)) {
                Column(
                    Modifier.width(40.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End,
                ) {
                    model.axis.ticks.reversed().forEach { tick ->
                        Text("${tick.toInt()} ${model.unit}", color = colors.secondary, style = AppleTypography.labelMedium)
                    }
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().onSizeChanged { chartWidth = it.width.toFloat() }
                        .then(
                            if (interactive) {
                                Modifier.pointerInput(model.values.size, chartWidth) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { selectAt(it.x) },
                                        onDragCancel = { selectedIndex = null },
                                        onDragEnd = { selectedIndex = null },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            selectAt(change.position.x)
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val values = model.values
                        if (values.isEmpty()) return@Canvas
                        model.axis.ticks.forEach { tick ->
                            val y = size.height - tick / model.axis.maximum * size.height
                            drawLine(colors.strokeStrong, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        val step = if (values.size <= 1) size.width else size.width / (values.size - 1)
                        val line = Path()
                        values.forEachIndexed { index, value ->
                            val x = if (values.size <= 1) size.width / 2f else step * index
                            val y = size.height - value / model.axis.maximum * size.height
                            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
                        }
                        if (spec.trendVisualStyle == TrendVisualStyle.AREA) {
                            drawPath(
                                Path().apply {
                                    addPath(line)
                                    lineTo(size.width, size.height)
                                    lineTo(0f, size.height)
                                    close()
                                },
                                colors.accentContainer
                            )
                        }
                        drawPath(line, colors.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                        selectedIndex?.takeIf { it in values.indices }?.let { index ->
                            val x = if (values.size <= 1) size.width / 2f else step * index
                            val y = size.height - values[index] / model.axis.maximum * size.height
                            drawLine(colors.accent, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            drawCircle(colors.accent, 4.dp.toPx(), Offset(x, y))
                        }
                    }
                    selectedIndex?.takeIf { it in model.values.indices }?.let { index ->
                        val point = points.takeLast(spec.trendRange.days).getOrNull(index)
                        Text(
                            "${point?.let { dateLabel.format(Date(it.dayStart)) } ?: ""} · ${model.values[index].toInt()} ${model.unit}",
                            color = colors.background,
                            style = AppleTypography.numericSmall,
                            modifier =
                                Modifier.align(Alignment.TopCenter).padding(KireiSpacing.xxs)
                                    .background(colors.text, KireiShapes.sm)
                                    .padding(horizontal = KireiSpacing.xs, vertical = KireiSpacing.xxs),
                        )
                    }
                }
            }
            if (model.dateTicks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Spacer(Modifier.width(40.dp))
                    model.dateTicks.forEach { dayStart ->
                        Text(
                            dateLabel.format(Date(dayStart)),
                            color = colors.secondary,
                            style = AppleTypography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DashboardLibraryShortcut(
    gameCount: Int,
    playedCount: Int,
    styleKey: String,
    colors: KireiColors,
    haze: HazeState?,
    onOpenLibrary: () -> Unit
) {
    GlassSurface(
        colors,
        haze,
        Modifier.fillMaxSize(),
        KireiShapes.lg,
        role = KireiGlassRole.CONTENT,
        onClick = onOpenLibrary
    ) {
        Column(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.GridView, null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Text("游戏库", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = KireiSpacing.xs))
            Text(
                if (styleKey == DashboardStyles.LIBRARY_PROGRESS) {
                    "已打开 $playedCount / $gameCount 款"
                } else {
                    "$gameCount 部游戏"
                },
                color = colors.secondary,
                style = AppleTypography.bodySmall
            )
            if (styleKey == DashboardStyles.LIBRARY_PROGRESS) {
                KireiProgressBar(
                    progress = if (gameCount == 0) 0f else playedCount.toFloat() / gameCount,
                    colors = colors,
                    modifier = Modifier.padding(top = KireiSpacing.sm),
                )
            }
        }
    }
}
