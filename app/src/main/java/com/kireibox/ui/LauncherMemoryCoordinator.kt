package com.kireibox.ui

import android.content.ComponentCallbacks2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Releases launcher-only resources without killing the process or changing engine quality. */
object LauncherMemoryCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onEngineHandoff() {
        scope.launch { releaseLauncherCoverResources() }
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            scope.launch { releaseLauncherCoverResources() }
        }
    }
}
