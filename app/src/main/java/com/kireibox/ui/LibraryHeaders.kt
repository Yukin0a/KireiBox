package com.kireibox.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiChip
import com.kireibox.designsystem.components.KireiLargeTitle
import com.kireibox.designsystem.components.KireiLargeTopBar
import com.kireibox.designsystem.components.KireiMenu
import com.kireibox.designsystem.components.KireiMenuItem
import com.kireibox.designsystem.components.KireiMenuItemSpec
import com.kireibox.designsystem.components.KireiSearchField
import com.kireibox.designsystem.components.KireiSearchFieldOptions
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.designsystem.components.KireiTopBarSlots
import com.yuki.yukihub.model.EngineType
import dev.chrisbanes.haze.HazeState

// Library top chrome. Portrait: a header block (large title, count, search + sort, engine chips) that
// scrolls with the grid under the shared KireiLargeTopBar, which only carries 添加 / the task button
// (or 完成 while editing). Landscape: no large title, one compact bar holds the whole toolbar. The
// bar's glass fades in as cards scroll under it. LibraryPage owns every piece of state; it flows in
// through the parameter objects.

/** Everything the library chrome renders, resolved by LibraryPage. */
internal data class LibraryHeaderModel(
    val colors: KireiColors,
    /** Glass source of the bar (null: no blur, the bar falls back to its opaque surface). */
    val haze: HazeState?,
    val visibleCount: Int,
    val query: String,
    val filter: EngineType?,
    val engines: List<EngineType>,
    val editMode: Boolean,
    val sortValue: String,
    /** No game at all: the filter chips have nothing to filter. */
    val libraryEmpty: Boolean,
    val taskButtonVisible: Boolean,
    /** Landscape filter menu. */
    val filterMenuExpanded: Boolean,
)

/** User intents raised by the library chrome; LibraryPage applies them to its state. */
internal data class LibraryHeaderActions(
    val onQueryChange: (String) -> Unit,
    val onSearchFocusChange: (Boolean) -> Unit,
    val onSortChange: (String) -> Unit,
    val onSelectFilter: (EngineType?) -> Unit,
    val onToggleFilterMenu: () -> Unit,
    val onDismissFilterMenu: () -> Unit,
    val onFinishEdit: () -> Unit,
    val onAddGame: () -> Unit,
)

/**
 * Where the bar and the header block sit. [gutterLeft]/[gutterRight] are the page gutters (the chip
 * row bleeds into them), [topInset] the safe-area top, [searchWidth] the landscape search capsule
 * and [collapse] the 0..1 scroll fraction, read in draw scopes only.
 */
internal data class LibraryBarFrame(
    val landscape: Boolean,
    val topInset: Dp,
    val gutterLeft: Dp,
    val gutterRight: Dp,
    val searchWidth: Dp,
    val collapse: () -> Float,
)

/** Dim and block input on [inert] controls: edit mode keeps the grid steady but numbering needs the full library. */
private fun Modifier.inertWhile(inert: Boolean): Modifier =
    if (!inert) {
        this
    } else {
        graphicsLayer { alpha = EDIT_DIM_ALPHA }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { }
    }

private const val EDIT_DIM_ALPHA = .6f

/** Portrait header block: the first grid item. Title and count fade out as the bar's compact title fades in. */
@Composable
internal fun LibraryPortraitHeader(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
    frame: LibraryBarFrame,
) {
    val colors = model.colors
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.graphicsLayer { alpha = KireiTopBarDefaults.largeTitleAlpha(frame.collapse()) }) {
            KireiLargeTitle("游戏库", colors)
            // An empty library has nothing to count, search, sort or filter: the empty state below says so.
            if (!model.libraryEmpty) {
                Text("${model.visibleCount} 部游戏", color = colors.secondary, style = AppleTypography.bodyMedium, maxLines = 1)
            }
        }
        if (!model.libraryEmpty) {
            Row(
                Modifier.fillMaxWidth().padding(top = KireiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
            ) {
                LibrarySearchField(model, actions, Modifier.weight(1f))
                LibraryEditSortMenu(model.sortValue, model.colors, actions.onSortChange, model.haze)
            }
            LibraryEngineChipRow(
                LibraryEngineChipRowSpec(model.filter, model.engines, frame.gutterLeft, frame.gutterRight, enabled = !model.editMode),
                colors,
                actions.onSelectFilter,
            )
        }
    }
}

@Composable
private fun LibrarySearchField(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
    modifier: Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Scrolled out of the grid, the field cannot hold focus any more (and may never say so).
    DisposableEffect(Unit) { onDispose { actions.onSearchFocusChange(false) } }
    Box(modifier.inertWhile(model.editMode)) {
        KireiSearchField(
            value = model.query,
            onValueChange = actions.onQueryChange,
            colors = model.colors,
            options =
                KireiSearchFieldOptions(
                    placeholder = "搜索游戏",
                    onFocusChange = actions.onSearchFocusChange,
                    onSearch = { focusManager.clearFocus() },
                ),
        )
    }
}

/**
 * The glass bar over the grid. Portrait shows the compact title once the large one scrolled away;
 * landscape keeps its left-aligned title, search and actions in the bar at all times.
 */
@Composable
internal fun LibraryTopBar(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
    frame: LibraryBarFrame,
    taskAnchor: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The slots add xxs themselves: buttons and title line up with the page gutter.
    val inset = (frame.gutterLeft - KireiSpacing.xxs).coerceAtLeast(0.dp)
    if (frame.landscape) {
        KireiLargeTopBar(
            title = "",
            collapse = frame.collapse,
            colors = model.colors,
            modifier = modifier,
            slots =
                KireiTopBarSlots(
                    navigation = { LibraryLandscapeTitle(model) },
                    actions = { LibraryLandscapeActions(model, actions, frame.searchWidth, taskAnchor) },
                    haze = model.haze,
                    topInset = frame.topInset,
                    horizontalInset = inset,
                ),
        )
    } else {
        KireiLargeTopBar(
            title = "游戏库",
            collapse = frame.collapse,
            colors = model.colors,
            modifier = modifier,
            slots =
                KireiTopBarSlots(
                    actions = { LibraryPortraitActions(model, actions, taskAnchor) },
                    haze = model.haze,
                    topInset = frame.topInset,
                    horizontalInset = inset,
                ),
        )
    }
}

@Composable
private fun LibraryPortraitActions(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
    taskAnchor: @Composable () -> Unit,
) {
    if (model.editMode) {
        LibraryDoneButton(model.colors, actions.onFinishEdit)
        return
    }
    if (model.taskButtonVisible) {
        taskAnchor()
        Spacer(Modifier.width(KireiSpacing.xxs))
    }
    GlassIconButton(Icons.Rounded.Add, "添加游戏", model.colors, model.haze, onClick = actions.onAddGame, opaqueFill = true)
}

@Composable
private fun LibraryDoneButton(
    colors: KireiColors,
    onFinishEdit: () -> Unit,
) {
    KireiButton("完成", colors, options = KireiButtonOptions(size = KireiButtonSize.MEDIUM, icon = Icons.Rounded.Done), onClick = onFinishEdit)
}

@Composable
private fun LibraryLandscapeTitle(model: LibraryHeaderModel) {
    val colors = model.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("游戏库", color = colors.text, style = AppleTypography.headlineMedium, maxLines = 1, modifier = Modifier.semantics { heading() })
        if (!model.libraryEmpty) {
            Text(
                "${model.visibleCount} 部",
                color = colors.secondary,
                style = AppleTypography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = KireiSpacing.xs),
            )
        }
    }
}

@Composable
private fun LibraryLandscapeActions(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
    searchWidth: Dp,
    taskAnchor: @Composable () -> Unit,
) {
    val colors = model.colors
    if (!model.libraryEmpty) {
        if (!model.editMode) {
            LibrarySearchField(model, actions, Modifier.width(searchWidth))
            Spacer(Modifier.width(KireiSpacing.xs))
        }
        LibraryEditSortMenu(model.sortValue, colors, actions.onSortChange, model.haze)
        if (model.editMode) {
            Spacer(Modifier.width(KireiSpacing.xs))
            LibraryDoneButton(colors, actions.onFinishEdit)
            return
        }
        Spacer(Modifier.width(KireiSpacing.xxs))
        LibraryEngineFilterButton(model, actions)
        Spacer(Modifier.width(KireiSpacing.xxs))
    }
    if (model.taskButtonVisible) {
        taskAnchor()
        Spacer(Modifier.width(KireiSpacing.xxs))
    }
    GlassIconButton(Icons.Rounded.Add, "添加游戏", colors, model.haze, onClick = actions.onAddGame, opaqueFill = true)
}

@Composable
private fun LibraryEngineFilterButton(
    model: LibraryHeaderModel,
    actions: LibraryHeaderActions,
) {
    val colors = model.colors
    val filter = model.filter
    // The capsule names the current filter (accent while one engine is picked), so no separate label is needed.
    val tint = if (filter != null) colors.accent else colors.text
    val label = filter?.displayName ?: "全部"
    Box {
        GlassSurface(
            colors,
            model.haze,
            Modifier.height(FilterButtonHeight).widthIn(max = FilterButtonMaxWidth).semantics {
                contentDescription = "筛选游戏：$label"
                role = Role.Button
            },
            KireiShapes.full,
            KireiGlassRole.ICON,
            actions.onToggleFilterMenu,
            opaqueFill = true,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = KireiSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
            ) {
                Icon(filter?.let(::libraryEngineIcon) ?: Icons.Rounded.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(FilterIconSize))
                Text(label, color = tint, style = AppleTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        KireiMenu(
            expanded = model.filterMenuExpanded,
            onDismissRequest = actions.onDismissFilterMenu,
            colors = model.colors,
            modifier = Modifier.widthIn(min = FilterMenuMinWidth),
        ) {
            KireiMenuItem(KireiMenuItemSpec("全部", selected = model.filter == null), model.colors) { actions.onSelectFilter(null) }
            model.engines.forEach { engine ->
                KireiMenuItem(KireiMenuItemSpec(engine.displayName, selected = model.filter == engine), model.colors) {
                    actions.onSelectFilter(engine)
                }
            }
        }
    }
}

private val FilterMenuMinWidth = 180.dp
private val FilterButtonHeight = 48.dp
private val FilterButtonMaxWidth = 168.dp
private val FilterIconSize = 18.dp

/** Engine filter chips (see [LibraryEngineChipRowSpec]); [onSelect] gets null for 全部. */
@Composable
internal fun LibraryEngineChipRow(
    spec: LibraryEngineChipRowSpec,
    colors: KireiColors,
    onSelect: (EngineType?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .inertWhile(!spec.enabled)
            .layout { measurable, constraints ->
                // Widen by the gutters and shift left: chips scroll to the screen edge.
                val start = spec.bleedStart.roundToPx()
                val width = constraints.maxWidth + start + spec.bleedEnd.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-start, 0) }
            }
            .horizontalScroll(rememberScrollState())
            .padding(start = spec.bleedStart, end = spec.bleedEnd),
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
    ) {
        KireiChip("全部", spec.selected == null, colors) { onSelect(null) }
        spec.engines.forEach { engine ->
            KireiChip(engine.displayName, spec.selected == engine, colors, icon = libraryEngineIcon(engine)) { onSelect(engine) }
        }
    }
}
