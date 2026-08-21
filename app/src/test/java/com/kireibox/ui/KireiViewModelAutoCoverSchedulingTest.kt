package com.kireibox.ui

import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KireiViewModelAutoCoverSchedulingTest {

    private fun game(id: Long) = Game(id = id, title = "Game $id")

    @Test
    fun firstScheduleAlwaysEnqueues() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1), game(2)), CoverSourceMode.VNDB, ""))
    }

    @Test
    fun identicalReloadDoesNotEnqueueAgain() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1), game(2)), CoverSourceMode.VNDB, ""))
        assertFalse(gate.shouldSchedule(listOf(game(1), game(2)), CoverSourceMode.VNDB, ""))
    }

    @Test
    fun gameOrderDoesNotChangeTheIdentityKey() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1), game(2)), CoverSourceMode.VNDB, ""))
        assertFalse(gate.shouldSchedule(listOf(game(2), game(1)), CoverSourceMode.VNDB, ""))
    }

    @Test
    fun themeAndLayoutOnlyReloadsKeepTheSameKey() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.VNDB, "token"))
        assertFalse(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.VNDB, "token"))
    }

    @Test
    fun titleOnlyChangeKeepsTheSameKey() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.VNDB, ""))
        assertFalse(gate.shouldSchedule(listOf(Game(id = 1, title = "Renamed")), CoverSourceMode.VNDB, ""))
    }

    @Test
    fun metadataSourceChangeEnqueuesAgain() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.VNDB, ""))
        assertTrue(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.BANGUMI, ""))
    }

    @Test
    fun credentialChangesOnlyAffectTheActiveBangumiFamilySource() {
        CoverSourceMode.entries.forEach { source ->
            val gate = AutoCoverSchedulingGate()
            assertTrue(gate.shouldSchedule(listOf(game(1)), source, "token-a"))
            assertEquals(
                "source=$source",
                source == CoverSourceMode.BANGUMI || source == CoverSourceMode.BANGUMI_MIRROR,
                gate.shouldSchedule(listOf(game(1)), source, "token-b"),
            )
        }
    }

    @Test
    fun gameIdentitySetChangeEnqueuesAgain() {
        val gate = AutoCoverSchedulingGate()
        assertTrue(gate.shouldSchedule(listOf(game(1), game(2)), CoverSourceMode.VNDB, ""))
        assertTrue(gate.shouldSchedule(listOf(game(1), game(2), game(3)), CoverSourceMode.VNDB, ""))
        assertTrue(gate.shouldSchedule(listOf(game(1)), CoverSourceMode.VNDB, ""))
    }
}
