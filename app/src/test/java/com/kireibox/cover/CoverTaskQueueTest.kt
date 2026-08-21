package com.kireibox.cover

import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.RankedCoverSearchResult
import com.yuki.yukihub.metadata.VnMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class CoverTaskQueueTest {
    @Test
    fun enqueueDeduplicatesByGameIdWhileTheFirstTaskIsActive() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)

        queue.enqueue(request(7))
        queue.enqueue(request(7))
        runCurrent()

        assertEquals(listOf(7L), queue.state.value.tasks.map { it.gameId })
        assertEquals(CoverTaskStatus.SEARCHING, queue.state.value.tasks.single().status)
    }

    @Test
    fun schedulerLimitsConcurrentSearchesToThreeAndDownloadsToTwo() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)

        (1L..5L).forEach { queue.enqueue(request(it)) }
        runCurrent()
        assertEquals(3, queue.state.value.tasks.count { it.status == CoverTaskStatus.SEARCHING })

        (1L..3L).forEach { backend.completeSearch(it, automatic = true) }
        runCurrent()

        assertEquals(2, queue.state.value.tasks.count { it.status == CoverTaskStatus.DOWNLOADING })
        assertTrue(queue.state.value.tasks.count { it.status == CoverTaskStatus.SEARCHING } <= 3)
        assertEquals(2, backend.saveRequests.size)
    }

    @Test
    fun pauseDoesNotStartQueuedTasksButLetsActiveTaskFinishThenResumeDispatches() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)

        queue.enqueue(request(1))
        runCurrent()
        queue.pause()
        queue.enqueue(request(2))
        backend.completeSearch(1, automatic = true)
        runCurrent()
        backend.completeSave(1)
        runCurrent()

        assertEquals(CoverQueueStatus.PAUSED, queue.state.value.queueStatus)
        assertEquals(CoverTaskStatus.COMPLETED, queue.state.value.tasks.single { it.gameId == 1L }.status)
        assertEquals(CoverTaskStatus.QUEUED, queue.state.value.tasks.single { it.gameId == 2L }.status)
        assertEquals(listOf(1L), backend.searchRequests)

        queue.resume()
        runCurrent()

        assertEquals(CoverTaskStatus.SEARCHING, queue.state.value.tasks.single { it.gameId == 2L }.status)
    }

    @Test
    fun lowConfidenceSearchNeedsReviewAndCachesCandidates() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(3))
        runCurrent()

        backend.completeSearch(3, automatic = false, candidates = candidates(51.0, 44.0))
        runCurrent()

        val task = queue.state.value.tasks.single()
        assertEquals(CoverTaskStatus.NEEDS_REVIEW, task.status)
        assertEquals(2, task.candidateCount)
        assertEquals(listOf(51.0, 44.0), queue.candidates(3).map { it.score })
        assertEquals(BindingCall(3, "REVIEW", 51.0), backend.bindingCalls.single())
    }

    @Test
    fun resolveReviewCompletesOnlyReviewRowsWithoutWritingAnAutoBinding() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(30))
        runCurrent()
        backend.completeSearch(30, automatic = false, candidates = candidates(51.0))
        runCurrent()

        queue.resolveReview(30)
        runCurrent()
        queue.resolveReview(999)
        runCurrent()

        assertEquals(CoverTaskStatus.COMPLETED, queue.state.value.tasks.single().status)
        assertEquals(listOf(BindingCall(30, "REVIEW", 51.0)), backend.bindingCalls)
    }

    @Test
    fun bindingCompletionEmitsOnlyAfterSlowPersistenceReturns() = runTest {
        val backend = SlowBindingBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        val events = mutableListOf<CoverBindingCompletion>()
        val collector = backgroundScope.launch { queue.bindingCompletions.collect { events += it } }
        runCurrent()

        queue.enqueue(request(31))
        runCurrent()
        backend.awaitBindingStarted(31, "REVIEW")

        assertTrue(events.isEmpty())

        backend.completeBinding(31, "REVIEW")
        runCurrent()

        assertEquals(listOf(CoverBindingCompletion(31, "REVIEW", true)), events)
        collector.cancel()
    }

    @Test
    fun cancelAfterSlowReviewPersistsDisabledLastForTheSameGame() = runTest {
        val backend = SlowBindingBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        val events = mutableListOf<CoverBindingCompletion>()
        val collector = backgroundScope.launch { queue.bindingCompletions.collect { events += it } }
        runCurrent()

        queue.enqueue(request(32))
        runCurrent()
        backend.awaitBindingStarted(32, "REVIEW")

        queue.cancel(32)
        runCurrent()
        assertEquals(listOf(BindingCall(32, "REVIEW", 51.0)), backend.bindingCalls)

        backend.completeBinding(32, "REVIEW")
        backend.awaitBindingStarted(32, "DISABLED")
        backend.completeBinding(32, "DISABLED")
        runCurrent()

        assertEquals("DISABLED", backend.persistedBindings.last().mode)
        assertEquals(
            listOf(
                CoverBindingCompletion(32, "REVIEW", true),
                CoverBindingCompletion(32, "DISABLED", true),
            ),
            events,
        )
        collector.cancel()
    }

    @Test
    fun automaticSaveSuccessAndFailureRecordTheirBindingModes() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(4))
        queue.enqueue(request(5))
        runCurrent()
        backend.completeSearch(4, automatic = true, candidates = candidates(91.0))
        backend.completeSearch(5, automatic = true, candidates = candidates(82.0))
        runCurrent()

        backend.completeSave(4, Result.success("saved"))
        backend.completeSave(5, Result.failure(IllegalStateException("disk full")))
        runCurrent()

        assertEquals(CoverTaskStatus.COMPLETED, queue.state.value.tasks.single { it.gameId == 4L }.status)
        assertEquals(CoverTaskStatus.FAILED, queue.state.value.tasks.single { it.gameId == 5L }.status)
        assertTrue(backend.bindingCalls.contains(BindingCall(4, "AUTO", 91.0)))
        assertTrue(backend.bindingCalls.contains(BindingCall(5, "RETRY", 82.0)))
    }

    @Test
    fun cancellingActiveTaskPreventsLateBackendResultFromOverwritingCancelledState() = runTest {
        val backend = FakeCoverTaskBackend(ignoreSearchCancellation = true)
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(6))
        runCurrent()

        queue.cancel(6)
        backend.completeSearch(6, automatic = true)
        runCurrent()

        assertEquals(CoverTaskStatus.CANCELLED, queue.state.value.tasks.single().status)
        assertEquals(BindingCall(6, "DISABLED", 0.0), backend.bindingCalls.single())
    }

    @Test
    fun retryRequeuesOnlyTerminalFailuresAndDismissRemovesOnlyCompletedAndCancelledRows() = runTest {
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(7))
        queue.enqueue(request(8))
        queue.enqueue(request(9))
        runCurrent()
        backend.completeSearch(7, automatic = true)
        backend.completeSearch(8, automatic = false, candidates = candidates(30.0))
        backend.completeSearch(9, automatic = true)
        runCurrent()
        backend.completeSave(7, Result.failure(IllegalArgumentException("network")))
        backend.completeSave(9)
        runCurrent()
        queue.cancel(8)

        queue.retry(7)
        queue.retry(9)
        queue.dismissSessionRows()

        assertEquals(listOf(7L), queue.state.value.tasks.map { it.gameId })
        assertEquals(CoverTaskStatus.SEARCHING, queue.state.value.tasks.single().status)
    }

    @Test
    fun concurrentDefaultEnqueueForOneGameStartsExactlyOneSearch() = runBlocking {
        val owner = SupervisorJob()
        val backend = ThreadedBackend()
        val queue = CoverTaskQueue(CoroutineScope(owner + Dispatchers.Default), backend)
        try {
            coroutineScope {
                List(256) { async(Dispatchers.Default) { queue.enqueue(request(90)) } }.awaitAll()
            }
            awaitUntil { backend.searchRequests.isNotEmpty() }

            assertEquals(listOf(90L), queue.state.value.tasks.map { it.gameId })
            assertEquals(1, backend.searchRequests.count { it == 90L })
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun concurrentDefaultSearchCompletionsNeverExceedTwoDownloads() = runBlocking {
        val owner = SupervisorJob()
        val backend = ThreadedBackend(searchCompletionBarrier = CyclicBarrier(3))
        val queue = CoverTaskQueue(CoroutineScope(owner + Dispatchers.Default), backend)
        try {
            (1L..3L).forEach { queue.enqueue(request(it)) }
            awaitUntil { backend.searchRequests.size == 3 }
            coroutineScope {
                (1L..3L).map { gameId -> async(Dispatchers.Default) { backend.completeSearch(gameId) } }.awaitAll()
            }
            awaitUntil { backend.saveRequests.size >= 2 }
            delay(100)

            assertTrue(backend.maximumSearches.get() <= 3)
            assertTrue(backend.maximumDownloads.get() <= 2)
            assertTrue(queue.state.value.tasks.count { it.status == CoverTaskStatus.DOWNLOADING } <= 2)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun cancellationReachesCooperativeSearchAndSaveAndLateSaveCallbacksKeepCancelledState() = runTest {
        val backend = CancellationProbeBackend()
        val queue = CoverTaskQueue(backgroundScope, backend)
        queue.enqueue(request(91))
        runCurrent()
        backend.awaitSearchStarted(91)

        queue.cancel(91)
        runCurrent()
        backend.awaitSearchCancelled(91)

        queue.enqueue(request(92))
        runCurrent()
        backend.awaitSearchStarted(92)
        backend.completeSearch(92)
        runCurrent()
        backend.awaitSaveStarted(92)

        queue.cancel(92)
        backend.emitLateSaveProgress(92, CoverDownloadProgress(99, 100))
        backend.completeSave(92)
        runCurrent()
        backend.awaitSaveCancelled(92)

        val cancelled = queue.state.value.tasks.single { it.gameId == 92L }
        assertEquals(CoverTaskStatus.CANCELLED, cancelled.status)
        assertEquals(0L, cancelled.bytesRead)
        assertFalse(queue.state.value.tasks.any { it.status == CoverTaskStatus.SEARCHING || it.status == CoverTaskStatus.DOWNLOADING })
    }

    @Test
    fun ownerScopeCancellationTurnsActiveRowsIntoCancelledInsteadOfLeavingStaleActiveState() = runTest {
        val owner = SupervisorJob()
        val backend = FakeCoverTaskBackend()
        val queue = CoverTaskQueue(CoroutineScope(owner + StandardTestDispatcher(testScheduler)), backend)
        queue.enqueue(request(93))
        runCurrent()

        owner.cancel()
        runCurrent()

        assertEquals(CoverTaskStatus.CANCELLED, queue.state.value.tasks.single().status)
        assertFalse(queue.state.value.hasActiveTasks)
    }

    @Test
    fun unconfinedBackendEntryAndBindingCanCoordinateConcurrentPauseWithoutHoldingQueueMonitor() = runBlocking {
        val owner = SupervisorJob()
        val backend = MonitorEscapeBackend()
        val queue = CoverTaskQueue(CoroutineScope(owner + Dispatchers.Unconfined), backend)
        backend.queue = queue
        try {
            queue.enqueue(request(94))

            assertTrue(backend.searchEnteredWithoutQueueMonitor.await())
            assertTrue(backend.bindingEnteredWithoutQueueMonitor.await())
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun concurrentDefaultUniqueEnqueuesNeverExceedThreeActiveSearches() = runBlocking {
        val owner = SupervisorJob()
        val backend = ThreadedBackend()
        val queue = CoverTaskQueue(CoroutineScope(owner + Dispatchers.Default), backend)
        try {
            coroutineScope {
                (1L..128L).map { gameId -> async(Dispatchers.Default) { queue.enqueue(request(gameId)) } }.awaitAll()
            }
            awaitUntil { backend.searchRequests.size == 3 }
            delay(100)

            assertEquals(3, backend.searchRequests.size)
            assertTrue(backend.maximumSearches.get() <= 3)
            assertTrue(queue.state.value.tasks.count { it.status == CoverTaskStatus.SEARCHING } <= 3)
        } finally {
            owner.cancel()
        }
    }

    private fun request(gameId: Long) = CoverTaskRequest(
        gameId = gameId,
        title = "Game $gameId",
        source = CoverSourceMode.VNDB,
    )

    private fun candidates(vararg scores: Double): List<RankedCoverCandidate> = scores.mapIndexed { index, score ->
        RankedCoverCandidate(VnMetadata(id = "candidate-$index", coverUrl = "https://covers.invalid/$index"), score)
    }

    private data class BindingCall(val gameId: Long, val mode: String, val score: Double)

    private suspend fun awaitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) delay(5)
        }
    }

    private class FakeCoverTaskBackend(
        private val ignoreSearchCancellation: Boolean = false,
    ) : CoverTaskBackend {
        val searchRequests = mutableListOf<Long>()
        val saveRequests = mutableListOf<Long>()
        val bindingCalls = mutableListOf<BindingCall>()
        private val searches = mutableMapOf<Long, CompletableDeferred<Result<RankedCoverSearchResult>>>()
        private val saves = mutableMapOf<Long, CompletableDeferred<Result<String>>>()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> {
            searchRequests += request.gameId
            val result = searches.getOrPut(request.gameId) { CompletableDeferred() }
            return if (ignoreSearchCancellation) withContext(NonCancellable) { result.await() } else result.await()
        }

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> {
            saveRequests += request.gameId
            return saves.getOrPut(request.gameId) { CompletableDeferred() }.await()
        }

        override suspend fun markBinding(gameId: Long, mode: String, score: Double) {
            bindingCalls += BindingCall(gameId, mode, score)
        }

        fun completeSearch(
            gameId: Long,
            automatic: Boolean,
            candidates: List<RankedCoverCandidate> = listOf(
                RankedCoverCandidate(VnMetadata(id = "candidate", coverUrl = "https://covers.invalid/default"), 100.0)
            ),
        ) {
            searches.getOrPut(gameId) { CompletableDeferred() }.complete(
                Result.success(
                    RankedCoverSearchResult(
                        candidates = candidates,
                        attemptedQueries = listOf("test"),
                        automatic = automatic,
                    )
                )
            )
        }

        fun completeSave(gameId: Long, result: Result<String> = Result.success("saved")) {
            saves.getOrPut(gameId) { CompletableDeferred() }.complete(result)
        }
    }

    private class ThreadedBackend(
        private val searchCompletionBarrier: CyclicBarrier? = null,
    ) : CoverTaskBackend {
        val searchRequests = ConcurrentLinkedQueue<Long>()
        val saveRequests = ConcurrentLinkedQueue<Long>()
        val maximumSearches = AtomicInteger()
        val maximumDownloads = AtomicInteger()
        private val searches = ConcurrentHashMap<Long, CompletableDeferred<Result<RankedCoverSearchResult>>>()
        private val saves = ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
        private val activeSearches = AtomicInteger()
        private val activeDownloads = AtomicInteger()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> {
            searchRequests += request.gameId
            val active = activeSearches.incrementAndGet()
            maximumSearches.accumulateAndGet(active, ::maxOf)
            return try {
                searches.computeIfAbsent(request.gameId) { CompletableDeferred() }.await().also {
                    searchCompletionBarrier?.await()
                }
            } finally {
                activeSearches.decrementAndGet()
            }
        }

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> {
            saveRequests += request.gameId
            val active = activeDownloads.incrementAndGet()
            maximumDownloads.accumulateAndGet(active, ::maxOf)
            return try {
                saves.computeIfAbsent(request.gameId) { CompletableDeferred() }.await()
            } finally {
                activeDownloads.decrementAndGet()
            }
        }

        override suspend fun markBinding(gameId: Long, mode: String, score: Double) = Unit

        fun completeSearch(gameId: Long) {
            searches.computeIfAbsent(gameId) { CompletableDeferred() }.complete(
                Result.success(RankedCoverSearchResult(candidates(100.0), listOf("test"), automatic = true))
            )
        }

        private fun candidates(score: Double) = listOf(
            RankedCoverCandidate(VnMetadata(id = "candidate-$score", coverUrl = "https://covers.invalid/$score"), score)
        )
    }

    private class SlowBindingBackend : CoverTaskBackend {
        val bindingCalls = mutableListOf<BindingCall>()
        val persistedBindings = mutableListOf<BindingCall>()
        private val bindingStarted = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
        private val bindingPermits = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> = Result.success(
            RankedCoverSearchResult(candidates(51.0), listOf("test"), automatic = false)
        )

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> = error("save is not expected")

        override suspend fun markBinding(gameId: Long, mode: String, score: Double) {
            val call = BindingCall(gameId, mode, score)
            bindingCalls += call
            bindingStarted.computeIfAbsent(key(gameId, mode)) { CompletableDeferred() }.complete(Unit)
            bindingPermits.computeIfAbsent(key(gameId, mode)) { CompletableDeferred() }.await()
            persistedBindings += call
        }

        suspend fun awaitBindingStarted(gameId: Long, mode: String) =
            bindingStarted.computeIfAbsent(key(gameId, mode)) { CompletableDeferred() }.await()

        fun completeBinding(gameId: Long, mode: String) {
            bindingPermits.computeIfAbsent(key(gameId, mode)) { CompletableDeferred() }.complete(Unit)
        }

        private fun candidates(score: Double) = listOf(
            RankedCoverCandidate(VnMetadata(id = "candidate-$score", coverUrl = "https://covers.invalid/$score"), score)
        )

        private fun key(gameId: Long, mode: String) = "$gameId:$mode"
    }

    private class CancellationProbeBackend : CoverTaskBackend {
        private val searches = ConcurrentHashMap<Long, CompletableDeferred<Result<RankedCoverSearchResult>>>()
        private val saves = ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
        private val searchStarted = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val saveStarted = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val searchCancelled = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val saveCancelled = ConcurrentHashMap<Long, CompletableDeferred<Unit>>()
        private val progress = ConcurrentHashMap<Long, AtomicReference<(CoverDownloadProgress) -> Unit>>()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> {
            searchStarted.computeIfAbsent(request.gameId) { CompletableDeferred() }.complete(Unit)
            return try {
                searches.computeIfAbsent(request.gameId) { CompletableDeferred() }.await()
            } catch (error: CancellationException) {
                searchCancelled.computeIfAbsent(request.gameId) { CompletableDeferred() }.complete(Unit)
                throw error
            }
        }

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> {
            progress.computeIfAbsent(request.gameId) { AtomicReference() }.set(onProgress)
            saveStarted.computeIfAbsent(request.gameId) { CompletableDeferred() }.complete(Unit)
            return try {
                saves.computeIfAbsent(request.gameId) { CompletableDeferred() }.await()
            } catch (error: CancellationException) {
                saveCancelled.computeIfAbsent(request.gameId) { CompletableDeferred() }.complete(Unit)
                throw error
            }
        }

        override suspend fun markBinding(gameId: Long, mode: String, score: Double) = Unit

        suspend fun awaitSearchStarted(gameId: Long) = searchStarted.computeIfAbsent(gameId) { CompletableDeferred() }.await()
        suspend fun awaitSaveStarted(gameId: Long) = saveStarted.computeIfAbsent(gameId) { CompletableDeferred() }.await()
        suspend fun awaitSearchCancelled(gameId: Long) = searchCancelled.computeIfAbsent(gameId) { CompletableDeferred() }.await()
        suspend fun awaitSaveCancelled(gameId: Long) = saveCancelled.computeIfAbsent(gameId) { CompletableDeferred() }.await()

        fun completeSearch(gameId: Long) {
            searches.computeIfAbsent(gameId) { CompletableDeferred() }.complete(
                Result.success(RankedCoverSearchResult(candidates = candidates(), attemptedQueries = listOf("test"), automatic = true))
            )
        }

        fun completeSave(gameId: Long) {
            saves.computeIfAbsent(gameId) { CompletableDeferred() }.complete(Result.success("saved"))
        }

        fun emitLateSaveProgress(gameId: Long, update: CoverDownloadProgress) {
            progress[gameId]?.get()?.invoke(update)
        }

        private fun candidates() = listOf(
            RankedCoverCandidate(VnMetadata(id = "candidate", coverUrl = "https://covers.invalid/candidate"), 100.0)
        )
    }

    private class MonitorEscapeBackend : CoverTaskBackend {
        lateinit var queue: CoverTaskQueue
        val searchEnteredWithoutQueueMonitor = CompletableDeferred<Boolean>()
        val bindingEnteredWithoutQueueMonitor = CompletableDeferred<Boolean>()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> {
            searchEnteredWithoutQueueMonitor.complete(canAnotherThreadPause())
            return Result.failure(IllegalStateException("probe search failure"))
        }

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> = error("save is not expected")

        override suspend fun markBinding(gameId: Long, mode: String, score: Double) {
            bindingEnteredWithoutQueueMonitor.complete(canAnotherThreadPause())
        }

        private fun canAnotherThreadPause(): Boolean {
            val paused = CountDownLatch(1)
            Thread {
                queue.pause()
                paused.countDown()
            }.start()
            return paused.await(500, TimeUnit.MILLISECONDS)
        }
    }
}
