package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRowMetricsTest {
    @Test
    fun orientationRowUsesExactlyTheSameAdaptiveMetricsAsCategoryRows() {
        HomeOrientation.entries.forEach { orientation ->
            assertEquals(
                resolveSettingsCategoryRowMetrics(orientation),
                resolveOrientationModeRowMetrics(orientation),
            )
        }
    }
}
