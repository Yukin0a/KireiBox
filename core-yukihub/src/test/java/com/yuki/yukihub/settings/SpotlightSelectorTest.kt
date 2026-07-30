package com.yuki.yukihub.settings

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotlightSelectorTest {
    private val games = listOf(
        Game(id = 1, title = "A", favorite = false, lastPlayedAt = 100, coverUri = "a"),
        Game(id = 2, title = "B", favorite = true, lastPlayedAt = 300, coverUri = "b"),
        Game(id = 3, title = "C", favorite = true, lastPlayedAt = 200, coverUri = "c"),
    )

    @Test
    fun `recent and favorite sources honor their pools`() {
        val recent = spec(SpotlightSource.RECENT, SpotlightStrategy.FIRST)
        val favorite = spec(SpotlightSource.FAVORITES, SpotlightStrategy.FIRST)
        assertEquals(2L, SpotlightSelector.select(recent, games, 1, 1)?.id)
        assertEquals(2L, SpotlightSelector.select(favorite, games, 1, 1)?.id)
    }

    @Test
    fun `fixed source never silently substitutes another game`() {
        assertEquals(
            1L,
            SpotlightSelector.select(
                spec(SpotlightSource.FIXED, SpotlightStrategy.FIRST).copy(gameId = 1),
                games,
                1,
                1,
            )?.id,
        )
        assertNull(
            SpotlightSelector.select(
                spec(SpotlightSource.FIXED, SpotlightStrategy.FIRST).copy(gameId = 99),
                games,
                1,
                1,
            ),
        )
    }

    @Test
    fun `daily random is stable for a day`() {
        val spec = spec(SpotlightSource.ALL, SpotlightStrategy.DAILY_RANDOM)
        assertEquals(
            SpotlightSelector.select(spec, games, 10, 20)?.id,
            SpotlightSelector.select(spec, games, 999, 20)?.id,
        )
    }

    private fun spec(source: SpotlightSource, strategy: SpotlightStrategy) = DashboardWidgetSpec(
        instanceId = "hero",
        type = DashboardWidgetType.GAME_SPOTLIGHT,
        size = DashboardWidgetSize(2, 2),
        spotlightSource = source,
        spotlightStrategy = strategy,
    )
}
