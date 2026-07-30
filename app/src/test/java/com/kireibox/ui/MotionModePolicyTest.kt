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

    @Test fun disabledSystemAnimationsAlwaysWin() {
        assertEquals(
            MotionMode.REDUCED,
            MotionModePolicy.resolve(MotionMode.POLISHED, capable.copy(animatorsEnabled = false))
        )
    }

    @Test fun explicitModeWinsWhenSystemAnimationsAreAvailable() {
        assertEquals(MotionMode.REDUCED, MotionModePolicy.resolve(MotionMode.REDUCED, capable))
        assertEquals(MotionMode.POLISHED, MotionModePolicy.resolve(MotionMode.POLISHED, capable.copy(lowRamDevice = true)))
    }
}
