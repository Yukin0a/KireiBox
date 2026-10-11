package com.kireibox.ons

import org.junit.Assert.assertEquals
import org.junit.Test

class OnsGlobalSettingsTest {
    @Test
    fun sharpnessRoundTripsThroughTheEngineString() {
        assertEquals("2", OnsGlobalSettings.formatSharpness(2f))
        assertEquals("2.5", OnsGlobalSettings.formatSharpness(2.5f))
        assertEquals("0.5", OnsGlobalSettings.formatSharpness(.5f))
        assertEquals(3.5f, OnsGlobalSettings.parseSharpness("3.5"), 0f)
    }

    @Test
    fun unreadableOrOutOfRangeSharpnessFallsBackToTheDefault() {
        assertEquals(OnsGlobalSettings.DEFAULT_SHARPNESS, OnsGlobalSettings.parseSharpness(null), 0f)
        assertEquals(OnsGlobalSettings.DEFAULT_SHARPNESS, OnsGlobalSettings.parseSharpness("abc"), 0f)
        assertEquals(OnsGlobalSettings.DEFAULT_SHARPNESS, OnsGlobalSettings.parseSharpness("11"), 0f)
        assertEquals(OnsGlobalSettings.DEFAULT_SHARPNESS, OnsGlobalSettings.parseSharpness("0"), 0f)
    }
}
