package com.yuki.yukihub.settings

/**
 * Legacy automatic-source values kept readable until the immersive editor moves to the
 * source/strategy/count contract.
 */
enum class ImmersiveGalleryAutoSource {
    RECENT_FIVE,
    RECENT_ALL,
    FAVORITES,
}

/**
 * Pure normalization and migration policy for the immersive home gallery.
 *
 * The policy deliberately does not touch dashboard drafts. Manual ids retain their input order,
 * duplicates and non-positive values are removed, and the result is capped at five entries. When
 * a library id set is supplied, ids not present in that set are treated as stale and removed.
 */
object ImmersiveGallerySettingsPolicy {
    const val MAX_MANUAL_IDS = ImmersiveGallerySettings.MAX_COUNT
    const val MIN_COUNT = ImmersiveGallerySettings.MIN_COUNT
    const val MAX_COUNT = ImmersiveGallerySettings.MAX_COUNT
    const val DEFAULT_COUNT = ImmersiveGallerySettings.DEFAULT_COUNT

    /** Missing, blank, or illegal legacy values resolve to the historical RECENT_FIVE default. */
    fun normalizeAutoSource(raw: String?): ImmersiveGalleryAutoSource =
        ImmersiveGalleryAutoSource.entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true)
        } ?: ImmersiveGalleryAutoSource.RECENT_FIVE

    fun normalizeMode(raw: String?): SpotlightSelectionMode =
        SpotlightSelectionMode.entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true)
        } ?: SpotlightSelectionMode.AUTOMATIC

    fun normalizeSelectionMode(raw: String?): ImmersiveGallerySelectionMode = normalizeMode(raw)

    /** FIXED is a dashboard-only source and therefore falls back to RECENT for immersive state. */
    fun normalizeSource(raw: String?): ImmersiveGallerySource =
        when (raw?.trim()?.uppercase()) {
            SpotlightSource.FAVORITES.name -> SpotlightSource.FAVORITES
            SpotlightSource.ALL.name -> SpotlightSource.ALL
            SpotlightSource.RECENT.name,
            ImmersiveGalleryAutoSource.RECENT_FIVE.name,
            ImmersiveGalleryAutoSource.RECENT_ALL.name,
            -> SpotlightSource.RECENT
            else -> SpotlightSource.RECENT
        }

    fun normalizeSource(source: ImmersiveGallerySource): ImmersiveGallerySource =
        when (source) {
            SpotlightSource.FAVORITES -> SpotlightSource.FAVORITES
            SpotlightSource.ALL -> SpotlightSource.ALL
            SpotlightSource.RECENT,
            SpotlightSource.FIXED,
            -> SpotlightSource.RECENT
        }

    fun normalizeStrategy(raw: String?): ImmersiveGalleryStrategy =
        SpotlightStrategy.entries.firstOrNull {
            it.name.equals(raw?.trim(), ignoreCase = true)
        } ?: SpotlightStrategy.FIRST

    /** Accepts either an Int or its persisted string form for callers at both API boundaries. */
    fun normalizeCount(raw: Any?): Int =
        when (raw) {
            is Int -> raw.coerceIn(MIN_COUNT, MAX_COUNT)
            is String -> raw.trim().toIntOrNull()?.coerceIn(MIN_COUNT, MAX_COUNT) ?: DEFAULT_COUNT
            else -> DEFAULT_COUNT
        }

    /**
     * Parses the comma-joined stored list while preserving order. A null valid-id set means that
     * only syntactic normalization is requested; an empty set means every stored id is stale.
     */
    fun decodeIds(
        raw: String?,
        validGameIds: Set<Long>? = null,
    ): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = LinkedHashSet<Long>()
        for (token in raw.split(',')) {
            val value = token.trim().toLongOrNull()
            if (value != null && value > 0L && (validGameIds == null || value in validGameIds)) {
                seen.add(value)
            }
            if (seen.size >= MAX_MANUAL_IDS) break
        }
        return seen.toList()
    }

    /** Encodes ids through the same normalization so the stored value never holds junk. */
    fun encodeIds(
        ids: List<Long>,
        validGameIds: Set<Long>? = null,
    ): String = normalizeIds(ids, validGameIds).joinToString(",")

    /** Normalization applied on write paths and when reconciling ids with the current library. */
    fun normalizeIds(
        ids: Iterable<Long>,
        validGameIds: Set<Long>? = null,
    ): List<Long> {
        val seen = LinkedHashSet<Long>()
        for (id in ids) {
            if (id > 0L && (validGameIds == null || id in validGameIds)) seen.add(id)
            if (seen.size >= MAX_MANUAL_IDS) break
        }
        return seen.toList()
    }

    fun normalizeManualIds(
        ids: Iterable<Long>,
        validGameIds: Set<Long>? = null,
    ): List<Long> = normalizeIds(ids, validGameIds)

    /**
     * Canonical manual-selection toggle shared by every immersive editor surface. Ids keep their
     * saved order, duplicates and non-positive values are dropped, and the list is capped at five.
     */
    fun toggleManualId(
        ids: Iterable<Long>,
        gameId: Long,
    ): List<Long> {
        val normalized = normalizeIds(ids)
        if (gameId in normalized) return normalized.filterNot { it == gameId }
        if (gameId <= 0L) return normalized
        return (normalized + gameId).take(MAX_MANUAL_IDS)
    }

    /** Canonical reorder within bounds only; out-of-range moves return the normalized list. */
    fun moveManualId(
        ids: Iterable<Long>,
        from: Int,
        to: Int,
    ): List<Long> {
        val normalized = normalizeIds(ids)
        if (from !in normalized.indices || to !in normalized.indices) return normalized
        return normalized.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun normalizeManualGameIds(
        ids: Iterable<Long>,
        validGameIds: Set<Long>? = null,
    ): List<Long> = normalizeIds(ids, validGameIds)

    fun decodeManualIds(
        raw: String?,
        validGameIds: Set<Long>? = null,
    ): List<Long> = decodeIds(raw, validGameIds)

    /** Converts a legacy auto-source value to the canonical source/strategy/count state. */
    fun fromLegacyAutoSource(source: ImmersiveGalleryAutoSource): ImmersiveGallerySettings =
        ImmersiveGallerySettings(
            source =
                when (source) {
                    ImmersiveGalleryAutoSource.RECENT_FIVE,
                    ImmersiveGalleryAutoSource.RECENT_ALL,
                    -> SpotlightSource.RECENT
                    ImmersiveGalleryAutoSource.FAVORITES -> SpotlightSource.FAVORITES
                },
            strategy = SpotlightStrategy.FIRST,
            count = DEFAULT_COUNT,
        )

    fun fromLegacyAutoSource(raw: String?): ImmersiveGallerySettings = fromLegacyAutoSource(normalizeAutoSource(raw))

    fun legacySettings(raw: String?): ImmersiveGallerySettings = fromLegacyAutoSource(raw)

    /** Best-effort mirror for old callers; new source/strategy/count fields remain authoritative. */
    fun toLegacyAutoSource(settings: ImmersiveGallerySettings): ImmersiveGalleryAutoSource =
        when (
            normalizeSource(settings.source)
        ) {
            SpotlightSource.FAVORITES -> ImmersiveGalleryAutoSource.FAVORITES
            SpotlightSource.ALL -> ImmersiveGalleryAutoSource.RECENT_ALL
            SpotlightSource.RECENT,
            SpotlightSource.FIXED,
            ->
                if (settings.strategy == SpotlightStrategy.FIRST &&
                    normalizeCount(settings.count) == DEFAULT_COUNT
                ) {
                    ImmersiveGalleryAutoSource.RECENT_FIVE
                } else {
                    ImmersiveGalleryAutoSource.RECENT_ALL
                }
        }

    fun normalize(
        settings: ImmersiveGallerySettings,
        validGameIds: Set<Long>? = null,
    ): ImmersiveGallerySettings =
        settings.copy(
            selectionMode = normalizeMode(settings.selectionMode.name),
            manualIds = normalizeIds(settings.manualIds, validGameIds),
            source = normalizeSource(settings.source),
            strategy = settings.strategy,
            count = normalizeCount(settings.count),
        )
}
