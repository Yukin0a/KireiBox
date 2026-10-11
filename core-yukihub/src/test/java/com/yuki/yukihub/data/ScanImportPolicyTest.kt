package com.yuki.yukihub.data

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanImportPolicyTest {
    @Test
    fun `removed game stays hidden when protection is enabled`() {
        assertTrue(ScanImportVisibilityPolicy.hiddenAfterRefresh(wasHidden = true, keepRemovedHidden = true))
    }

    @Test
    fun `removed game becomes visible when protection is disabled`() {
        assertFalse(ScanImportVisibilityPolicy.hiddenAfterRefresh(wasHidden = true, keepRemovedHidden = false))
    }

    @Test
    fun `visible game never becomes hidden during refresh`() {
        assertFalse(ScanImportVisibilityPolicy.hiddenAfterRefresh(wasHidden = false, keepRemovedHidden = true))
    }

    @Test
    fun `scan summaries aggregate counts and preserve added order`() {
        val first = Game(id = 1L, title = "A")
        val second = Game(id = 2L, title = "B")
        val summary =
            ScanImportSummary(listOf(first), updatedCount = 2, keptHiddenCount = 1) +
                ScanImportSummary(listOf(second), updatedCount = 3, skippedCount = 2)

        assertEquals(listOf(1L, 2L), summary.addedGames.map(Game::id))
        assertEquals(2, summary.addedCount)
        assertEquals(5, summary.updatedCount)
        assertEquals(1, summary.keptHiddenCount)
        assertEquals(2, summary.skippedCount)
    }
}
