package com.kireibox.cover

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverNoMatchStoreTest {
    @Test
    fun addThenContainsRoundTripsThroughPreferences() {
        val prefs = fakePrefs()
        val store = CoverNoMatchStore(prefs)
        val key = CoverNoMatchStore.key(gameId = 42, title = "千恋万花")

        assertFalse(store.contains(key))
        store.add(key)
        assertTrue(store.contains(key))
        // A fresh store over the same preferences sees the persisted value.
        assertTrue(CoverNoMatchStore(prefs).contains(key))
        assertEquals(key, prefs.getString(CoverNoMatchStore.PREF_KEY, null)?.let { raw -> singleKey(raw) })
    }

    @Test
    fun addIsIdempotent() {
        val prefs = fakePrefs()
        val store = CoverNoMatchStore(prefs)
        val key = CoverNoMatchStore.key(gameId = 1, title = "Same Game")

        store.add(key)
        store.add(key)
        store.add(key)

        assertEquals(1, keyCount(prefs))
    }

    @Test
    fun clearRemovesEverything() {
        val prefs = fakePrefs()
        val store = CoverNoMatchStore(prefs)
        store.add(CoverNoMatchStore.key(1, "A"))
        store.add(CoverNoMatchStore.key(2, "B"))

        store.clear()

        assertFalse(store.contains(CoverNoMatchStore.key(1, "A")))
        assertEquals(null, prefs.getString(CoverNoMatchStore.PREF_KEY, null))
    }

    @Test
    fun clearForGameOnlyRemovesThatGame() {
        val prefs = fakePrefs()
        val store = CoverNoMatchStore(prefs)
        val keep = CoverNoMatchStore.key(2, "B")
        store.add(CoverNoMatchStore.key(1, "A"))
        store.add(keep)

        store.clearForGame(1)

        assertFalse(store.contains(CoverNoMatchStore.key(1, "A")))
        assertTrue(store.contains(keep))
    }

    @Test
    fun differentTitlesProduceDifferentKeys() {
        assertFalse(
            CoverNoMatchStore.key(1, "Title") == CoverNoMatchStore.key(1, "Another Title"),
        )
        // Normalization makes whitespace/case differences irrelevant.
        assertEquals(
            CoverNoMatchStore.key(1, "  Same Title  "),
            CoverNoMatchStore.key(1, "same title"),
        )
    }

    @Test
    fun storeIsBoundedToMaxEntries() {
        val prefs = fakePrefs()
        val store = CoverNoMatchStore(prefs)
        repeat(CoverNoMatchStore.MAX_ENTRIES + 25) { index ->
            store.add(CoverNoMatchStore.key(index.toLong() + 1, "Title $index"))
        }

        assertEquals(CoverNoMatchStore.MAX_ENTRIES, keyCount(prefs))
        // The oldest entries were evicted, the newest survived.
        assertFalse(store.contains(CoverNoMatchStore.key(1, "Title 0")))
        assertTrue(
            store.contains(
                CoverNoMatchStore.key(CoverNoMatchStore.MAX_ENTRIES.toLong() + 25, "Title ${CoverNoMatchStore.MAX_ENTRIES + 24}"),
            ),
        )
    }

    @Test
    fun malformedPayloadIsIgnored() {
        val prefs = fakePrefs()
        prefs.edit().putString(CoverNoMatchStore.PREF_KEY, "not json").apply()
        val store = CoverNoMatchStore(prefs)

        assertFalse(store.contains(CoverNoMatchStore.key(1, "A")))

        store.add(CoverNoMatchStore.key(1, "A"))
        assertTrue(store.contains(CoverNoMatchStore.key(1, "A")))
    }

    private fun keyCount(prefs: SharedPreferences): Int =
        prefs.getString(CoverNoMatchStore.PREF_KEY, null)
            ?.let { org.json.JSONArray(it).length() }
            ?: 0

    private fun singleKey(raw: String): String = org.json.JSONArray(raw).optString(0)

    private fun fakePrefs(): SharedPreferences {
        val store = mutableMapOf<String, Any?>()
        return object : SharedPreferences {
            override fun getAll(): Map<String, *> = store.toMap()

            override fun getString(
                key: String?,
                defValue: String?
            ): String? = store[key] as? String ?: defValue

            @Suppress("UNCHECKED_CAST")
            override fun getStringSet(
                key: String?,
                defValues: MutableSet<String>?
            ): MutableSet<String>? = store[key] as? MutableSet<String> ?: defValues

            override fun getInt(
                key: String?,
                defValue: Int
            ): Int = store[key] as? Int ?: defValue

            override fun getLong(
                key: String?,
                defValue: Long
            ): Long = store[key] as? Long ?: defValue

            override fun getFloat(
                key: String?,
                defValue: Float
            ): Float = store[key] as? Float ?: defValue

            override fun getBoolean(
                key: String?,
                defValue: Boolean
            ): Boolean = store[key] as? Boolean ?: defValue

            override fun contains(key: String?): Boolean = store.containsKey(key)

            override fun edit(): SharedPreferences.Editor {
                val edits = mutableMapOf<String, Any?>()
                val removals = mutableSetOf<String>()
                return object : SharedPreferences.Editor {
                    override fun putString(
                        key: String?,
                        value: String?
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = value
                        return this
                    }

                    override fun putStringSet(
                        key: String?,
                        values: MutableSet<String>?
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = values
                        return this
                    }

                    override fun putInt(
                        key: String?,
                        value: Int
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = value
                        return this
                    }

                    override fun putLong(
                        key: String?,
                        value: Long
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = value
                        return this
                    }

                    override fun putFloat(
                        key: String?,
                        value: Float
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = value
                        return this
                    }

                    override fun putBoolean(
                        key: String?,
                        value: Boolean
                    ): SharedPreferences.Editor {
                        if (key != null) edits[key] = value
                        return this
                    }

                    override fun remove(key: String?): SharedPreferences.Editor {
                        if (key != null) removals += key
                        return this
                    }

                    override fun clear(): SharedPreferences.Editor {
                        store.clear()
                        return this
                    }

                    override fun commit(): Boolean {
                        apply()
                        return true
                    }

                    override fun apply() {
                        removals.forEach(store::remove)
                        store.putAll(edits)
                    }
                }
            }

            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        }
    }
}
