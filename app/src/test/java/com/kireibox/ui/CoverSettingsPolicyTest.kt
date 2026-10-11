package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSettingsPolicyTest {
    @Test
    fun separatesPendingFromConfiguredWithoutChangingLibraryOrder() {
        val games =
            listOf(
                game(1, "MANUAL", hasCover = true),
                game(2, "REVIEW"),
                game(3, "AUTO", hasCover = true),
                game(4, "DISABLED"),
                game(5, "RETRY"),
                game(6, "NONE"),
                game(7, "REVIEW", hasCover = true),
            )

        val groups = CoverSettingsPolicy.groups(games)

        assertEquals(listOf(2L, 5L, 6L), groups.pending.map { it.id })
        assertEquals(listOf(1L, 3L, 4L, 7L), groups.configured.map { it.id })
        assertEquals(true, CoverSettingsPolicy.needsUserReview(games[1]))
        assertEquals(false, CoverSettingsPolicy.needsUserReview(games.last()))
    }

    @Test
    fun reviewFirstLeadsWithCoversAwaitingTheUserAndKeepsLibraryOrderWithinTiers() {
        val pending =
            listOf(
                game(1, "NONE"),
                game(2, "RETRY"),
                game(3, "REVIEW"),
                game(4, "AUTO"),
                game(5, "NONE"),
                game(6, "REVIEW"),
            )

        // 3 and 6 need review by binding mode, 5 has a NEEDS_REVIEW task, 2 retries, 4 is matching.
        val ordered = CoverSettingsPolicy.reviewFirst(pending, reviewTaskGameIds = setOf(5L), activeTaskGameIds = setOf(4L))

        assertEquals(listOf(3L, 5L, 6L, 2L, 4L, 1L), ordered.map { it.id })
        // Without any review signal the order is exactly the library order.
        val plain = listOf(game(1, "NONE"), game(4, "AUTO"), game(5, "NONE"))
        assertEquals(listOf(1L, 4L, 5L), CoverSettingsPolicy.reviewFirst(plain, emptySet(), emptySet()).map { it.id })
    }

    private fun game(
        id: Long,
        mode: String,
        hasCover: Boolean = false
    ) = Game(
        id = id,
        title = "Game $id",
        vndbBindingMode = mode,
        coverUri = if (hasCover) "file:///cover-$id.jpg" else null,
    )
}
