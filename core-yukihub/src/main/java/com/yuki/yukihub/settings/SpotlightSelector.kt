package com.yuki.yukihub.settings

import com.yuki.yukihub.model.Game

/** Pure, deterministic selection policy used by both the home page and tests. */
object SpotlightSelector {
    fun select(
        spec: DashboardWidgetSpec,
        games: List<Game>,
        sessionSeed: Long,
        epochDay: Long,
    ): Game? {
        val ordered = games.sortedWith(
            compareByDescending<Game> { it.lastPlayedAt }
                .thenBy { it.title.orEmpty() }
                .thenBy { it.id },
        )
        val sourcePool = when (spec.spotlightSource) {
            SpotlightSource.FIXED -> ordered.filter { it.id == spec.gameId }
            SpotlightSource.RECENT -> ordered.filter { it.lastPlayedAt > 0L }.ifEmpty { ordered }
            SpotlightSource.FAVORITES -> ordered.filter { it.favorite }
            SpotlightSource.ALL -> ordered
        }
        if (sourcePool.isEmpty()) return null
        val pool = sourcePool.filter { !it.coverUri.isNullOrBlank() }.ifEmpty { sourcePool }
        val salt = when (spec.spotlightStrategy) {
            SpotlightStrategy.FIRST -> return pool.firstOrNull()
            SpotlightStrategy.RANDOM_EACH_LAUNCH -> sessionSeed
            SpotlightStrategy.DAILY_RANDOM -> epochDay
        }
        val mixed = mix64(salt xor spec.instanceId.hashCode().toLong())
        return pool[Math.floorMod(mixed, pool.size.toLong()).toInt()]
    }

    private fun mix64(input: Long): Long {
        var value = input
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
