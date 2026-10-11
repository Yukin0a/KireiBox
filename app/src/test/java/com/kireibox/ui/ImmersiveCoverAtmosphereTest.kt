package com.kireibox.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 封面氛围 (the default immersive backdrop) is the alpha.16 background, reproduced layer for layer
 * (user decision 2026-10-05, after the r5 enlarged-cover attempt was rejected as far too saturated).
 * These pin alpha.16's constants and layer stack for both themes; the wiring is pinned in
 * ImmersiveBackdropDrawPolicyTest and NsfwCoverWiringTest.
 */
class ImmersiveCoverAtmosphereTest {
    private val darkPurple = ImmersivePaletteResolver.resolve(isDark = true, accent = AccentChoice.CLASSIC)
    private val lightPurple = ImmersivePaletteResolver.resolve(isDark = false, accent = AccentChoice.CLASSIC)
    private val atmosphere = ImmersiveBackdropStyle.COVER_ZOOM

    @Test
    fun theTokensAreAlpha16sForBothThemes() {
        val dark = ImmersiveCoverAtmosphere.tokens(isDark = true)
        assertEquals(Color(0xFF05070D), dark.base)
        assertEquals(Color(0xF705070D), dark.scrimStart)
        assertEquals(Color(0xB805070D), dark.scrimMid)
        assertEquals(listOf(.38f, .28f, .20f, .22f, .16f), listOf(dark.blendAlpha, dark.radialAlpha, dark.radialReducedAlpha, dark.glowAlpha, dark.glowReducedAlpha))

        val light = ImmersiveCoverAtmosphere.tokens(isDark = false)
        assertEquals(Color(0xFFF4F2F9), light.base)
        assertEquals(Color(0xF7F6F3FC), light.scrimStart)
        assertEquals(Color(0xB8F6F3FC), light.scrimMid)
        assertEquals(listOf(.26f, .18f, .12f, .14f, .10f), listOf(light.blendAlpha, light.radialAlpha, light.radialReducedAlpha, light.glowAlpha, light.glowReducedAlpha))

        assertEquals(.12f, IMMERSIVE_BACKDROP_BITMAP_ALPHA, 0f)
        assertEquals(900f, ImmersiveCoverAtmosphere.BLEND_RADIUS, 0f)
        assertEquals(1500f, ImmersiveCoverAtmosphere.RADIAL_RADIUS, 0f)
    }

    @Test
    fun onlyTheBackdropTokensReplaceThePalette() {
        listOf(darkPurple, lightPurple).forEach { palette ->
            val tokens = ImmersiveCoverAtmosphere.tokens(palette.isDark)
            val swapped = palette.withCoverAtmosphere()
            assertEquals(tokens.base, swapped.base)
            assertEquals(tokens.scrimStart, swapped.scrimStart)
            assertEquals(tokens.scrimMid, swapped.scrimMid)
            assertEquals(tokens.blendAlpha, swapped.blendAlpha, 0f)
            assertEquals(tokens.radialAlpha, swapped.radialAlpha, 0f)
            assertEquals(tokens.radialReducedAlpha, swapped.radialReducedAlpha, 0f)
            assertEquals(tokens.glowAlpha, swapped.glowAlpha, 0f)
            assertEquals(tokens.glowReducedAlpha, swapped.glowReducedAlpha, 0f)
            // Ink, glass and accent stay the app's own (the Dock, titles and controls read them).
            assertEquals(listOf(palette.primary, palette.secondary, palette.glass, palette.glassBorder), listOf(swapped.primary, swapped.secondary, swapped.glass, swapped.glassBorder))
            assertEquals(listOf(palette.accent, palette.accentBright, palette.playInk), listOf(swapped.accent, swapped.accentBright, swapped.playInk))
            assertEquals(listOf(palette.cardOverlayBottom, palette.atmosphere), listOf(swapped.cardOverlayBottom, swapped.atmosphere))
        }
    }

    @Test
    fun theCoverToneIsUsedRawWithoutAccentHintPastelOrAtmosphere() {
        val blue = CoverToneExtractor.fromArgbPixels(IntArray(16) { 0xFF2D4C8C.toInt() })
        assertEquals(Color(blue.argb), immersiveCoverAtmosphereTint(blue))
        // The fallback tone (no cover yet) is drawn as it is, like alpha.16.
        val fallback = CoverToneExtractor.fromArgbPixels(intArrayOf())
        assertEquals(Color(fallback.argb), immersiveCoverAtmosphereTint(fallback))
    }

    @Test
    fun theGlowIsAlpha16sOvalUnderTheCentreCard() {
        val glow = ImmersiveCoverAtmosphereGlow(HomeRect(x = 100f, y = 300f, w = 600f, h = 900f))
        assertEquals(600f * 2.24f, glow.size.width, .001f)
        assertEquals(900f * .34f, glow.size.height, .001f)
        assertEquals(100f - 600f * .62f, glow.topLeft.x, .001f)
        assertEquals(300f + 900f * .78f, glow.topLeft.y, .001f)
        assertEquals(maxOf(600f * 2.24f, 900f * .34f) * .50f, glow.radius, .001f)
    }

    @Test
    fun theStackIsAlpha16sInBothThemes() {
        val plan = immersiveBackdropDrawPlan(true, true, 0f, reducedTransparency = false, style = atmosphere)
        // base -> raw cover at .12 -> blend radial -> horizontal scrim -> radial -> glow, nothing else.
        assertEquals(ImmersiveBackdropCoverSource.ATMOSPHERE, plan.coverSource)
        assertEquals(IMMERSIVE_BACKDROP_BITMAP_ALPHA, plan.leftBitmapAlpha, 0f)
        assertTrue(plan.drawBlendRadial && plan.drawHorizontalScrim && plan.drawRadial && plan.drawGlow)
        assertFalse(plan.drawAurora)
        val size = Size(1080f, 2400f)
        val center = HomeRect(100f, 300f, 600f, 900f)
        val tone = Color(0xFF2D4C8C)
        listOf(darkPurple, lightPurple).forEach { palette ->
            val tokens = ImmersiveCoverAtmosphere.tokens(palette.isDark)
            val fx = resolveImmersiveFx(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD))
            val brushes = ImmersiveBackdropBrushes(ImmersiveBackdropLook(plan.layers(), fx, palette.withCoverAtmosphere(), center, 1f), tone, size)
            val name = if (palette.isDark) "dark" else "light"
            assertEquals("$name base", tokens.base, brushes.base)
            assertNotNull("$name blend radial", brushes.blendRadial)
            assertNotNull("$name scrim", brushes.scrim)
            assertNotNull("$name radial", brushes.radial)
            assertNotNull("$name glow", brushes.glow)
            assertNotNull("$name oval glow", brushes.glowOval)
            assertNull("$name has no 氛围色 floor", brushes.coverFloor)
            assertNull("$name has no aurora", brushes.aurora)
            assertNull("$name has no vignette by default", brushes.vignette)
        }
    }

    @Test
    fun aSwipeCrossfadesTheRawCoversByAlphaLikeAlpha16() {
        val mid = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = false, style = atmosphere)
        assertEquals(.06f, mid.leftBitmapAlpha, .0001f)
        assertEquals(.06f, mid.rightBitmapAlpha, .0001f)
        assertEquals(2, mid.bitmapLayerCount)
        val rest = immersiveBackdropDrawPlan(true, true, 0f, reducedTransparency = false, style = atmosphere)
        assertEquals(1, rest.bitmapLayerCount)
        assertEquals(0f, rest.rightBitmapAlpha, 0f)
        assertEquals(.12f, rest.leftBitmapAlpha, 0f)
    }

    @Test
    fun reducedTransparencyHasNoCoverAndNoBlendRadial() {
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = true, style = atmosphere)
        assertEquals(ImmersiveBackdropCoverSource.NONE, plan.coverSource)
        assertEquals(0, plan.bitmapLayerCount)
        assertFalse(plan.drawBlendRadial)
        assertTrue(plan.drawHorizontalScrim && plan.drawRadial && plan.drawGlow)
        // The runtime swaps the style for 氛围色, which is the same stack without cover pixels.
        val fx = resolveImmersiveFx(ImmersiveVisualSettings(), ImmersiveFxEnvironment(reducedTransparency = true))
        assertEquals(ImmersiveBackdropStyle.TONE, fx.backdrop)
        assertEquals(ImmersiveBackdropCoverSource.NONE, immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency))
    }

    @Test
    fun theCoverIsNeitherBlurredNorScaledNorHeldBack() {
        assertFalse("the decoded cover is drawn itself", ImmersiveBackdropCoverSource.ATMOSPHERE.needsBlurredCopy())
        assertTrue(ImmersiveBackdropCoverSource.RAW.needsBlurredCopy() && ImmersiveBackdropCoverSource.BLURRED.needsBlurredCopy())
        assertFalse(ImmersiveBackdropCoverSource.NONE.needsBlurredCopy())
        listOf(true, false).forEach { dark ->
            assertEquals(1f, immersiveImageryAlphaScale(dark, ImmersiveBackdropCoverSource.ATMOSPHERE), 0f)
            assertEquals(0f, immersiveImageryWashAlpha(dark, ImmersiveBackdropCoverSource.ATMOSPHERE, FxLevel.MEDIUM), 0f)
        }
        assertEquals("the standard 背景强度 is the identity", 1f, FxLevel.MEDIUM.backdropIntensityScale(), 0f)
    }

    @Test
    fun theStandardPresetDoesNotDarkenOrRoughenTheBackdrop() {
        val standard = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)
        val fx = resolveImmersiveFx(standard)
        assertEquals(atmosphere, fx.backdrop)
        assertEquals(ImmersiveBackdropCoverSource.ATMOSPHERE, immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency))
        assertFalse("alpha.16 had no vignette: it would darken the corners", fx.vignette)
        assertFalse("alpha.16 had no grain, and the cover does not move", fx.grain || fx.kenBurns || fx.gyroParallax)
        assertEquals(FxLevel.MEDIUM, fx.intensity)
        // The 华丽 preset keeps its vignette.
        assertTrue(resolveImmersiveFx(ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH)).vignette)
    }

    @Test
    fun anEmptyGalleryKeepsTheAccentStage() {
        val fx = resolveImmersiveFx(ImmersiveVisualSettings())
        assertEquals(ImmersiveBackdropStyle.TONE, fx.forHero(hasHero = false).backdrop)
        assertSame(fx, fx.forHero(hasHero = true))
        val aurora = resolveImmersiveFx(ImmersiveVisualSettings().copy(backdrop = ImmersiveBackdropStyle.AURORA))
        assertSame(aurora, aurora.forHero(hasHero = false))
    }

    @Test
    fun theRendererDrawsTheRawCoverWithoutPerFrameObjects() {
        val support = UiSourceFiles.file("ImmersiveHomeBackdropSupport.kt")
        assertTrue("alpha.16's clipped oval glow", support.contains("drawOval(brush = brush, topLeft = oval.topLeft, size = oval.size, alpha = alpha)"))
        assertTrue("the glow brush is built once with the cached brushes", support.contains("Brush.radialGradient(listOf(tint.copy(alpha = glowAlpha), Color.Transparent), radius = glowOval.radius)"))
        assertTrue("the wide radial keeps the raw tone", support.contains("if (atmosphere) tint else immersiveAtmosphereTint("))
        // The r5 enlarged-cover machinery is gone: no zoom, ink, veil or desaturated blur copy.
        listOf("ImmersiveCoverZoom", "zoomInk", "zoomWash", "drawImmersiveCoverVeil", "BlendMode.Modulate", "saturateArgb").forEach { removed ->
            assertFalse("$removed is removed", support.contains(removed))
        }
        val backdrop = UiSourceFiles.file("ImmersiveHomeBackdrop.kt")
        assertTrue(backdrop.contains("ImmersiveBackdropCoverSource.ATMOSPHERE -> this?.bitmap"))
        assertTrue(backdrop.contains("atmosphere -> immersiveCoverAtmosphereTint(leftHero.tone)"))
        assertTrue(backdrop.contains("val backdropPalette = if (atmosphere) palette.withCoverAtmosphere() else palette"))
        val preview = UiSourceFiles.file("ImmersivePresetPreview.kt")
        assertTrue("the preset card previews the same stack", preview.contains("ImmersiveBackdropStyle.COVER_ZOOM -> drawPresetCoverAtmosphere("))
        assertTrue(preview.contains("ImmersiveBackdropStyle.COVER_ZOOM -> ImmersiveCoverAtmosphere.tokens(palette.isDark).base"))
        assertTrue(immersivePresetDescription(ImmersiveFxPreset.STANDARD).startsWith("封面氛围"))
    }
}
