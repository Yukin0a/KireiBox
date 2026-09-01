package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualOrderSelectionPolicyTest {
    private val games = listOf(
        Game(id = 1, title = "F1", favorite = true),
        Game(id = 2, title = "F2", favorite = true),
        Game(id = 3, title = "A"),
        Game(id = 4, title = "B"),
        Game(id = 5, title = "C"),
    )

    @Test
    fun tapsAssignAndRemoveCompactNumbersInsideEachGroup() {
        var draft = ManualOrderSelectionPolicy.empty()
        draft = ManualOrderSelectionPolicy.toggle(draft, games[1])
        draft = ManualOrderSelectionPolicy.toggle(draft, games[0])
        draft = ManualOrderSelectionPolicy.toggle(draft, games[4])
        draft = ManualOrderSelectionPolicy.toggle(draft, games[2])
        assertEquals(mapOf(2L to 1, 1L to 2, 5L to 1, 3L to 2), ManualOrderSelectionPolicy.positions(games, draft, false))

        draft = ManualOrderSelectionPolicy.toggle(draft, games[4])
        assertEquals(mapOf(2L to 1, 1L to 2, 3L to 1), ManualOrderSelectionPolicy.positions(games, draft, false))
    }

    @Test
    fun finishPutsChosenFirstAndAppendsUnchosenInPreviousOrder() {
        val draft = ManualOrderSelectionDraft(favoriteIds = listOf(2), regularIds = listOf(5, 3))

        val result = ManualOrderSelectionPolicy.finalize(games, draft)

        assertEquals(listOf(2L, 1L), result.favoriteIds)
        assertEquals(listOf(5L, 3L, 4L), result.regularIds)
    }

    @Test
    fun temporarilyMissingGameKeepsItsDraftPositionForUndo() {
        val draft = ManualOrderSelectionDraft(regularIds = listOf(5, 3, 4))
        assertEquals(mapOf(5L to 1, 4L to 2), ManualOrderSelectionPolicy.positions(games.filter { it.id != 3L }, draft, false))
        assertEquals(mapOf(5L to 1, 3L to 2, 4L to 3), ManualOrderSelectionPolicy.positions(games, draft, false))
    }
}
