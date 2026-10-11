package com.kireibox.cover

import android.content.SharedPreferences
import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.settings.YukiSettingsStore
import org.json.JSONArray

/**
 * Persistent, bounded dedup for automatic cover searches that found no auto-bindable candidate.
 *
 * A background task that ends with no candidate (or only a non-bindable hint) remembers a stable
 * key built from the game id and the normalized title hash, so a later automatic pass skips
 * re-querying the same remote catalogs. A rename produces a different hash and therefore a new
 * key, which naturally re-enables the game. The store lives in the shared preferences through
 * [YukiSettingsStore.rawPreferences] as a JSON array of strings; an empty/absent value means
 * nothing is remembered yet, so a fresh install keeps the legacy behaviour.
 */
internal class CoverNoMatchStore(
    private val preferences: SharedPreferences,
) {
    @Synchronized
    fun contains(key: String): Boolean = read().contains(key)

    @Synchronized
    fun add(key: String) {
        if (key.isBlank()) return
        val keys = read().toMutableList()
        keys.remove(key)
        keys += key
        while (keys.size > MAX_ENTRIES) keys.removeAt(0)
        write(keys)
    }

    /** Drops every remembered key that belongs to [gameId]. */
    @Synchronized
    fun clearForGame(gameId: Long) {
        val prefix = "$gameId:"
        val keys = read()
        val remaining = keys.filterNot { it.startsWith(prefix) }
        if (remaining != keys) write(remaining)
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(PREF_KEY).apply()
    }

    private fun read(): List<String> {
        val raw = preferences.getString(PREF_KEY, null)
        val array =
            raw
                ?.takeIf(String::isNotBlank)
                ?.let { value -> runCatching { JSONArray(value) }.getOrNull() }
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val key = array.optString(index)
                if (key.isNotBlank()) add(key)
            }
        }
    }

    private fun write(keys: List<String>) {
        if (keys.isEmpty()) {
            preferences.edit().remove(PREF_KEY).apply()
            return
        }
        val array = JSONArray()
        keys.forEach(array::put)
        preferences.edit().putString(PREF_KEY, array.toString()).apply()
    }

    companion object {
        const val PREF_KEY: String = YukiSettingsStore.KEY_COVER_NO_MATCH_STORE

        /** Keeps the preferences blob bounded while still covering realistic libraries. */
        const val MAX_ENTRIES: Int = 500

        /** Stable dedup key: game id plus the hash of the normalized search title. */
        fun key(
            gameId: Long,
            title: String
        ): String = "$gameId:${TitleMatchScorer.normalize(title).hashCode()}"
    }
}
