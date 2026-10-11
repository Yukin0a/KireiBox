package com.kireibox.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLoadCoordinatorTest {
    @Test
    fun `concurrent requests for one cache key decode only once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val cache = mutableMapOf<String, String>()
            var decodeCalls = 0
            val coordinator = CoverLoadCoordinator<String, String>(this, cache::get, cache::put)

            val first =
                async {
                    coordinator.load("hero") {
                        decodeCalls += 1
                        gate.await()
                        "bitmap"
                    }
                }
            runCurrent()
            val second =
                async {
                    coordinator.load("hero") {
                        decodeCalls += 1
                        "duplicate"
                    }
                }
            runCurrent()

            assertEquals(1, decodeCalls)
            gate.complete(Unit)
            assertEquals("bitmap", first.await())
            assertEquals("bitmap", second.await())
            assertEquals("bitmap", cache["hero"])
        }

    @Test
    fun `cancelling the last request cancels decoder and never caches its bitmap`() =
        runTest {
            val decodeCancelled = CompletableDeferred<Unit>()
            val cache = mutableMapOf<String, String>()
            val coordinator = CoverLoadCoordinator<String, String>(this, cache::get, cache::put)

            val pending =
                async {
                    coordinator.load("hero") {
                        try {
                            CompletableDeferred<Unit>().await()
                            "stale"
                        } finally {
                            decodeCancelled.complete(Unit)
                        }
                    }
                }
            runCurrent()
            pending.cancelAndJoin()
            runCurrent()

            assertTrue(decodeCancelled.isCompleted)
            assertFalse(cache.containsKey("hero"))
        }

    @Test
    fun `a cancelled caller still releases its in flight decoder`() =
        runBlocking {
            val decoderCancelled = CompletableDeferred<Unit>()
            val decoderStarted = CompletableDeferred<Unit>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val coordinator = CoverLoadCoordinator<String, String>(scope, { null }) { _, _ -> }
            try {
                val caller =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        coordinator.load("hero") {
                            try {
                                decoderStarted.complete(Unit)
                                CompletableDeferred<Unit>().await()
                                "stale"
                            } finally {
                                decoderCancelled.complete(Unit)
                            }
                        }
                    }
                decoderStarted.await()
                caller.cancelAndJoin()
                withTimeout(1_000) { decoderCancelled.await() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `cancelling one shared consumer leaves its decoder alive for the other`() =
        runBlocking {
            val decoderStarted = CompletableDeferred<Unit>()
            val releaseDecoder = CompletableDeferred<Unit>()
            val decoderCancelled = CompletableDeferred<Unit>()
            val scope = CoroutineScope(Dispatchers.IO)
            val coordinator = CoverLoadCoordinator<String, String>(scope, { null }) { _, _ -> }
            try {
                val first =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        coordinator.load("hero") {
                            try {
                                decoderStarted.complete(Unit)
                                releaseDecoder.await()
                                "bitmap"
                            } finally {
                                decoderCancelled.complete(Unit)
                            }
                        }
                    }
                decoderStarted.await()
                val second =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        coordinator.load("hero") { "duplicate" }
                    }
                first.cancelAndJoin()
                assertFalse(decoderCancelled.isCompleted)
                releaseDecoder.complete(Unit)
                assertEquals("bitmap", second.await())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `null decode leaves no completed flight so the same key retries`() =
        runBlocking {
            val cache = mutableMapOf<String, String>()
            val scope = CoroutineScope(Dispatchers.IO)
            val coordinator = CoverLoadCoordinator<String, String>(scope, cache::get, cache::put)
            var decodeCalls = 0
            try {
                assertEquals(
                    null,
                    coordinator.load("hero") {
                        decodeCalls += 1
                        null
                    }
                )
                assertEquals(
                    null,
                    coordinator.load("hero") {
                        decodeCalls += 1
                        null
                    }
                )
                assertEquals(2, decodeCalls)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `failed decode leaves no completed flight so the same key retries`() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val coordinator = CoverLoadCoordinator<String, String>(scope, { null }) { _, _ -> }
            var decodeCalls = 0
            try {
                repeat(2) {
                    val failure =
                        runCatching {
                            coordinator.load("hero") {
                                decodeCalls += 1
                                error("temporary failure")
                            }
                        }.exceptionOrNull()
                    assertEquals("temporary failure", failure?.message)
                }
                assertEquals(2, decodeCalls)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `successful decode is served from cache without another decode`() =
        runBlocking {
            val cache = mutableMapOf<String, String>()
            val scope = CoroutineScope(Dispatchers.IO)
            val coordinator = CoverLoadCoordinator<String, String>(scope, cache::get, cache::put)
            var decodeCalls = 0
            try {
                assertEquals(
                    "bitmap",
                    coordinator.load("hero") {
                        decodeCalls += 1
                        "bitmap"
                    }
                )
                assertEquals(
                    "bitmap",
                    coordinator.load("hero") {
                        decodeCalls += 1
                        "duplicate"
                    }
                )
                assertEquals(1, decodeCalls)
            } finally {
                scope.cancel()
            }
        }
}
