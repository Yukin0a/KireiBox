package com.kireibox.ui

import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveBackdropDrawPolicyTest {
    @Test
    fun `full backdrop composition is represented by one Compose Canvas layer`() {
        val plan =
            immersiveBackdropDrawPlan(
                leftBitmapAvailable = true,
                rightBitmapAvailable = true,
                fraction = .5f,
                reducedTransparency = false,
            )

        assertEquals(1, plan.composeCanvasLayerCount)
        assertEquals(2, plan.bitmapLayerCount)
        assertTrue(plan.drawBlendRadial)
        assertTrue(plan.drawHorizontalScrim)
        assertTrue(plan.drawRadial)
        assertTrue(plan.drawGlow)
        assertEquals(.06f, plan.leftBitmapAlpha, .0001f)
        assertEquals(.06f, plan.rightBitmapAlpha, .0001f)
    }

    @Test
    fun `bitmap alpha below threshold is skipped and threshold remains visible`() {
        assertFalse(shouldDrawImmersiveBackdropBitmap(.0199f))
        assertTrue(shouldDrawImmersiveBackdropBitmap(IMMERSIVE_BACKDROP_BITMAP_VISIBILITY_EPSILON))
        assertTrue(shouldDrawImmersiveBackdropBitmap(.12f))
    }

    @Test
    fun `transition endpoint epsilon gates old and new bitmap layers`() {
        val start =
            immersiveBackdropDrawPlan(
                true,
                true,
                IMMERSIVE_BACKDROP_ENDPOINT_EPSILON,
                reducedTransparency = false,
            )
        val justAfterStart =
            immersiveBackdropDrawPlan(
                true,
                true,
                IMMERSIVE_BACKDROP_ENDPOINT_EPSILON + .0001f,
                reducedTransparency = false,
            )
        val justBeforeEnd =
            immersiveBackdropDrawPlan(
                true,
                true,
                1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON - .0001f,
                reducedTransparency = false,
            )
        val end =
            immersiveBackdropDrawPlan(
                true,
                true,
                1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON,
                reducedTransparency = false,
            )

        assertTrue(start.isAtStartEndpoint)
        assertFalse(start.isAtEndEndpoint)
        assertFalse(start.drawRightBitmap)
        assertTrue(start.drawLeftBitmap)
        assertEquals(1, start.bitmapLayerCount)

        assertFalse(justAfterStart.isAtStartEndpoint)
        assertFalse(justAfterStart.drawRightBitmap)
        assertTrue(justAfterStart.drawLeftBitmap)

        assertFalse(justBeforeEnd.isAtEndEndpoint)
        assertTrue(justBeforeEnd.drawRightBitmap)
        assertFalse(justBeforeEnd.drawLeftBitmap)

        assertFalse(end.isAtStartEndpoint)
        assertTrue(end.isAtEndEndpoint)
        assertTrue(end.drawRightBitmap)
        assertFalse(end.drawLeftBitmap)
        assertEquals(1, end.bitmapLayerCount)
    }

    @Test
    fun `transition normalization snaps only within the endpoint epsilon`() {
        assertEquals(
            0f,
            normalizeImmersiveBackdropTransition(IMMERSIVE_BACKDROP_ENDPOINT_EPSILON),
            .0001f,
        )
        assertEquals(
            IMMERSIVE_BACKDROP_ENDPOINT_EPSILON + .0001f,
            normalizeImmersiveBackdropTransition(IMMERSIVE_BACKDROP_ENDPOINT_EPSILON + .0001f),
            .0001f,
        )
        assertEquals(
            1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON - .0001f,
            normalizeImmersiveBackdropTransition(1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON - .0001f),
            .0001f,
        )
        assertEquals(
            1f,
            normalizeImmersiveBackdropTransition(1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON),
            .0001f,
        )
    }

    @Test
    fun `away from endpoints retains both old and new bitmap layers`() {
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = false)

        assertFalse(plan.isAtStartEndpoint)
        assertFalse(plan.isAtEndEndpoint)
        assertTrue(plan.drawLeftBitmap)
        assertTrue(plan.drawRightBitmap)
        assertEquals(2, plan.bitmapLayerCount)
    }

    @Test
    fun `reduced transparency keeps the single pass but omits bitmap and blend layers`() {
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = true)

        assertEquals(1, plan.composeCanvasLayerCount)
        assertEquals(0, plan.bitmapLayerCount)
        assertFalse(plan.drawBlendRadial)
        assertTrue(plan.drawHorizontalScrim)
        assertTrue(plan.drawRadial)
        assertTrue(plan.drawGlow)
    }

    @Test
    fun `the live backdrop renders exactly this plan in one draw node`() {
        val backdrop = UiSourceFiles.file("ImmersiveHomeBackdrop.kt")
        val support = UiSourceFiles.file("ImmersiveHomeBackdropSupport.kt")
        assertTrue("ImmersiveBackdrop builds its scene from the plan", backdrop.contains("immersiveBackdropDrawPlan("))
        assertTrue("the plan's style is the already-degraded one", backdrop.contains("style = fx.backdrop"))
        assertTrue("one drawWithCache node draws everything", backdrop.contains("drawWithCache {"))
        assertTrue("frame clock and tilt are read only while drawing", backdrop.contains("onDrawBehind { drawImmersiveBackdrop(scene, brushes, clock.seconds, parallax) }"))
        assertTrue(support.contains("plan.leftBitmapAlpha") && support.contains("plan.rightBitmapAlpha"))
        assertTrue("reduced-alpha palette fields are live again", support.contains("palette.radialReducedAlpha") && support.contains("palette.glowReducedAlpha"))
        assertTrue(support.contains("palette.blendAlpha"))
    }

    @Test
    fun `blurred cover backdrop draws the blurred bitmaps without the tone blend`() {
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = false, style = ImmersiveBackdropStyle.BLUR_COVER)

        assertEquals(1, plan.composeCanvasLayerCount)
        assertEquals(ImmersiveBackdropCoverSource.BLURRED, plan.coverSource)
        assertEquals(IMMERSIVE_BACKDROP_BLUR_ALPHA / 2f, plan.leftBitmapAlpha, .0001f)
        assertEquals(IMMERSIVE_BACKDROP_BLUR_ALPHA / 2f, plan.rightBitmapAlpha, .0001f)
        assertEquals(2, plan.bitmapLayerCount)
        assertFalse(plan.drawBlendRadial)
        assertFalse(plan.drawAurora)
        assertTrue(plan.drawHorizontalScrim && plan.drawRadial && plan.drawGlow)
    }

    @Test
    fun `aurora draws over a quieter blurred cover in the same pass`() {
        val plan = immersiveBackdropDrawPlan(true, false, 0f, reducedTransparency = false, style = ImmersiveBackdropStyle.AURORA)

        assertEquals(1, plan.composeCanvasLayerCount)
        assertEquals(ImmersiveBackdropCoverSource.BLURRED, plan.coverSource)
        assertEquals(IMMERSIVE_BACKDROP_AURORA_COVER_ALPHA, plan.leftBitmapAlpha, .0001f)
        assertTrue(plan.drawAurora)
        assertTrue(plan.drawLeftBitmap)
        assertFalse(plan.drawBlendRadial)
    }

    @Test
    fun `cover atmosphere is alpha 16s stack with the raw cover crossfaded by alpha`() {
        // 默认「封面氛围」: alpha.16's layers (raw cover at .12, blend radial, scrim, radial, glow); the
        // two covers crossfade by alpha exactly like alpha.16 (ImmersiveCoverAtmosphereTest pins the numbers).
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = false, style = ImmersiveBackdropStyle.COVER_ZOOM)

        assertEquals(1, plan.composeCanvasLayerCount)
        assertEquals(ImmersiveBackdropCoverSource.ATMOSPHERE, plan.coverSource)
        assertEquals(IMMERSIVE_BACKDROP_BITMAP_ALPHA / 2f, plan.leftBitmapAlpha, .0001f)
        assertEquals(IMMERSIVE_BACKDROP_BITMAP_ALPHA / 2f, plan.rightBitmapAlpha, .0001f)
        assertEquals(2, plan.bitmapLayerCount)
        assertTrue(plan.drawBlendRadial && plan.drawHorizontalScrim && plan.drawRadial && plan.drawGlow)
        assertFalse(plan.drawAurora)

        // While the incoming cover is still decoding, the outgoing one fades as before.
        val loading = immersiveBackdropDrawPlan(true, false, .5f, reducedTransparency = false, style = ImmersiveBackdropStyle.COVER_ZOOM)
        assertEquals(IMMERSIVE_BACKDROP_BITMAP_ALPHA / 2f, loading.leftBitmapAlpha, .0001f)
        // At rest only the current cover is drawn.
        val rest = immersiveBackdropDrawPlan(true, true, 0f, reducedTransparency = false, style = ImmersiveBackdropStyle.COVER_ZOOM)
        assertEquals(1, rest.bitmapLayerCount)
        assertEquals(IMMERSIVE_BACKDROP_BITMAP_ALPHA, rest.leftBitmapAlpha, 0f)
        // Reduced transparency: no cover and no blend radial, like alpha.16 (the caller has already swapped in the tone backdrop).
        val reduced = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = true, style = ImmersiveBackdropStyle.COVER_ZOOM)
        assertEquals(ImmersiveBackdropCoverSource.NONE, reduced.coverSource)
        assertFalse(reduced.drawBlendRadial)
    }

    @Test
    fun `solid backdrop paints no cover and no scrims but keeps the glow`() {
        val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = false, style = ImmersiveBackdropStyle.SOLID)

        assertEquals(ImmersiveBackdropCoverSource.NONE, plan.coverSource)
        assertEquals(0, plan.bitmapLayerCount)
        assertFalse(plan.drawHorizontalScrim || plan.drawRadial || plan.drawAurora || plan.drawBlendRadial)
        assertTrue(plan.drawGlow)
    }

    @Test
    fun `reduced transparency drops cover imagery for every style`() {
        ImmersiveBackdropStyle.entries.forEach { style ->
            val plan = immersiveBackdropDrawPlan(true, true, .5f, reducedTransparency = true, style = style)
            assertEquals(style.name, 0, plan.bitmapLayerCount)
            assertFalse(style.name, plan.drawAurora)
            assertEquals(1, plan.composeCanvasLayerCount)
        }
    }

    @Test
    fun `side shadows are zero and center preserves the caller source elevation`() {
        assertEquals(0f, immersiveCardShadowElevationPx(.49f, 16f), .0001f)
        assertEquals(0f, immersiveCardShadowElevationPx(.5f, 16f), .0001f)
        assertEquals(8f, immersiveCardShadowElevationPx(.75f, 16f), .0001f)
        assertEquals(12f, immersiveCardShadowElevationPx(1f, 12f), .0001f)
        assertEquals(16f, immersiveCardShadowElevationPx(1f, 16f), .0001f)
    }
}
