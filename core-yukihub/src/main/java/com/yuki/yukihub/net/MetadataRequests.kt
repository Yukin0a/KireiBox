package com.yuki.yukihub.net

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.util.AppExecutors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Bridges the retained synchronous Java clients to cancellable coroutine-owned requests. */
object MetadataRequests {
    private val current = ThreadLocal<RequestScope>()
    private val active = ConcurrentHashMap.newKeySet<RequestScope>()

    /**
     * Metadata requests block a thread for the whole network round trip, and a blocked source can
     * sit there until its connect timeout. Running them on the shared fixed-size [AppExecutors.io]
     * pool starves every other request behind a slow/unreachable host, so they get a dedicated
     * elastic pool instead: cached threads grow with demand and are reused afterwards.
     */
    private val executor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "kirei-metadata").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }

    suspend fun <T> run(
        source: CoverSourceMode,
        block: () -> T,
    ): T =
        suspendCancellableCoroutine { continuation ->
            val scope = RequestScope(source) { continuation.cancel() }
            active += scope
            val future =
                executor.submit {
                    current.set(scope)
                    try {
                        scope.checkActive()
                        val result = block()
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        current.remove()
                        active -= scope
                    }
                }
            continuation.invokeOnCancellation {
                scope.cancel()
                future.cancel(true)
                active -= scope
            }
        }

    fun checkActive() {
        current.get()?.checkActive()
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }

    fun cancel(source: CoverSourceMode) = active.filter { it.source == source }.forEach { it.cancel() }

    fun <T> track(
        call: Call,
        block: () -> T,
    ): T {
        val scope = current.get()
        scope?.add(call)
        return block() // Keep the call registered through response-body consumption.
    }

    private class RequestScope(val source: CoverSourceMode, val cancelOwner: () -> Unit) {
        private var cancelled = false
        private val calls = mutableSetOf<Call>()

        @Synchronized fun checkActive() {
            if (cancelled) throw CancellationException("请求已取消")
        }

        @Synchronized fun add(call: Call) {
            checkActive()
            calls += call
        }

        fun cancel() {
            synchronized(this) {
                if (cancelled) return
                cancelled = true
                cancelOwner()
                calls.forEach(Call::cancel)
            }
        }
    }
}
