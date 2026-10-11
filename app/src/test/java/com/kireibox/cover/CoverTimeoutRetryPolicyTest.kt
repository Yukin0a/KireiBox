package com.kireibox.cover

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CoverTimeoutRetryPolicyTest {
    @Test
    fun timeoutSucceedsOnThirdAttemptWithTwoRetries() =
        runTest {
            val attempts = AtomicInteger()
            val retries = mutableListOf<Int>()

            val result =
                CoverTimeoutRetryPolicy.run(onRetry = { retries += it }) {
                    if (attempts.incrementAndGet() < 3) {
                        Result.failure(SocketTimeoutException("timeout"))
                    } else {
                        Result.success("ok")
                    }
                }
            advanceUntilIdle()

            assertEquals("ok", result.getOrThrow())
            assertEquals(3, attempts.get())
            assertEquals(listOf(2, 3), retries)
        }

    @Test
    fun thirdTimeoutUsesStableUserMessage() =
        runTest {
            var attempts = 0
            val result =
                CoverTimeoutRetryPolicy.run<String> {
                    attempts += 1
                    Result.failure(SocketTimeoutException("read timed out"))
                }

            assertEquals(3, attempts)
            assertEquals(CoverTimeoutRetryPolicy.FINAL_MESSAGE, result.exceptionOrNull()?.message)
        }

    @Test
    fun nonTimeoutIsNotRetriedAndCancellationEscapes() =
        runTest {
            var attempts = 0
            val disk =
                CoverTimeoutRetryPolicy.run<String> {
                    attempts += 1
                    Result.failure(IllegalStateException("disk full"))
                }
            assertTrue(disk.isFailure)
            assertEquals(1, attempts)

            val cancelled =
                runCatching {
                    CoverTimeoutRetryPolicy.run<String> { throw CancellationException("cancel") }
                }
            assertTrue(cancelled.exceptionOrNull() is CancellationException)
        }
}
