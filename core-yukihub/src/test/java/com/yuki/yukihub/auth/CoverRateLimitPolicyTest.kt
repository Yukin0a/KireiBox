package com.yuki.yukihub.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverRateLimitPolicyTest {
    private companion object {
        const val DAY = 86_400_000L
    }

    @Test fun dailyQuotaCodeIsRecognisedSeparatelyFromTheMinuteWindow() {
        assertTrue(CoverRateLimitPolicy.isDailyQuota("""{"code":"QUOTA_EXCEEDED"}"""))
        assertFalse(CoverRateLimitPolicy.isDailyQuota("""{"code":"RATE_LIMITED"}"""))
        assertFalse(CoverRateLimitPolicy.isDailyQuota(null))
        assertFalse(CoverRateLimitPolicy.isDailyQuota(""))
    }

    @Test fun quotaCooldownRunsUntilTheNextUtcMidnight() {
        // 00:00:00 UTC -> a full day remains.
        assertEquals(DAY, CoverRateLimitPolicy.quotaCooldownMillis(0L))
        // One hour into the UTC day -> 23 hours remain.
        assertEquals(23 * 3_600_000L, CoverRateLimitPolicy.quotaCooldownMillis(3_600_000L))
        // Last millisecond -> the shortest non-zero window.
        assertEquals(1L, CoverRateLimitPolicy.quotaCooldownMillis(DAY - 1))
    }

    @Test fun quotaCooldownIsAlwaysWithinOneDay() {
        listOf(0L, 1L, 999L, 12_345_678L, DAY - 1, DAY, DAY * 3 + 42).forEach { now ->
            val cooldown = CoverRateLimitPolicy.quotaCooldownMillis(now)
            assertTrue("cooldown must be positive at $now", cooldown in 1..DAY)
        }
    }
}
