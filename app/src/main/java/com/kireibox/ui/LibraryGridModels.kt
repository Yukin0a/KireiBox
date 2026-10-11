package com.kireibox.ui

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

internal data class LibraryEmptyStateModel(
    val title: String,
    val message: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    /** The ChisaKi mark leads the "nothing here yet" state (P6), not the no-match state. */
    val showMascot: Boolean = false,
)

internal enum class LibraryEmptyKind { LIBRARY_EMPTY, NO_MATCH }

/**
 * Which empty state the library grid shows. While the first load is running or has failed the
 * shell owns the spinner / error prompt, so an empty list must not claim the library is empty.
 */
internal object LibraryEmptyStatePolicy {
    fun resolve(
        loadState: HomeLoadState,
        totalGames: Int,
        filtering: Boolean,
    ): LibraryEmptyKind? =
        when {
            totalGames == 0 && loadState != HomeLoadState.LOADED -> null
            totalGames == 0 -> LibraryEmptyKind.LIBRARY_EMPTY
            filtering -> LibraryEmptyKind.NO_MATCH
            else -> null
        }
}

/** Copy and action of the library empty state [kind] (null: no empty state). */
internal fun libraryEmptyStateModel(
    kind: LibraryEmptyKind?,
    onAddDirectory: () -> Unit,
    onClearFilters: () -> Unit,
): LibraryEmptyStateModel? =
    when (kind) {
        LibraryEmptyKind.LIBRARY_EMPTY ->
            LibraryEmptyStateModel(
                title = "游戏库还是空的",
                message = "添加游戏目录，自动识别游戏",
                actionLabel = "添加游戏目录",
                onAction = onAddDirectory,
                showMascot = true,
            )
        LibraryEmptyKind.NO_MATCH ->
            LibraryEmptyStateModel(
                title = "没有匹配的游戏",
                message = "试试换个关键词，或清除筛选条件",
                actionLabel = "清除筛选",
                onAction = onClearFilters,
            )
        null -> null
    }

/**
 * Engine filter chips. [bleedStart]/[bleedEnd] are the page gutters: the row reaches the screen
 * edges and scrolls under them, with the first and last chip still aligned to the gutter, instead
 * of being cut by a straight edge at the gutter. In edit mode the row stays (dimmed, inert) so the
 * grid below does not jump.
 */
internal data class LibraryEngineChipRowSpec(
    val selected: EngineType?,
    val engines: List<EngineType>,
    val bleedStart: Dp,
    val bleedEnd: Dp,
    val enabled: Boolean,
)

/** Card meta line (plan P4): relative last-played time, e.g. "3 天前", or "未游玩". */
internal object LibraryLastPlayedLabel {
    private const val DAYS_PER_WEEK = 7L
    private const val DAYS_PER_MONTH = 30L

    fun of(
        lastPlayedAtMs: Long,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (lastPlayedAtMs <= 0L) return "未游玩"
        val played = Instant.ofEpochMilli(lastPlayedAtMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(played, today)
        return when {
            days <= 0L -> "今天"
            days == 1L -> "昨天"
            days < DAYS_PER_WEEK -> "$days 天前"
            days < DAYS_PER_MONTH -> "${days / DAYS_PER_WEEK} 周前"
            played.year == today.year -> "${played.monthValue}月${played.dayOfMonth}日"
            else -> "${played.year}年${played.monthValue}月"
        }
    }
}

/**
 * Where the grid draws. It fills the window so cards scroll under the top bar; [contentLeft] and
 * [contentRight] are the page gutters, [contentTopDp] clears the bar, and the optional [header] is
 * the portrait title / search / filter block that scrolls with the cards.
 */
internal data class LibraryGridLayoutSpec(
    val gridColumns: GridCells,
    val gridState: LazyGridState,
    val adaptiveMetrics: AdaptiveUiMetrics,
    val bottomContentPadding: Dp,
    val contentTopDp: Float,
    val contentLeft: Dp,
    val contentRight: Dp,
    val modifier: Modifier,
    val header: (@Composable () -> Unit)? = null,
)

internal data class LibraryGridAppearanceSpec(
    val colors: KireiColors,
    val polishedMotion: Boolean,
    val editMode: Boolean,
    val removingGameId: Long?,
    val reducedMotion: Boolean,
    val compactCards: Boolean,
)

internal data class LibraryGridReorderSpec(
    val manualSort: Boolean,
    val customReorderEnabled: Boolean,
    val customDraft: ManualOrderSelectionDraft?,
    val customPositions: Map<Long, Int>,
)

internal data class LibraryGridModel(
    val visible: List<Game>,
    val appearance: LibraryGridAppearanceSpec,
    val reorder: LibraryGridReorderSpec,
    val emptyState: LibraryEmptyStateModel? = null,
)

internal data class LibraryGridActions(
    val viewModel: KireiViewModel,
    val onRequestRemove: (Game) -> Unit,
    val onEditModeChange: (Boolean) -> Unit,
    val onCustomToggle: (Game) -> Unit,
)
