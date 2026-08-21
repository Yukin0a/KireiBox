package com.kireibox.ui

import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveToneWiringIntegrationTest {
    @Test
    fun toneWorkUsesTheBackgroundDispatcherAndTheExistingBitmapCacheKey() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "immersive-tone-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var observedThread: String? = null
            var observedKey: String? = null
            val expected = CoverTone(0xFF112233.toInt(), .31f, .42f, usedFallback = false)

            val actual = computeImmersiveTone(
                cacheKey = "game-42|IMMERSIVE_BACKDROP|1080x1080",
                bitmap = null,
                dispatcher = dispatcher,
            ) { key, _ ->
                observedThread = Thread.currentThread().name
                observedKey = key
                expected
            }

            assertEquals(expected, actual)
            assertEquals("game-42|IMMERSIVE_BACKDROP|1080x1080", observedKey)
            assertTrue(observedThread.orEmpty().startsWith("immersive-tone-test"))
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun cancellingTheOwnerCancelsPendingToneWorkBeforeItCanPublish() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = Job()
        val started = CompletableDeferred<Unit>()
        var published = false

        val job = CoroutineScope(owner + dispatcher).launch {
            try {
                computeImmersiveTone(
                    cacheKey = "old-page",
                    bitmap = null,
                    dispatcher = dispatcher,
                ) { _, _ ->
                    started.complete(Unit)
                    awaitCancellation()
                }
                published = true
            } catch (_: CancellationException) {
                // Cancellation is the expected lifecycle result.
            }
        }

        runCurrent()
        assertTrue(started.isCompleted)
        owner.cancel()
        runCurrent()
        job.join()

        assertFalse(published)
    }
}
