package com.yuki.yukihub.launcherbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tvp.kirikiri2.EnginePlaySessionJournal

class EngineSessionReconcilePolicyTest {
    @Test
    fun `finished journal is committed even before process disappears`() {
        assertTrue(
            EngineSessionReconcilePolicy.shouldFinalize(
                EnginePlaySessionJournal.STATE_FINISHED,
                42,
                setOf(42),
            ),
        )
    }

    @Test
    fun `running or paused live engine is not committed`() {
        assertFalse(
            EngineSessionReconcilePolicy.shouldFinalize(
                EnginePlaySessionJournal.STATE_RUNNING,
                42,
                setOf(42),
            ),
        )
        assertFalse(
            EngineSessionReconcilePolicy.shouldFinalize(
                EnginePlaySessionJournal.STATE_PAUSED,
                42,
                setOf(42),
            ),
        )
    }

    @Test
    fun `dead or legacy process is committed from its checkpoint only`() {
        assertTrue(
            EngineSessionReconcilePolicy.shouldFinalize(
                EnginePlaySessionJournal.STATE_RUNNING,
                42,
                emptySet(),
            ),
        )
        assertTrue(
            EngineSessionReconcilePolicy.shouldFinalize(
                EnginePlaySessionJournal.STATE_RUNNING,
                0,
                emptySet(),
            ),
        )
    }
}
