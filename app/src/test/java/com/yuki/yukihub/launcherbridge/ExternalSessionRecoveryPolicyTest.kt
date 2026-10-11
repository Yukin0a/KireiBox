package com.yuki.yukihub.launcherbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalSessionRecoveryPolicyTest {
    @Test fun liveProcessKeepsTheSessionOpen() {
        assertNull(
            ExternalSessionRecoveryPolicy.recoveredDurationMs(
                processAlive = true,
                startedAtWallMs = 1_000L,
                nowWallMs = 60_000L,
            ),
        )
    }

    @Test fun freshDeadProcessIsKeptWithinSpawnGrace() {
        assertNull(
            ExternalSessionRecoveryPolicy.recoveredDurationMs(
                processAlive = false,
                startedAtWallMs = 1_000L,
                nowWallMs = 1_000L + ExternalSessionRecoveryPolicy.PROCESS_SPAWN_GRACE_MS - 1L,
            ),
        )
    }

    @Test fun deadProcessPastGraceRecoversWallClockTime() {
        val duration =
            ExternalSessionRecoveryPolicy.recoveredDurationMs(
                processAlive = false,
                startedAtWallMs = 1_000L,
                nowWallMs = 1_000L + 20L * 60L * 1000L,
            )
        assertEquals(20L * 60L * 1000L, duration)
    }

    @Test fun recoveredDurationIsCappedAtThirtyMinutes() {
        val duration =
            ExternalSessionRecoveryPolicy.recoveredDurationMs(
                processAlive = false,
                startedAtWallMs = 1_000L,
                nowWallMs = 1_000L + 6L * 60L * 60L * 1000L,
            )
        assertEquals(ExternalSessionRecoveryPolicy.MAX_RECOVERED_PLAY_SESSION_MS, duration)
    }
}
