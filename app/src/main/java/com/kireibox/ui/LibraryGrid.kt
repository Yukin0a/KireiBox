package com.kireibox.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiEmptyState
import com.kireibox.designsystem.components.KireiEmptyStateSpec
import com.yuki.yukihub.model.Game

@Composable
internal fun LibraryGameGrid(
    layout: LibraryGridLayoutSpec,
    model: LibraryGridModel,
    actions: LibraryGridActions,
) {
    val grouped =
        model.appearance.editMode &&
            model.reorder.customReorderEnabled &&
            (model.reorder.manualSort || model.reorder.customDraft != null)
    val bottomContentPadding = layout.bottomContentPadding
    LazyVerticalGrid(
        columns = layout.gridColumns,
        state = layout.gridState,
        modifier = layout.modifier,
        contentPadding =
            PaddingValues.Absolute(
                left = layout.contentLeft,
                top = layout.contentTopDp.dp,
                right = layout.contentRight,
                bottom =
                    maxOf(
                        bottomContentPadding.value,
                        LibraryCardLayoutPolicy.MIN_DOCK_CLEARANCE_DP,
                    ).dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(layout.adaptiveMetrics.gridSpacingDp.dp),
        verticalArrangement = Arrangement.spacedBy(layout.adaptiveMetrics.gridSpacingDp.dp),
    ) {
        layout.header?.let { header ->
            item(key = LIBRARY_HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        libraryEmptyStateItem(model.visible, model.emptyState, model.appearance.colors, compact = layout.adaptiveMetrics.orientation == HomeOrientation.LANDSCAPE)
        libraryReorderHintItem(model.reorder.customDraft, model.appearance.colors)
        libraryGridSections(model, actions, grouped)
    }
}

private const val LIBRARY_HEADER_KEY = "library-header"

private fun LazyGridScope.libraryEmptyStateItem(
    visible: List<Game>,
    emptyState: LibraryEmptyStateModel?,
    colors: KireiColors,
    compact: Boolean,
) {
    // Landscape is short: a smaller mascot and no extra top space keep the button above the Dock.
    val mascotSize = if (compact) LibraryEmptyMascotCompactSize else LibraryEmptyMascotSize
    if (visible.isEmpty() && emptyState != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            KireiEmptyState(
                spec =
                    KireiEmptyStateSpec(
                        title = emptyState.title,
                        message = emptyState.message,
                        actionLabel = emptyState.actionLabel,
                    ),
                colors = colors,
                modifier = Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else KireiSpacing.lg),
                onAction = emptyState.onAction,
                illustration =
                    if (emptyState.showMascot) {
                        { KireiMascot(Modifier.size(mascotSize).clip(KireiShapes.lg), contentDescription = null) }
                    } else {
                        null
                    },
            )
        }
    }
}

private val LibraryEmptyMascotSize = 112.dp
private val LibraryEmptyMascotCompactSize = 64.dp

private fun LazyGridScope.libraryReorderHintItem(
    customDraft: ManualOrderSelectionDraft?,
    colors: KireiColors,
) {
    if (customDraft != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "依次点按卡片，设置 1、2、3… 顺序",
                color = colors.accent,
                style = AppleTypography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

private fun LazyGridScope.libraryGridSections(
    model: LibraryGridModel,
    actions: LibraryGridActions,
    grouped: Boolean,
) {
    val appearance = model.appearance
    val reorder = model.reorder
    val favorites = if (grouped) model.visible.filter(Game::favorite) else model.visible
    if (grouped && favorites.isNotEmpty()) {
        librarySectionHeader(
            title =
                if (reorder.customDraft == null) {
                    "收藏"
                } else {
                    "收藏 · 已选 ${selectedCount(reorder, true, model.visible)}/${favorites.size}"
                },
            colors = appearance.colors,
        )
    }
    libraryGameCards(favorites, appearance, reorder, actions)
    if (grouped) {
        val others = model.visible.filterNot(Game::favorite)
        if (others.isNotEmpty()) {
            librarySectionHeader(
                title =
                    if (reorder.customDraft == null) {
                        "其他游戏"
                    } else {
                        "其他游戏 · 已选 ${selectedCount(reorder, false, model.visible)}/${others.size}"
                    },
                colors = appearance.colors,
            )
            libraryGameCards(others, appearance, reorder, actions)
        }
    }
}

private fun selectedCount(
    reorder: LibraryGridReorderSpec,
    favorite: Boolean,
    visible: List<Game>,
): Int = ManualOrderSelectionPolicy.selectedCount(reorder.customDraft, favorite, visible)

private fun LazyGridScope.librarySectionHeader(
    title: String,
    colors: KireiColors,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(title, color = colors.secondary, style = AppleTypography.bodySmall)
    }
}

private fun LazyGridScope.libraryGameCards(
    games: List<Game>,
    appearance: LibraryGridAppearanceSpec,
    reorder: LibraryGridReorderSpec,
    actions: LibraryGridActions,
) {
    items(games, key = { it.id }) { game ->
        LibraryGridCard(game, appearance, reorder, actions)
    }
}

@Composable
private fun LazyGridItemScope.LibraryGridCard(
    game: Game,
    appearance: LibraryGridAppearanceSpec,
    reorder: LibraryGridReorderSpec,
    actions: LibraryGridActions,
) {
    GameCard(
        game = game,
        colors = appearance.colors,
        modifier = libraryCardAnimation(appearance.polishedMotion),
        editMode = appearance.editMode,
        removing = appearance.removingGameId == game.id,
        reducedMotion = appearance.reducedMotion,
        compact = appearance.compactCards,
        onOpen = { actions.viewModel.selectedGame(game) },
        customOrderPosition = reorder.customPositions[game.id],
        customSelectionActive = reorder.customDraft != null,
        onLongClick = { actions.onEditModeChange(true) },
        onEditClick = { actions.onCustomToggle(game) },
        onRemove = { actions.onRequestRemove(game) },
    )
}

private fun LazyGridItemScope.libraryCardAnimation(polishedMotion: Boolean): Modifier =
    if (polishedMotion) {
        Modifier.animateItem(
            fadeInSpec = tween(180),
            placementSpec =
                spring(
                    dampingRatio = .82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            fadeOutSpec = tween(160),
        )
    } else {
        Modifier.animateItem(
            fadeInSpec = tween(100),
            placementSpec = tween(1),
            fadeOutSpec = tween(100),
        )
    }
