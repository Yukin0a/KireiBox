package com.kireibox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Settings tile palette (spec §2.3, r4 S1 — user request 「设置页左边的选项图标颜色都不太配合」).
 * The alpha.17 tiles mixed OKLCH lightness .55–.78 and chroma .04–.21, and white glyphs fell to
 * 2.1:1 on orange. The tonal palette gives every hue the same OKLCH lightness and chroma (a
 * harmonious set that sits with the purple accent and the graphite neutrals in both themes) and a
 * same-hue glyph that clears WCAG 1.4.11's 3:1 with room to spare.
 */
class KireiTileColorTest {
    private val chromatic = KireiTileColor.entries - setOf(KireiTileColor.SLATE, KireiTileColor.GRAY)

    @Test
    fun everyContainerAndGlyphSharesOnePerceivedLightnessPerTheme() {
        KireiTileColor.entries.forEach { tile ->
            assertNear("$tile dark container L", .32, oklab(tile.container(isDark = true)).l, .012)
            assertNear("$tile dark glyph L", .82, oklab(tile.glyph(isDark = true)).l, .012)
            assertNear("$tile light container L", .935, oklab(tile.container(isDark = false)).l, .012)
            assertNear("$tile light glyph L", .52, oklab(tile.glyph(isDark = false)).l, .012)
        }
    }

    @Test
    fun chromaIsEvenAcrossHuesAndNeverNeon() {
        chromatic.forEach { tile ->
            // Equal chroma, only trimmed where sRGB cannot reach it (teal / blue / indigo).
            assertInRange("$tile dark container C", .045, .057, oklab(tile.container(isDark = true)).c)
            assertInRange("$tile dark glyph C", .085, .102, oklab(tile.glyph(isDark = true)).c)
            assertInRange("$tile light container C", .028, .037, oklab(tile.container(isDark = false)).c)
            assertInRange("$tile light glyph C", .08, .132, oklab(tile.glyph(isDark = false)).c)
            // The stored hue is the OKLCH hue the colors were derived from.
            val hue = oklab(tile.glyph(isDark = false)).h
            assertTrue("$tile hue ${tile.hue} vs %.1f".format(hue), hueDistance(hue, tile.hue.toDouble()) < 4.0)
        }
        listOf(KireiTileColor.SLATE, KireiTileColor.GRAY).forEach { neutral ->
            assertTrue("$neutral stays a graphite neutral", oklab(neutral.glyph(isDark = false)).c < .04)
        }
    }

    @Test
    fun glyphsClearNonTextContrastOnTheirContainers() {
        KireiTileColor.entries.forEach { tile ->
            assertTrue("$tile dark glyph/container", contrast(tile.glyph(isDark = true), tile.container(isDark = true)) >= 7.0)
            assertTrue("$tile light glyph/container", contrast(tile.glyph(isDark = false), tile.container(isDark = false)) >= 4.3)
            // The colored glyph alone still reads on the list group if a container blends in.
            assertTrue("$tile light glyph on the white group", contrast(tile.glyph(isDark = false), KireiTokens.LightNeutrals.surface) >= 4.5)
            assertTrue("$tile dark glyph on the graphite group", contrast(tile.glyph(isDark = true), KireiTokens.DarkNeutrals.surface) >= 7.0)
        }
    }

    @Test
    fun chromaticHuesAreSpreadSoNeighboursStayDistinct() {
        val hues = chromatic.map { it.hue.toDouble() }
        hues.forEachIndexed { i, a ->
            hues.drop(i + 1).forEach { b -> assertTrue("hues $a and $b too close", hueDistance(a, b) >= 25.0) }
        }
        assertEquals(chromatic.size, hues.distinct().size)
    }

    private data class Lch(
        val l: Double,
        val c: Double,
        val h: Double,
    )

    /** sRGB → OKLab (Björn Ottosson) → LCh. */
    private fun oklab(color: Color): Lch {
        fun linear(v: Float): Double = if (v <= .04045f) v / 12.92 else ((v + .055) / 1.055).pow(2.4)
        val r = linear(color.red)
        val g = linear(color.green)
        val b = linear(color.blue)
        val l = cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        val okL = .2104542553 * l + .7936177850 * m - .0040720468 * s
        val okA = 1.9779984951 * l - 2.4285922050 * m + .4505937099 * s
        val okB = .0259040371 * l + .7827717662 * m - .8086757660 * s
        val hue = (Math.toDegrees(atan2(okB, okA)) + 360.0) % 360.0
        return Lch(okL, hypot(okA, okB), hue)
    }

    private fun hueDistance(
        a: Double,
        b: Double,
    ): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + .05) / (minOf(la, lb) + .05)
    }

    private fun assertNear(
        label: String,
        expected: Double,
        actual: Double,
        tolerance: Double,
    ) = assertTrue("$label: %.3f vs $expected".format(actual), abs(actual - expected) <= tolerance)

    private fun assertInRange(
        label: String,
        min: Double,
        max: Double,
        actual: Double,
    ) = assertTrue("$label: %.3f not in $min..$max".format(actual), actual in min..max)
}
