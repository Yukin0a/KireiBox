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

    fun needsUserReview(game: Game): Boolean = game.coverUri.isNullOrBlank() && game.vndbBindingMode.equals("REVIEW", ignoreCase = true)

    /**
     * A cover the user still has to resolve: awaiting review ([needsUserReview]) or an automatic
     * match that failed and was parked as RETRY. RETRY matters because the queue is in-memory, so
     * after a restart the binding is the only surviving signal that a game needs attention.
     */
    fun needsAttention(game: Game): Boolean {
        if (!game.coverUri.isNullOrBlank()) return false
        return game.vndbBindingMode.equals("REVIEW", ignoreCase = true) ||
            game.vndbBindingMode.equals("RETRY", ignoreCase = true)
    }

    /**
     * Pending covers in the order the user should act on them: games awaiting review (binding mode
     * REVIEW or a NEEDS_REVIEW task) first, then retries, then ones still matching in the
     * background, then the rest. Stable, so library order is kept inside each tier.
     */
    fun reviewFirst(
        pending: List<Game>,
        reviewTaskGameIds: Set<Long>,
        activeTaskGameIds: Set<Long>,
    ): List<Game> =
        pending.sortedBy { game ->
            when {
                needsUserReview(game) || game.id in reviewTaskGameIds -> 0
                game.vndbBindingMode.equals("RETRY", ignoreCase = true) -> 1
                game.id in activeTaskGameIds -> 2
                else -> 3
            }
        }

    private fun isConfigured(
        game: Game,
        mode: String
    ): Boolean = !game.coverUri.isNullOrBlank() || mode == "DISABLED"
}
