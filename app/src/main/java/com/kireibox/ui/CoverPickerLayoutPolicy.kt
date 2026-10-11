package com.kireibox.ui

import com.kireibox.designsystem.components.KireiEmptyStateSpec
import com.yuki.yukihub.metadata.model.MetadataCover

/** How the cover picker arranges its search side and its results (r5 · B, spec §10「r5 · B」). */
internal enum class CoverPickerArrangement {
    /** Phones in portrait (and narrow windows): one column, results between the search and the actions. */
    SINGLE,

    /** Tablets and foldables (≥ 700 × 560dp): the single column inside a centered card. */
    CARD,

    /** Landscape phones and other short, wide windows: search on the left, results and actions on the right. */
    TWO_PANE,
}

internal data class CoverPickerFrame(
    val arrangement: CoverPickerArrangement,
    /** Width of the left (search) pane in [CoverPickerArrangement.TWO_PANE]; 0 otherwise. */
    val sidePaneWidthDp: Float = 0f,
)

/** One line of the results list: a row of candidate cards, or the cover versions of the selected one. */
internal sealed interface CoverResultRow {
    val key: String

    data class Cards(val candidates: List<CoverCandidateUi>) : CoverResultRow {
        override val key: String get() = "cards:${candidates.first().key}"
    }

    data class Variants(val candidate: CoverCandidateUi) : CoverResultRow {
        override val key: String get() = "variants:${candidate.key}"
    }
}

/**
 * Pure layout of the cover picker. The arrangement is decided from the whole window, not the space
 * left above the keyboard, so opening the keyboard never flips the layout; the keyboard only
 * shortens the panes (safeDrawing includes the IME).
 */
internal object CoverPickerLayoutPolicy {
    const val CARD_MIN_WIDTH_DP = 700f
    const val CARD_MIN_HEIGHT_DP = 560f
    const val TWO_PANE_MIN_WIDTH_DP = 560f
    const val SIDE_PANE_FRACTION = .34f
    const val SIDE_PANE_MIN_DP = 248f
    const val SIDE_PANE_MAX_DP = 340f

    /** Narrowest candidate card: a 72dp cover, its gap and about eight characters of title per line. */
    const val CARD_MIN_WIDTH_FOR_COLUMN_DP = 220f
    const val CARD_GAP_DP = 12f
    const val MAX_COLUMNS = 3

    fun resolve(
        widthDp: Float,
        heightDp: Float,
    ): CoverPickerFrame =
        when {
            widthDp >= CARD_MIN_WIDTH_DP && heightDp >= CARD_MIN_HEIGHT_DP -> CoverPickerFrame(CoverPickerArrangement.CARD)
            widthDp >= TWO_PANE_MIN_WIDTH_DP && widthDp > heightDp ->
                CoverPickerFrame(CoverPickerArrangement.TWO_PANE, (widthDp * SIDE_PANE_FRACTION).coerceIn(SIDE_PANE_MIN_DP, SIDE_PANE_MAX_DP))
            else -> CoverPickerFrame(CoverPickerArrangement.SINGLE)
        }

    /** Candidate cards per row for the results width: as many 220dp cards as fit, one to three. */
    fun columns(resultsWidthDp: Float): Int = ((resultsWidthDp + CARD_GAP_DP) / (CARD_MIN_WIDTH_FOR_COLUMN_DP + CARD_GAP_DP)).toInt().coerceIn(1, MAX_COLUMNS)

    /**
     * Candidates in rows of [columns]. When the selected candidate has more than one cover, its
     * versions follow the row that holds it (full width), so choosing a card never reflows the
     * cards before it and the other cards keep their columns.
     */
    fun rows(
        candidates: List<CoverCandidateUi>,
        columns: Int,
        selectedKey: String?,
    ): List<CoverResultRow> =
        candidates.chunked(columns.coerceAtLeast(1)).flatMap { row ->
            val selected = row.firstOrNull { it.key == selectedKey }?.takeIf { it.metadata.covers.size > 1 }
            listOfNotNull(CoverResultRow.Cards(row), selected?.let { CoverResultRow.Variants(it) })
        }

    /** True when every source that was asked failed to answer (timeout / unreachable). */
    fun allUnreachable(search: CoverSearchUi): Boolean = search.sourceHits.isNotEmpty() && search.sourceFailed.isNotEmpty() && search.sourceFailed.size == search.sourceHits.size

    /** The empty results state; the action re-runs the search on every source. */
    fun emptyState(
        allUnreachable: Boolean,
        includeAdult: Boolean,
    ): KireiEmptyStateSpec =
        when {
            allUnreachable -> KireiEmptyStateSpec("全部来源都连不上", "请检查网络后再试一次", "重新搜索")
            // The option lives in settings, so point there instead of duplicating the control here.
            !includeAdult -> KireiEmptyStateSpec("没有找到可用封面", "「搜索结果包含成人作品」已关闭，可在「设置 › 封面与元数据」里打开", "重新搜索")
            else -> KireiEmptyStateSpec("没有找到可用封面", "换个名称再试试", "重新搜索")
        }
}

/** Caption under a cover version: catalog, pixel size and its content rating in plain words. */
internal object CoverVariantCaption {
    fun text(cover: MetadataCover): String =
        listOfNotNull(
            cover.source.takeIf { it.isNotBlank() },
            if (cover.width > 0 && cover.height > 0) "${cover.width}×${cover.height}" else null,
            rating(cover.sexual),
        ).joinToString(" · ")

    /** VNDB-style rating (safe / suggestive / explicit, or 0–2); unknown ratings are not shown. */
    fun rating(sexual: String): String? {
        val level =
            when (sexual.trim().lowercase()) {
                "safe" -> 0.0
                "suggestive" -> 1.0
                "explicit" -> 2.0
                else -> sexual.trim().toDoubleOrNull()
            }
        return when {
            level == null -> null
            level < .5 -> "全年龄"
            level < 1.5 -> "轻微暗示"
            else -> "成人向"
        }
    }
}
