package com.kireibox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kireibox.cover.CoverQueueStatus
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskContainer
import com.kireibox.cover.CoverTaskPresentationPolicy
import com.kireibox.cover.CoverTaskRowAction
import com.kireibox.cover.CoverTaskStatus
import com.kireibox.cover.CoverTaskUi
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiAccentTokens
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiProgressBar
import com.kireibox.designsystem.components.KireiSheet
import com.kireibox.designsystem.components.KireiSheetSpec
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.components.KireiStatusTone
import com.kireibox.designsystem.components.LocalKireiListDivider
import com.kireibox.designsystem.components.kireiListDivider
import com.kireibox.designsystem.dangerSolid
import com.yuki.yukihub.model.Game
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Cover task toolbar button, popup, bottom sheet and centre content.

@Composable
internal fun CoverTaskToolbarAnchor(
    queue: CoverQueueUiState,
    panelOpen: Boolean,
    colors: KireiColors,
    haze: HazeState?,
    layoutMode: KireiLayoutMode,
    windowWidth: Dp,
    windowHeight: Dp,
    safeTop: Dp,
    safeRight: Dp,
    safeBottom: Dp,
    listState: LazyListState,
    viewModel: KireiViewModel,
) {
    val metrics =
        CoverTaskPresentationPolicy.anchoredContainerMetrics(
            windowWidthDp = windowWidth.value,
            windowHeightDp = windowHeight.value,
            safeTopDp = safeTop.value,
            safeRightDp = safeRight.value,
            safeBottomDp = safeBottom.value,
        )
    Box {
        CoverTaskButton(
            queue = queue,
            colors = colors,
            haze = haze,
            onClick = {
                if (panelOpen) viewModel.closeCoverTaskCenter() else viewModel.openCoverTaskCenter()
            },
        )
        if (panelOpen && CoverTaskPresentationPolicy.containerFor(layoutMode) == CoverTaskContainer.ANCHORED_POPOVER) {
            CoverTaskPopup(
                queue = queue,
                listState = listState,
                colors = colors,
                metrics = metrics,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
internal fun CoverTaskButton(
    queue: CoverQueueUiState,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit,
) {
    val state = CoverTaskPresentationPolicy.buttonState(queue.tasks)
    val layout = Alpha11KireiBoxAppIntegration.taskButtonLayout(state.pendingCount)
    // Progress always reads in the accent (a dark red ring vanishes on the dark glass); a failure shows in the glyph and the badge.
    val progressColor = colors.accent
    Box(Modifier.size(layout.buttonSizeDp.dp)) {
        GlassSurface(
            colors = colors,
            haze = haze,
            modifier =
                Modifier.size(layout.buttonSizeDp.dp).semantics(mergeDescendants = true) {
                    contentDescription = state.contentDescription
                },
            shape = CircleShape,
            role = KireiGlassRole.ICON,
            onClick = onClick,
            opaqueFill = true,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                    val ring = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = colors.surface3,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = ring,
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.aggregateProgress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = ring,
                    )
                }
                Icon(
                    imageVector = if (state.showCompletionCheck) Icons.Rounded.Check else Icons.Rounded.CollectionsBookmark,
                    contentDescription = null,
                    tint =
                        when {
                            state.hasFailure -> colors.danger
                            state.showCompletionCheck -> colors.success
                            else -> colors.text
                        },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (layout.badgeVisible) {
            // The AA badge pair (spec §2.1): plain danger with white digits misses 4.5:1 at this size.
            val badgeInk = if (state.hasFailure) colors.dangerSolid else KireiAccentTokens(colors.accent, colors.onAccent)
            Box(
                Modifier.align(Alignment.TopEnd)
                    .height(layout.badgeHeightDp.dp)
                    .sizeIn(minWidth = layout.badgeMinWidthDp.dp)
                    .background(badgeInk.accent, KireiShapes.full)
                    .border(1.dp, colors.surfaceOverlay, KireiShapes.full)
                    .padding(horizontal = layout.badgeHorizontalPaddingDp.dp)
                    .semantics {
                        contentDescription = "待处理 ${layout.badgeAccessibilityCount}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    layout.badgeLabel,
                    color = badgeInk.onAccent,
                    style = AppleTypography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun CoverTaskPopup(
    queue: CoverQueueUiState,
    listState: LazyListState,
    colors: KireiColors,
    metrics: CoverTaskPresentationPolicy.AnchoredContainerMetrics,
    viewModel: KireiViewModel,
) {
    val offsetY = with(LocalDensity.current) { 56.dp.roundToPx() }
    // The popover has the view model at hand, so its rows can show the games' covers.
    val games by remember(viewModel) { viewModel.state.map { it.games }.distinctUntilChanged() }.collectAsState(emptyList())
    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, offsetY),
        onDismissRequest = viewModel::closeCoverTaskCenter,
        properties =
            PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        // e3 container (surfaceOverlay + strokeStrong hairline): a window-sized popup would clip a shadow anyway.
        Box(
            Modifier.width(metrics.widthDp.dp)
                .heightIn(max = metrics.maxHeightDp.dp)
                .clip(KireiShapes.xl)
                .background(colors.surfaceOverlay)
                .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.xl),
        ) {
            CoverTaskCenterContent(
                model = CoverTaskCenterModel(queue, games, (metrics.maxHeightDp.dp - CoverTaskChromeHeight).coerceAtLeast(0.dp)),
                listState = listState,
                colors = colors,
                actions =
                    CoverTaskActions(
                        onPause = viewModel::pauseCoverQueue,
                        onResume = viewModel::resumeCoverQueue,
                        onReview = viewModel::openCoverReview,
                        onRetry = viewModel::retryCoverTask,
                        onCancel = viewModel::cancelCoverTask,
                        onClose = viewModel::closeCoverTaskCenter,
                    ),
            )
        }
    }
}

/**
 * The portrait sheet. [games] lets the rows show their covers; without them (the default) a row
 * falls back to a neutral tile.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
internal fun CoverTaskBottomSheet(
    queue: CoverQueueUiState,
    listState: LazyListState,
    colors: KireiColors,
    onClose: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReview: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onCancel: (Long) -> Unit,
    games: List<Game> = emptyList(),
) {
    // Shared KireiSheet look (handle, xl top corners, scrim token); the task list scrolls itself.
    KireiSheet(colors, onClose, KireiSheetSpec(scrollable = false, maxContentHeight = CoverTaskSheetMaxHeight)) {
        ModalSystemBarVisibilityEffect(isDark = colors.isDark)
        CoverTaskCenterContent(
            model = CoverTaskCenterModel(queue, games, CoverTaskSheetListHeight),
            listState = listState,
            colors = colors,
            // The sheet closes by its handle, scrim and back: no close button of its own.
            actions = CoverTaskActions(onPause, onResume, onReview, onRetry, onCancel),
        )
    }
}

/** Header + the 480dp task list, so the sheet never caps the list. */
private val CoverTaskSheetMaxHeight = 600.dp
private val CoverTaskSheetListHeight = 480.dp

/** Vertical room of the header row and the panel padding around the list, for the popover's list cap. */
private val CoverTaskChromeHeight = 88.dp

/** What the task center shows: the queue, the games whose covers lead the rows, and how tall the list may grow. */
internal data class CoverTaskCenterModel(
    val queue: CoverQueueUiState,
    val games: List<Game>,
    val listMaxHeight: Dp,
)

/** Everything a task row or the header can trigger; [onClose] is null where the container closes itself. */
internal data class CoverTaskActions(
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onReview: (Long) -> Unit,
    val onRetry: (Long) -> Unit,
    val onCancel: (Long) -> Unit,
    val onClose: (() -> Unit)? = null,
)

@Composable
internal fun CoverTaskCenterContent(
    model: CoverTaskCenterModel,
    listState: LazyListState,
    colors: KireiColors,
    actions: CoverTaskActions,
) {
    val queue = model.queue
    val running = queue.queueStatus == CoverQueueStatus.RUNNING
    val gamesById = remember(model.games) { model.games.associateBy { it.id } }
    Column(Modifier.fillMaxWidth().padding(vertical = KireiSpacing.md)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TaskHeaderMinHeight).padding(horizontal = KireiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
        ) {
            Text("封面任务", color = colors.text, style = AppleTypography.headlineMedium, modifier = Modifier.weight(1f).semantics { heading() })
            Text("${(queue.aggregateProgress * 100).toInt()}%", color = colors.secondary, style = AppleTypography.numericMedium)
            KireiButton(
                if (running) "暂停" else "继续",
                colors,
                options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL),
                onClick = if (running) actions.onPause else actions.onResume,
            )
            actions.onClose?.let { close ->
                KireiButton("关闭", colors, options = KireiButtonOptions(style = KireiButtonStyle.PLAIN, size = KireiButtonSize.SMALL), onClick = close)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = model.listMaxHeight),
            state = listState,
            contentPadding = PaddingValues(horizontal = KireiSpacing.md),
        ) {
            CoverTaskPresentationPolicy.sections(queue.tasks).forEach { section -> taskSection(section, gamesById, colors, actions) }
        }
    }
}

private val TaskHeaderMinHeight = 48.dp

/** Section caption and its rows as one group card; a gap closes the section. */
private fun LazyListScope.taskSection(
    section: CoverTaskPresentationPolicy.Section,
    gamesById: Map<Long, Game>,
    colors: KireiColors,
    actions: CoverTaskActions,
) {
    groupCaption(section.title, colors, key = section.title)
    groupItems(section.tasks, colors, { it.gameId }) { task -> CoverTaskRow(task, gamesById[task.gameId], colors, actions) }
    pickerGap(key = "gap-${section.title}", height = KireiSpacing.md)
}

private val TaskThumbWidth = 36.dp
private val TaskThumbHeight = 48.dp
private val TaskThumbGlyph = 18.dp
private val TaskTextStart = KireiSpacing.groupInner + TaskThumbWidth + KireiSpacing.iconTileTextGap

/**
 * One task: cover, title, state chip with the source, a progress bar while downloading or the
 * failure reason, and the row's action. Laid out like a KireiListRow (same metrics and divider) —
 * the row component has no slot for a chip and a bar under the title.
 */
@Composable
private fun CoverTaskRow(
    task: CoverTaskUi,
    game: Game?,
    colors: KireiColors,
    actions: CoverTaskActions,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = KireiSpacing.rowMinHeightTwoLine)
            .kireiListDivider(LocalKireiListDivider.current, colors, TaskTextStart)
            .padding(horizontal = KireiSpacing.groupInner, vertical = KireiSpacing.rowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverTaskThumbnail(game, colors)
        Spacer(Modifier.width(KireiSpacing.iconTileTextGap))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
            Text(task.title, color = colors.text, style = AppleTypography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                KireiStatusChip(CoverTaskPresentationPolicy.chipLabel(task), taskTone(task.status), colors)
                Text(task.source.label, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (task.status) {
                CoverTaskStatus.DOWNLOADING -> {
                    KireiProgressBar(CoverTaskPresentationPolicy.downloadProgress(task), colors, Modifier.padding(top = KireiSpacing.xxs))
                    Text(CoverTaskPresentationPolicy.downloadBytesLabel(task), color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 1)
                }
                CoverTaskStatus.FAILED ->
                    task.error?.let { Text(it, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                else -> Unit
            }
        }
        CoverTaskRowButton(task, colors, actions)
    }
}

@Composable
private fun CoverTaskRowButton(
    task: CoverTaskUi,
    colors: KireiColors,
    actions: CoverTaskActions,
) {
    val action = CoverTaskPresentationPolicy.rowActionFor(task.status)
    if (action == CoverTaskRowAction.NONE) return
    val label = CoverTaskPresentationPolicy.actionLabel(action)
    val (style, onClick) =
        when (action) {
            CoverTaskRowAction.SELECT -> KireiButtonStyle.SECONDARY to { actions.onReview(task.gameId) }
            CoverTaskRowAction.RETRY -> KireiButtonStyle.SECONDARY to { actions.onRetry(task.gameId) }
            CoverTaskRowAction.UNDO -> KireiButtonStyle.TEXT to { actions.onRetry(task.gameId) }
            else -> KireiButtonStyle.PLAIN to { actions.onCancel(task.gameId) }
        }
    Spacer(Modifier.width(KireiSpacing.sm))
    KireiButton(
        label,
        colors,
        // Names the game: a list of identical "取消" buttons tells TalkBack nothing.
        Modifier.semantics { contentDescription = "$label ${task.title}" },
        KireiButtonOptions(style = style, size = KireiButtonSize.SMALL),
        onClick,
    )
}

/** Failure and review are the rows that need the user (warning dot); running work is accent, finished work positive. */
private fun taskTone(status: CoverTaskStatus): KireiStatusTone =
    when (status) {
        CoverTaskStatus.NEEDS_REVIEW, CoverTaskStatus.FAILED -> KireiStatusTone.WARNING
        CoverTaskStatus.DOWNLOADING -> KireiStatusTone.ACCENT
        CoverTaskStatus.COMPLETED -> KireiStatusTone.POSITIVE
        CoverTaskStatus.QUEUED, CoverTaskStatus.SEARCHING, CoverTaskStatus.CANCELLED -> KireiStatusTone.NEUTRAL
    }

@Composable
private fun CoverTaskThumbnail(
    game: Game?,
    colors: KireiColors,
) {
    val size = Modifier.size(TaskThumbWidth, TaskThumbHeight)
    if (game != null) {
        GameCoverArtwork(
            game = game,
            modifier = size.border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.sm),
            contentScale = ContentScale.Crop,
            cornerRadius = KireiShapes.Radius.sm,
            role = CoverImageRole.THUMBNAIL,
            showFallbackMetadata = false,
        )
    } else {
        Box(size.background(colors.surfaceRaised, KireiShapes.sm), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(TaskThumbGlyph))
        }
    }
}
