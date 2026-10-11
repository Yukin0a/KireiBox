package com.kireibox.ui

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveGalleryEditorPolicyTest {
    private fun game(
        id: Long,
        title: String = "G$id"
    ): Game = Game(id = id, title = title)

    @Test
    fun automaticRecentAllKeepsOnlyRealPlayHistoryNewestFirstDeduped() {
        val games =
            listOf(
                game(3L).also { it.lastPlayedAt = 50L },
                game(1L).also { it.lastPlayedAt = 300L },
                game(2L),
                game(1L).also { it.lastPlayedAt = 300L },
            )
        assertEquals(
            listOf(1L, 3L),
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                listOf(2L, 3L),
                ImmersiveGalleryAutoSource.RECENT_ALL,
            ).map { it.id },
        )
    }

    @Test
    fun manualModeShowsOnlyTheSelectedGamesInSavedOrder() {
        val games = listOf(game(1L), game(2L), game(3L), game(4L))
        assertEquals(
            listOf(2L, 4L),
            ImmersiveGalleryEditorPolicy
                .orderGames(games, SpotlightSelectionMode.MANUAL, listOf(2L, 4L)).map { it.id },
        )
    }

    @Test
    fun manualModeStaysEmptyWhenNothingIsSelectedOrEveryIdIsStale() {
        val games = listOf(game(1L), game(2L))
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(games, SpotlightSelectionMode.MANUAL, emptyList()),
        )
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(games, SpotlightSelectionMode.MANUAL, listOf(99L)),
        )
    }

    @Test
    fun recentFiveKeepsOnlyPlayedGamesInMostRecentOrderUpToFive() {
        val games =
            listOf(
                game(1L).also { it.lastPlayedAt = 100L },
                game(2L),
                game(3L).also { it.lastPlayedAt = 300L },
                game(4L).also { it.lastPlayedAt = 200L },
                game(5L).also { it.lastPlayedAt = 50L },
                game(6L).also { it.lastPlayedAt = 400L },
            )
        val ordered =
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.RECENT_FIVE,
            )
        assertEquals(listOf(6L, 3L, 4L, 1L, 5L), ordered.map { it.id })
        assertEquals(5, ordered.size)
    }

    @Test
    fun recentFiveNeverPadsWithUnplayedGamesWhenPlayHistoryExists() {
        val games =
            listOf(
                game(1L).also { it.lastPlayedAt = 100L },
                game(2L),
                game(3L),
            )
        val ordered =
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.RECENT_FIVE,
            )
        assertEquals(listOf(1L), ordered.map { it.id })
    }

    @Test
    fun recentFiveReturnsEmptyWhenNoPlayHistoryExists() {
        val games = listOf(game(1L), game(2L), game(3L), game(4L), game(5L), game(6L))
        val ordered =
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.RECENT_FIVE,
            )
        assertEquals(emptyList<Game>(), ordered)
    }

    @Test
    fun recentAllExcludesUnplayedGamesAndKeepsNewestFirst() {
        val games =
            listOf(
                game(3L).also { it.lastPlayedAt = 50L },
                game(1L).also { it.lastPlayedAt = 300L },
                game(2L),
                game(4L).also { it.lastPlayedAt = 100L },
            )
        val ordered =
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.RECENT_ALL,
            )
        assertEquals(listOf(1L, 4L, 3L), ordered.map { it.id })
    }

    @Test
    fun recentAllReturnsEmptyWhenNoPlayHistoryExists() {
        val games = listOf(game(1L), game(2L), game(3L))
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.RECENT_ALL,
            ),
        )
    }

    @Test
    fun favoritesPicksOnlyFavoritesInRecentOrder() {
        val games =
            listOf(
                game(1L).also {
                    it.lastPlayedAt = 100L
                    it.favorite = true
                },
                game(2L).also { it.favorite = true },
                game(3L).also { it.lastPlayedAt = 300L },
                game(4L).also {
                    it.lastPlayedAt = 200L
                    it.favorite = true
                },
            )
        val ordered =
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.FAVORITES,
            )
        assertEquals(listOf(4L, 1L, 2L), ordered.map { it.id })
    }

    @Test
    fun favoritesStaysEmptyWhenNoFavoriteExists() {
        val games = listOf(game(1L), game(2L))
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                ImmersiveGalleryAutoSource.FAVORITES,
            ),
        )
    }

    @Test
    fun canonicalSourcesKeepRecentAndFavoritesEmptyAndMakeAllExplicit() {
        val games =
            listOf(
                game(1L).also { it.lastPlayedAt = 100L },
                game(2L),
            )
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(
                games.map { it.copy(lastPlayedAt = 0L) },
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.RECENT,
            ),
        )
        assertEquals(
            emptyList<Game>(),
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.FAVORITES,
            ),
        )
        assertEquals(
            games,
            ImmersiveGalleryEditorPolicy.orderGames(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.ALL,
            ),
        )
    }

    @Test
    fun emptyStateSeparatesLibrarySetupSelectionHistoryAndFavorites() {
        val games = listOf(game(1L))
        assertEquals(
            ImmersiveGalleryEmptyState.BUILD_LIBRARY,
            ImmersiveGalleryEditorPolicy.emptyState(
                emptyList(),
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.ALL,
            ),
        )
        assertEquals(
            ImmersiveGalleryEmptyState.SELECT_GAMES,
            ImmersiveGalleryEditorPolicy.emptyState(
                games,
                SpotlightSelectionMode.MANUAL,
                emptyList(),
                SpotlightSource.RECENT,
            ),
        )
        assertEquals(
            ImmersiveGalleryEmptyState.NO_PLAY_HISTORY,
            ImmersiveGalleryEditorPolicy.emptyState(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.RECENT,
            ),
        )
        assertEquals(
            ImmersiveGalleryEmptyState.NO_FAVORITES,
            ImmersiveGalleryEditorPolicy.emptyState(
                games,
                SpotlightSelectionMode.AUTOMATIC,
                emptyList(),
                SpotlightSource.FAVORITES,
            ),
        )
    }

    @Test
    fun maxPagesCoercesIntoOneToFive() {
        assertEquals(1, ImmersiveGalleryEditorPolicy.maxPagesCoerced(0))
        assertEquals(5, ImmersiveGalleryEditorPolicy.maxPagesCoerced(9))
        assertEquals(3, ImmersiveGalleryEditorPolicy.maxPagesCoerced(3))
        assertEquals(DashboardLayouts.MAX_SPOTLIGHT_PAGES, 5)
    }

    @Test
    fun manualSaveRequiresAtLeastOneGame() {
        assertFalse(ImmersiveGalleryEditorPolicy.canSaveManual(emptyList()))
        assertTrue(ImmersiveGalleryEditorPolicy.canSaveManual(listOf(1L)))
    }

    @Test
    fun toggleAddsAndRemovesPreservingOrderAndCap() {
        assertEquals(listOf(1L, 2L), ImmersiveGalleryEditorPolicy.toggleManualId(listOf(1L), 2L))
        assertEquals(listOf(1L), ImmersiveGalleryEditorPolicy.toggleManualId(listOf(1L, 2L), 2L))
        val five = (1L..5L).toList()
        assertEquals(five, ImmersiveGalleryEditorPolicy.toggleManualId(five.take(4), 5L))
        assertEquals(five, ImmersiveGalleryEditorPolicy.toggleManualId(five, 6L))
    }

    @Test
    fun moveSwapsWithinBoundsOnly() {
        assertEquals(listOf(2L, 1L, 3L), ImmersiveGalleryEditorPolicy.moveManualId(listOf(1L, 2L, 3L), 0, 1))
        assertEquals(listOf(2L, 3L, 1L), ImmersiveGalleryEditorPolicy.moveManualId(listOf(1L, 2L, 3L), 0, 2))
        assertEquals(listOf(1L, 2L, 3L), ImmersiveGalleryEditorPolicy.moveManualId(listOf(1L, 2L, 3L), 0, 9))
        assertEquals(listOf(1L, 2L, 3L), ImmersiveGalleryEditorPolicy.moveManualId(listOf(1L, 2L, 3L), 5, 0))
    }

    @Test
    fun manualSelectionMissingDetectsAllInvalidEntries() {
        assertTrue(ImmersiveGalleryEditorPolicy.manualSelectionMissing(listOf(9L, 10L), setOf(1L, 2L)))
        assertFalse(ImmersiveGalleryEditorPolicy.manualSelectionMissing(listOf(9L, 2L), setOf(1L, 2L)))
        assertTrue(ImmersiveGalleryEditorPolicy.manualSelectionMissing(emptyList(), setOf(1L, 2L)))
    }

    @Test
    fun labelsCoverEveryAutomaticSourceAndStrategy() {
        listOf(SpotlightSource.RECENT, SpotlightSource.FAVORITES, SpotlightSource.ALL, SpotlightSource.FIXED).forEach {
            assertTrue(ImmersiveGalleryEditorPolicy.automaticSourceLabel(it).isNotBlank())
            assertTrue(ImmersiveGalleryEditorPolicy.automaticSourceDescription(it).isNotBlank())
        }
        listOf(SpotlightStrategy.FIRST, SpotlightStrategy.RANDOM_EACH_LAUNCH, SpotlightStrategy.DAILY_RANDOM).forEach {
            assertTrue(ImmersiveGalleryEditorPolicy.strategyLabel(it).isNotBlank())
        }
    }
}
