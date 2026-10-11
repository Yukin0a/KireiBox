package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiLargeTitle
import com.kireibox.designsystem.components.KireiLargeTopBar
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiSkeleton
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.designsystem.components.KireiTopBarSlots
import com.kireibox.designsystem.components.LocalKireiListDivider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

// Frame shared by the file/app/shortcut pickers: the settings sub-page chrome (large title that
// collapses into a glass top bar) over a lazy list whose rows read as one KireiListGroup card,
// and a safe bottom bar for the page's single primary action.

/**
 * What a picker page shows around its list: [title] and back action, optional [topActions] in the bar,
 * the page's one primary [bottomAction], and a [scrollResetKey] whose change scrolls the list back to its top.
 */
internal data class PickerPageSpec(
    val title: String,
    val onBack: () -> Unit,
    val topActions: (@Composable RowScope.() -> Unit)? = null,
    val bottomAction: PickerAction? = null,
    val scrollResetKey: Any? = null,
)

/** The page's primary action, drawn as a PRIMARY pill in the bottom bar. */
internal data class PickerAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

/** Widest the picker lists grow: landscape and tablets keep a centered column like the settings pages. */
internal val PickerContentMaxWidth = 640.dp

private const val PICKER_TITLE_KEY = "picker-title"

@Composable
internal fun PickerPage(
    spec: PickerPageSpec,
    colors: KireiColors,
    haze: HazeState?,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    // A new folder (or any other reset key) starts at its top, where the large title and the location row are.
    LaunchedEffect(spec.scrollResetKey) { listState.scrollToItem(0) }
    val action = spec.bottomAction
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // The keyboard is left out on purpose: the page must not recompose for every IME animation frame.
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
    // A bar inside the shell's own hazeSource cannot blur that source, so the list is its own source.
    val pageHaze = remember { HazeState() }
    val barHaze = pageHaze.takeIf { haze != null }
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val gutter = AdaptiveLayoutConstants.PAGE_GUTTER_DP.dp
        val left = insets.calculateLeftPadding(direction)
        val right = insets.calculateRightPadding(direction)
        val room = (maxWidth - left - right - gutter * 2).coerceAtLeast(0.dp)
        val extra = (room - PickerContentMaxWidth).coerceAtLeast(0.dp) / 2f
        val sideStart = left + gutter + extra
        val sideEnd = right + gutter + extra
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize().then(if (barHaze != null) Modifier.hazeSource(barHaze) else Modifier),
                    state = listState,
                    contentPadding =
                        PaddingValues(
                            start = sideStart,
                            top = top + KireiTopBarDefaults.CompactHeight,
                            end = sideEnd,
                            bottom = if (action == null) bottom + KireiSpacing.xl else KireiSpacing.md,
                        ),
                ) {
                    item(key = PICKER_TITLE_KEY) {
                        KireiLargeTitle(
                            spec.title,
                            colors,
                            Modifier.padding(bottom = KireiSpacing.sm).graphicsLayer {
                                alpha = KireiTopBarDefaults.largeTitleAlpha(KireiTopBarDefaults.collapseFraction(listState, density))
                            },
                        )
                    }
                    content()
                }
                KireiLargeTopBar(
                    title = spec.title,
                    collapse = { KireiTopBarDefaults.collapseFraction(listState, density) },
                    colors = colors,
                    modifier = Modifier.align(Alignment.TopCenter),
                    slots =
                        KireiTopBarSlots(
                            navigation = { GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", colors, barHaze, spec.onBack) },
                            actions = spec.topActions,
                            haze = barHaze,
                            topInset = top,
                            // The slot adds xxs itself: the back button's edge sits on the content edge.
                            horizontalInset = (sideStart - KireiSpacing.xxs).coerceAtLeast(0.dp),
                        ),
                )
            }
            if (action != null) PickerActionBar(action, colors, sideStart, sideEnd, bottom)
        }
    }
}

@Composable
private fun PickerActionBar(
    action: PickerAction,
    colors: KireiColors,
    start: Dp,
    end: Dp,
    bottomInset: Dp,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(colors.background)
            .drawBehind {
                val stroke = KireiElevation.strokeWidth.toPx()
                drawLine(colors.stroke, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
            }
            .padding(start = start, end = end, top = KireiSpacing.sm, bottom = KireiSpacing.sm + bottomInset),
    ) {
        KireiButton(action.label, colors, Modifier.fillMaxWidth(), KireiButtonOptions(enabled = action.enabled), action.onClick)
    }
}

/** Space between two blocks of a picker list (a group and the next caption). */
internal fun LazyListScope.pickerGap(
    key: Any,
    height: Dp = KireiSpacing.groupGap,
) {
    item(key = key) { Spacer(Modifier.height(height)) }
}

/** Group caption above a block of rows (labelLarge, secondary), aligned with the row text like KireiListGroup's. */
internal fun LazyListScope.groupCaption(
    title: String,
    colors: KireiColors,
    key: Any,
) {
    item(key = key) {
        Text(
            title,
            color = colors.secondary,
            style = AppleTypography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = KireiSpacing.groupInner)
                    .padding(bottom = KireiSpacing.groupCaptionGap)
                    .semantics { heading() },
        )
    }
}

private val FirstSliceShape = KireiShapes.lg.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
private val LastSliceShape = KireiShapes.lg.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)

/**
 * [items] as lazy rows that together look like one KireiListGroup card (lg corners, hairline
 * stroke, divider drawn by each KireiListRow under its text): a plain Column group would compose
 * and decode every row of a long list at once.
 */
internal fun <T : Any> LazyListScope.groupItems(
    items: List<T>,
    colors: KireiColors,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
        Box(Modifier.groupSlice(first = index == 0, last = index == items.lastIndex, colors = colors)) {
            CompositionLocalProvider(LocalKireiListDivider provides (index < items.lastIndex)) { row(item) }
        }
    }
}

/** A single-row group: empty and error states, so a caption never floats over nothing. */
internal fun LazyListScope.groupStateRow(
    key: Any,
    colors: KireiColors,
    spec: KireiListRowSpec,
) {
    groupItems(listOf(key), colors, { it }) { KireiListRow(spec, colors) }
}

/** Loading placeholder: a few skeleton rows in group form. */
internal fun LazyListScope.groupSkeleton(colors: KireiColors) {
    groupItems(List(SKELETON_ROWS) { it }, colors, { "picker-skeleton-$it" }) { index ->
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = KireiSpacing.rowMinHeightTwoLine)
                .padding(horizontal = KireiSpacing.groupInner, vertical = KireiSpacing.rowVerticalPadding)
                .clearAndSetSemantics { if (index == 0) contentDescription = "加载中" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KireiSkeleton(colors, Modifier.size(KireiSpacing.iconTileSize), KireiShapes.sm)
            Spacer(Modifier.width(KireiSpacing.iconTileTextGap))
            Column(verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                KireiSkeleton(colors, Modifier.width(SKELETON_TITLE_WIDTH).height(SKELETON_TITLE_HEIGHT), KireiShapes.xs)
                KireiSkeleton(colors, Modifier.width(SKELETON_SUBTITLE_WIDTH).height(SKELETON_SUBTITLE_HEIGHT), KireiShapes.xs)
            }
        }
    }
}

private const val SKELETON_ROWS = 4
private val SKELETON_TITLE_WIDTH = 160.dp
private val SKELETON_TITLE_HEIGHT = 14.dp
private val SKELETON_SUBTITLE_WIDTH = 96.dp
private val SKELETON_SUBTITLE_HEIGHT = 10.dp

/**
 * One row's slice of the group card: the card shape's corners on the first/last slice and the
 * card's hairline stroke drawn over the content. The stroke is the whole card's rounded rect
 * extended past the slice, so neighbouring corners and edges fall outside the clip.
 */
private fun Modifier.groupSlice(
    first: Boolean,
    last: Boolean,
    colors: KireiColors,
): Modifier {
    val shape =
        when {
            first && last -> KireiShapes.lg
            first -> FirstSliceShape
            last -> LastSliceShape
            else -> RectangleShape
        }
    return fillMaxWidth()
        .clip(shape)
        .background(colors.surface)
        .drawWithContent {
            drawContent()
            val stroke = KireiElevation.strokeWidth.toPx()
            val half = stroke / 2f
            val radius = KireiShapes.Radius.lg.toPx()
            val reach = radius * 2f
            val top = if (first) half else -reach
            val bottom = if (last) size.height - half else size.height + reach
            drawRoundRect(
                color = colors.stroke,
                topLeft = Offset(half, top),
                size = Size(size.width - stroke, bottom - top),
                cornerRadius = CornerRadius(radius - half),
                style = Stroke(stroke),
            )
        }
}
