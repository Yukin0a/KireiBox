package com.kireibox.cover

import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal object CoverTimeoutRetryPolicy {
    const val TOTAL_ATTEMPTS = 3
    const val FINAL_MESSAGE = "连接超时，已自动尝试 3 次"

    fun isTimeout(error: Throwable?): Boolean {
        var current = error
        val visited = HashSet<Throwable>()
        while (current != null && visited.add(current)) {
            if (current is SocketTimeoutException) return true
            val message = current.message.orEmpty().lowercase(Locale.ROOT)
            if (
                "timeout" in message ||
                "time out" in message ||
                "timed out" in message ||
                "time_out" in message
            ) return true
            current = current.cause
        }
        return false
    }

    suspend fun <T> run(
        onRetry: suspend (nextAttempt: Int) -> Unit = {},
        operation: suspend (attempt: Int) -> Result<T>,
    ): Result<T> {
        var lastError: Throwable = SocketTimeoutException(FINAL_MESSAGE)
        for (attempt in 1..TOTAL_ATTEMPTS) {
            val result = try {
                operation(attempt)
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
            if (result.isSuccess) return result
            val error = result.exceptionOrNull() ?: IllegalStateException("封面请求失败")
            if (error is CancellationException) throw error
            if (!isTimeout(error)) return result
            lastError = error
            if (attempt < TOTAL_ATTEMPTS) {
                onRetry(attempt + 1)
                delay(if (attempt == 1) 1_000L else 2_000L)
            }
        }
        return Result.failure(SocketTimeoutException(FINAL_MESSAGE).apply { initCause(lastError) })
    }

    fun normalizeFinalError(error: Throwable): Throwable =
        if (isTimeout(error)) SocketTimeoutException(FINAL_MESSAGE).apply { initCause(error) } else error
}
