package com.kireibox.ui

import com.yuki.yukihub.settings.MotionDecisionInputs
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.MotionModePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionModePolicyTest {
    private val capable = MotionDecisionInputs(true, false, 256, false)

    @Test fun autoSelectsPolishedForCapableDevice() {
        assertEquals(MotionMode.POLISHED, MotionModePolicy.resolve(MotionMode.AUTO, capable))
    }

    @Test fun autoSelectsReducedForConstrainedConditions() {
        assertEquals(MotionMode.REDUCED, MotionModePolicy.resolve(MotionMode.AUTO, capable.copy(lowRamDevice = true)))
        assertEquals(MotionMode.REDUCED, MotionModePolicy.resolve(MotionMode.AUTO, capable.copy(memoryClassMb = 192)))
        assertEquals(MotionMode.REDUCED, MotionModePolicy.resolve(MotionMode.AUTO, capable.copy(powerSaveMode = true)))
    }

    @Test fun autoFallsBackToReducedWhenSystemAnimationsAreOff() {
        assertEquals(
            MotionMode.REDUCED,
            MotionModePolicy.resolve(MotionMode.AUTO, capable.copy(animatorsEnabled = false))
        )
    }

    @Test fun explicitModeWinsInEveryConstrainedCondition() {
        assertEquals(MotionMode.REDUCED, MotionModePolicy.resolve(MotionMode.REDUCED, capable))
        // 完整 is a real choice: system animations off, low RAM, tiny heap and power save all yield
        // the full experience instead of silently pausing it.
        val constrained = capable.copy(animatorsEnabled = false, lowRamDevice = true, memoryClassMb = 128, powerSaveMode = true)
        assertEquals(MotionMode.POLISHED, MotionModePolicy.resolve(MotionMode.POLISHED, constrained))
        assertEquals(MotionMode.POLISHED, MotionModePolicy.resolve(MotionMode.POLISHED, capable.copy(lowRamDevice = true)))
    }
}
