package com.yuki.yukihub.metadata.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Session-scoped health for cover sources.
 *
 * A source that is unreachable (blocked network path, DNS black hole, hard outage) would
 * otherwise cost a full connect timeout on *every* search. After a couple of consecutive
 * failures it is parked for a cooldown, so the hybrid search keeps its latency without
 * permanently giving up: the next search after the cooldown probes it again.
 */
object CoverSourceHealth {
    private const val FAILURE_THRESHOLD = 2
    private const val COOLDOWN_MS = 5 * 60_000L

    private val failures = ConcurrentHashMap<CoverSourceMode, Int>()
    private val blockedUntil = ConcurrentHashMap<CoverSourceMode, Long>()

    @JvmStatic
    fun isAvailable(source: CoverSourceMode): Boolean = (blockedUntil[source] ?: 0L) <= System.currentTimeMillis()

    @JvmStatic
    fun recordSuccess(source: CoverSourceMode) {
        failures.remove(source)
        blockedUntil.remove(source)
    }

    @JvmStatic
    fun recordFailure(source: CoverSourceMode) {
        val count = failures.merge(source, 1) { a, b -> a + b } ?: 1
        if (count >= FAILURE_THRESHOLD) {
            blockedUntil[source] = System.currentTimeMillis() + COOLDOWN_MS
            failures.remove(source)
        }
    }

    /** Test/UI hook: forget all recorded failures. */
    @JvmStatic
    fun reset() {
        failures.clear()
        blockedUntil.clear()
    }
}
