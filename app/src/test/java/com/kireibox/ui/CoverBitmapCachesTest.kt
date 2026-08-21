package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class CoverBitmapCachesTest {
    @Test
    fun cacheBudgetsInclude24MiBForSharpLibraryCards() {
        assertEquals(8 * 1024, CoverBitmapCaches.ORDINARY_KIB)
        assertEquals(24 * 1024, CoverBitmapCaches.LIBRARY_CARD_KIB)
        assertEquals(20 * 1024, CoverBitmapCaches.HERO_KIB)
        assertEquals(12 * 1024, CoverBitmapCaches.BACKDROP_KIB)
    }

    @Test
    fun totalBitmapBudgetIs64MiB() {
        assertEquals(64 * 1024, CoverBitmapCaches.TOTAL_KIB)
        assertEquals(
            CoverBitmapCaches.ORDINARY_KIB + CoverBitmapCaches.LIBRARY_CARD_KIB +
                CoverBitmapCaches.HERO_KIB + CoverBitmapCaches.BACKDROP_KIB,
            CoverBitmapCaches.TOTAL_KIB,
        )
    }

    @Test
    fun toneCacheHasABoundedDefaultCapacity() {
        assertEquals(64, CoverBitmapCaches.TONE_CACHE_CAPACITY)
    }

    @Test
    fun toneCacheReturnsSameToneForSameKeyWithoutRecompute() {
        val cache = CoverToneCache()
        var computes = 0
        val tone = cache.getOrCompute("cover|HERO|1024x1536") {
            computes++
            CoverTone(0xFF5B5AC8.toInt(), .4f, .3f, false)
        }
        val again = cache.getOrCompute("cover|HERO|1024x1536") {
            computes++
            CoverTone(0xFF5B5AC8.toInt(), .4f, .3f, false)
        }
        assertEquals(1, computes)
        assertEquals(tone, again)
    }

    @Test
    fun toneCacheRecomputesWhenKeyChanges() {
        val cache = CoverToneCache()
        var computes = 0
        cache.getOrCompute("cover|A|256x384") {
            computes++
            CoverTone(1, 0f, 0f, true)
        }
        cache.getOrCompute("cover|B|256x384") {
            computes++
            CoverTone(2, 0f, 0f, true)
        }
        assertEquals(2, computes)
    }

    @Test
    fun toneCacheEvictsLeastRecentlyUsedWhenBounded() {
        val cache = CoverToneCache(capacity = 2)
        var computes = 0
        fun tone(argb: Int) = CoverTone(argb, .3f, .3f, false)
        cache.getOrCompute("a") { computes++; tone(1) }
        cache.getOrCompute("b") { computes++; tone(2) }
        cache.getOrCompute("a") { computes++; tone(1) }
        cache.getOrCompute("c") { computes++; tone(3) }
        cache.getOrCompute("a") { computes++; tone(1) }
        assertEquals(3, computes)
        cache.getOrCompute("b") { computes++; tone(2) }
        assertEquals(4, computes)
    }

    @Test
    fun slowKeyComputationDoesNotBlockAnotherKey() {
        val cache = CoverToneCache()
        val slowStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val slowFinished = CountDownLatch(1)
        val slowTone = CoverTone(0xFF111111.toInt(), .1f, .1f, false)
        val fastTone = CoverTone(0xFF222222.toInt(), .2f, .2f, false)

        val slowThread = Thread {
            cache.getOrCompute("slow") {
                slowStarted.countDown()
                releaseSlow.await(10, TimeUnit.SECONDS)
                slowTone
            }
            slowFinished.countDown()
        }
        slowThread.start()
        assertTrue(
            "slow compute must start before the fast key is requested",
            slowStarted.await(5, TimeUnit.SECONDS),
        )

        val executor = Executors.newSingleThreadExecutor()
        var fast: CoverTone? = null
        try {
            val fastFuture = executor.submit<CoverTone> {
                cache.getOrCompute("fast") { fastTone }
            }
            fast = fastFuture.get(2, TimeUnit.SECONDS)
            // Deterministic: releaseSlow has not been counted down yet, so the
            // slow computation cannot have finished before the fast key returned.
            assertFalse(
                "slow computation finished before the fast key returned; " +
                    "compute must not hold the cache lock",
                slowFinished.await(0, TimeUnit.MILLISECONDS),
            )
        } catch (e: TimeoutException) {
            fail("fast key blocked behind a slow key computation; compute must not hold the cache lock")
        } finally {
            releaseSlow.countDown()
            slowThread.join(10_000)
            executor.shutdownNow()
        }

        assertNotNull(fast)
        assertEquals(fastTone, fast)
        assertEquals(slowTone, cache.getOrCompute("slow") { CoverTone(-1, 0f, 0f, false) })
    }

    @Test
    fun concurrentMissesForSameKeyAllSucceedAndCacheRemainsUsable() {
        val cache = CoverToneCache()
        val start = CountDownLatch(1)
        val expected = CoverTone(0xFF33AA33.toInt(), .5f, .5f, false)
        val results = CopyOnWriteArrayList<CoverTone>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val workers = (0 until 8).map {
            Thread {
                start.await()
                try {
                    results.add(
                        cache.getOrCompute("same") {
                            Thread.sleep(50)
                            expected
                        },
                    )
                } catch (t: Throwable) {
                    failures.add(t)
                }
            }
        }
        workers.forEach { it.start() }
        start.countDown()
        workers.forEach { it.join(10_000) }

        assertTrue("no concurrent computation may fail: ${failures.joinToString()}", failures.isEmpty())
        assertEquals(8, results.size)
        assertTrue("every caller must receive the computed tone", results.all { it == expected })

        var computes = 0
        val cached = cache.getOrCompute("same") {
            computes++
            CoverTone(-1, 0f, 0f, false)
        }
        assertEquals(expected, cached)
        assertEquals("completed computation must be cached and reused", 0, computes)
    }
}
