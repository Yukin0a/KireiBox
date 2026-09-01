package com.yuki.yukihub.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPrunePolicyTest {
    @Test
    fun visibleMissingFolderIsPruned() {
        assertTrue(ScanPrunePolicy.shouldPrune(seen = false, resolves = false, hidden = false))
    }

    @Test
    fun seenInScanIsNeverPruned() {
        assertFalse(ScanPrunePolicy.shouldPrune(seen = true, resolves = false, hidden = false))
    }

    @Test
    fun existingFolderIsNeverPruned() {
        assertFalse(ScanPrunePolicy.shouldPrune(seen = false, resolves = true, hidden = false))
    }

    @Test
    fun hiddenRemovedGameIsNeverPruned() {
        assertFalse(ScanPrunePolicy.shouldPrune(seen = false, resolves = false, hidden = true))
    }
}
