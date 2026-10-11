package com.kireibox.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class CoverToneExtractorTest {
    @Test
    fun emptyOrInvalidPixelsUseTheMutedDefaultFallback() {
        val tone = CoverToneExtractor.fromArgbPixels(intArrayOf())
        assertTrue(tone.usedFallback)
        assertEquals(CoverToneExtractor.DEFAULT_FALLBACK_ARGB, tone.argb)
        // The default is the muted graphite-rose token, no longer the old #5B5AC8 purple.
        assertEquals(0xFF6A5560.toInt(), CoverToneExtractor.DEFAULT_FALLBACK_ARGB)
        assertTrue("default fallback stays a muted neutral", tone.saturation < .25f)
    }

    @Test
    fun nullBitmapFallbackIsNeverCachedByBitmapCacheKey() {
        val cacheKey = "null-fallback-must-not-stick|HERO|1024x2048"
        val first =
            CoverToneExtractor.toneFor(
                cacheKey = cacheKey,
                bitmap = null,
                fallbackArgb = 0xFF112233.toInt(),
            )
        val second =
            CoverToneExtractor.toneFor(
                cacheKey = cacheKey,
                bitmap = null,
                fallbackArgb = 0xFF445566.toInt(),
            )

        assertTrue(first.usedFallback)
        assertEquals(0xFF112233.toInt(), first.argb)
        assertTrue(second.usedFallback)
        assertEquals(0xFF445566.toInt(), second.argb)
    }

    @Test
    fun warmCoverTonePreservesItsHueAndIsClamped() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                IntArray(24 * 24) { 0xFFFF4B20.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(12f, hueOf(tone.argb), 1.5f)
        assertTrue(tone.saturation in .28f..0.48f)
        assertTrue(tone.value in .22f..0.38f)
        // The packed color itself is clamped (not only the reported fields): a regression that
        // packed the unclamped value/saturation would show here. ±.01 covers 8-bit rounding.
        assertTrue("packed value ${valueOf(tone.argb)}", valueOf(tone.argb) in .21f..0.39f)
        assertTrue("packed saturation ${saturationOf(tone.argb)}", saturationOf(tone.argb) in .27f..0.49f)
    }

    @Test
    fun neutralCoverToneStaysWithinRequiredSaturationAndValueRange() {
        // Monochrome art has no hue of its own: it must not be boosted into an invented red/maroon
        // (hue 0°), but take the fallback's graphite-rose hue at a low, bounded saturation.
        listOf(0xFF777777.toInt(), 0xFF101010.toInt(), 0xFFF0F0F0.toInt(), 0xFF7A7777.toInt()).forEach { grey ->
            val tone = CoverToneExtractor.fromArgbPixels(IntArray(24 * 24) { grey })
            assertTrue(!tone.usedFallback)
            assertEquals(CoverToneExtractor.NEUTRAL_SATURATION, tone.saturation, .0001f)
            assertTrue("neutral saturation stays below the chromatic clamp", tone.saturation < .28f)
            assertTrue(tone.value >= .22f && tone.value <= .38f)
            assertTrue("packed grey stays low-chroma", saturationOf(tone.argb) < .2f)
            assertTrue("packed value is clamped", valueOf(tone.argb) in .21f..0.39f)
            assertEquals(hueOf(CoverToneExtractor.DEFAULT_FALLBACK_ARGB), hueOf(tone.argb), 3f)
        }
        assertEquals(328.6f, CoverToneExtractor.fallbackHue, .5f)
    }

    @Test
    fun greenHueKeepsItsHue() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                IntArray(24 * 24) { 0xFF00C800.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(120f, hueOf(tone.argb), 0.6f)
    }

    @Test
    fun yellowGreenHueKeepsItsHue() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 89.9
                IntArray(24 * 24) { 0xFF80FF00.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(90f, hueOf(tone.argb), 1.0f)
    }

    @Test
    fun cyanHueKeepsItsHue() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                IntArray(24 * 24) { 0xFF00C8C8.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(180f, hueOf(tone.argb), 0.6f)
    }

    @Test
    fun yellowHuePreservesItsHue() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                IntArray(24 * 24) { 0xFFFFC800.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(47f, hueOf(tone.argb), 1.5f)
    }

    @Test
    fun redHueAtTheZeroDegreeSeamIsPreserved() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 345.2
                IntArray(24 * 24) { 0xFFFF003F.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(345f, hueOf(tone.argb), 1.5f)
    }

    @Test
    fun magentaHueIsPreserved() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 329.9
                IntArray(24 * 24) { 0xFFFF0080.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(330f, hueOf(tone.argb), 1.0f)
    }

    @Test
    fun hueJustBelowSeventyIsPreserved() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 69.65
                IntArray(24 * 24) { 0xFFD6FF00.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(70f, hueOf(tone.argb), 1.5f)
    }

    @Test
    fun hueJustAboveSeventyIsPreserved() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 70.1
                IntArray(24 * 24) { 0xFFD4FF00.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(70f, hueOf(tone.argb), 1.0f)
    }

    @Test
    fun redMagentaHueNearTheSeamIsPreserved() {
        val tone =
            CoverToneExtractor.fromArgbPixels(
                // hue ≈ 344.7
                IntArray(24 * 24) { 0xFFFF0041.toInt() },
            )
        assertTrue(!tone.usedFallback)
        assertEquals(345f, hueOf(tone.argb), 1.0f)
    }

    @Test
    fun samplingConstantsBoundWorkTo64x64() {
        assertEquals(64, CoverToneExtractor.MAX_SAMPLE_DIMENSION)
        assertEquals(64 * 64, CoverToneExtractor.MAX_SAMPLES)
    }

    @Test
    fun samplingIsBoundedToAtMost4096SamplesForAnyImageSize() {
        val huge = CoverToneExtractor.sampleArgbPixels(10_000, 10_000) { _, _ -> 0xFF000000.toInt() }
        assertEquals(64 * 64, huge.size)
        val boundary = CoverToneExtractor.sampleArgbPixels(64, 64) { _, _ -> 0xFF000000.toInt() }
        assertEquals(4096, boundary.size)
        val portrait = CoverToneExtractor.sampleArgbPixels(512, 768) { _, _ -> 0xFF000000.toInt() }
        assertTrue(portrait.size <= 4096)
    }

    @Test
    fun samplingIsDeterministicForIdenticalInput() {
        val first = CoverToneExtractor.sampleArgbPixels(512, 768) { x, y -> (x * 31 + y * 17) or 0xFF000000.toInt() }
        val second = CoverToneExtractor.sampleArgbPixels(512, 768) { x, y -> (x * 31 + y * 17) or 0xFF000000.toInt() }
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun samplingCoversEveryPixelForImagesNoLargerThan64() {
        val pixels =
            CoverToneExtractor.sampleArgbPixels(8, 8) { x, y ->
                if (x == 7 && y == 7) 0xFFFF0000.toInt() else 0xFF000000.toInt()
            }
        assertEquals(64, pixels.size)
        assertEquals(0xFFFF0000.toInt(), pixels.last())
    }

    @Test
    fun samplingUsesAStableStrideLatticeForLargeImages() {
        val stride = 12
        val onLattice =
            CoverToneExtractor.sampleArgbPixels(512, 768) { x, y ->
                if (x % stride == 0 && y % stride == 0) 0xFFFF0000.toInt() else 0xFF000000.toInt()
            }
        assertEquals(2_752, onLattice.size)
        assertTrue(onLattice.all { it == 0xFFFF0000.toInt() })

        val offLattice =
            CoverToneExtractor.sampleArgbPixels(512, 768) { x, y ->
                if (x == 5 && y == 5) 0xFFFF0000.toInt() else 0xFF000000.toInt()
            }
        assertTrue(offLattice.none { it == 0xFFFF0000.toInt() })
    }

    @Test
    fun sharedSamplerUsesAtMost4096BoundedPixelReadsAndIncludesImageEdges() {
        val reads = mutableListOf<PixelRead>()
        val reader: (Int, Int, Int, Int) -> Int = { x, y, width, height ->
            reads += PixelRead(x, y, width, height)
            0xFF000000.toInt() or (x and 0xFFFF)
        }
        val sampler =
            CoverToneExtractor::class.java.declaredMethods.firstOrNull { method ->
                method.name.startsWith("sampleArgbPixels") &&
                    method.parameterTypes.size == 3 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    method.parameterTypes[2].isInstance(reader)
            }

        assertNotNull(
            "CoverToneExtractor must expose sampleArgbPixels(width, height, 1x1 pixel reader)",
            sampler,
        )
        sampler!!.isAccessible = true
        val samples =
            sampler.invoke(
                CoverToneExtractor,
                1_000_000,
                2_000_000,
                reader,
            ) as IntArray

        assertEquals(reads.size, samples.size)
        assertTrue(reads.size <= 4_096)
        assertTrue(samples.size <= 4_096)
        assertTrue(reads.all { it.width == 1 && it.height == 1 })
        assertTrue(reads.all { it.x in 0..999_999 && it.y in 0..1_999_999 })
        assertEquals(0, reads.minOf(PixelRead::x))
        assertEquals(999_999, reads.maxOf(PixelRead::x))
        assertEquals(0, reads.minOf(PixelRead::y))
        assertEquals(1_999_999, reads.maxOf(PixelRead::y))
        assertEquals(PixelRead(0, 0, 1, 1), reads.first())
        assertEquals(PixelRead(999_999, 1_999_999, 1, 1), reads.last())
    }

    @Test
    fun toneCacheDoesNotHoldItsLockWhileBitmapPixelsAreRead() {
        val firstReadStarted = CountDownLatch(1)
        val releaseFirstRead = CountDownLatch(1)
        val secondReadStarted = CountDownLatch(1)
        val firstFailure = AtomicReference<Throwable?>()
        val secondFailure = AtomicReference<Throwable?>()
        val firstBitmap =
            TestImageBitmap(argb = 0xFF336699.toInt()) { readCount ->
                if (readCount == 1) {
                    firstReadStarted.countDown()
                    check(releaseFirstRead.await(3, TimeUnit.SECONDS))
                }
            }
        val secondBitmap =
            TestImageBitmap(argb = 0xFF669933.toInt()) { readCount ->
                if (readCount == 1) secondReadStarted.countDown()
            }
        val firstThread =
            thread(start = false, name = "tone-cache-first") {
                runCatching {
                    CoverToneExtractor.toneFor("lock-granularity-first", firstBitmap)
                }.exceptionOrNull()?.let(firstFailure::set)
            }
        val secondThread =
            thread(start = false, name = "tone-cache-second") {
                runCatching {
                    CoverToneExtractor.toneFor("lock-granularity-second", secondBitmap)
                }.exceptionOrNull()?.let(secondFailure::set)
            }

        firstThread.start()
        try {
            assertTrue("first bitmap never reached pixel reading", firstReadStarted.await(1, TimeUnit.SECONDS))
            secondThread.start()
            assertTrue(
                "a blocked computation for one key must not serialize another key's pixel reads",
                secondReadStarted.await(1, TimeUnit.SECONDS),
            )
        } finally {
            releaseFirstRead.countDown()
            firstThread.join(3_000L)
            secondThread.join(3_000L)
        }

        assertFalse(firstThread.isAlive)
        assertFalse(secondThread.isAlive)
        assertNull(firstFailure.get())
        assertNull(secondFailure.get())
    }

    @Test
    fun toneCacheDoesNotInsertFallbackOrFailedComputations() {
        val fallbackKey = "uncached-computation-fallback"
        val fallback =
            CoverToneExtractor.toneFor(
                cacheKey = fallbackKey,
                bitmap = TestImageBitmap(argb = 0x00000000),
                fallbackArgb = 0xFF112233.toInt(),
            )
        val afterFallback =
            CoverToneExtractor.toneFor(
                cacheKey = fallbackKey,
                bitmap = TestImageBitmap(argb = 0xFF336699.toInt()),
                fallbackArgb = 0xFF445566.toInt(),
            )

        assertTrue(fallback.usedFallback)
        assertEquals(0xFF112233.toInt(), fallback.argb)
        assertFalse(afterFallback.usedFallback)

        val failedKey = "uncached-computation-error"
        val failure =
            runCatching {
                CoverToneExtractor.toneFor(
                    cacheKey = failedKey,
                    bitmap =
                        TestImageBitmap(
                            argb = 0xFF000000.toInt(),
                            failure = IllegalStateException("pixel read failed"),
                        ),
                )
            }.exceptionOrNull()
        val afterFailure =
            CoverToneExtractor.toneFor(
                cacheKey = failedKey,
                bitmap = TestImageBitmap(argb = 0xFF663399.toInt()),
            )

        assertNotNull(failure)
        assertFalse(afterFailure.usedFallback)
    }

    @Test
    fun toneCacheServesCachedToneWithoutRecomputingForSameKey() {
        val reads = AtomicInteger(0)
        val bitmap = TestImageBitmap(argb = 0xFF336699.toInt()) { reads.incrementAndGet() }
        val key = "cached-tone-hit|HERO|128x128"

        val first = CoverToneExtractor.toneFor(key, bitmap)
        val readsAfterFirst = reads.get()
        val second = CoverToneExtractor.toneFor(key, bitmap)

        assertFalse(first.usedFallback)
        assertEquals(first, second)
        assertTrue("first lookup must read bitmap pixels", readsAfterFirst > 0)
        assertEquals(
            "a cache hit for the same key must not read bitmap pixels again",
            readsAfterFirst,
            reads.get(),
        )
    }

    @Test
    fun sameKeyConcurrentMissesAllComputeAndKeepCacheUsable() {
        val bitmap = TestImageBitmap(argb = 0xFF336699.toInt())
        val key = "same-key-concurrent|HERO|128x128"
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val tones = java.util.Collections.synchronizedList(mutableListOf<CoverTone>())
        val workers =
            (1..8).map { index ->
                thread(name = "tone-same-key-$index") {
                    try {
                        tones += CoverToneExtractor.toneFor(key, bitmap)
                    } catch (failure: Throwable) {
                        failures += failure
                    }
                }
            }
        workers.forEach { it.join(5_000L) }

        assertTrue("no worker may fail while computing the same key", failures.isEmpty())
        assertEquals(8, tones.size)
        assertTrue(tones.all { !it.usedFallback })

        val reads = AtomicInteger(0)
        val fresh = TestImageBitmap(argb = 0xFF336699.toInt()) { reads.incrementAndGet() }
        val cached = CoverToneExtractor.toneFor(key, fresh)
        assertFalse(cached.usedFallback)
        assertEquals(0, reads.get())
    }

    @Test
    fun toneCacheEvictsOldestEntryAndKeepsRecentEntryCached() {
        val reads = AtomicInteger(0)
        val bitmaps =
            (1..65).map { index ->
                TestImageBitmap(argb = 0xFF112233.toInt() + index) { reads.incrementAndGet() }
            }
        bitmaps.forEachIndexed { index, bitmap ->
            val tone = CoverToneExtractor.toneFor("lru-key-$index", bitmap)
            assertFalse(tone.usedFallback)
        }
        val readsBeforeRequery = reads.get()

        CoverToneExtractor.toneFor("lru-key-0", bitmaps[0])
        CoverToneExtractor.toneFor("lru-key-64", bitmaps[64])

        assertEquals(
            "the oldest key must have been evicted and recompute exactly once",
            readsBeforeRequery + 4096,
            reads.get(),
        )
    }

    private data class PixelRead(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    )

    private class TestImageBitmap(
        private val argb: Int,
        private val failure: RuntimeException? = null,
        private val onRead: (Int) -> Unit = {},
    ) : ImageBitmap {
        private val readCount = AtomicInteger(0)

        override val width: Int = 128
        override val height: Int = 128
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha: Boolean = true
        override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888

        override fun readPixels(
            buffer: IntArray,
            startX: Int,
            startY: Int,
            width: Int,
            height: Int,
            bufferOffset: Int,
            stride: Int,
        ) {
            failure?.let { throw it }
            onRead(readCount.incrementAndGet())
            buffer[bufferOffset] = argb
        }

        override fun prepareToDraw() = Unit
    }

    private fun redOf(argb: Int): Int = (argb ushr 16) and 0xFF

    private fun greenOf(argb: Int): Int = (argb ushr 8) and 0xFF

    private fun blueOf(argb: Int): Int = argb and 0xFF

    private fun valueOf(argb: Int): Float = maxOf(redOf(argb), greenOf(argb), blueOf(argb)) / 255f

    private fun saturationOf(argb: Int): Float {
        val max = maxOf(redOf(argb), greenOf(argb), blueOf(argb))
        val min = minOf(redOf(argb), greenOf(argb), blueOf(argb))
        return if (max == 0) 0f else (max - min).toFloat() / max
    }

    private fun hueOf(argb: Int): Float {
        val r = redOf(argb) / 255f
        val g = greenOf(argb) / 255f
        val b = blueOf(argb) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        return when {
            delta <= .0001f -> 0f
            max == r -> (60f * ((g - b) / delta)).let { if (it < 0f) it + 360f else it }
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
    }
}
