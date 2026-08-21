package com.yuki.yukihub.settings

import com.yuki.yukihub.model.Game

/** Pure, deterministic selection policy used by both the home page and tests. */
object SpotlightSelector {
    fun select(
        spec: DashboardWidgetSpec,
        games: List<Game>,
        sessionSeed: Long,
        epochDay: Long,
    ): Game? = selectPages(spec, games, sessionSeed, epochDay).firstOrNull()

    /**
     * Selects the carousel pages without changing the persisted dashboard schema.
     * Covered games are preferred in automatic sources, but an all-default-cover source
     * remains usable. Manual mode preserves the saved explicit order and never silently
     * substitutes another game when every saved id is missing.
     */
    fun selectPages(
        spec: DashboardWidgetSpec,
        games: List<Game>,
        sessionSeed: Long,
        epochDay: Long,
    ): List<Game> {
        if (spec.spotlightSelectionMode == SpotlightSelectionMode.MANUAL) {
            val byId = games.associateBy { it.id }
            return spec.spotlightGameIds
                .mapNotNull(byId::get)
                .take(spec.spotlightMaxPages.coerceIn(1, MAX_PAGES))
        }
        val ordered = games.sortedWith(
            compareByDescending<Game> { it.lastPlayedAt }
                .thenBy { it.title.orEmpty() }
                .thenBy { it.id },
        )
        val sourcePool = when (spec.spotlightSource) {
            SpotlightSource.FIXED -> ordered.filter { it.id == spec.gameId }
            // RECENT is intentionally empty until a real play session exists. ALL is the
            // only source allowed to expose the whole library.
            SpotlightSource.RECENT -> ordered.filter { it.lastPlayedAt > 0L }
            SpotlightSource.FAVORITES -> ordered.filter { it.favorite }
            SpotlightSource.ALL -> ordered
        }
        if (sourcePool.isEmpty()) return emptyList()
        val pool = sourcePool.filter { !it.coverUri.isNullOrBlank() }.ifEmpty { sourcePool }
        val pages: List<Game> = when (spec.spotlightStrategy) {
            SpotlightStrategy.FIRST -> pool
            SpotlightStrategy.RANDOM_EACH_LAUNCH -> randomized(pool, spec.instanceId, sessionSeed)
            SpotlightStrategy.DAILY_RANDOM -> randomized(pool, spec.instanceId, epochDay)
        }
        return pages.take(spec.spotlightMaxPages.coerceIn(1, MAX_PAGES))
    }

    private const val MAX_PAGES = 5

    private fun randomized(pool: List<Game>, instanceId: String, salt: Long): List<Game> =
        pool.sortedWith(
            compareBy<Game> { game -> mix64(salt xor instanceId.hashCode().toLong() xor game.id) }
                .thenBy { it.id },
        )

    private fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
