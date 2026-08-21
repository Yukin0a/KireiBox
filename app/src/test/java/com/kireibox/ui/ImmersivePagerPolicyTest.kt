package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersivePagerPolicyTest {
    @Test
    fun singleGameHidesThePager() {
        assertEquals(0, ImmersivePagerPolicy.visibleCount(0))
        assertEquals(0, ImmersivePagerPolicy.visibleCount(1))
        assertTrue(ImmersivePagerPolicy.visibleIndexes(1, 0).isEmpty())
    }

    @Test
    fun twoToSevenGamesShowEveryItem() {
        for (count in 2..7) {
            assertEquals(count, ImmersivePagerPolicy.visibleCount(count))
            assertEquals(count, ImmersivePagerPolicy.visibleIndexes(count, 0).size)
            assertEquals((0 until count).toList(), ImmersivePagerPolicy.visibleIndexes(count, count / 2))
        }
    }

    @Test
    fun moreThanSevenGamesUseAStableSevenItemWindow() {
        assertEquals(7, ImmersivePagerPolicy.visibleCount(8))
        assertEquals(7, ImmersivePagerPolicy.visibleCount(50))
        assertEquals(listOf(5, 6, 7, 0, 1, 2, 3), ImmersivePagerPolicy.visibleIndexes(8, 0))
        assertEquals(listOf(4, 5, 6, 7, 0, 1, 2), ImmersivePagerPolicy.visibleIndexes(8, 7))
    }

    @Test
    fun sevenItemWindowCentersOnTheSelectionCircularly() {
        assertEquals(listOf(7, 8, 9, 0, 1, 2, 3), ImmersivePagerPolicy.visibleIndexes(10, 0))
        assertEquals((0..6).toList(), ImmersivePagerPolicy.visibleIndexes(10, 3))
        assertEquals((1..7).toList(), ImmersivePagerPolicy.visibleIndexes(10, 4))
        assertEquals((2..8).toList(), ImmersivePagerPolicy.visibleIndexes(10, 5))
        assertEquals(listOf(6, 7, 8, 9, 0, 1, 2), ImmersivePagerPolicy.visibleIndexes(10, 9))
    }

    @Test
    fun windowWrapsAtBothEndsAndNormalizesSelection() {
        assertEquals(listOf(17, 18, 19, 0, 1, 2, 3), ImmersivePagerPolicy.visibleIndexes(20, 0))
        assertEquals(listOf(16, 17, 18, 19, 0, 1, 2), ImmersivePagerPolicy.visibleIndexes(20, 19))
        assertEquals(listOf(14, 15, 16, 17, 18, 19, 0), ImmersivePagerPolicy.visibleIndexes(20, -3))
        assertEquals(listOf(16, 17, 18, 19, 0, 1, 2), ImmersivePagerPolicy.visibleIndexes(20, 99))
    }
}
