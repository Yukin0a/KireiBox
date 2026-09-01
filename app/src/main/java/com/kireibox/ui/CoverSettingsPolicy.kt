package com.kireibox.ui

import com.yuki.yukihub.model.Game
import java.util.Locale

internal data class CoverSettingsGroups(
    val pending: List<Game>,
    val configured: List<Game>,
)

internal object CoverSettingsPolicy {
    fun groups(games: List<Game>): CoverSettingsGroups {
        val pending = ArrayList<Game>()
        val configured = ArrayList<Game>()
        games.forEach { game ->
            val mode = game.vndbBindingMode.trim().uppercase(Locale.ROOT)
            if (isConfigured(game, mode)) configured += game else pending += game
        }
        return CoverSettingsGroups(pending, configured)
    }

    fun needsUserReview(game: Game): Boolean =
        game.coverUri.isNullOrBlank() && game.vndbBindingMode.equals("REVIEW", ignoreCase = true)

    private fun isConfigured(game: Game, mode: String): Boolean =
        !game.coverUri.isNullOrBlank() || mode == "DISABLED"
}
