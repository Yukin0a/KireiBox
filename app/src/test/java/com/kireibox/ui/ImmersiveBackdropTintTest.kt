package com.kireibox.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Backdrop color decisions (plan §6.1) and the per-tint brush cache (plan §6.5). */
class ImmersiveBackdropTintTest {
    private val dark = ImmersivePaletteResolver.resolve(isDark = true, accent = AccentChoice.SAKURA)
    private val light = ImmersivePaletteResolver.resolve(isDark = false, accent = AccentChoice.SAKURA)
    private val blueCover = CoverToneExtractor.fromArgbPixels(IntArray(16) { 0xFF2D4C8C.toInt() })

    @Test
    fun minimalPresetTintsWithTheCoverNotTheRawAccent() {
        // MINIMAL = TONE + dynamic tint off: the whole home used to turn sakura rose-brown.
        val tint = immersiveBackdropTint(blueCover, dark, dynamicTint = false)
        val cover = Color(blueCover.argb)
        assertTrue("tint stays near the cover tone", distance(tint, cover) < distance(tint, dark.accent))
        // lerp runs in Oklab, so allow twice the nominal 10% share in sRGB distance.
        assertTrue("the accent is only a faint hint", distance(tint, cover) <= 2f * IMMERSIVE_ACCENT_HINT * distance(cover, dark.accent))
        assertEquals("dynamic tint on: the pure cover tone", cover, immersiveBackdropTint(blueCover, dark, dynamicTint = true))
    }

    /** r4: a light accent (the default purple, sakura) must not lift the dark 简约 backdrop. */
    @Test
    fun theAccentHintShiftsHueButNeverBrightensTheDarkBackdrop() {
        val coverValue = value(Color(blueCover.argb))
        AccentChoice.entries.forEach { accent ->
            val palette = ImmersivePaletteResolver.resolve(isDark = true, accent = accent)
            val tint = immersiveBackdropTint(blueCover, palette, dynamicTint = false)
            assertEquals("$accent keeps the cover's brightness", coverValue, value(tint), .01f)
        }
    }

    @Test
    fun noCoverMeansANeutralTintInEveryAccent() {
        val fallback = CoverToneExtractor.fromArgbPixels(intArrayOf())
        AccentChoice.entries.forEach { accent ->
            val palette = ImmersivePaletteResolver.resolve(isDark = true, accent = accent)
            val tint = immersiveBackdropTint(fallback, palette, dynamicTint = true)
            assertTrue("$accent: neutral graphite, not the accent", saturation(tint) < .1f)
        }
        assertTrue(saturation(immersiveBackdropTint(null, dark, dynamicTint = false)) < .2f)
    }

    @Test
    fun lightThemeTintsArePastelsOfTheSameHue() {
        val tint = immersiveBackdropTint(blueCover, light, dynamicTint = true)
        assertTrue("light tint is a pastel, far above the dark cover tone", tint.luminance() > .4f && tint.luminance() > 5f * Color(blueCover.argb).luminance())
        assertTrue(saturation(tint) <= .33f)
        assertEquals(hue(Color(blueCover.argb)), hue(tint), 2f)
    }

    @Test
    fun rimLightIsALiftedHairlineNotADarkOutline() {
        val edge = KireiTokens.Immersive.CardEdge
        val rim = immersiveRimHighlight(Color(blueCover.argb))
        assertTrue("rim is lighter than the neutral edge", rim.luminance() > edge.luminance())
        assertEquals("rim keeps the hairline alpha", edge.alpha, rim.alpha, .01f)
    }

    @Test
    fun dragFramesReuseTheTwoToneBrushSetsAndASettledSwipeKeepsTheIncomingOne() {
        val fx = resolveImmersiveFx(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD))
        val plan = immersiveBackdropDrawPlan(true, true, .4f, reducedTransparency = false, style = fx.backdrop)
        val look = ImmersiveBackdropLook(plan.layers(), fx, dark, HomeRect(100f, 300f, 600f, 900f), 1f)
        val scene = ImmersiveBackdropScene(plan, look, Color(blueCover.argb), Color(0xFF8C2D4C), .4f, null, null)
        val cache = ImmersiveBackdropBrushCache()
        val size = Size(1080f, 2400f)
        val first = cache.frame(scene, size)
        val left = first.left
        val right = first.right
        assertNotNull(left)
        assertNotNull(right)
        repeat(12) { frame ->
            val next = cache.frame(scene.copy(fraction = .05f + frame * .07f), size)
            assertSame("drag frames rebuild no brushes", left, next.left)
            assertSame(right, next.right)
        }
        val settled = cache.frame(scene.copy(leftTint = scene.rightTint, fraction = 0f), size)
        assertSame("the incoming tone's brushes become the settled ones", right, settled.left)
        assertNull(settled.right)
    }

    @Test
    fun parallaxPublishesOnlyVisibleMovement() {
        listOf(1f, 2.625f, 3.5f).forEach { density ->
            val epsilon = immersiveParallaxPublishEpsilon(density)
            assertTrue(epsilon >= .002f)
            assertTrue("dead band ≈ half a pixel of backdrop pan", epsilon * IMMERSIVE_PARALLAX_BACKDROP_DP * density in .49f..0.51f || epsilon == .002f)
        }
    }

    private fun distance(
        a: Color,
        b: Color,
    ): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun saturation(color: Color): Float = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)[1]

    private fun hue(color: Color): Float = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)[0]

    private fun value(color: Color): Float = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)[2]
}
