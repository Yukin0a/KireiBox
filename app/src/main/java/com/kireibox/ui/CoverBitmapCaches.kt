package com.kireibox.ui

/**
 * Cache budgets for launcher cover bitmap memory (single source of truth).
 * KireiBoxApp wiring consumes these through CoverDecodePolicy constants.
 */
internal object CoverBitmapCaches {
    /** Ordinary thumbnail cover bitmap cache: 8 MiB. */
    const val ORDINARY_KIB: Int = 8 * 1024

    /** High-resolution game-library card cache: 24 MiB. */
    const val LIBRARY_CARD_KIB: Int = 24 * 1024

    /** Hero cover bitmap cache: 20 MiB. */
    const val HERO_KIB: Int = 20 * 1024

    /** Immersive backdrop cover bitmap cache: 12 MiB. */
    const val BACKDROP_KIB: Int = 12 * 1024

    /** Total cover bitmap budget: 64 MiB. */
    const val TOTAL_KIB: Int = ORDINARY_KIB + LIBRARY_CARD_KIB + HERO_KIB + BACKDROP_KIB

    /** Bounded tone cache capacity (entries), keeping extracted tones off the bitmap caches. */
    const val TONE_CACHE_CAPACITY: Int = 64

    /** Single mapping from an image role to its bitmap cache budget; every caller routes here. */
    fun budgetKibFor(role: CoverImageRole): Int =
        when (role) {
            CoverImageRole.THUMBNAIL -> ORDINARY_KIB
            CoverImageRole.LIBRARY_CARD -> LIBRARY_CARD_KIB
            CoverImageRole.HERO -> HERO_KIB
            CoverImageRole.IMMERSIVE_BACKDROP -> BACKDROP_KIB
        }
}

/**
 * Single bounded, access-ordered tone cache used by [CoverToneExtractor]: the same key returns the
 * same tone, a changed key recomputes, and the oldest least-recently-used entry is evicted once the
 * capacity is exceeded.
 *
 * [getOrCompute] never holds the lock while [compute] runs: only lookups and inserts are
 * synchronized, so a slow computation for one key can never block another key's lookup or
 * computation. Concurrent misses for the same key may compute redundantly; the last insert wins and
 * every caller still receives a valid tone.
 */
internal class CoverToneCache(
    private val capacity: Int = CoverBitmapCaches.TONE_CACHE_CAPACITY,
) {
    private val lock = Any()
    private val tones =
        object : LinkedHashMap<String, CoverTone>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverTone>?): Boolean = size > capacity
        }

    /** Cache hit or null; never computes. */
    fun get(key: String): CoverTone? = synchronized(lock) { tones[key] }

    /** Inserts a tone for [key]; callers must not insert fallback tones. */
    fun put(
        key: String,
        tone: CoverTone
    ) {
        synchronized(lock) { tones[key] = tone }
    }

    fun getOrCompute(
        key: String,
        compute: () -> CoverTone
    ): CoverTone {
        get(key)?.let { return it }
        val tone = compute()
        put(key, tone)
        return tone
    }

    fun clear() = synchronized(lock) { tones.clear() }
}
