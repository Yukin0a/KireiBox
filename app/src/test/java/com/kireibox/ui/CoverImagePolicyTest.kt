package com.kireibox.ui

import androidx.compose.ui.graphics.FilterQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverImagePolicyTest {
    @Test
    fun usesBoundedMemoryAndConcurrency() {
        assertEquals(256, CoverImagePolicy.TARGET_WIDTH_PX)
        assertEquals(384, CoverImagePolicy.TARGET_HEIGHT_PX)
        assertEquals(8 * 1024, CoverImagePolicy.BITMAP_CACHE_KIB)
        assertEquals(1_080, CoverImagePolicy.LIBRARY_CARD_MAX_WIDTH_PX)
        assertEquals(1_620, CoverImagePolicy.LIBRARY_CARD_MAX_HEIGHT_PX)
        assertEquals(2, CoverImagePolicy.MAX_PARALLEL_DECODES)
    }

    @Test
    fun libraryCardsUseMeasuredBProfileTargetsAndDistinctKeys() {
        val measured = CoverImagePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 489, 573)
        assertEquals(489, measured.targetWidthPx)
        assertEquals(573, measured.targetHeightPx)
        assertEquals("cover|LIBRARY_CARD|489x573", measured.cacheKey)

        val capped = CoverImagePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 2_400, 3_600)
        assertEquals(1_080, capped.targetWidthPx)
        assertEquals(1_620, capped.targetHeightPx)
        assertFalse(measured.cacheKey == capped.cacheKey)
        assertFalse(
            measured.cacheKey == CoverImagePolicy.request(
                "cover",
                CoverImageRole.THUMBNAIL,
                489,
                573,
            ).cacheKey,
        )
    }

    @Test
    fun librarySamplerKeepsEnoughPixelsToCoverTheCard() {
        val request = CoverImagePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 489, 573)

        assertEquals(4, CoverImagePolicy.sampleSize(2_048, 3_072, request))
        assertEquals(1, CoverImagePolicy.sampleSize(512, 768, request))
        assertEquals(1, CoverImagePolicy.sampleSize(256, 384, request))
    }

    @Test
    fun libraryPostDecodeScalePreservesAspectAndNeverUpscales() {
        assertEquals(
            CoverPixelSize(489, 734),
            CoverImagePolicy.libraryCardScaledSize(512, 768, 489, 573),
        )
        assertEquals(
            CoverPixelSize(256, 384),
            CoverImagePolicy.libraryCardScaledSize(256, 384, 489, 573),
        )
        assertNull(CoverImagePolicy.libraryCardScaledSize(0, 768, 489, 573))
        assertNull(CoverImagePolicy.libraryCardScaledSize(512, 768, 0, 573))
    }

    @Test
    fun onlyLibraryCardsUseHighQualityComposeFiltering() {
        assertEquals(FilterQuality.High, CoverImagePolicy.filterQuality(CoverImageRole.LIBRARY_CARD))
        assertEquals(FilterQuality.Low, CoverImagePolicy.filterQuality(CoverImageRole.THUMBNAIL))
        assertEquals(FilterQuality.Low, CoverImagePolicy.filterQuality(CoverImageRole.HERO))
        assertEquals(FilterQuality.Low, CoverImagePolicy.filterQuality(CoverImageRole.IMMERSIVE_BACKDROP))
    }

    @Test
    fun samplesLargePostersToTheTargetEnvelope() {
        assertEquals(8, CoverImagePolicy.sampleSize(2048, 3072))
        assertEquals(4, CoverImagePolicy.sampleSize(1000, 1500))
        assertEquals(2, CoverImagePolicy.sampleSize(512, 768))
    }

    @Test
    fun rejectsInvalidOrPathologicalImageBounds() {
        assertNull(CoverImagePolicy.sampleSize(0, 768))
        assertNull(CoverImagePolicy.sampleSize(768, -1))
        assertNull(CoverImagePolicy.sampleSize(20_000, 20_000))
    }

    @Test
    fun immersiveBackdropCapsBothDimensionsNear720() {
        val request = CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertEquals(720, request.targetWidthPx)
        assertEquals(720, request.targetHeightPx)

        val small = CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 640, 960)
        assertEquals(640, small.targetWidthPx)
        assertEquals(720, small.targetHeightPx)

        val bounded = CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 4_000, 5_000)
        assertEquals(720, bounded.targetWidthPx)
        assertEquals(720, bounded.targetHeightPx)
    }

    @Test
    fun immersiveBackdropSamplingKeepsTheShortEdgeAtOrBelow720() {
        assertEquals(1, CoverImagePolicy.sampleSize(720, 720, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(2, CoverImagePolicy.sampleSize(721, 720, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(4, CoverImagePolicy.sampleSize(1_080, 1_920, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(8, CoverImagePolicy.sampleSize(2_160, 3_840, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(16, CoverImagePolicy.sampleSize(4_320, 7_680, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(4, CoverImagePolicy.sampleSize(1_920, 1_080, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(2, CoverImagePolicy.sampleSize(1_000, 1_000, CoverImageRole.IMMERSIVE_BACKDROP))
    }

    @Test
    fun immersiveBackdropCacheKeyIsolatedFromOtherRoles() {
        val thumbnail = CoverImagePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        val hero = CoverImagePolicy.request("cover", CoverImageRole.HERO, 1_200, 1_800)
        val backdrop = CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertFalse(thumbnail.cacheKey == backdrop.cacheKey)
        assertFalse(hero.cacheKey == backdrop.cacheKey)
        assertEquals(
            backdrop.cacheKey,
            CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080).cacheKey,
        )
    }

    @Test
    fun acceptsUnknownOrBoundedNetworkBodiesOnly() {
        assertTrue(CoverImagePolicy.acceptsNetworkLength(-1))
        assertTrue(CoverImagePolicy.acceptsNetworkLength(CoverImagePolicy.MAX_NETWORK_BYTES))
        assertFalse(CoverImagePolicy.acceptsNetworkLength(CoverImagePolicy.MAX_NETWORK_BYTES + 1L))
    }

    @Test
    fun thumbnailEnvelopeIsSmallWhileHeroUsesLargerCappedRequest() {
        assertEquals(256, CoverImagePolicy.TARGET_WIDTH_PX)
        assertEquals(384, CoverImagePolicy.TARGET_HEIGHT_PX)
        assertEquals(8 * 1024, CoverImagePolicy.BITMAP_CACHE_KIB)
        assertEquals(2, CoverImagePolicy.sampleSize(512, 768, CoverImageRole.THUMBNAIL))
        assertEquals(8, CoverImagePolicy.sampleSize(5_760, 8_640, CoverImageRole.HERO))

        val hero = CoverImagePolicy.request("cover", CoverImageRole.HERO, 3_000, 4_000)
        assertEquals(1_080, hero.targetWidthPx)
        assertEquals(1_620, hero.targetHeightPx)
        assertEquals(480, CoverImagePolicy.request("cover", CoverImageRole.HERO, 480, 720).targetWidthPx)
        assertEquals(720, CoverImagePolicy.request("cover", CoverImageRole.HERO, 480, 720).targetHeightPx)
        assertEquals(20 * 1024, CoverImagePolicy.HERO_BITMAP_CACHE_KIB)
    }

    @Test
    fun libraryHeroAndBackdropBudgetsMatchTheSharedCacheBudgetPolicy() {
        assertEquals(CoverBitmapCaches.LIBRARY_CARD_KIB, CoverImagePolicy.LIBRARY_CARD_BITMAP_CACHE_KIB)
        assertEquals(24 * 1024, CoverImagePolicy.LIBRARY_CARD_BITMAP_CACHE_KIB)
        assertEquals(CoverBitmapCaches.HERO_KIB, CoverImagePolicy.HERO_BITMAP_CACHE_KIB)
        assertEquals(CoverBitmapCaches.BACKDROP_KIB, CoverImagePolicy.BACKDROP_BITMAP_CACHE_KIB)
        assertEquals(12 * 1024, CoverImagePolicy.BACKDROP_BITMAP_CACHE_KIB)
        assertEquals(64 * 1024, CoverBitmapCaches.TOTAL_KIB)
    }

    @Test
    fun heroCacheKeysIsolateRoleUriAndSizeBuckets() {
        val thumbnail = CoverImagePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        val heroSmall = CoverImagePolicy.request("cover", CoverImageRole.HERO, 480, 720)
        val heroLarge = CoverImagePolicy.request("cover", CoverImageRole.HERO, 1_200, 1_800)

        assertFalse(thumbnail.cacheKey == heroSmall.cacheKey)
        assertFalse(heroSmall.cacheKey == heroLarge.cacheKey)
        assertEquals(heroSmall.cacheKey, CoverImagePolicy.request("cover", CoverImageRole.HERO, 480, 720).cacheKey)
    }

    @Test
    fun worstCaseArgbBytesFitTheirCacheBudgetsWithHeadroom() {
        // ARGB_8888 = 4 bytes/pixel; these are the largest bitmaps each role can produce.
        val thumbnailBytes = CoverImagePolicy.TARGET_WIDTH_PX.toLong() * CoverImagePolicy.TARGET_HEIGHT_PX * 4L
        val libraryCardBytes = CoverImagePolicy.LIBRARY_CARD_MAX_WIDTH_PX.toLong() *
            CoverImagePolicy.LIBRARY_CARD_MAX_HEIGHT_PX * 4L
        val heroBytes = CoverImagePolicy.HERO_MAX_WIDTH_PX.toLong() * CoverImagePolicy.HERO_MAX_HEIGHT_PX * 4L
        val backdropBytes = CoverImagePolicy.BACKDROP_MAX_EDGE_PX.toLong() * CoverImagePolicy.BACKDROP_MAX_EDGE_PX * 4L

        assertEquals(393_216L, thumbnailBytes)
        assertEquals(6_998_400L, libraryCardBytes)
        assertEquals(6_998_400L, heroBytes)
        assertEquals(2_073_600L, backdropBytes)

        assertTrue(
            "8 MiB must hold at least 20 worst-case thumbnails",
            CoverImagePolicy.BITMAP_CACHE_KIB * 1024L >= 20L * thumbnailBytes,
        )
        assertTrue(
            "24 MiB must hold at least 3 maximum-size library cards",
            CoverBitmapCaches.LIBRARY_CARD_KIB * 1024L >= 3L * libraryCardBytes,
        )
        assertTrue(
            "20 MiB must hold at least 2 worst-case heroes",
            CoverImagePolicy.HERO_BITMAP_CACHE_KIB * 1024L >= 2L * heroBytes,
        )
        assertTrue(
            "20 MiB holds fewer than 3 worst-case heroes (documents the boundary)",
            CoverImagePolicy.HERO_BITMAP_CACHE_KIB * 1024L < 3L * heroBytes,
        )
        assertTrue(
            "12 MiB must hold at least 5 worst-case backdrops",
            CoverImagePolicy.BACKDROP_BITMAP_CACHE_KIB * 1024L >= 5L * backdropBytes,
        )
    }

    @Test
    fun smallImagesDecodeAtNativeSizeWithoutUpscaling() {
        // inSampleSize == 1 means native-resolution decode; the target envelope is a
        // downsampling ceiling, never an upscale floor.
        assertEquals(1, CoverImagePolicy.sampleSize(128, 192))
        assertEquals(1, CoverImagePolicy.sampleSize(100, 100))
        assertEquals(1, CoverImagePolicy.sampleSize(64, 64, CoverImageRole.HERO))
        assertEquals(1, CoverImagePolicy.sampleSize(300, 300, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(1, CoverImagePolicy.sampleSize(720, 720, CoverImageRole.HERO))
    }

    @Test
    fun sampleSizeBoundariesAtExactCaps() {
        assertEquals(1, CoverImagePolicy.sampleSize(256, 384))
        assertEquals(2, CoverImagePolicy.sampleSize(257, 384))
        assertEquals(1, CoverImagePolicy.sampleSize(1_080, 1_620, CoverImageRole.HERO))
        assertEquals(2, CoverImagePolicy.sampleSize(1_081, 1_620, CoverImageRole.HERO))
        assertEquals(2, CoverImagePolicy.sampleSize(2_160, 3_240, CoverImageRole.HERO))
    }

    @Test
    fun heroCacheKeysDifferWhenSameBucketWouldProduceDifferentSamples() {
        // Regression: bucket-based keys collided for targets that decode to
        // different samples, so one cache slot could serve the wrong-size bitmap
        // (2048x3072 source: target 800x1200 -> sample 4, target 1024x1536 -> sample 2).
        val small = CoverImagePolicy.request("cover", CoverImageRole.HERO, 800, 1_200)
        val medium = CoverImagePolicy.request("cover", CoverImageRole.HERO, 1_024, 1_536)

        assertEquals(4, CoverImagePolicy.sampleSize(2_048, 3_072, small))
        assertEquals(2, CoverImagePolicy.sampleSize(2_048, 3_072, medium))
        assertFalse(small.cacheKey == medium.cacheKey)
    }

    @Test
    fun cacheKeyUsesExactClampedTargetDims() {
        val hero = CoverImagePolicy.request("cover", CoverImageRole.HERO, 800, 1_200)
        assertEquals("cover|HERO|800x1200", hero.cacheKey)

        val thumbnail = CoverImagePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        assertEquals("cover|THUMBNAIL|256x384", thumbnail.cacheKey)

        val backdrop = CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertEquals("cover|IMMERSIVE_BACKDROP|720x720", backdrop.cacheKey)
    }

    @Test
    fun cacheKeyIsStableForIdenticalTargets() {
        assertEquals(
            CoverImagePolicy.request("cover", CoverImageRole.HERO, 800, 1_200).cacheKey,
            CoverImagePolicy.request("cover", CoverImageRole.HERO, 800, 1_200).cacheKey,
        )
        assertEquals(
            CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 1_080, 2_340).cacheKey,
            CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 1_080, 2_340).cacheKey,
        )
    }

    @Test
    fun cacheKeyClampsExtremeComponentSizesWithoutOverflow() {
        val huge = CoverImagePolicy.request("cover", CoverImageRole.HERO, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(1_080, huge.targetWidthPx)
        assertEquals(1_620, huge.targetHeightPx)
        assertEquals("cover|HERO|1080x1620", huge.cacheKey)

        val zero = CoverImagePolicy.request("cover", CoverImageRole.HERO, 0, 0)
        assertEquals("cover|HERO|1x1", zero.cacheKey)

        val hugeBackdrop =
            CoverImagePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals("cover|IMMERSIVE_BACKDROP|720x720", hugeBackdrop.cacheKey)
    }
}
