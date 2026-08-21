package com.yuki.yukihub.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveGallerySettingsPolicyTest {
    @Test
    fun normalizeAutoSourceFallsBackToRecentFiveForMissingOrIllegalValues() {
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, ImmersiveGallerySettingsPolicy.normalizeAutoSource(null))
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, ImmersiveGallerySettingsPolicy.normalizeAutoSource(""))
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, ImmersiveGallerySettingsPolicy.normalizeAutoSource("garbage"))
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, ImmersiveGallerySettingsPolicy.normalizeAutoSource("recent_five"))
        assertEquals(ImmersiveGalleryAutoSource.RECENT_FIVE, ImmersiveGallerySettingsPolicy.normalizeAutoSource("RECENT_FIVE"))
    }

    @Test
    fun normalizeAutoSourceAcceptsEveryEntryCaseInsensitively() {
        assertEquals(ImmersiveGalleryAutoSource.RECENT_ALL, ImmersiveGallerySettingsPolicy.normalizeAutoSource("recent_all"))
        assertEquals(ImmersiveGalleryAutoSource.RECENT_ALL, ImmersiveGallerySettingsPolicy.normalizeAutoSource("RECENT_ALL"))
        assertEquals(ImmersiveGalleryAutoSource.FAVORITES, ImmersiveGallerySettingsPolicy.normalizeAutoSource("favorites"))
        assertEquals(ImmersiveGalleryAutoSource.FAVORITES, ImmersiveGallerySettingsPolicy.normalizeAutoSource("FAVORITES"))
    }

    @Test
    fun normalizeModeDefaultsToAutomatic() {
        assertEquals(SpotlightSelectionMode.AUTOMATIC, ImmersiveGallerySettingsPolicy.normalizeMode(null))
        assertEquals(SpotlightSelectionMode.AUTOMATIC, ImmersiveGallerySettingsPolicy.normalizeMode(""))
        assertEquals(SpotlightSelectionMode.AUTOMATIC, ImmersiveGallerySettingsPolicy.normalizeMode("garbage"))
        assertEquals(SpotlightSelectionMode.AUTOMATIC, ImmersiveGallerySettingsPolicy.normalizeMode("automatic"))
        assertEquals(SpotlightSelectionMode.MANUAL, ImmersiveGallerySettingsPolicy.normalizeMode("MANUAL"))
        assertEquals(SpotlightSelectionMode.MANUAL, ImmersiveGallerySettingsPolicy.normalizeMode("manual"))
    }

    @Test
    fun decodeIdsKeepsStableOrderAndDropsJunk() {
        assertEquals(emptyList<Long>(), ImmersiveGallerySettingsPolicy.decodeIds(null))
        assertEquals(emptyList<Long>(), ImmersiveGallerySettingsPolicy.decodeIds(""))
        assertEquals(listOf(2L, 5L), ImmersiveGallerySettingsPolicy.decodeIds("2, 5"))
        assertEquals(listOf(2L, 5L), ImmersiveGallerySettingsPolicy.decodeIds("x,2,,0,-1,5"))
        assertEquals(listOf(2L, 5L), ImmersiveGallerySettingsPolicy.decodeIds("2,2,5,5"))
    }

    @Test
    fun decodeIdsCapsAtFive() {
        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L),
            ImmersiveGallerySettingsPolicy.decodeIds("1,2,3,4,5,6,7,8"),
        )
    }

    @Test
    fun encodeRoundTripsThroughNormalization() {
        assertEquals("", ImmersiveGallerySettingsPolicy.encodeIds(emptyList()))
        assertEquals("3,1", ImmersiveGallerySettingsPolicy.encodeIds(listOf(0L, 3L, 1L, 1L)))
        assertEquals("3,1", ImmersiveGallerySettingsPolicy.encodeIds(listOf(3L, 1L)))
        assertEquals("1,2,3,4,5", ImmersiveGallerySettingsPolicy.encodeIds((1L..9L).toList()))
    }

    @Test
    fun canonicalSourceStrategyAndCountHaveDeterministicDefaults() {
        assertEquals(SpotlightSource.RECENT, ImmersiveGallerySettingsPolicy.normalizeSource(null))
        assertEquals(SpotlightSource.RECENT, ImmersiveGallerySettingsPolicy.normalizeSource("FIXED"))
        assertEquals(SpotlightSource.FAVORITES, ImmersiveGallerySettingsPolicy.normalizeSource("favorites"))
        assertEquals(SpotlightSource.ALL, ImmersiveGallerySettingsPolicy.normalizeSource("ALL"))
        assertEquals(SpotlightStrategy.FIRST, ImmersiveGallerySettingsPolicy.normalizeStrategy(null))
        assertEquals(SpotlightStrategy.DAILY_RANDOM, ImmersiveGallerySettingsPolicy.normalizeStrategy("daily_random"))
        assertEquals(1, ImmersiveGallerySettingsPolicy.normalizeCount(-4))
        assertEquals(5, ImmersiveGallerySettingsPolicy.normalizeCount("99"))
        assertEquals(3, ImmersiveGallerySettingsPolicy.normalizeCount(" 3 "))
    }

    @Test
    fun legacySourcesMigrateToRecentOrFavoritesFirstAndFive() {
        assertEquals(
            ImmersiveGallerySettings(
                selectionMode = SpotlightSelectionMode.AUTOMATIC,
                manualIds = emptyList(),
                source = SpotlightSource.RECENT,
                strategy = SpotlightStrategy.FIRST,
                count = 5,
            ),
            ImmersiveGallerySettingsPolicy.fromLegacyAutoSource(ImmersiveGalleryAutoSource.RECENT_FIVE),
        )
        assertEquals(
            ImmersiveGallerySettings(
                selectionMode = SpotlightSelectionMode.AUTOMATIC,
                manualIds = emptyList(),
                source = SpotlightSource.RECENT,
                strategy = SpotlightStrategy.FIRST,
                count = 5,
            ),
            ImmersiveGallerySettingsPolicy.fromLegacyAutoSource(ImmersiveGalleryAutoSource.RECENT_ALL),
        )
        assertEquals(
            ImmersiveGallerySettings(
                selectionMode = SpotlightSelectionMode.AUTOMATIC,
                manualIds = emptyList(),
                source = SpotlightSource.FAVORITES,
                strategy = SpotlightStrategy.FIRST,
                count = 5,
            ),
            ImmersiveGallerySettingsPolicy.fromLegacyAutoSource(ImmersiveGalleryAutoSource.FAVORITES),
        )
    }

    @Test
    fun normalizeSettingsPreservesOrderAndDropsStaleIds() {
        val normalized = ImmersiveGallerySettings(
            selectionMode = SpotlightSelectionMode.MANUAL,
            manualIds = listOf(8L, 99L, 8L, 3L, 4L, 5L, 6L),
            source = SpotlightSource.FIXED,
            strategy = SpotlightStrategy.DAILY_RANDOM,
            count = 0,
        ).normalized(validGameIds = setOf(8L, 3L, 4L, 5L, 6L))

        assertEquals(SpotlightSelectionMode.MANUAL, normalized.selectionMode)
        assertEquals(listOf(8L, 3L, 4L, 5L, 6L), normalized.manualIds)
        assertEquals(SpotlightSource.RECENT, normalized.source)
        assertEquals(SpotlightStrategy.DAILY_RANDOM, normalized.strategy)
        assertEquals(1, normalized.count)
    }
}
