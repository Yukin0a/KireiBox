package com.yuki.yukihub.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverAutoMatchNetworkPolicyTest {
    @Test
    fun legacyDefaultNeverBlocksRegardlessOfMetering() {
        assertTrue(CoverAutoMatchNetworkPolicy.shouldRun(nowUnmetered = true, wifiOnly = false))
        assertTrue(CoverAutoMatchNetworkPolicy.shouldRun(nowUnmetered = false, wifiOnly = false))
    }

    @Test
    fun wifiOnlyRunsOnUnmeteredNetwork() {
        assertTrue(CoverAutoMatchNetworkPolicy.shouldRun(nowUnmetered = true, wifiOnly = true))
    }

    @Test
    fun wifiOnlySkipsMeteredNetwork() {
        assertFalse(CoverAutoMatchNetworkPolicy.shouldRun(nowUnmetered = false, wifiOnly = true))
    }
}
