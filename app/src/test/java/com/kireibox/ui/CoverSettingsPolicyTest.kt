package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSettingsPolicyTest {
    @Test
    fun separatesPendingFromConfiguredWithoutChangingLibraryOrder() {
        val games = listOf(
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

    private fun game(id: Long, mode: String, hasCover: Boolean = false) = Game(
        id = id,
        title = "Game $id",
        vndbBindingMode = mode,
        coverUri = if (hasCover) "file:///cover-$id.jpg" else null,
    )
}
