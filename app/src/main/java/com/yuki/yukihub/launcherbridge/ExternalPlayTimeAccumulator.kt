package com.yuki.yukihub.launcherbridge

/**
 * Pure monotonic-clock accumulator for external play sessions.
 *
 * Android-free on purpose: the clock is injected so JVM tests can drive
 * start/pause/resume/finish without a device. Screen-off pauses are folded
 * into [pause]/[resume] by the Android wrapper; negative clock deltas and
 * oversized single ticks are clamped before accumulation.
 */
internal class ExternalPlayTimeAccumulator(
    private val nowElapsed: () -> Long,
    private val maxTickMs: Long = MAX_TICK_MS,
) {
    private var accumulatedMs = 0L
    private var startedElapsedAt = -1L
    private var pausedForScreenOff = false

    fun start() {
        accumulatedMs = 0L
        startedElapsedAt = nowElapsed()
        pausedForScreenOff = false
    }

    fun pause() {
        if (startedElapsedAt < 0L || pausedForScreenOff) return
        accumulatedMs += positiveDelta(nowElapsed())
        startedElapsedAt = -1L
        pausedForScreenOff = true
    }

    fun resume() {
        if (!pausedForScreenOff) return
        startedElapsedAt = nowElapsed()
        pausedForScreenOff = false
    }

    /** Returns the accumulated duration and resets the accumulator for reuse. */
    fun finish(): Long {
        if (startedElapsedAt >= 0L) {
            accumulatedMs += positiveDelta(nowElapsed())
        }
        val total = accumulatedMs.coerceAtLeast(0L)
        accumulatedMs = 0L
        startedElapsedAt = -1L
        pausedForScreenOff = false
        return total
    }

    fun isPausedForScreenOff(): Boolean = pausedForScreenOff

    private fun positiveDelta(now: Long): Long {
        val raw = (now - startedElapsedAt).coerceAtLeast(0L)
        return if (raw > maxTickMs) maxTickMs else raw
    }

    companion object {
        const val MAX_TICK_MS = 12L * 60L * 60L * 1000L
    }
}
