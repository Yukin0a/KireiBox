package com.kireibox.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        assertEquals(
            CoverPickerOrigin.TASK_CENTER,
            CoverPickerNavigationPolicy.retryOrigin(CoverPickerOrigin.TASK_CENTER),
        )
    }

    @Test
    fun manualSearchNeverAutoAppliesEvenWhenProviderReportsAutomatic() {
        assertFalse(
            CoverSelectionPolicy.shouldAutoApply(
                CoverSearchIntent.MANUAL,
                automaticMatch = true,
                candidateCount = 2,
            )
        )
    }

    @Test
    fun backgroundDiscoveryAutoAppliesAutomaticCandidate() {
        assertTrue(
            CoverSelectionPolicy.shouldAutoApply(
                CoverSearchIntent.BACKGROUND,
                automaticMatch = true,
                candidateCount = 1,
            )
        )
    }

    @Test
    fun automaticResultWithoutCandidatesIsNeverApplied() {
        assertFalse(
            CoverSelectionPolicy.shouldAutoApply(
                CoverSearchIntent.BACKGROUND,
                automaticMatch = true,
                candidateCount = 0,
            )
        )
    }
}
