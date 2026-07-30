package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOverviewPolicyTest {
    @Test fun recentSortPutsPlayedGamesFirstThenUsesTitle() {
        val never = Game(id = 1, title = "Never", lastPlayedAt = 0)
        val beta = Game(id = 2, title = "Beta", lastPlayedAt = 20)
        val alpha = Game(id = 3, title = "Alpha", lastPlayedAt = 20)
        assertEquals(
            listOf(3L, 2L, 1L),
            LibraryOverviewPolicy.sorted(listOf(never, beta, alpha), LibraryOverviewSort.RECENT).map { it.id }
        )
    }

    @Test fun playTimeSortUsesTitleForTies() {
        val short = Game(id = 1, title = "Short", totalPlayTime = 60)
        val beta = Game(id = 2, title = "Beta", totalPlayTime = 300)
        val alpha = Game(id = 3, title = "Alpha", totalPlayTime = 300)
        assertEquals(
            listOf(3L, 2L, 1L),
            LibraryOverviewPolicy.sorted(listOf(short, beta, alpha), LibraryOverviewSort.PLAY_TIME).map { it.id }
        )
    }

    @Test fun compactTimeUsesMinuteAndHourLabels() {
        assertEquals("0m", LibraryOverviewPolicy.compactPlayTime(0))
        assertEquals("1m", LibraryOverviewPolicy.compactPlayTime(30))
        assertEquals("5m", LibraryOverviewPolicy.compactPlayTime(300))
        assertEquals("1h", LibraryOverviewPolicy.compactPlayTime(3_600))
        assertEquals("1.5h", LibraryOverviewPolicy.compactPlayTime(5_400))
    }

    @Test fun dockPaddingIncludesInsetsAndClearance() {
        assertEquals(106f, LibraryOverviewPolicy.dockContentPaddingDp(0f), .001f)
        assertEquals(130f, LibraryOverviewPolicy.dockContentPaddingDp(24f), .001f)
        assertEquals(106f, LibraryOverviewPolicy.dockContentPaddingDp(-4f), .001f)
    }
}
