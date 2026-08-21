package com.yuki.yukihub.settings

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `recent with no play history stays empty for every strategy and count`() {
        val noHistory = games.map { it.copy(lastPlayedAt = 0L) }
        SpotlightStrategy.entries.forEach { strategy ->
            listOf(1, 3, 5).forEach { count ->
                val spec = spec(SpotlightSource.RECENT, strategy).copy(spotlightMaxPages = count)
                assertTrue(
                    "recent must not fall back to all games for $strategy/$count",
                    SpotlightSelector.selectPages(spec, noHistory, sessionSeed = 7, epochDay = 11).isEmpty(),
                )
                assertNull(SpotlightSelector.select(spec, noHistory, sessionSeed = 7, epochDay = 11))
            }
        }
    }

    @Test
    fun `empty favorites stays empty and all is the only all-games source`() {
        val unplayed = games.map { it.copy(lastPlayedAt = 0L, favorite = false) }
        val favorites = spec(SpotlightSource.FAVORITES, SpotlightStrategy.DAILY_RANDOM)
        assertTrue(SpotlightSelector.selectPages(favorites, unplayed, 1, 20).isEmpty())
        assertNull(SpotlightSelector.select(favorites, unplayed, 1, 20))

        val all = spec(SpotlightSource.ALL, SpotlightStrategy.FIRST).copy(spotlightMaxPages = 2)
        assertEquals(listOf(1L, 2L), SpotlightSelector.selectPages(all, unplayed, 1, 20).map { it.id })
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

    @Test
    fun `pages prefer covers cap at five and retain the compatible first selection`() {
        val pages = SpotlightSelector.selectPages(
            spec(SpotlightSource.ALL, SpotlightStrategy.FIRST),
            games + listOf(
                Game(id = 4, title = "D", lastPlayedAt = 50, coverUri = "d"),
                Game(id = 5, title = "E", lastPlayedAt = 40, coverUri = "e"),
                Game(id = 6, title = "F", lastPlayedAt = 30, coverUri = "f"),
                Game(id = 7, title = "G", lastPlayedAt = 20),
            ),
            sessionSeed = 1,
            epochDay = 1,
        )

        assertEquals(listOf(2L, 3L, 1L, 4L, 5L), pages.map { it.id })
        assertEquals(pages.first().id, SpotlightSelector.select(spec(SpotlightSource.ALL, SpotlightStrategy.FIRST), games + listOf(
            Game(id = 4, title = "D", lastPlayedAt = 50, coverUri = "d"),
            Game(id = 5, title = "E", lastPlayedAt = 40, coverUri = "e"),
            Game(id = 6, title = "F", lastPlayedAt = 30, coverUri = "f"),
            Game(id = 7, title = "G", lastPlayedAt = 20),
        ), 1, 1)?.id)
    }

    @Test
    fun `fixed pages contain only the selected game`() {
        val pages = SpotlightSelector.selectPages(
            spec(SpotlightSource.FIXED, SpotlightStrategy.DAILY_RANDOM).copy(gameId = 3),
            games,
            sessionSeed = 10,
            epochDay = 20,
        )
        assertEquals(listOf(3L), pages.map { it.id })
    }

    @Test
    fun `launch and daily random page orders are stable yet vary with their salts`() {
        val manyGames = games + listOf(
            Game(id = 4, title = "D", coverUri = "d"),
            Game(id = 5, title = "E", coverUri = "e"),
            Game(id = 6, title = "F", coverUri = "f"),
        )
        val launch = spec(SpotlightSource.ALL, SpotlightStrategy.RANDOM_EACH_LAUNCH)
        val daily = spec(SpotlightSource.ALL, SpotlightStrategy.DAILY_RANDOM)

        assertEquals(
            SpotlightSelector.selectPages(launch, manyGames, 101, 1),
            SpotlightSelector.selectPages(launch, manyGames, 101, 999),
        )
        assertNotEquals(
            SpotlightSelector.selectPages(launch, manyGames, 101, 1),
            SpotlightSelector.selectPages(launch, manyGames, 202, 1),
        )
        assertEquals(
            SpotlightSelector.selectPages(daily, manyGames, 101, 10),
            SpotlightSelector.selectPages(daily, manyGames, 202, 10),
        )
        assertNotEquals(
            SpotlightSelector.selectPages(daily, manyGames, 101, 10),
            SpotlightSelector.selectPages(daily, manyGames, 101, 11),
        )
    }

    @Test
    fun `manual selection preserves explicit order and drops missing games`() {
        val spec = manualSpec(listOf(3L, 1L, 99L, 2L), maxPages = 5)
        assertEquals(
            listOf(3L, 1L, 2L),
            SpotlightSelector.selectPages(spec, games, sessionSeed = 1, epochDay = 1).map { it.id },
        )
    }

    @Test
    fun `manual selection never substitutes when every id is missing`() {
        val spec = manualSpec(listOf(99L, 98L), maxPages = 5)
        assertTrue(SpotlightSelector.selectPages(spec, games, 1, 1).isEmpty())
        assertNull(SpotlightSelector.select(spec, games, 1, 1))
    }

    @Test
    fun `manual max pages caps the rendered list`() {
        val spec = manualSpec(listOf(1L, 2L, 3L), maxPages = 2)
        assertEquals(listOf(1L, 2L), SpotlightSelector.selectPages(spec, games, 1, 1).map { it.id })
    }

    @Test
    fun `automatic max pages honors the saved count`() {
        val spec = spec(SpotlightSource.ALL, SpotlightStrategy.FIRST).copy(spotlightMaxPages = 2)
        assertEquals(2, SpotlightSelector.selectPages(spec, games, 1, 1).size)
    }

    private fun manualSpec(ids: List<Long>, maxPages: Int) = DashboardWidgetSpec(
        instanceId = "hero",
        type = DashboardWidgetType.GAME_SPOTLIGHT,
        size = DashboardWidgetSize(2, 2),
        spotlightSelectionMode = SpotlightSelectionMode.MANUAL,
        spotlightGameIds = ids,
        spotlightMaxPages = maxPages,
    )

    private fun spec(source: SpotlightSource, strategy: SpotlightStrategy) = DashboardWidgetSpec(
        instanceId = "hero",
        type = DashboardWidgetType.GAME_SPOTLIGHT,
        size = DashboardWidgetSize(2, 2),
        spotlightSource = source,
        spotlightStrategy = strategy,
    )
}
