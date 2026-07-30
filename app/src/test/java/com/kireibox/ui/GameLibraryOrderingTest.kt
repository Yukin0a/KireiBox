package com.kireibox.ui

import com.yuki.yukihub.data.GameLibraryOrdering
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLibraryOrderingTest {
    @Test fun favoritesLeadEverySortAndUseConfiguredOrderInsideGroup() {
        val games = listOf(
            Game(id = 1, title = "Zulu", favorite = false, lastPlayedAt = 900, createdAt = 900),
            Game(id = 2, title = "Beta", favorite = true, lastPlayedAt = 100, createdAt = 300),
            Game(id = 3, title = "Alpha", favorite = true, lastPlayedAt = 200, createdAt = 200)
        )
        assertEquals(listOf(3L, 2L, 1L), GameLibraryOrdering.sorted(games, YukiSettingsStore.SORT_RECENT).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), GameLibraryOrdering.sorted(games, YukiSettingsStore.SORT_NEWEST).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), GameLibraryOrdering.sorted(games, YukiSettingsStore.SORT_NAME).map { it.id })
    }

    @Test fun sqlOrderAlwaysBeginsWithFavoriteAndHasStableIdFallback() {
        listOf(YukiSettingsStore.SORT_RECENT, YukiSettingsStore.SORT_NEWEST, YukiSettingsStore.SORT_NAME).forEach {
            val clause = GameLibraryOrdering.orderByClause(it)
            assertTrue(clause.startsWith("favorite DESC"))
            assertTrue(clause.endsWith("id ASC"))
        }
    }
}
