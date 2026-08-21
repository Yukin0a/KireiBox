package com.kireibox.ui

import android.app.Application
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KireiViewModelImmersiveGalleryTest {
    @Test
    fun setImmersiveGalleryModeDelegatesToWriterAndTriggersReload() {
        var written: SpotlightSelectionMode? = null
        var reloadCount = 0
        val vm = KireiViewModel(
            Application(),
            testReloadAction = { reloadCount++ },
            testImmersiveGalleryModeWriter = { mode -> written = mode },
        )
        vm.setImmersiveGalleryMode(SpotlightSelectionMode.MANUAL)
        assertEquals(SpotlightSelectionMode.MANUAL, written)
        assertEquals(1, reloadCount)
    }

    @Test
    fun setImmersiveGalleryIdsDelegatesOrderedIdsToWriter() {
        var written: List<Long>? = null
        var reloadCount = 0
        val vm = KireiViewModel(
            Application(),
            testReloadAction = { reloadCount++ },
            testImmersiveGalleryIdsWriter = { ids -> written = ids },
        )
        vm.setImmersiveGalleryIds(listOf(5L, 3L, 1L))
        assertEquals(listOf(5L, 3L, 1L), written)
        assertEquals(1, reloadCount)
    }

    @Test
    fun setImmersiveGalleryModeBackToAutomaticDelegatesCorrectly() {
        var written: SpotlightSelectionMode? = null
        var reloadCount = 0
        val vm = KireiViewModel(
            Application(),
            testReloadAction = { reloadCount++ },
            testImmersiveGalleryModeWriter = { mode -> written = mode },
        )
        vm.setImmersiveGalleryMode(SpotlightSelectionMode.AUTOMATIC)
        assertEquals(SpotlightSelectionMode.AUTOMATIC, written)
        assertEquals(1, reloadCount)
    }

    @Test
    fun setImmersiveGalleryAutoSourceDelegatesToWriterAndTriggersReload() {
        var written: ImmersiveGalleryAutoSource? = null
        var reloadCount = 0
        val vm = KireiViewModel(
            Application(),
            testReloadAction = { reloadCount++ },
            testImmersiveGalleryAutoWriter = { source -> written = source },
        )
        vm.setImmersiveGalleryAutoSource(ImmersiveGalleryAutoSource.FAVORITES)
        assertEquals(ImmersiveGalleryAutoSource.FAVORITES, written)
        assertEquals(1, reloadCount)
    }

    @Test
    fun canonicalGallerySettersNormalizeAndTriggerReload() {
        var source: SpotlightSource? = null
        var strategy: SpotlightStrategy? = null
        var count: Int? = null
        var reloadCount = 0
        val vm = KireiViewModel(
            Application(),
            testReloadAction = { reloadCount++ },
            testImmersiveGallerySourceWriter = { source = it },
            testImmersiveGalleryStrategyWriter = { strategy = it },
            testImmersiveGalleryCountWriter = { count = it },
        )

        vm.setImmersiveGallerySource(SpotlightSource.ALL)
        vm.setImmersiveGalleryStrategy(SpotlightStrategy.DAILY_RANDOM)
        vm.setImmersiveGalleryCount(99)

        assertEquals(SpotlightSource.ALL, source)
        assertEquals(SpotlightStrategy.DAILY_RANDOM, strategy)
        assertEquals(5, count)
        assertEquals(3, reloadCount)
    }

    @Test
    fun latestWinsGateInvalidatesOlderImmersiveReloadsDeterministically() {
        val gate = LatestWinsGate()
        val first = gate.issue()
        val second = gate.issue()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }
}
