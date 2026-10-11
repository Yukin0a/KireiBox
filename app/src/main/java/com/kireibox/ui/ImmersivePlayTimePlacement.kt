package com.kireibox.ui

import com.yuki.yukihub.model.Game
import kotlin.math.max
import kotlin.math.min

/** The play-time label may wrap to this many lines before it is cut with "…". */
internal const val IMMERSIVE_PLAY_TIME_MAX_LINES = 2

private const val PLAYED_PREFIX = "已游玩 "
private const val NO_BREAK_SPACE = '\u00A0'
private const val WORD_JOINER = "\u2060"
private const val BREAK_OPPORTUNITY = '\u200B'
private const val FOUR_GLYPH_PHRASE = 4

/**
 * Every distinct play-time label in the gallery (「尚未游玩」, 「已游玩 12 小时 30 分钟」…), longest first. All of
 * them are measured — the gallery holds at most five games — so the slot stays put for every game in every
 * wording: the shortest full sentence is not the narrowest compact one (「尚未游玩」 is wider than 「1 分钟」).
 */
internal fun immersivePlayTimeReferenceLabels(games: List<Game>): List<String> =
    games
        .map { LibraryOverviewPolicy.immersivePlayTime(it.totalPlayTime) }
        .distinct()
        .sortedByDescending(String::length)

/** The wording of the landscape label: the full sentence, or — for the narrowest columns — the amount alone. */
internal enum class ImmersiveLabelForm { FULL, COMPACT }

/**
 * The landscape label as drawn. COMPACT drops 「已游玩」 (「12 小时 30 分钟」 next to the clock icon; TalkBack
 * still hears the full sentence). Lines may only break between the label's parts — 「已游玩」 · 「12 小时」 ·
 * 「30 分钟」: a number keeps its unit (no-break space) and a word keeps its glyphs (word joiner), so a wrap
 * never strands 「钟」 on its own line. A four-glyph phrase may break in the middle (「尚未」 / 「游玩」).
 */
internal fun immersivePlayTimeDisplay(
    label: String,
    form: ImmersiveLabelForm,
): String {
    val wording = if (form == ImmersiveLabelForm.COMPACT) label.removePrefix(PLAYED_PREFIX) else label
    val parts = wording.split(' ').filter(String::isNotEmpty).map(::joinWordGlyphs)
    val groups = mutableListOf<String>()
    var index = 0
    while (index < parts.size) {
        val part = parts[index]
        val unit = parts.getOrNull(index + 1)
        if (part.all(Char::isDigit) && unit != null) {
            groups += part + NO_BREAK_SPACE + unit
            index += 2
        } else {
            groups += part
            index += 1
        }
    }
    return groups.joinToString(" ")
}

private fun joinWordGlyphs(word: String): String =
    when {
        word.all(Char::isDigit) -> word
        word.length == FOUR_GLYPH_PHRASE -> joinWordGlyphs(word.take(2)) + BREAK_OPPORTUNITY + joinWordGlyphs(word.drop(2))
        else -> word.toList().joinToString(WORD_JOINER)
    }

/** The parts a displayed label may wrap between; each must fit a line whole. */
internal fun immersivePlayTimeParts(displayed: String): List<String> = displayed.split(' ', BREAK_OPPORTUNITY).filter(String::isNotEmpty)

/**
 * The play-time label measured at a width limit: the label's width and height including the icon,
 * gap and padding (one or two lines), and whether the text still had to be cut with "…" there.
 */
internal data class ImmersiveLabelBox(
    val widthPx: Float,
    val heightPx: Float,
    val ellipsized: Boolean = false,
)

/** Where the landscape play-time label sits relative to the play pill. */
internal enum class ImmersivePlayTimeSlot { BELOW, BESIDE }

/**
 * Landscape play-time placement (r4 · I2). [area] is the room the label may use: its position, the full
 * width up to the information column's right edge and the height it needs. [play] is the play pill,
 * unchanged unless a short window needs room beside it. [form] is the wording that fits.
 */
internal data class ImmersiveLandscapePlayTime(
    val slot: ImmersivePlayTimeSlot,
    val area: HomeRect,
    val play: HomeRect,
    val ellipsized: Boolean,
    val form: ImmersiveLabelForm = ImmersiveLabelForm.FULL,
)

internal fun ImmersiveLandscapePlayTime.anchor(): ImmersivePlayTimeAnchor =
    ImmersivePlayTimeAnchor(
        xPx = area.x,
        yPx = area.y,
        maxWidthPx = area.w,
        heightPx = area.h,
        centeredOnPlay = slot == ImmersivePlayTimeSlot.BESIDE,
        compactLabel = form == ImmersiveLabelForm.COMPACT,
    )

/** Measures the label at a maximum width in a wording. */
internal typealias LabelMeasure = (maxWidthPx: Float, form: ImmersiveLabelForm) -> ImmersiveLabelBox

/**
 * Places the landscape play-time label inside the information column (engine · title · 继续游戏 · play
 * pill). The label always measures against the column's full width — from the column's left edge to the
 * far-left card minus the card gutter — never against the play pill's width; it wraps to a second line
 * before it ellipsizes, and it never covers the text above the play row, the pager dots or the Dock.
 * The first placement that shows the whole label wins:
 *
 * 1. the full sentence below the play pill (the I1 look) when it fits above the pager lane — or, where the
 *    pager dots sit right of the column (every landscape phone), above the Dock, or beside it when the
 *    label ends left of the Dock (wrapped to two lines narrower than the column if that is what it takes);
 * 2. the full sentence beside the pill, centred on it (short windows such as 1600×720 or 1920×1080 @480);
 * 3. the amount alone (「12 小时 30 分钟」, next to the clock icon) below, then beside the pill;
 * 4. only then the pill narrows — never below a circle as wide as it is tall — just enough for the full
 *    sentence, or else the amount, beside it;
 * 5. "…" only when even that cannot fit (a 3-digit-hour total at a large font on a 640×360dp phone).
 *
 * [LabelMeasure] is real text layout in the app and a glyph model in tests. The app measures every label in
 * the gallery so the slot, the wording and the pill stay put while swiping.
 */
internal object ImmersivePlayTimePlacement {
    /** Compose rounds dp → px per element (icon, gap, padding); this keeps a one-line fit from wrapping. */
    private const val LAYOUT_SLACK_PX = 2f
    private const val EPSILON_PX = .5f

    /** Half a landscape pager dot (28 design px) plus the same again as clearance. */
    private const val PAGER_DOT_REACH_PX = 28f

    fun landscape(
        layout: ImmersiveHomeLayout,
        gapPx: Float,
        measure: LabelMeasure,
    ): ImmersiveLandscapePlayTime? = lane(layout, gapPx)?.let { lane -> resolve(layout.play, lane, measure) }

    /**
     * The column's horizontal span and the vertical room between the text above the play row and the pager.
     * [belowBottom] limits the label under the pill: the pager's text line only where the pager dots overlap
     * the column, otherwise the Dock's top edge (the column has the room down to the Dock to itself) — or,
     * when the label's content ends left of the Dock ([dockLeft]), the Dock's bottom edge ([besideDockBottom]):
     * the label then sits beside the Dock, never on it.
     */
    private data class Lane(
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
        val gap: Float,
        val belowBottom: Float = bottom,
        val dockLeft: Float = Float.NEGATIVE_INFINITY,
        val besideDockBottom: Float? = null,
    ) {
        val width: Float get() = (right - left).coerceAtLeast(0f)
        val height: Float get() = (bottom - top).coerceAtLeast(0f)

        /** Where a label below the pill whose content is [contentWidth] wide may end. */
        fun belowLimit(contentWidth: Float): Float {
            val besideDock = besideDockBottom?.takeIf { left + contentWidth + gap <= dockLeft }
            return if (besideDock != null) max(belowBottom, besideDock) else belowBottom
        }

        /**
         * Widths a label below the pill is laid out at: the whole column, then — when the Dock reaches under
         * the column — only up to the Dock, so the full sentence wraps to two lines beside it instead of
         * giving way to the amount alone.
         */
        fun belowWidths(): List<Float> {
            val besideDock = (dockLeft - gap - left).takeIf { besideDockBottom != null && it >= 1f && it < width }
            return listOfNotNull(width, besideDock)
        }
    }

    private fun lane(
        layout: ImmersiveHomeLayout,
        gapPx: Float,
    ): Lane? {
        val title = layout.title
        val pagerY = layout.pagerCenters.firstOrNull()?.y
        if (title == null || pagerY == null) return null
        val above = layout.subtitle ?: title
        val gap = gapPx.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
        val textBottom = resolveImmersiveTextBottom(pagerY, layout.renderScale)
        val pagerReach = PAGER_DOT_REACH_PX * layout.renderScale
        val pagerOverlapsColumn =
            layout.pagerCenters.minOf { it.x } - pagerReach < title.x + title.w &&
                layout.pagerCenters.maxOf { it.x } + pagerReach > title.x
        val dockLimit = layout.dock.y - IMMERSIVE_PAGER_TEXT_GAP_PX * layout.renderScale
        return Lane(
            left = title.x,
            right = title.x + title.w,
            top = above.y + above.h,
            bottom = textBottom,
            gap = gap,
            belowBottom = if (pagerOverlapsColumn) textBottom else max(textBottom, dockLimit),
            dockLeft = layout.dock.x,
            besideDockBottom = if (pagerOverlapsColumn) null else layout.dock.y + layout.dock.h,
        )
    }

    private fun resolve(
        play: HomeRect,
        lane: Lane,
        measure: LabelMeasure,
    ): ImmersiveLandscapePlayTime {
        val forms = ImmersiveLabelForm.entries.asSequence()
        val keepPill = forms.flatMap { form -> sequenceOf(below(play, lane, form, measure), besideFullPill(play, lane, form, measure)) }.filterNotNull()
        val narrowPill = forms.map { form -> besideNarrowedPill(play, lane, form, measure) }
        return (keepPill + narrowPill).firstOrNull { !it.ellipsized } ?: besideNarrowedPill(play, lane, ImmersiveLabelForm.COMPACT, measure)
    }

    private fun below(
        play: HomeRect,
        lane: Lane,
        form: ImmersiveLabelForm,
        measure: LabelMeasure,
    ): ImmersiveLandscapePlayTime? {
        val top = play.y + play.h + lane.gap
        return lane.belowWidths().firstNotNullOfOrNull { width ->
            val box = measure(width, form)
            val fits = !box.ellipsized && top + box.heightPx <= lane.belowLimit(box.widthPx) + EPSILON_PX
            if (fits) ImmersiveLandscapePlayTime(ImmersivePlayTimeSlot.BELOW, HomeRect(lane.left, top, width, box.heightPx), play, false, form) else null
        }
    }

    private fun besideFullPill(
        play: HomeRect,
        lane: Lane,
        form: ImmersiveLabelForm,
        measure: LabelMeasure,
    ): ImmersiveLandscapePlayTime? {
        val box = measure(lane.right - (play.x + play.w + lane.gap), form)
        val fits = !box.ellipsized && box.heightPx <= lane.height + EPSILON_PX
        return if (fits) beside(play, lane, play.w, box, form) else null
    }

    /** The narrowest pill (a circle) frees the most room; the pill then gives up only what the label needs. */
    private fun besideNarrowedPill(
        play: HomeRect,
        lane: Lane,
        form: ImmersiveLabelForm,
        measure: LabelMeasure,
    ): ImmersiveLandscapePlayTime {
        val narrowest = min(play.w, play.h)
        val atNarrowest = measure(lane.right - (play.x + narrowest + lane.gap), form)
        val pillWidth = (lane.right - lane.gap - atNarrowest.widthPx - LAYOUT_SLACK_PX - play.x).coerceIn(narrowest, play.w)
        return beside(play, lane, pillWidth, measure(lane.right - (play.x + pillWidth + lane.gap), form), form)
    }

    private fun beside(
        play: HomeRect,
        lane: Lane,
        pillWidth: Float,
        box: ImmersiveLabelBox,
        form: ImmersiveLabelForm,
    ): ImmersiveLandscapePlayTime {
        val x = play.x + pillWidth + lane.gap
        val y =
            (play.y + play.h / 2f - box.heightPx / 2f)
                .coerceIn(lane.top, max(lane.top, lane.bottom - box.heightPx))
        return ImmersiveLandscapePlayTime(
            slot = ImmersivePlayTimeSlot.BESIDE,
            area = HomeRect(x, y, (lane.right - x).coerceAtLeast(0f), box.heightPx),
            play = play.copy(w = pillWidth),
            ellipsized = box.ellipsized,
            form = form,
        )
    }
}
