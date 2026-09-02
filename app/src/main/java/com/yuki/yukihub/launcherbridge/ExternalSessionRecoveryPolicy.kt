package com.yuki.yukihub.launcherbridge

/**
 * Decides how to settle an external play session whose launcher process died.
 *
 * Pure and Android-free for JVM testing. Mirrors Rinne's launch-gate recovery:
 * keep the gate while the target process is alive or still within its spawn
 * grace window; otherwise silently recover using wall-clock time capped at
 * [MAX_RECOVERED_PLAY_SESSION_MS].
 */
internal object ExternalSessionRecoveryPolicy {
    const val PROCESS_SPAWN_GRACE_MS = 5_000L
    const val MAX_RECOVERED_PLAY_SESSION_MS = 30L * 60L * 1000L

    /**
     * Returns the duration to record, or null when the session must be kept open.
     */
    fun recoveredDurationMs(
        processAlive: Boolean,
        startedAtWallMs: Long,
        nowWallMs: Long,
    ): Long? {
        if (processAlive) return null
        val elapsed = nowWallMs - startedAtWallMs
        if (elapsed < PROCESS_SPAWN_GRACE_MS) return null
        return elapsed.coerceAtLeast(0L).coerceAtMost(MAX_RECOVERED_PLAY_SESSION_MS)
    }
}
