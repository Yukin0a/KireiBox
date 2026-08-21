package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveBackdropDrawPolicyTest {
    @Test
    fun `full backdrop composition is represented by one Compose Canvas layer`() {
        val plan = immersiveBackdropDrawPlan(
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
        val start = immersiveBackdropDrawPlan(
            true,
            true,
            IMMERSIVE_BACKDROP_ENDPOINT_EPSILON,
            reducedTransparency = false,
        )
        val justAfterStart = immersiveBackdropDrawPlan(
            true,
            true,
            IMMERSIVE_BACKDROP_ENDPOINT_EPSILON + .0001f,
            reducedTransparency = false,
        )
        val justBeforeEnd = immersiveBackdropDrawPlan(
            true,
            true,
            1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON - .0001f,
            reducedTransparency = false,
        )
        val end = immersiveBackdropDrawPlan(
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
    fun `side shadows are zero and center preserves the caller source elevation`() {
        assertEquals(0f, immersiveCardShadowElevationPx(.49f, 16f), .0001f)
        assertEquals(0f, immersiveCardShadowElevationPx(.5f, 16f), .0001f)
        assertEquals(8f, immersiveCardShadowElevationPx(.75f, 16f), .0001f)
        assertEquals(12f, immersiveCardShadowElevationPx(1f, 12f), .0001f)
        assertEquals(16f, immersiveCardShadowElevationPx(1f, 16f), .0001f)
    }
}
