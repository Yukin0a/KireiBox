package com.kireibox.app.launcher

import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.KireiActions
import com.kireibox.ui.KireiBoxApp

/** Installs the Compose shell and finishes the window/launch wiring after [LauncherActivity.onCreate]. */
internal fun LauncherActivity.initializeLauncher() {
    setContent {
        KireiBoxApp(
            viewModel,
            KireiActions(
                chooseDirectory = { chooseStorage() },
                pickAddGame = { pick -> chooseStorage(pick) },
                chooseAndroidApp = viewModel::openAndroidAppPicker,
                chooseExternalShortcuts = viewModel::openExternalShortcutPicker,
                chooseCoverFromAlbum = {
                    coverAlbumPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                chooseCoverFromFiles = { coverFilePicker.launch(arrayOf("image/*")) },
                launchGame = this::launchGame,
                pinGameShortcut = this::pinGameShortcut,
                selectedGame = viewModel::selectedGame,
                launchKrkrNative = this::launchKrkrNative,
                checkPermissions = this::checkPermissions,
                requestNativeAccess = this::requestNativeFileAccess,
                repairPermission = this::repairPermission,
                restoreRemovedGame = viewModel::restoreRemovedGame,
                copyLogs = this::copyDiagnosticLogs,
                exportLogs = this::exportDiagnosticLogs,
                setDarkMode = { dark ->
                    viewModel.setDarkMode(dark)
                    applySystemBarTone(dark)
                },
                installUpdate = this::installUpdate,
            ),
            prompt = launcherPrompt.value,
            onPromptDismiss = { launcherPrompt.value = null },
            fileBrowserRequest = fileBrowserRequest.value,
            onFileBrowserDismiss = { fileBrowserRequest.value = null },
            onFileBrowserUseSaf = {
                val request = fileBrowserRequest.value
                fileBrowserRequest.value = null
                request?.let { openSystemPicker(it.pick) }
            },
            onFileBrowserSelected = { uri ->
                val request = fileBrowserRequest.value
                fileBrowserRequest.value = null
                val pick = request?.pick
                when {
                    request == null -> Unit
                    pick == null -> viewModel.addScanRoot(uri)
                    else -> routeAddGamePick(pick, uri)
                }
            },
            onSystemBarIconsLight = this::applyStatusBarTone
        )
    }
    applySystemBars()
    syncApplicationNightModeWhenHidden()
    consumeDesktopGameIntent(intent)
}
