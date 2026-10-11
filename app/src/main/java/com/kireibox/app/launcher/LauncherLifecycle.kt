package com.kireibox.app.launcher

import com.kireibox.app.LauncherActivity
import com.yuki.yukihub.launcherbridge.LauncherGameLaunchBridge

/** [LauncherActivity.onResume] body: session reconciliation, system bars and deferred work. */
internal fun LauncherActivity.onLauncherResume() {
    applySystemBars()
    viewModel.refreshShortcutServiceStatus()
    if (launcherHandoffActive) {
        launcherHandoffActive = false
        viewModel.resumeAfterEngineHandoff()
    }
    if (externalPlayActive && runningSessionId > 0) {
        val sessionId = runningSessionId
        val effectiveDuration = externalPlayTimer?.finish() ?: 0L
        externalPlayTimer = null
        runningSessionId = -1L
        externalPlayActive = false
        // Monotonic, screen-off-aware duration measured while the launcher was
        // in background; the persisted gate is released after settlement.
        LauncherGameLaunchBridge.finishExternalSessionAsync(this, sessionId, effectiveDuration, viewModel::reload)
    } else if (engineWasOpened && runningSessionId > 0) {
        val sessionId = runningSessionId
        val journaled = runningSessionJournaled
        runningSessionId = -1L
        engineWasOpened = false
        runningSessionJournaled = false
        if (journaled) {
            LauncherGameLaunchBridge.reconcilePlaySessionsAsync(this, viewModel::reload)
        } else {
            LauncherGameLaunchBridge.finishExternalSessionAsync(this, sessionId, 0L, viewModel::reload)
        }
    } else {
        // Launcher process death during an external session is silently recovered;
        // reconcilePlaySessionsAsync always fires its callback, so it owns the UI reload.
        LauncherGameLaunchBridge.recoverStaleExternalSessionAsync(this)
        LauncherGameLaunchBridge.reconcilePlaySessionsAsync(this, viewModel::reload)
    }
    val pending = pendingNativeLaunch
    if (pending != null && hasNativeFileAccess()) {
        pendingNativeLaunch = null
        window.decorView.post { launchPreparedGame(pending) }
    }
    if (pendingNativeShell && hasNativeFileAccess()) {
        pendingNativeShell = false
        window.decorView.post { openKrkrNativeShell() }
    }
    if (pendingBrowserOpen && hasNativeFileAccess()) {
        val browserPick = pendingBrowserPick
        pendingBrowserOpen = false
        pendingBrowserPick = null
        window.decorView.post { openBuiltinBrowser(browserPick) }
    }
    val updatePath = pendingUpdateApkPath
    if (updatePath != null && updateInstaller.canRequestPackageInstalls()) {
        pendingUpdateApkPath = null
        window.decorView.post { installUpdate(updatePath) }
    }
    viewModel.onAppForeground()
}
