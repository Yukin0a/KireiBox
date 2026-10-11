package com.kireibox.ui

import androidx.compose.ui.graphics.FilterQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverDecodePolicyTest {
    @Test
    fun usesBoundedMemoryAndConcurrency() {
        assertEquals(256, CoverDecodePolicy.TARGET_WIDTH_PX)
        assertEquals(384, CoverDecodePolicy.TARGET_HEIGHT_PX)
        assertEquals(8 * 1024, CoverBitmapCaches.ORDINARY_KIB)
        assertEquals(1_080, CoverDecodePolicy.LIBRARY_CARD_MAX_WIDTH_PX)
        assertEquals(1_620, CoverDecodePolicy.LIBRARY_CARD_MAX_HEIGHT_PX)
        assertEquals(2, CoverDecodePolicy.MAX_PARALLEL_DECODES)
    }

    @Test
    fun libraryCardsUseMeasuredBProfileTargetsAndDistinctKeys() {
        val measured = CoverDecodePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 489, 573)
        assertEquals(489, measured.targetWidthPx)
        assertEquals(573, measured.targetHeightPx)
        assertEquals("cover|LIBRARY_CARD|489x573|blur=0", measured.cacheKey)

        val capped = CoverDecodePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 2_400, 3_600)
        assertEquals(1_080, capped.targetWidthPx)
        assertEquals(1_620, capped.targetHeightPx)
        assertFalse(measured.cacheKey == capped.cacheKey)
        assertFalse(
            measured.cacheKey ==
                CoverDecodePolicy.request(
                    "cover",
                    CoverImageRole.THUMBNAIL,
                    489,
                    573,
                ).cacheKey,
        )
    }

    @Test
    fun librarySamplerKeepsEnoughPixelsToCoverTheCard() {
        val request = CoverDecodePolicy.request("cover", CoverImageRole.LIBRARY_CARD, 489, 573)

        assertEquals(4, CoverDecodePolicy.sampleSize(2_048, 3_072, request))
        assertEquals(1, CoverDecodePolicy.sampleSize(512, 768, request))
        assertEquals(1, CoverDecodePolicy.sampleSize(256, 384, request))
    }

    @Test
    fun libraryPostDecodeScalePreservesAspectAndNeverUpscales() {
        assertEquals(
            CoverPixelSize(489, 734),
            CoverDecodePolicy.libraryCardScaledSize(512, 768, 489, 573),
        )
        assertEquals(
            CoverPixelSize(256, 384),
            CoverDecodePolicy.libraryCardScaledSize(256, 384, 489, 573),
        )
        assertNull(CoverDecodePolicy.libraryCardScaledSize(0, 768, 489, 573))
        assertNull(CoverDecodePolicy.libraryCardScaledSize(512, 768, 0, 573))
    }

    @Test
    fun immersiveArtworkUsesHighQualityComposeFiltering() {
        assertEquals(FilterQuality.High, CoverDecodePolicy.filterQuality(CoverImageRole.LIBRARY_CARD))
        assertEquals(FilterQuality.High, CoverDecodePolicy.filterQuality(CoverImageRole.HERO))
        assertEquals(FilterQuality.High, CoverDecodePolicy.filterQuality(CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals("small carousel/side cards use mipmapped filtering to avoid aliasing", FilterQuality.Medium, CoverDecodePolicy.filterQuality(CoverImageRole.THUMBNAIL))
    }

    @Test
    fun samplesLargePostersToTheTargetEnvelope() {
        assertEquals(8, CoverDecodePolicy.sampleSize(2048, 3072))
        assertEquals(4, CoverDecodePolicy.sampleSize(1000, 1500))
        assertEquals(2, CoverDecodePolicy.sampleSize(512, 768))
    }

    @Test
    fun rejectsInvalidOrPathologicalImageBounds() {
        assertNull(CoverDecodePolicy.sampleSize(0, 768))
        assertNull(CoverDecodePolicy.sampleSize(768, -1))
        assertNull(CoverDecodePolicy.sampleSize(20_000, 20_000))
    }

    @Test
    fun immersiveBackdropCapsBothDimensionsNear720() {
        val request = CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertEquals(720, request.targetWidthPx)
        assertEquals(720, request.targetHeightPx)

        val small = CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 640, 960)
        assertEquals(640, small.targetWidthPx)
        assertEquals(720, small.targetHeightPx)

        val bounded = CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 4_000, 5_000)
        assertEquals(720, bounded.targetWidthPx)
        assertEquals(720, bounded.targetHeightPx)
    }

    @Test
    fun immersiveBackdropSamplingKeepsTheShortEdgeAtOrBelow720() {
        assertEquals(1, CoverDecodePolicy.sampleSize(720, 720, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(2, CoverDecodePolicy.sampleSize(721, 720, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(4, CoverDecodePolicy.sampleSize(1_080, 1_920, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(8, CoverDecodePolicy.sampleSize(2_160, 3_840, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(16, CoverDecodePolicy.sampleSize(4_320, 7_680, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(4, CoverDecodePolicy.sampleSize(1_920, 1_080, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(2, CoverDecodePolicy.sampleSize(1_000, 1_000, CoverImageRole.IMMERSIVE_BACKDROP))
    }

    @Test
    fun immersiveBackdropCacheKeyIsolatedFromOtherRoles() {
        val thumbnail = CoverDecodePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        val hero = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 1_200, 1_800)
        val backdrop = CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertFalse(thumbnail.cacheKey == backdrop.cacheKey)
        assertFalse(hero.cacheKey == backdrop.cacheKey)
        assertEquals(
            backdrop.cacheKey,
            CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080).cacheKey,
        )
    }

    @Test
    fun acceptsUnknownOrBoundedNetworkBodiesOnly() {
        assertTrue(CoverDecodePolicy.acceptsNetworkLength(-1))
        assertTrue(CoverDecodePolicy.acceptsNetworkLength(CoverDecodePolicy.MAX_NETWORK_BYTES))
        assertFalse(CoverDecodePolicy.acceptsNetworkLength(CoverDecodePolicy.MAX_NETWORK_BYTES + 1L))
    }

    @Test
    fun thumbnailEnvelopeIsSmallWhileHeroUsesLargerCappedRequest() {
        assertEquals(256, CoverDecodePolicy.TARGET_WIDTH_PX)
        assertEquals(384, CoverDecodePolicy.TARGET_HEIGHT_PX)
        assertEquals(8 * 1024, CoverBitmapCaches.ORDINARY_KIB)
        assertEquals(2, CoverDecodePolicy.sampleSize(512, 768, CoverImageRole.THUMBNAIL))
        assertEquals(8, CoverDecodePolicy.sampleSize(5_760, 8_640, CoverImageRole.HERO))

        val hero = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 3_000, 4_000)
        assertEquals(1_080, hero.targetWidthPx)
        assertEquals(1_620, hero.targetHeightPx)
        assertEquals(480, CoverDecodePolicy.request("cover", CoverImageRole.HERO, 480, 720).targetWidthPx)
        assertEquals(720, CoverDecodePolicy.request("cover", CoverImageRole.HERO, 480, 720).targetHeightPx)
        assertEquals(20 * 1024, CoverBitmapCaches.HERO_KIB)
    }

    @Test
    fun libraryHeroAndBackdropBudgetsMatchTheSharedCacheBudgetPolicy() {
        assertEquals(24 * 1024, CoverBitmapCaches.LIBRARY_CARD_KIB)
        assertEquals(12 * 1024, CoverBitmapCaches.BACKDROP_KIB)
        assertEquals(64 * 1024, CoverBitmapCaches.TOTAL_KIB)
    }

    @Test
    fun heroCacheKeysIsolateRoleUriAndSizeBuckets() {
        val thumbnail = CoverDecodePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        val heroSmall = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 480, 720)
        val heroLarge = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 1_200, 1_800)

        assertFalse(thumbnail.cacheKey == heroSmall.cacheKey)
        assertFalse(heroSmall.cacheKey == heroLarge.cacheKey)
        assertEquals(heroSmall.cacheKey, CoverDecodePolicy.request("cover", CoverImageRole.HERO, 480, 720).cacheKey)
    }

    @Test
    fun worstCaseArgbBytesFitTheirCacheBudgetsWithHeadroom() {
        // ARGB_8888 = 4 bytes/pixel; these are the largest bitmaps each role can produce.
        val thumbnailBytes = CoverDecodePolicy.TARGET_WIDTH_PX.toLong() * CoverDecodePolicy.TARGET_HEIGHT_PX * 4L
        val libraryCardBytes =
            CoverDecodePolicy.LIBRARY_CARD_MAX_WIDTH_PX.toLong() *
                CoverDecodePolicy.LIBRARY_CARD_MAX_HEIGHT_PX * 4L
        val heroBytes = CoverDecodePolicy.HERO_MAX_WIDTH_PX.toLong() * CoverDecodePolicy.HERO_MAX_HEIGHT_PX * 4L
        val backdropBytes = CoverDecodePolicy.BACKDROP_MAX_EDGE_PX.toLong() * CoverDecodePolicy.BACKDROP_MAX_EDGE_PX * 4L

        assertEquals(393_216L, thumbnailBytes)
        assertEquals(6_998_400L, libraryCardBytes)
        assertEquals(6_998_400L, heroBytes)
        assertEquals(2_073_600L, backdropBytes)

        assertTrue(
            "8 MiB must hold at least 20 worst-case thumbnails",
            CoverBitmapCaches.ORDINARY_KIB * 1024L >= 20L * thumbnailBytes,
        )
        assertTrue(
            "24 MiB must hold at least 3 maximum-size library cards",
            CoverBitmapCaches.LIBRARY_CARD_KIB * 1024L >= 3L * libraryCardBytes,
        )
        assertTrue(
            "20 MiB must hold at least 2 worst-case heroes",
            CoverBitmapCaches.HERO_KIB * 1024L >= 2L * heroBytes,
        )
        assertTrue(
            "20 MiB holds fewer than 3 worst-case heroes (documents the boundary)",
            CoverBitmapCaches.HERO_KIB * 1024L < 3L * heroBytes,
        )
        assertTrue(
            "12 MiB must hold at least 5 worst-case backdrops",
            CoverBitmapCaches.BACKDROP_KIB * 1024L >= 5L * backdropBytes,
        )
    }

    @Test
    fun smallImagesDecodeAtNativeSizeWithoutUpscaling() {
        // inSampleSize == 1 means native-resolution decode; the target envelope is a
        // downsampling ceiling, never an upscale floor.
        assertEquals(1, CoverDecodePolicy.sampleSize(128, 192))
        assertEquals(1, CoverDecodePolicy.sampleSize(100, 100))
        assertEquals(1, CoverDecodePolicy.sampleSize(64, 64, CoverImageRole.HERO))
        assertEquals(1, CoverDecodePolicy.sampleSize(300, 300, CoverImageRole.IMMERSIVE_BACKDROP))
        assertEquals(1, CoverDecodePolicy.sampleSize(720, 720, CoverImageRole.HERO))
    }

    @Test
    fun sampleSizeBoundariesAtExactCaps() {
        assertEquals(1, CoverDecodePolicy.sampleSize(256, 384))
        assertEquals(2, CoverDecodePolicy.sampleSize(257, 384))
        assertEquals(1, CoverDecodePolicy.sampleSize(1_080, 1_620, CoverImageRole.HERO))
        assertEquals(2, CoverDecodePolicy.sampleSize(1_081, 1_620, CoverImageRole.HERO))
        assertEquals(2, CoverDecodePolicy.sampleSize(2_160, 3_240, CoverImageRole.HERO))
    }

    @Test
    fun heroCacheKeysDifferWhenSameBucketWouldProduceDifferentSamples() {
        // Regression: bucket-based keys collided for targets that decode to
        // different samples, so one cache slot could serve the wrong-size bitmap
        // (2048x3072 source: target 800x1200 -> sample 4, target 1024x1536 -> sample 2).
        val small = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200)
        val medium = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 1_024, 1_536)

        assertEquals(4, CoverDecodePolicy.sampleSize(2_048, 3_072, small))
        assertEquals(2, CoverDecodePolicy.sampleSize(2_048, 3_072, medium))
        assertFalse(small.cacheKey == medium.cacheKey)
    }

    @Test
    fun cacheKeyUsesExactClampedTargetDims() {
        val hero = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200)
        assertEquals("cover|HERO|800x1200|blur=0", hero.cacheKey)

        val thumbnail = CoverDecodePolicy.request("cover", CoverImageRole.THUMBNAIL, 256, 384)
        assertEquals("cover|THUMBNAIL|256x384|blur=0", thumbnail.cacheKey)

        val backdrop = CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 2_340, 1_080)
        assertEquals("cover|IMMERSIVE_BACKDROP|720x720|blur=0", backdrop.cacheKey)
    }

    @Test
    fun cacheKeyIsStableForIdenticalTargets() {
        assertEquals(
            CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200).cacheKey,
            CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200).cacheKey,
        )
        assertEquals(
            CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 1_080, 2_340).cacheKey,
            CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, 1_080, 2_340).cacheKey,
        )
    }

    @Test
    fun cacheKeyClampsExtremeComponentSizesWithoutOverflow() {
        val huge = CoverDecodePolicy.request("cover", CoverImageRole.HERO, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(1_080, huge.targetWidthPx)
        assertEquals(1_620, huge.targetHeightPx)
        assertEquals("cover|HERO|1080x1620|blur=0", huge.cacheKey)

        val zero = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 0, 0)
        assertEquals("cover|HERO|1x1|blur=0", zero.cacheKey)

        val hugeBackdrop =
            CoverDecodePolicy.request("cover", CoverImageRole.IMMERSIVE_BACKDROP, Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals("cover|IMMERSIVE_BACKDROP|720x720|blur=0", hugeBackdrop.cacheKey)
    }

    @Test
    fun blurredAndClearRequestsUseDistinctStableCacheKeys() {
        val clear = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200)
        val blurred = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200, blurred = true)

        assertFalse(clear.blurred)
        assertTrue(blurred.blurred)
        assertFalse(clear.cacheKey == blurred.cacheKey)
        assertEquals("cover|HERO|800x1200|blur=1", blurred.cacheKey)
        assertEquals(
            blurred.cacheKey,
            CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200, blurred = true).cacheKey,
        )
    }

    @Test
    fun displayKeyStaysStableAcrossAnimatedSizeChangesOnly() {
        val initial = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 800, 1_200, blurred = true)
        val resized = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 900, 1_350, blurred = true)
        val clear = CoverDecodePolicy.request("cover", CoverImageRole.HERO, 900, 1_350)
        val differentCover = CoverDecodePolicy.request("other", CoverImageRole.HERO, 900, 1_350, blurred = true)

        assertEquals(initial.displayKey, resized.displayKey)
        assertFalse(initial.cacheKey == resized.cacheKey)
        assertFalse(initial.displayKey == clear.displayKey)
        assertFalse(initial.displayKey == differentCover.displayKey)
    }
}
