package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRevealMathTest {
    @Test
    fun upwardCollapseConsumesOnlyRemainingSearchHeight() {
        val move = SearchRevealMath.move(offsetPx = 18f, maxOffsetPx = 72f, deltaPx = -48f)
        assertEquals(0f, move.offsetPx, 0.001f)
        assertEquals(-18f, move.consumedPx, 0.001f)
    }

    @Test
    fun offsetNeverEscapesBoundsDuringRepeatedGestures() {
        var offset = 0f
        repeat(10) {
            offset = SearchRevealMath.move(offset, 72f, 500f).offsetPx
            assertEquals(72f, offset, 0.001f)
            offset = SearchRevealMath.move(offset, 72f, -500f).offsetPx
            assertEquals(0f, offset, 0.001f)
        }
    }

    @Test
    fun thresholdAndVelocityChooseStableTarget() {
        assertEquals(36f, SearchRevealMath.settleThreshold(72f), 0f)
        assertFalse(SearchRevealMath.shouldShow(35.9f, 36f, 0f, false))
        assertTrue(SearchRevealMath.shouldShow(36f, 36f, 0f, false))
        assertTrue(SearchRevealMath.shouldShow(1f, 36f, 800f, false))
        assertFalse(SearchRevealMath.shouldShow(71f, 36f, -800f, false))
        assertTrue(SearchRevealMath.shouldShow(0f, 44f, -2000f, true))
    }

    @Test
    fun midListDownwardDragDoesNotReveal() {
        val move = SearchRevealMath.move(
            offsetPx = 0f,
            maxOffsetPx = 72f,
            deltaPx = 24f,
            atListStart = false,
        )

        assertEquals(0f, move.offsetPx, 0f)
        assertEquals(0f, move.consumedPx, 0f)
    }

    @Test
    fun listStartDownwardDragRevealsOneToOne() {
        val move = SearchRevealMath.move(
            offsetPx = 8f,
            maxOffsetPx = 72f,
            deltaPx = 24f,
            atListStart = true,
        )

        assertEquals(32f, move.offsetPx, 0f)
        assertEquals(24f, move.consumedPx, 0f)
    }

    @Test
    fun upwardDragConsumesRevealBeforeLeavingResidualDistance() {
        val delta = -48f
        val move = SearchRevealMath.move(18f, 72f, delta, atListStart = false)

        assertEquals(0f, move.offsetPx, 0f)
        assertEquals(-18f, move.consumedPx, 0f)
        assertEquals(-30f, delta - move.consumedPx, 0f)
    }

    @Test
    fun nonFiniteInputsNeverPublishNaNOrEscapeBounds() {
        val nan = SearchRevealMath.move(Float.NaN, Float.NaN, Float.NaN)
        val positive = SearchRevealMath.move(10f, 72f, Float.POSITIVE_INFINITY)
        val negative = SearchRevealMath.move(10f, 72f, Float.NEGATIVE_INFINITY)

        assertEquals(0f, nan.offsetPx, 0f)
        assertEquals(72f, positive.offsetPx, 0f)
        assertEquals(0f, negative.offsetPx, 0f)
        assertFalse(nan.offsetPx.isNaN())
    }

    @Test
    fun upwardFlingIsFullyConsumedWhenItCannotCloseSearch() {
        val split = SearchRevealMath.splitUpwardFling(-1200f, 40f, 72f)
        assertEquals(-1200f, split.consumedVelocityY, 0.001f)
        assertEquals(0f, split.residualVelocityY, 0.001f)
    }

    @Test
    fun upwardFlingHandsOffOnlyResidualVelocityAfterCollapse() {
        val split = SearchRevealMath.splitUpwardFling(-3000f, 300f, 72f)
        assertTrue(split.consumedVelocityY < 0f)
        assertTrue(split.residualVelocityY < 0f)
        assertEquals(-3000f, split.consumedVelocityY + split.residualVelocityY, 0.01f)
    }

    @Test
    fun hiddenSearchDoesNotConsumeFling() {
        val split = SearchRevealMath.splitUpwardFling(-2000f, 200f, 0f)
        assertEquals(0f, split.consumedVelocityY, 0.001f)
        assertEquals(-2000f, split.residualVelocityY, 0.001f)
    }
}
