package com.kireibox.ui

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r5 · A2 (user: 「沉浸式横屏的『继续游戏』在下边有一小点字被下面的遮挡住了」).
 *
 * Root cause: the label was a 32 design px font in a fixed 36 px box (1.125 em). Compose's default LineHeightStyle
 * (Proportional, Trim.Both) leaves a one-line paragraph at the font's own height — with the CJK fallback font
 * (Noto Sans CJK: ascent 1.16 em, descent .288 em) 1.448 em — so the paragraph overflowed its box and
 * TextOverflow.Ellipsis clipped it at the box's bottom edge, cutting the glyphs' bottom ~.1 em (3–4 px on a phone).
 * The rule now: a 1.375 em line box laid out with Trim.None (exactly the box, glyphs placed by the font's baseline
 * ratio), a height floor instead of a fixed box, and a 12 px gap to the play pill so the stack keeps its height.
 */
class ImmersiveContinueLineTest {
    /** Font metrics in em: the line's ascent/descent and the glyph ink above/below the baseline. */
    private data class Font(val name: String, val ascent: Float, val descent: Float, val inkAbove: Float, val inkBelow: Float)

    // Ideographs fill the em box (.88 above, .12 below the baseline); Roboto's descenders reach .21 em.
    private val cjk = Font("Noto Sans CJK", 1.16f, .288f, .88f, .12f)
    private val latin = Font("Roboto", .928f, .244f, .74f, .21f)

    /** Where Compose's Trim.None + Proportional line puts the baseline in a box of [lineEm]. */
    private fun baselineEm(
        font: Font,
        lineEm: Float,
    ): Float = lineEm * font.ascent / (font.ascent + font.descent)

    private val lineEm = IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX / IMMERSIVE_SUBTITLE_FONT_SIZE_PX

    @Test
    fun theOldBoxWasShorterThanTheLineComposeLaysOut() {
        val oldLineEm = 36f / IMMERSIVE_SUBTITLE_FONT_SIZE_PX
        // Trim.Both keeps a single line at its natural height: taller than the old box, hence the clip.
        assertTrue(cjk.ascent + cjk.descent > oldLineEm)
        // …and its baseline at the natural ascent, so the bottom of the glyphs fell outside the box.
        assertTrue(cjk.ascent + cjk.inkBelow > oldLineEm)
    }

    @Test
    fun everyGlyphOfTheLabelFitsItsLineBox() {
        assertTrue("the line box leaves room for CJK ink", lineEm >= 1.3f)
        for (font in listOf(cjk, latin)) {
            val baseline = baselineEm(font, lineEm)
            assertTrue("${font.name} ink starts inside the box", baseline - font.inkAbove >= 0f)
            // Some air (≥ .05 em) between the lowest ink — CJK strokes, Latin descenders — and the box's bottom edge.
            assertTrue("${font.name} descenders end inside the box", baseline + font.inkBelow <= lineEm - .05f)
        }
    }

    @Test
    fun theLabelKeepsItsSpacingAboveThePlayPillOnEveryDeviceAndFontScale() {
        val devices = listOf(2400f to 1080f, 1600f to 720f, 2340f to 1080f, 2560f to 1600f, 1920f to 1080f, 1280f to 800f)
        val densities = listOf(2.625f, 2f, 2.75f, 2f, 3f, 1.33125f)
        devices.zip(densities).forEach { (size, d) ->
            val (width, height) = size
            for (fontScale in listOf(1f, 1.3f)) {
                val metrics = resolveAdaptiveUiMetrics(width / d, height / d, 0f, 0f, 0f, 24f)
                val layout =
                    resolveImmersiveLayout(
                        HomeOrientation.LANDSCAPE,
                        width,
                        height,
                        ImmersiveSafeBounds(0f, 0f, width, height - 24f * d),
                        dockRect = metrics.dockRect.toPx(Density(d, fontScale)),
                        bottomClearancePx = 8f * d,
                        fontScale = fontScale,
                        minimumPlayHeightPx = 48f * d,
                    )
                val case = "${width.toInt()}x${height.toInt()} fs$fontScale"
                val subtitle = layout.subtitle
                assertNotNull("$case shows 「继续游戏」", subtitle)
                val s = layout.renderScale
                val fontPx = IMMERSIVE_SUBTITLE_FONT_SIZE_PX * s * fontScale
                assertEquals("$case the box is the style's line height", IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX * s * fontScale, subtitle!!.h, .01f)
                assertTrue("$case the box fits the glyphs", subtitle.h >= fontPx * 1.3f)
                assertTrue("$case below the title", layout.title!!.y + layout.title!!.h <= subtitle.y + .01f)
                assertEquals("$case 12 px above the pill", IMMERSIVE_SUBTITLE_PLAY_GAP_PX * s, layout.play.y - (subtitle.y + subtitle.h), .01f)
                // The glyphs' ink ends ≥ 16 design px above the pill (the unclipped 36 + 20 design gave 15).
                val inkBottom = subtitle.y + (baselineEm(cjk, lineEm) + cjk.inkBelow) * fontPx
                assertTrue("$case ink clears the pill", layout.play.y - inkBottom >= 16f * s)
            }
        }
    }

    @Test
    fun atUnitFontScaleThePillKeepsItsPlace() {
        // 36 + 20 = 44 + 12: the label's box grew into the old gap, so the stack (and the pill) did not move.
        assertEquals(36f + 20f, IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX + IMMERSIVE_SUBTITLE_PLAY_GAP_PX, 0f)
    }

    @Test
    fun theLabelIsLaidOutWithTheWholeLineBoxAndAHeightFloor() {
        val cards = UiSourceFiles.file("ImmersiveHomeCards.kt")
        val start = cards.indexOf("\"继续游戏\",")
        assertTrue(start >= 0)
        val label = cards.substring(cards.lastIndexOf("layout.subtitle?.let {", start), cards.indexOf("zIndex(7f)", start))
        assertTrue(label.contains("style = continueStyle,"))
        assertTrue(label.contains("immersiveContinueLineStyle("))
        assertTrue(label.contains("designSp(IMMERSIVE_SUBTITLE_FONT_SIZE_PX, density, scale)"))
        assertTrue(label.contains("designSp(IMMERSIVE_SUBTITLE_LINE_HEIGHT_PX, density, scale)"))
        assertTrue("a height floor, not a fixed clipping box", label.contains("Modifier.homeTextRect(it, density)"))
        val metrics = UiSourceFiles.file("ImmersiveHomeMetrics.kt")
        assertTrue(metrics.contains("LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None)"))
        assertTrue(metrics.contains(".heightIn(min = pxDp(rect.h, density))"))
    }
}
