package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlightCarouselPolicyTest {
    @Test
    fun `autoplay waits six seconds and requires multiple visible resumed idle pages`() {
        assertEquals(6_000L, SpotlightCarouselPolicy.AUTO_ADVANCE_MILLIS)
        assertFalse(SpotlightCarouselPolicy.shouldAutoAdvance(1, 6_000, true, true, false))
        assertFalse(SpotlightCarouselPolicy.shouldAutoAdvance(2, 5_999, true, true, false))
        assertFalse(SpotlightCarouselPolicy.shouldAutoAdvance(2, 6_000, true, true, true))
        assertFalse(SpotlightCarouselPolicy.shouldAutoAdvance(2, 6_000, false, true, false))
        assertFalse(SpotlightCarouselPolicy.shouldAutoAdvance(2, 6_000, true, false, false))
        assertTrue(SpotlightCarouselPolicy.shouldAutoAdvance(2, 6_000, true, true, false))
    }

    @Test
    fun `next page wraps and prefetch targets only current and next`() {
        assertEquals(0, SpotlightCarouselPolicy.nextPage(2, 3))
        assertEquals(0, SpotlightCarouselPolicy.nextPage(0, 1))
        assertEquals(listOf(1, 2), SpotlightCarouselPolicy.prefetchIndexes(1, 4))
        assertEquals(listOf(2, 0), SpotlightCarouselPolicy.prefetchIndexes(2, 3))
    }

    @Test
    fun `only direct user drag pauses timer while programmatic animation keeps running`() {
        assertTrue(SpotlightCarouselPolicy.timerPausedForPager(userDragActive = true, scrollInProgress = true))
        assertFalse(SpotlightCarouselPolicy.timerPausedForPager(userDragActive = false, scrollInProgress = true))
        assertFalse(SpotlightCarouselPolicy.timerPausedForPager(userDragActive = false, scrollInProgress = false))
    }

    @Test
    fun `timer stays anchored to settled page while animation crosses page threshold`() {
        assertEquals(0, SpotlightCarouselPolicy.timerAnchorPage(currentPage = 1, settledPage = 0))
        assertEquals(1, SpotlightCarouselPolicy.timerAnchorPage(currentPage = 1, settledPage = 1))
    }

    @Test
    fun `auto advance uses the explicit four hundred eighty millisecond motion`() {
        assertEquals(480, SpotlightCarouselPolicy.ANIMATE_MILLIS)
    }

    @Test
    fun `circular virtual pages show alternate game on both sides for two games`() {
        assertEquals(2_000, SpotlightCircularCarouselPolicy.virtualPageCount(2))
        assertEquals(1_000, SpotlightCircularCarouselPolicy.startVirtualPage(2, 0))
        assertEquals(0, SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(1_000, 2))
        assertEquals(1, SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(999, 2))
        assertEquals(1, SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(1_001, 2))
    }

    @Test
    fun `circular virtual pages loop three to five games symmetrically`() {
        assertEquals(3_000, SpotlightCircularCarouselPolicy.virtualPageCount(3))
        assertEquals(1_500, SpotlightCircularCarouselPolicy.startVirtualPage(3, 0))
        assertEquals(2, SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(1_499, 3))
        assertEquals(1, SpotlightCircularCarouselPolicy.gameIndexForVirtualPage(1_501, 3))
        assertEquals(5_000, SpotlightCircularCarouselPolicy.virtualPageCount(5))
    }

    @Test
    fun `nearest virtual page keeps the current game after data changes`() {
        assertEquals(1_499, SpotlightCircularCarouselPolicy.nearestVirtualPage(2, 3, 1_500))
        assertEquals(1_501, SpotlightCircularCarouselPolicy.nearestVirtualPage(1, 3, 1_500))
        assertEquals(1_500, SpotlightCircularCarouselPolicy.nearestVirtualPage(0, 3, 1_500))
        assertEquals(0, SpotlightCircularCarouselPolicy.nearestVirtualPage(0, 1, 42))
    }

    @Test
    fun `single and empty libraries never loop`() {
        assertEquals(1, SpotlightCircularCarouselPolicy.virtualPageCount(1))
        assertEquals(0, SpotlightCircularCarouselPolicy.virtualPageCount(0))
        assertEquals(0, SpotlightCircularCarouselPolicy.startVirtualPage(1, 0))
        assertEquals(0, SpotlightCircularCarouselPolicy.startVirtualPage(0, 0))
    }
}
