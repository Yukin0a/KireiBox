package com.kireibox.ui

import com.kireibox.update.UpdateManifest
import com.kireibox.update.UpdateRelease
import com.kireibox.update.UpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePromptPolicyTest {
    @Test
    fun availableAndErrorsUseNonPersistentLaterAction() {
        assertEquals(UpdatePromptBackAction.LATER, updatePromptBackAction(UpdateUiState.Available(release())))
        assertEquals(UpdatePromptBackAction.LATER, updatePromptBackAction(UpdateUiState.Error("offline")))
    }

    @Test
    fun activeDownloadAndReadyPackageHaveExplicitBackActions() {
        assertEquals(
            UpdatePromptBackAction.CANCEL_DOWNLOAD,
            updatePromptBackAction(UpdateUiState.Downloading(release(), 1, 2, 1)),
        )
        assertEquals(
            UpdatePromptBackAction.DISCARD_READY,
            updatePromptBackAction(UpdateUiState.ReadyToInstall(release(), "/cache/update.apk")),
        )
    }

    private fun release() = UpdateRelease(
        releaseId = 22,
        tagName = "v0.5.0-alpha.15",
        releaseName = "Alpha 15",
        notes = "notes",
        pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.15",
        apkUrl = "https://github.com/Yukin0a/KireiBox/releases/download/v0.5.0-alpha.15/update.apk",
        apkSize = 1024,
        manifest = UpdateManifest(
            packageName = "com.kireibox.app",
            versionCode = 22,
            versionName = "0.5.0-alpha.15",
            minSdk = 26,
            abis = listOf("arm64-v8a"),
            apkAsset = "update.apk",
            sha256 = "1".repeat(64),
            signingCertSha256 = "2".repeat(64),
        ),
    )
}
