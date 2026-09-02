package com.yuki.yukihub.launcherbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPlayTimeAccumulatorTest {
    private var now = 0L
    private val accumulator = ExternalPlayTimeAccumulator(nowElapsed = { now })

    @Test fun startPauseResumeFinishAccumulatesActiveSpans() {
        accumulator.start()
        now = 10_000L
        accumulator.pause()
        now = 25_000L
        accumulator.resume()
        now = 30_000L
        assertEquals(15_000L, accumulator.finish())
    }

    @Test fun screenOffSpanIsNeverCounted() {
        accumulator.start()
        now = 5_000L
        accumulator.pause()
        assertTrue(accumulator.isPausedForScreenOff())
        now = 60_000L
        accumulator.resume()
        now = 65_000L
        assertEquals(10_000L, accumulator.finish())
    }

    @Test fun backwardClockDeltaIsClampedToZero() {
        now = 5_000L
        accumulator.start()
        now = 1_000L
        assertEquals(0L, accumulator.finish())
    }

    @Test fun singleOversizedTickIsCapped() {
        val capped = ExternalPlayTimeAccumulator(nowElapsed = { now }, maxTickMs = 60_000L)
        capped.start()
        now = 9 * 60L * 60L * 1000L
        assertEquals(60_000L, capped.finish())
    }

    @Test fun finishWithoutStartIsZero() {
        assertFalse(accumulator.isPausedForScreenOff())
        assertEquals(0L, accumulator.finish())
    }
}
