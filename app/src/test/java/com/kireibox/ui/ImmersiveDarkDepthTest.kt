package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.FxLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * r4 (user request 1): the dark immersive home is as dark and moody as alpha.17 again, and its default
 * reads as 「氛围色」 + purple (the accent) rather than a grey room. These pin the depth and mood policy
 * (ImmersiveDarkDepth) the renderer applies; the light theme must stay untouched.
 */
class ImmersiveDarkDepthTest {
    private val dark = ImmersivePaletteResolver.resolve(isDark = true)
    private val light = ImmersivePaletteResolver.resolve(isDark = false)
    private val blueTone = Color(CoverToneExtractor.fromArgbPixels(IntArray(16) { 0xFF2D4C8C.toInt() }).argb)

    @Test
    fun theDarkBaseIsNearBlackInTheAccentHue() {
        // The token stays the neutral graphite; the immersive base carries the accent hue at the token's
        // luminance (r4 · V1: alpha.17's #05070D was blue-black, a neutral base read as a grey room).
        val token = KireiTokens.Immersive.DarkBase
        assertEquals(Color(0xFF0A0A0C), token)
        AccentChoice.entries.forEach { accent ->
            val palette = ImmersivePaletteResolver.resolve(isDark = true, accent = accent)
            // Within one 8-bit step: sRGB colors are stored with 8 bits per channel.
            assertEquals("$accent: as dark as the graphite token", token.luminance(), palette.base.luminance(), .0004f)
            assertTrue("$accent: darker than the app's graphite page", palette.base.luminance() < KireiTokens.DarkNeutrals.background.luminance())
            // 8-bit channels at such a low value move the hue by a few degrees.
            assertTrue("$accent: carries the accent hue", hueDistance(hue(palette.base), hue(palette.accent)) < 12f)
            assertEquals(ImmersiveDarkDepth.BASE_SATURATION, saturation(palette.base), .03f)
        }
        assertEquals("light pages keep the neutral light base", KireiTokens.Immersive.LightBase, light.base)
    }

    @Test
    fun everyAccentTintsTheRoomEquallyDeep() {
        val shades = AccentChoice.entries.map { ImmersivePaletteResolver.resolve(isDark = true, accent = it).atmosphere }
        shades.forEach { shade ->
            assertEquals(ImmersiveDarkDepth.ATMOSPHERE_LUMINANCE, shade.luminance(), .002f)
            assertEquals(ImmersiveDarkDepth.ATMOSPHERE_SATURATION, saturation(shade), .03f)
        }
        assertEquals("light palettes keep the accent", light.accent, light.atmosphere)
    }

    @Test
    fun theDarkAtmosphereReadsAsTheAccentEvenOnAWarmCover() {
        // r4 · V1 (「默认配置要是氛围色和紫色」): a warm brown cover tone must not turn the default room grey-brown.
        val warm = Color.hsv(20f, .42f, .34f)
        val purpleHue = hue(dark.accent)
        listOf(true, false).forEach { dynamicTint ->
            val room = immersiveAtmosphereTint(warm, dark, dynamicTint)
            assertTrue("dynamic=$dynamicTint leans to the purple accent", hueDistance(hue(room), purpleHue) < 45f)
            assertTrue("dynamic=$dynamicTint is colored, not grey", saturation(room) >= .3f)
        }
        val withCover = immersiveAtmosphereTint(warm, dark, dynamicTint = true)
        val accentLed = immersiveAtmosphereTint(warm, dark, dynamicTint = false)
        assertTrue("「动态取色」 keeps more of the cover", distance(withCover, warm) < distance(accentLed, warm))
        assertEquals("light pages keep the tint", warm, immersiveAtmosphereTint(warm, light, dynamicTint = true))
    }

    @Test
    fun theFloorGlowLeansToTheAccentOnlyOnDarkPages() {
        val glow = immersiveGlowTint(blueTone, dark)
        assertTrue("cover tone + accent glow", distance(glow, dark.atmosphere) < distance(blueTone, dark.atmosphere))
        assertTrue("still carries the cover", distance(glow, blueTone) < distance(dark.atmosphere, blueTone))
        assertTrue("a saturated accent, not the grey-lavender light accent", saturation(glow) > saturation(lerp(blueTone, dark.accent, .5f)))
        assertEquals(.5f, ImmersiveDarkDepth.GLOW_ACCENT_MIX, 0f)
        assertEquals("light pages keep the cover tone", blueTone, immersiveGlowTint(blueTone, light))
    }

    @Test
    fun darkPagesHoldCoverPixelsBackAndLightPagesAreUntouched() {
        assertEquals(.7f, immersiveImageryAlphaScale(isDark = true, source = ImmersiveBackdropCoverSource.RAW), 0f)
        assertEquals(.5f, immersiveImageryAlphaScale(isDark = true, source = ImmersiveBackdropCoverSource.BLURRED), 0f)
        assertEquals(1f, immersiveImageryAlphaScale(isDark = true, source = ImmersiveBackdropCoverSource.NONE), 0f)
        assertEquals("封面氛围 is alpha.16 verbatim", 1f, immersiveImageryAlphaScale(isDark = true, source = ImmersiveBackdropCoverSource.ATMOSPHERE), 0f)
        ImmersiveBackdropCoverSource.entries.forEach { source ->
            assertEquals("light $source", 1f, immersiveImageryAlphaScale(isDark = false, source = source), 0f)
        }
        assertEquals(.4f, immersiveAuroraAlphaScale(isDark = true), 0f)
        assertEquals(1f, immersiveAuroraAlphaScale(isDark = false), 0f)
    }

    @Test
    fun blurredImageryGetsADarkScrimThatEasesWithIntensity() {
        val scrims = FxLevel.entries.map { immersiveImageryWashAlpha(isDark = true, source = ImmersiveBackdropCoverSource.BLURRED, level = it) }
        assertEquals(listOf(.48f, .42f, .36f), scrims)
        FxLevel.entries.forEach { level ->
            assertEquals("no imagery, no scrim", 0f, immersiveImageryWashAlpha(true, ImmersiveBackdropCoverSource.RAW, level), 0f)
            assertEquals(0f, immersiveImageryWashAlpha(true, ImmersiveBackdropCoverSource.NONE, level), 0f)
            assertEquals("light keeps its pastel wash", IMMERSIVE_LIGHT_COVER_WASH, immersiveImageryWashAlpha(false, ImmersiveBackdropCoverSource.BLURRED, level), 0f)
            assertEquals(0f, immersiveImageryWashAlpha(false, ImmersiveBackdropCoverSource.RAW, level), 0f)
        }
    }

    @Test
    fun theDarkAuroraIsDeepNotALightHaze() {
        val colors = immersiveAuroraColors(blueTone, dark)
        assertEquals(blueTone, colors[0])
        assertTrue("the accent blob sinks toward the base", colors[1].luminance() < dark.accent.luminance())
        assertTrue("never the light highlight", colors[1].luminance() < dark.accentBright.luminance())
        val shiftedValue = CoverToneExtractor.rgbToHsv(colors[2].red, colors[2].green, colors[2].blue)[2]
        assertTrue(shiftedValue <= ImmersiveDarkDepth.AURORA_SHIFTED_MAX_VALUE + .01f)
        assertEquals("light keeps its highlight", light.accentBright, immersiveAuroraColors(immersivePastel(blueTone), light)[1])
    }

    @Test
    fun anEmptyDarkGalleryIsAlpha17sAccentRoomNotAGreyRoom() {
        AccentChoice.entries.forEach { accent ->
            val palette = ImmersivePaletteResolver.resolve(isDark = true, accent = accent)
            val tint = immersiveEmptyGalleryTint(palette, dynamicTint = true)
            assertTrue("$accent keeps the accent hue", hueDistance(hue(tint), hue(palette.accent)) < 3f)
            assertEquals("$accent is colored like alpha.17's #5B5AC8 room, not grey", ImmersiveDarkDepth.EMPTY_TONE_SATURATION, saturation(tint), .03f)
            assertEquals("$accent: every accent equally deep", ImmersiveDarkDepth.EMPTY_TONE_LUMINANCE, tint.luminance(), .002f)
        }
        assertEquals("light empty pages keep the neutral pastel", immersiveBackdropTint(null, light, true), immersiveEmptyGalleryTint(light, dynamicTint = true))
    }

    @Test
    fun theFaintCoverIsSoftenedAndFadesBehindTheControlsOnDarkPages() {
        // r4 · V2: logos and credits of the 氛围色 cover no longer show around the pill, pager and Dock.
        assertTrue(immersiveSoftensRawCover(isDark = true))
        assertFalse("light pages keep the raw cover", immersiveSoftensRawCover(isDark = false))
        assertTrue(ImmersiveDarkDepth.COVER_FLOOR_START in .5f..<.8f)
        assertTrue(ImmersiveDarkDepth.COVER_FLOOR_ALPHA in .2f..<.7f)
    }

    @Test
    fun theDarkVignetteIsLighterThanBeforeAndTheLightOneIsUnchanged() {
        assertEquals(.28f * .85f, immersiveVignetteAlpha(isDark = true, increasedContrast = false, intensity = 1f), .0001f)
        assertEquals(.28f * .2f, immersiveVignetteAlpha(isDark = false, increasedContrast = false, intensity = 1f), .0001f)
        assertTrue(immersiveVignetteAlpha(true, increasedContrast = true, intensity = 1f) > immersiveVignetteAlpha(true, false, 1f))
    }

    private fun distance(
        a: Color,
        b: Color,
    ): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun saturation(color: Color): Float = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)[1]

    private fun hue(color: Color): Float = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)[0]

    private fun hueDistance(
        a: Float,
        b: Float,
    ): Float = abs(a - b).let { minOf(it, 360f - it) }
}
