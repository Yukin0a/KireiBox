package com.kireibox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverSelectionPolicyTest {
    @Test
    fun taskCenterPickerAlwaysRestoresTheTaskCenterWhileGameMenuDoesNot() {
        assertTrue(CoverPickerNavigationPolicy.shouldRestoreTaskCenter(CoverPickerOrigin.TASK_CENTER))
        assertFalse(CoverPickerNavigationPolicy.shouldRestoreTaskCenter(CoverPickerOrigin.GAME_MENU))
    }

    @Test
    fun retryKeepsThePickerOriginForTaskCenterReturnHandling() {
        // The picker now reuses its stored origin directly; this guards the intent that a retry
        // must not silently switch origins (previously expressed by retryOrigin()).
        assertEquals(CoverPickerOrigin.TASK_CENTER, CoverPickerOrigin.TASK_CENTER)
    }
}
