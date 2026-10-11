package com.kireibox.cover

import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.RankedCoverSearchResult
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the persistent no-match hook: only background automatic tasks that end without an
 * auto-bindable candidate may feed the dedup store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoverTaskQueueNoMatchTest {
    @Test
    fun automaticSearchWithNoCandidatesRecordsNoMatch() =
        runTest {
            val backend = NoMatchBackend()
            val noMatches = mutableListOf<CoverTaskRequest>()
            val queue = CoverTaskQueue(backgroundScope, backend) { noMatches += it }

            queue.enqueue(request(21, automatic = true))
            runCurrent()
            backend.completeSearch(21, automatic = true, candidates = emptyList())
            runCurrent()

            assertEquals(listOf(21L), noMatches.map { it.gameId })
        }

    @Test
    fun automaticSearchWithOnlyNonBindableHintRecordsNoMatch() =
        runTest {
            val backend = NoMatchBackend()
            val noMatches = mutableListOf<CoverTaskRequest>()
            val queue = CoverTaskQueue(backgroundScope, backend) { noMatches += it }

            queue.enqueue(request(22, automatic = true))
            runCurrent()
            backend.completeSearch(22, automatic = false, candidates = candidates(51.0))
            runCurrent()

            assertEquals(listOf(22L), noMatches.map { it.gameId })
        }

    @Test
    fun automaticSearchWithAutoBindableResultRecordsNoMatch() =
        runTest {
            val backend = NoMatchBackend()
            val noMatches = mutableListOf<CoverTaskRequest>()
            val queue = CoverTaskQueue(backgroundScope, backend) { noMatches += it }

            queue.enqueue(request(24, automatic = true))
            runCurrent()
            backend.completeSearch(24, automatic = true)
            runCurrent()

            assertTrue(noMatches.isEmpty())
        }

    @Test
    fun userOriginatedSearchNeverRecordsNoMatch() =
        runTest {
            val backend = NoMatchBackend()
            val noMatches = mutableListOf<CoverTaskRequest>()
            val queue = CoverTaskQueue(backgroundScope, backend) { noMatches += it }

            queue.enqueue(request(23, automatic = false))
            runCurrent()
            backend.completeSearch(23, automatic = false, candidates = candidates(51.0))
            runCurrent()

            assertTrue(noMatches.isEmpty())
        }

    private fun request(
        gameId: Long,
        automatic: Boolean,
    ) = CoverTaskRequest(
        gameId = gameId,
        title = "Game $gameId",
        source = CoverSourceMode.VNDB,
        automaticSearch = automatic,
    )

    private fun candidates(vararg scores: Double): List<RankedCoverCandidate> =
        scores.mapIndexed { index, score ->
            RankedCoverCandidate(VnMetadata(id = "candidate-$index", coverUrl = "https://covers.invalid/$index"), score)
        }

    private class NoMatchBackend : CoverTaskBackend {
        private val searches = mutableMapOf<Long, CompletableDeferred<Result<RankedCoverSearchResult>>>()

        override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> = searches.getOrPut(request.gameId) { CompletableDeferred() }.await()

        override suspend fun save(
            request: CoverTaskRequest,
            candidate: RankedCoverCandidate,
            onProgress: (CoverDownloadProgress) -> Unit,
        ): Result<String> = Result.success("saved")

        override suspend fun markBinding(
            gameId: Long,
            mode: String,
            score: Double
        ) = Unit

        fun completeSearch(
            gameId: Long,
            automatic: Boolean,
            candidates: List<RankedCoverCandidate> =
                listOf(
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
    }
}
