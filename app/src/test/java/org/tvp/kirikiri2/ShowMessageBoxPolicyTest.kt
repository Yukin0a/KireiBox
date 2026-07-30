package org.tvp.kirikiri2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowMessageBoxPolicyTest {
    @Test fun defaultPolicyNeverSkips() {
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo("SHOW_ALL", 1, 0))
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(null, 1, 10_000))
    }

    @Test fun skipPolicyOnlyAcceptsOneButtonInsideThirtySeconds() {
        val policy = "SKIP_SINGLE_BUTTON_STARTUP_INFO"
        assertTrue(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 1, 0))
        assertTrue(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 1, 30_000))
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 1, -1))
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 1, 30_001))
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 2, 5_000))
        assertFalse(ShowMessageBoxRunnable.shouldSkipSingleButtonStartupInfo(policy, 0, 5_000))
    }
}
