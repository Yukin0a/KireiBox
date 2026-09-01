package com.kireibox.ui

import com.yuki.yukihub.model.Game

internal data class ManualOrderSelectionDraft(
    val favoriteIds: List<Long> = emptyList(),
    val regularIds: List<Long> = emptyList(),
)

internal data class FinalManualOrder(
    val favoriteIds: List<Long>,
    val regularIds: List<Long>,
)

/** Pure numbered-selection rules shared by portrait and landscape library editors. */
internal object ManualOrderSelectionPolicy {
    fun empty(): ManualOrderSelectionDraft = ManualOrderSelectionDraft()

    fun toggle(draft: ManualOrderSelectionDraft, game: Game): ManualOrderSelectionDraft {
        val source = if (game.favorite) draft.favoriteIds else draft.regularIds
        val changed = if (game.id in source) source - game.id else source + game.id
        return if (game.favorite) draft.copy(favoriteIds = changed) else draft.copy(regularIds = changed)
    }

    fun positions(
        games: List<Game>,
        draft: ManualOrderSelectionDraft?,
        showExistingOrder: Boolean,
    ): Map<Long, Int> {
        val visibleIds = games.mapTo(hashSetOf()) { it.id }
        val favorites = when {
            draft != null -> draft.favoriteIds
            showExistingOrder -> games.filter(Game::favorite).map(Game::id)
            else -> emptyList()
        }.filter { it in visibleIds }
        val regular = when {
            draft != null -> draft.regularIds
            showExistingOrder -> games.filterNot(Game::favorite).map(Game::id)
            else -> emptyList()
        }.filter { it in visibleIds }
        return buildMap {
            favorites.forEachIndexed { index, id -> put(id, index + 1) }
            regular.forEachIndexed { index, id -> put(id, index + 1) }
        }
    }

    fun finalize(games: List<Game>, draft: ManualOrderSelectionDraft): FinalManualOrder {
        fun finish(group: List<Game>, selected: List<Long>): List<Long> {
            val current = group.map(Game::id)
            val currentSet = current.toHashSet()
            val chosen = selected.filter { it in currentSet }.distinct()
            return chosen + current.filterNot { it in chosen }
        }
        return FinalManualOrder(
            favoriteIds = finish(games.filter(Game::favorite), draft.favoriteIds),
            regularIds = finish(games.filterNot(Game::favorite), draft.regularIds),
        )
    }

    fun selectedCount(draft: ManualOrderSelectionDraft?, favorite: Boolean, games: List<Game>): Int {
        if (draft == null) return games.count { it.favorite == favorite }
        val visible = games.filter { it.favorite == favorite }.mapTo(hashSetOf()) { it.id }
        return (if (favorite) draft.favoriteIds else draft.regularIds).count { it in visible }
    }
}
