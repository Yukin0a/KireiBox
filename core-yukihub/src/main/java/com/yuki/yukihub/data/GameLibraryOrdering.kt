package com.yuki.yukihub.data

import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore

object GameLibraryOrdering {
    fun orderByClause(sortMode: String): String =
        when (sortMode) {
            YukiSettingsStore.SORT_NEWEST ->
                "favorite DESC, created_at DESC, title COLLATE NOCASE ASC, id ASC"
            YukiSettingsStore.SORT_NAME ->
                "favorite DESC, title COLLATE NOCASE ASC, id ASC"
            YukiSettingsStore.SORT_MANUAL ->
                "favorite DESC, manual_order ASC, id ASC"
            else ->
                "favorite DESC, last_played_at DESC, created_at DESC, title COLLATE NOCASE ASC, id ASC"
        }

    fun sorted(
        games: List<Game>,
        sortMode: String,
    ): List<Game> {
        val base = compareByDescending<Game> { it.favorite }
        val comparator =
            when (sortMode) {
                YukiSettingsStore.SORT_NEWEST ->
                    base
                        .thenByDescending { it.createdAt }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() }
                        .thenBy { it.id }
                YukiSettingsStore.SORT_NAME ->
                    base
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() }
                        .thenBy { it.id }
                YukiSettingsStore.SORT_MANUAL ->
                    base
                        .thenBy { it.manualOrder }
                        .thenBy { it.id }
                else ->
                    base
                        .thenByDescending { it.lastPlayedAt }
                        .thenByDescending { it.createdAt }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() }
                        .thenBy { it.id }
            }
        return games.sortedWith(comparator)
    }
}
