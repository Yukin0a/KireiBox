package com.kireibox.ui

import androidx.compose.ui.unit.Density
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * r4 · I2 (user request 6): in landscape the left column's play-time label (「尚未游玩」, 「已游玩 …」) must be
 * complete on every device and never end in "…" while there is room to its right, and it must never cover
 * 「继续游戏」 / the title above the play pill. The device matrix mirrors the emulator matrix used for the
 * screenshots in artifacts/ui-alpha17/r4/landscape/ (window px, density, font scale 1.0 / 1.3), built the
 * way ImmersiveHome builds its layout (adaptive Dock, shared top chrome, 48dp play floor).
 */
class ImmersivePlayTimePlacementTest {
    private data class Device(val name: String, val widthPx: Float, val heightPx: Float, val density: Float)

    private val devices =
        listOf(
            Device("2400x1080@420", 2400f, 1080f, 2.625f),
            Device("1600x720@320", 1600f, 720f, 2f),
            Device("1920x1080@480", 1920f, 1080f, 3f),
            Device("2340x1080@440", 2340f, 1080f, 2.75f),
            Device("1280x800@213", 1280f, 800f, 1.33125f),
            Device("2560x1600@320", 2560f, 1600f, 2f),
            Device("3200x1440@560", 3200f, 1440f, 3.5f),
            Device("2208x1840@420", 2208f, 1840f, 2.625f),
        )
    private val fontScales = listOf(1f, 1.3f)
    private val labels = listOf("尚未游玩", "不足 1 分钟", "已游玩 5 分钟", "已游玩 12 小时 30 分钟")

    /** A 3-digit-hour total: the longest label the policy produces. */
    private val longestLabel = "已游玩 128 小时 45 分钟"

    @Test
    fun everyDeviceShowsEveryLabelCompletelyInsideTheInformationColumn() {
        val cases = devices.flatMap { device -> fontScales.flatMap { fontScale -> (labels + longestLabel).map { Triple(device, fontScale, it) } } }
        cases.forEach { (device, fontScale, label) ->
            val scene = scene(device, fontScale)
            val placement = place(scene, listOf(label))
            val case = "${device.name} fs$fontScale 「$label」"
            assertFalse("$case must not ellipsize", placement.ellipsized)
            assertFalse("$case must fit its area", scene.model.box(listOf(immersivePlayTimeDisplay(label, placement.form)), placement.area.w).ellipsized)
            assertInsideColumn(case, scene, placement, listOf(label))
        }
    }

    @Test
    fun theContinueLineAndTheEngineLabelAreShownWholeOnEveryDevice() {
        // 「继续游戏」 and the engine label are single lines across the whole column (designSp × font scale).
        devices.flatMap { device -> fontScales.map { device to it } }.forEach { (device, fontScale) ->
            val layout = scene(device, fontScale).layout
            val case = "${device.name} fs$fontScale"
            val subtitle = layout.subtitle
            val metadata = requireNotNull(layout.metadata)
            assertNotNull("$case shows 「继续游戏」", subtitle)
            val glyph = layout.renderScale * fontScale
            assertTrue("$case 「继续游戏」 fits one line", 4f * 32f * glyph <= requireNotNull(subtitle).w)
            assertTrue("$case 「Android 应用」 fits one line", (8 * .55f + 2f) * 30f * glyph <= metadata.w)
        }
    }

    @Test
    fun labelsThatFitTheColumnMeasureAgainstTheWholeColumnNotThePlayPill() {
        // Before r4 · I2 the label was capped at the play pill's width (292 design px × render scale):
        // 「已游玩 12 小时 30 分钟」 ended in "…" on every phone with most of the column still free.
        val scene = scene(devices.first { it.name == "2400x1080@420" }, 1f)
        val label = "已游玩 12 小时 30 分钟"
        val column = scene.layout.title!!
        val oneLine = scene.model.box(listOf(immersivePlayTimeDisplay(label, ImmersiveLabelForm.FULL)), Float.MAX_VALUE)
        assertTrue("the label is wider than the play pill", oneLine.widthPx > scene.layout.play.w)
        assertTrue("…but fits the column", oneLine.widthPx <= column.w)

        val placement = place(scene, listOf(label))
        assertEquals(ImmersivePlayTimeSlot.BELOW, placement.slot)
        assertEquals("the full sentence", ImmersiveLabelForm.FULL, placement.form)
        assertEquals(column.w, placement.area.w, .01f)
        assertEquals("one line, no wrap", scene.model.oneLineHeight(), placement.area.h, .01f)
        assertEquals("the play pill keeps its size", scene.layout.play, placement.play)
    }

    @Test
    fun aLabelWiderThanTheColumnWrapsToTwoLinesBeforeItEllipsizes() {
        val scene = scene(devices.first { it.name == "2400x1080@420" }, 1f)
        val column = scene.layout.title!!
        // A label between one and two column widths: it must wrap, not end in "…".
        val label = "已游玩 128 小时 45 分钟 · 上次游玩 3 天前"
        val shown = immersivePlayTimeDisplay(label, ImmersiveLabelForm.FULL)
        assertTrue(scene.model.box(listOf(shown), Float.MAX_VALUE).widthPx > column.w)

        val placement = place(scene, listOf(label))
        assertFalse(placement.ellipsized)
        assertEquals(ImmersiveLabelForm.FULL, placement.form)
        assertEquals(2, scene.model.lines(shown, placement.area.w))
        assertEquals(scene.model.twoLineHeight(), placement.area.h, .01f)
        assertInsideColumn("wrapped label", scene, placement, listOf(label))
    }

    @Test
    fun theLongestLabelOnTheNarrowestColumnStillFitsBesideTheDock() {
        // A 3-digit-hour total at 1.3× in the 150dp column of a 640×360dp phone. It used to be the one "…" /
        // circular-pill case; with the room beside the Dock (the label ends left of it) the amount fits below
        // the full pill on two lines.
        val scene = scene(devices.first { it.name == "1920x1080@480" }, 1.3f)
        val placement = place(scene, listOf(longestLabel))
        assertFalse(placement.ellipsized)
        assertEquals(ImmersivePlayTimeSlot.BELOW, placement.slot)
        assertEquals(ImmersiveLabelForm.COMPACT, placement.form)
        assertEquals("the pill keeps its size", scene.layout.play, placement.play)
        assertInsideColumn("longest label", scene, placement, listOf(longestLabel))
        // …while the everyday labels on that same column are complete.
        for (label in labels) assertFalse(label, place(scene, listOf(label)).ellipsized)
    }

    @Test
    fun widerColumnsKeepThePillAndPreferTheFullSentence() {
        for (device in devices.filter { it.name in setOf("2400x1080@420", "2560x1600@320", "3200x1440@560", "2208x1840@420") }) {
            for (fontScale in fontScales) {
                val scene = scene(device, fontScale)
                val placement = place(scene, labels)
                val case = "${device.name} fs$fontScale"
                assertEquals(case, ImmersivePlayTimeSlot.BELOW, placement.slot)
                assertEquals(case, ImmersiveLabelForm.FULL, placement.form)
                assertEquals(case, scene.layout.play, placement.play)
            }
        }
    }

    @Test
    fun ellipsisIsOnlyTheLastResortBeyondTwoLines() {
        val scene = scene(devices.first { it.name == "2400x1080@420" }, 1f)
        val label = "已游玩 12 小时 30 分钟".repeat(6)
        val placement = place(scene, listOf(label))
        assertTrue(placement.ellipsized)
        assertEquals(scene.model.twoLineHeight(), placement.area.h, .01f)
    }

    @Test
    fun shortWindowsNeverLiftTheLabelOverTheContinueLine() {
        // 1600x720 and 1920x1080 @480 have no room between the play pill and the pager's text line; the old
        // fallback lifted the label above the pill, straight over 「继续游戏」. The label now goes below the pill
        // (where the pager dots sit right of the column, the column has the room down to the Dock) or beside it.
        for (name in listOf("1600x720@320", "1920x1080@480")) {
            for (fontScale in fontScales) {
                val scene = scene(devices.first { it.name == name }, fontScale)
                val placement = place(scene, labels)
                val case = "$name fs$fontScale"
                assertNotNull("$case keeps 「继续游戏」", scene.layout.subtitle)
                when (placement.slot) {
                    ImmersivePlayTimeSlot.BELOW -> assertTrue("$case label starts below the pill", placement.area.y >= placement.play.y + placement.play.h)
                    ImmersivePlayTimeSlot.BESIDE -> assertTrue("$case label starts right of the pill", placement.area.x >= placement.play.x + placement.play.w)
                }
                assertFalse(case, placement.ellipsized)
                assertInsideColumn(case, scene, placement, labels)
            }
        }
    }

    @Test
    fun theFullSentenceIsKeptWhereTheColumnHasRoomDownToTheDock() {
        // r4 QA: on 2400x1080 at 1.3× and on 1600x720 the label dropped 「已游玩」 and stacked 「12 小时 / 30 分钟」
        // beside the pill although the column below it was empty (the pager dots sit right of the column, and
        // the Dock only reaches under the column's right part, so the label can wrap beside it).
        val cases = listOf("2400x1080@420" to 1f, "2400x1080@420" to 1.3f, "1600x720@320" to 1f, "1600x720@320" to 1.3f, "1920x1080@480" to 1f)
        for ((name, fontScale) in cases) {
            val scene = scene(devices.first { it.name == name }, fontScale)
            val placement = place(scene, labels)
            val case = "$name fs$fontScale"
            assertEquals("$case shows the whole sentence", ImmersiveLabelForm.FULL, placement.form)
            assertEquals("$case below the pill", ImmersivePlayTimeSlot.BELOW, placement.slot)
            assertEquals("$case keeps the pill", scene.layout.play, placement.play)
            assertInsideColumn(case, scene, placement, labels)
        }
    }

    @Test
    fun theBrandSharesTheInformationColumnsLeftEdgeInLandscape() {
        // r4 QA: 「KireiBox」 started ~13dp right of the column on a cutout phone (and left of it without one).
        for (device in devices) {
            for (fontScale in fontScales) {
                val scene = scene(device, fontScale)
                val brand = immersiveBrandTitleRect(scene.chromeTitle, scene.layout, HomeOrientation.LANDSCAPE)
                val case = "${device.name} fs$fontScale"
                assertEquals("$case one left edge", scene.layout.title!!.x, brand.x, .01f)
                assertEquals("$case the right edge stays the chrome's", scene.chromeTitle.x + scene.chromeTitle.w, brand.x + brand.w, .01f)
                assertEquals("$case same line", scene.chromeTitle.y, brand.y, 0f)
            }
        }
        val scene = scene(devices.first(), 1f)
        assertEquals("portrait keeps the shared chrome", scene.chromeTitle, immersiveBrandTitleRect(scene.chromeTitle, scene.layout, HomeOrientation.PORTRAIT))
    }

    @Test
    fun aNarrowedPlayPillStaysAtLeastACircleWithItsHeightAndPosition() {
        for (device in devices) {
            for (fontScale in fontScales) {
                val scene = scene(device, fontScale)
                val placement = place(scene, labels)
                val play = scene.layout.play
                val case = "${device.name} fs$fontScale"
                assertEquals(case, play.x, placement.play.x, .01f)
                assertEquals(case, play.y, placement.play.y, .01f)
                assertEquals(case, play.h, placement.play.h, .01f)
                assertTrue(case, placement.play.w <= play.w + .01f)
                assertTrue("$case pill never narrower than a circle", placement.play.w >= min(play.w, play.h) - .01f)
                assertTrue("$case 48dp touch height", placement.play.h >= 48f * device.density - .01f)
            }
        }
    }

    @Test
    fun theSlotIsSolvedForEveryLabelInTheGallerySoSwipingNeverMovesIt() {
        val games =
            listOf(0L, 30_000L, 5 * 60_000L, 750 * 60_000L, 7_725 * 60_000L, 0L)
                .mapIndexed { index, playTime -> Game(id = index.toLong(), title = "Game $index", totalPlayTime = playTime) }
        val reference = immersivePlayTimeReferenceLabels(games)
        assertEquals("已游玩 128 小时 45 分钟", reference.first())
        assertTrue(reference.contains("已游玩 12 小时 30 分钟"))
        assertEquals(reference.distinct(), reference)
        // Every distinct label, the shortest full sentence included: in the compact wording 「尚未游玩」 is
        // wider than 「1 分钟」, so dropping it would size the slot too small for the unplayed game.
        assertEquals(games.map { LibraryOverviewPolicy.immersivePlayTime(it.totalPlayTime) }.toSet(), reference.toSet())
        assertTrue(reference.contains("尚未游玩"))
    }

    @Test
    fun anUnplayedGameAmongFourPlayedOnesStillFitsTheCompactSlot() {
        // Four 「已游玩 N 分钟」 and one 「尚未游玩」: the compact wording of the shortest full sentence is the widest.
        val games =
            listOf(60_000L, 2 * 60_000L, 3 * 60_000L, 4 * 60_000L, 0L)
                .mapIndexed { index, playTime -> Game(id = index.toLong(), title = "Game $index", totalPlayTime = playTime) }
        val reference = immersivePlayTimeReferenceLabels(games)
        val scene = scene(devices.first { it.name == "1920x1080@480" }, 1.3f)
        val placement = place(scene, reference)
        val unplayed = scene.model.box(listOf(immersivePlayTimeDisplay("尚未游玩", placement.form)), placement.area.w)
        assertFalse("「尚未游玩」 fits the slot solved for the gallery", unplayed.ellipsized)
        assertTrue("…without growing past it", unplayed.heightPx <= placement.area.h + .01f)
        assertInsideColumn("five-game gallery", scene, placement, reference)
    }

    @Test
    fun theDisplayedLabelOnlyBreaksBetweenItsPartsAndTheCompactFormKeepsTheAmount() {
        fun plain(text: String) = text.replace("\u2060", "").replace("\u200B", "").replace('\u00A0', ' ')
        val full = immersivePlayTimeDisplay("已游玩 12 小时 30 分钟", ImmersiveLabelForm.FULL)
        assertEquals("已游玩 12 小时 30 分钟", plain(full))
        // Break opportunities (regular spaces) only between 「已游玩」 · 「12 小时」 · 「30 分钟」.
        assertEquals(listOf("已游玩", "12 小时", "30 分钟"), full.split(' ').map(::plain))
        assertEquals("12 小时 30 分钟", plain(immersivePlayTimeDisplay("已游玩 12 小时 30 分钟", ImmersiveLabelForm.COMPACT)))
        assertEquals("尚未游玩", plain(immersivePlayTimeDisplay("尚未游玩", ImmersiveLabelForm.COMPACT)))
        assertEquals("a four-glyph phrase breaks in the middle", listOf("尚未", "游玩"), immersivePlayTimeParts(immersivePlayTimeDisplay("尚未游玩", ImmersiveLabelForm.FULL)).map(::plain))
        assertEquals(listOf("不足", "1 分钟"), immersivePlayTimeDisplay("不足 1 分钟", ImmersiveLabelForm.FULL).split(' ').map(::plain))
    }

    @Test
    fun noInformationColumnMeansNoLandscapePlacement() {
        val layout = scene(devices.first(), 1f).layout.copy(title = null)
        assertEquals(null, ImmersivePlayTimePlacement.landscape(layout, 10f) { _, _ -> ImmersiveLabelBox(10f, 10f) })
    }

    private fun assertInsideColumn(
        case: String,
        scene: Scene,
        placement: ImmersiveLandscapePlayTime,
        shown: List<String>,
    ) {
        val layout = scene.layout
        val column = layout.title!!
        val area = placement.area
        val above = layout.subtitle ?: column
        val farLeft = layout.cardSlots.getValue(ImmersiveSlot.FAR_LEFT).rect
        val pagerLane = resolveImmersiveTextBottom(layout.pagerCenters.first().y, layout.renderScale)
        assertTrue("$case starts in the column", area.x >= column.x - .01f)
        assertTrue("$case ends at the column's right edge", area.x + area.w <= column.x + column.w + .01f)
        assertTrue("$case stays left of the cards", area.x + area.w <= farLeft.x + .01f)
        assertTrue("$case never covers 「继续游戏」/the title", area.y >= above.y + above.h - .01f)
        // The pager dots: the label either stays above their text line or entirely left of the dots.
        val dotsLeft = layout.pagerCenters.minOf { it.x } - 14f * layout.renderScale
        assertTrue("$case clears the pager dots", area.y + area.h <= pagerLane + .5f || area.x + area.w <= dotsLeft)
        if (placement.slot == ImmersivePlayTimeSlot.BESIDE) assertTrue("$case beside the pill stays above the pager lane", area.y + area.h <= pagerLane + .5f)
        // The Dock: the label ends above it, or its content ends left of it and it stays above the Dock's bottom.
        val content = scene.model.box(shown.map { immersivePlayTimeDisplay(it, placement.form) }, area.w).widthPx
        val aboveDock = area.y + area.h <= layout.dock.y - 16f * layout.renderScale + .5f
        val besideDock = area.x + content <= layout.dock.x + .5f && area.y + area.h <= layout.dock.y + layout.dock.h + .5f
        assertTrue("$case clears the Dock", aboveDock || besideDock)
        if (placement.slot == ImmersivePlayTimeSlot.BELOW) {
            // The whole column, or (wrapped to sit beside the Dock) exactly up to the Dock.
            val wholeColumn = abs(column.w - area.w) < .01f
            assertTrue("$case below the pill uses the whole column or ends at the Dock", wholeColumn || area.x + area.w <= layout.dock.x + .01f)
            assertTrue(case, area.y >= placement.play.y + placement.play.h)
        }
    }

    private data class Scene(val layout: ImmersiveHomeLayout, val model: GlyphModel, val gapPx: Float, val chromeTitle: HomeRect)

    private fun place(
        scene: Scene,
        labels: List<String>,
    ): ImmersiveLandscapePlayTime {
        val placement =
            ImmersivePlayTimePlacement.landscape(scene.layout, scene.gapPx) { maxWidthPx, form ->
                scene.model.box(labels.map { immersivePlayTimeDisplay(it, form) }, maxWidthPx)
            }
        assertNotNull(placement)
        return requireNotNull(placement)
    }

    /** ImmersiveHome's landscape wiring in plain numbers: dp metrics → px layout, the play-time spec in sp. */
    private fun scene(
        device: Device,
        fontScale: Float,
    ): Scene {
        val d = device.density
        val navigationDp = 24f
        val density = Density(d, fontScale)
        val safe = ImmersiveSafeBounds(0f, 0f, device.widthPx, device.heightPx - navigationDp * d)
        val metrics = resolveAdaptiveUiMetrics(device.widthPx / d, device.heightPx / d, 0f, 0f, 0f, navigationDp)
        val sizeClass = if (metrics.libraryColumns >= 5) HomeSizeClass.WIDE else HomeSizeClass.COMPACT
        val safeFrameDp = HomeRect(0f, 0f, device.widthPx / d, (device.heightPx - navigationDp * d) / d)
        val chrome = HomePresentationPolicy.topChromeSpec(safeFrameDp, sizeClass).toPx(density)
        val layout =
            resolveImmersiveLayout(
                orientation = HomeOrientation.LANDSCAPE,
                windowWidthPx = device.widthPx,
                windowHeightPx = device.heightPx,
                safeBounds = safe,
                dockRect = metrics.dockRect.toPx(density),
                bottomClearancePx = AdaptiveLayoutConstants.CONTENT_DOCK_CLEARANCE_DP * d,
                fontScale = fontScale,
                topChrome = chrome,
                minimumPlayHeightPx = 48f * d,
            )
        val spec = resolveImmersivePlayTimeVisualSpec(HomeOrientation.LANDSCAPE, metrics.scale, fontScale)
        return Scene(layout, GlyphModel(spec, d, fontScale), spec.anchorGapDp * d, chrome.titleRect)
    }

    /**
     * A stand-in for Compose's text layout of the displayed label: CJK glyphs 1em, digits .56em, spaces and
     * no-break spaces .26em, word joiners 0 (Noto Sans CJK + Roboto); lines break only at regular spaces,
     * as [immersivePlayTimeDisplay] allows. Box = text + the label's icon, gap and padding, at least the
     * spec's minimum height; its width is the widest line.
     */
    private class GlyphModel(
        private val spec: ImmersivePlayTimeVisualSpec,
        private val density: Float,
        private val fontScale: Float,
    ) {
        private val fontPx = spec.fontSizeSp * fontScale * density
        private val linePx = spec.lineHeightSp * fontScale * density
        private val chromePx = (spec.iconSizeDp + spec.contentGapDp + spec.horizontalPaddingDp * 2f) * density
        private val verticalPx = spec.verticalPaddingDp * 2f * density
        private val floorPx = max(spec.minimumHeightDp, spec.iconSizeDp) * density

        fun oneLineHeight(): Float = max(floorPx, linePx + verticalPx)

        fun twoLineHeight(): Float = max(floorPx, linePx * IMMERSIVE_PLAY_TIME_MAX_LINES + verticalPx)

        fun lines(
            label: String,
            maxWidthPx: Float,
        ): Int = lineWidths(label, maxWidthPx).size

        /** Greedy wrap at the break opportunities; trailing spaces do not count toward a line's width. */
        private fun lineWidths(
            label: String,
            maxWidthPx: Float,
        ): List<Float> {
            val limit = (maxWidthPx - chromePx).coerceAtLeast(1f)
            val lines = mutableListOf(0f)
            var pending = 0f
            tokens(label).forEach { token ->
                val width = token.sumOf { advance(it).toDouble() }.toFloat()
                when {
                    token == " " || token == "\u200B" -> pending += width
                    lines.last() + pending + width <= limit || lines.last() == 0f -> lines[lines.lastIndex] = lines.last() + pending + width
                    else -> lines += width
                }
                if (token != " " && token != "\u200B") pending = 0f
            }
            return lines
        }

        fun box(
            labels: List<String>,
            maxWidthPx: Float,
        ): ImmersiveLabelBox =
            labels.map { label ->
                val widths = lineWidths(label, maxWidthPx)
                val lines = widths.size
                ImmersiveLabelBox(
                    // Like the app: the widest laid-out line, so a narrowed pill keeps every pixel it can.
                    widthPx = widths.max() + chromePx,
                    heightPx = max(floorPx, linePx * min(lines, IMMERSIVE_PLAY_TIME_MAX_LINES) + verticalPx),
                    // A part wider than the line would be split mid-word: that counts as cut, like "…".
                    ellipsized = lines > IMMERSIVE_PLAY_TIME_MAX_LINES || splitsAPart(label, maxWidthPx),
                )
            }.reduce { a, b -> ImmersiveLabelBox(max(a.widthPx, b.widthPx), max(a.heightPx, b.heightPx), a.ellipsized || b.ellipsized) }

        private fun splitsAPart(
            label: String,
            maxWidthPx: Float,
        ): Boolean = immersivePlayTimeParts(label).any { part -> part.sumOf { advance(it).toDouble() } > (maxWidthPx - chromePx).coerceAtLeast(1f) }

        private fun advance(c: Char): Float =
            fontPx *
                when {
                    c == '\u2060' || c == '\u200B' -> 0f
                    c == ' ' || c == '\u00A0' -> .26f
                    c.isDigit() -> .56f
                    c.code < 128 -> .55f
                    else -> 1f
                }

        private fun tokens(label: String): List<String> = Regex("[^ \u200B]+|[ \u200B]").findAll(label).map { it.value }.toList()
    }
}
