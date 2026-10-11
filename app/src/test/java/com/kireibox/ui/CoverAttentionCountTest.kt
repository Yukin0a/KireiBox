package com.kireibox.ui

import com.kireibox.cover.CoverQueueStatus
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskStatus
import com.kireibox.cover.CoverTaskUi
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 设置 red dot must include failed automatic cover matches, not just covers awaiting review:
 * on a blocked/anonymous network the common end state is a batch of FAILED tasks, which previously
 * left settings with no indicator at all.
 */
class CoverAttentionCountTest {
    @Test
    fun countsReviewAndFailedTasksButNotSuccessfulOrRunningOnes() {
        val games =
            listOf(
                game(1, "REVIEW"),
                game(2, "RETRY"),
                game(3, "AUTO", hasCover = true),
                game(4, "NONE"),
            )
        val queue =
            CoverQueueUiState(
                tasks =
                    listOf(
                        task(1, CoverTaskStatus.NEEDS_REVIEW),
                        task(2, CoverTaskStatus.FAILED),
                        task(3, CoverTaskStatus.COMPLETED),
                        task(4, CoverTaskStatus.SEARCHING),
                    ),
                queueStatus = CoverQueueStatus.RUNNING,
            )

        // Game 1 (REVIEW / NEEDS_REVIEW) and game 2 (FAILED); the queued duplicate of game 1 is not
        // double counted, and completed/searching tasks never light the dot.
        assertEquals(2, coverAttentionCount(games, queue))
    }

    @Test
    fun aFailedTaskWithoutAGameStillCountsAndTheReviewOnlyCountStaysSmaller() {
        val games = listOf(game(1, "RETRY"))
        val queue =
            CoverQueueUiState(
                tasks = listOf(task(1, CoverTaskStatus.FAILED), task(99, CoverTaskStatus.FAILED)),
                queueStatus = CoverQueueStatus.RUNNING,
            )

        assertEquals(2, coverAttentionCount(games, queue))
        assertEquals("the review-only projection ignores failures", 0, pendingCoverReviewCount(games, queue))
    }

    @Test
    fun aRetryGameLightsTheDotAfterRestartWithoutAnyQueueTask() {
        // The queue is in-memory: after a restart the RETRY binding is the only surviving signal
        // that an automatic match failed, so the dot must not go dark.
        val games = listOf(game(1, "RETRY"), game(2, "NONE"), game(3, "DISABLED"))
        val emptyQueue = CoverQueueUiState(emptyList(), CoverQueueStatus.RUNNING)

        assertEquals(1, coverAttentionCount(games, emptyQueue))
    }

    private fun game(
        id: Long,
        mode: String,
        hasCover: Boolean = false,
    ) = Game(
        id = id,
        title = "Game $id",
        vndbBindingMode = mode,
        coverUri = if (hasCover) "file:///cover-$id.jpg" else null,
    )

    private fun task(
        gameId: Long,
        status: CoverTaskStatus,
    ) = CoverTaskUi(
        gameId = gameId,
        title = "Game $gameId",
        source = CoverSourceMode.BANGUMI,
        status = status,
        bytesRead = 0,
        totalBytes = null,
        candidateCount = 0,
        matchScore = null,
        error = null,
    )
}
