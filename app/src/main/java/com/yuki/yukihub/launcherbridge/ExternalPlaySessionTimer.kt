package com.yuki.yukihub.launcherbridge

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Launcher-side monotonic timer for games hosted by another app
 * (Android app cards and GameHub/Shizuku shortcuts).
 *
 * Counts [SystemClock.elapsedRealtime] while the user plays, pauses on
 * [Intent.ACTION_SCREEN_OFF] and resumes once the device is unlocked
 * ([Intent.ACTION_SCREEN_ON] only when the keyguard is gone, or
 * [Intent.ACTION_USER_PRESENT]), so lock-screen and screen-off time never
 * inflate the recorded duration.
 */
class ExternalPlaySessionTimer(private val appContext: Context) {
    private val accumulator = ExternalPlayTimeAccumulator(nowElapsed = SystemClock::elapsedRealtime)
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> accumulator.pause()
                Intent.ACTION_SCREEN_ON -> if (!isDeviceLocked()) accumulator.resume()
                Intent.ACTION_USER_PRESENT -> accumulator.resume()
            }
        }
    }

    fun start() {
        registerReceiverOnce()
        accumulator.start()
    }

    /** Returns the accumulated duration, unregisters the receiver and resets the accumulator. */
    fun finish(): Long {
        cleanup()
        return accumulator.finish()
    }

    fun cleanup() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(screenStateReceiver) }
            receiverRegistered = false
        }
    }

    private fun registerReceiverOnce() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            ContextCompat.registerReceiver(
                appContext,
                screenStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        } catch (_: SecurityException) {
            // Best effort: the monotonic clock still protects against wall-clock jumps.
        }
    }

    private fun isDeviceLocked(): Boolean = try {
        (appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
    } catch (_: SecurityException) {
        // Treat restricted devices as unlocked so the timer keeps running.
        false
    }
}
