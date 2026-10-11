package com.kireibox.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end coverage of the "installed alpha.14 → GitHub has alpha.17" update-detection scenario
 * that `KIREIBOX_DEBUG_INSTALLED_VERSION_NAME/CODE` reproduces on a debug build.
 */
class UpdateInstalledVersionScenarioTest {
    private fun release(
        versionName: String,
        versionCode: Int?
    ): UpdateRelease =
        UpdateRelease(
            releaseId = 1L,
            tagName = "v$versionName",
            releaseName = "KireiBox $versionName",
            notes = "",
            pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/v$versionName",
            apkUrl = "https://github.com/Yukin0a/KireiBox/releases/download/v$versionName/app.apk",
            apkSize = 1L,
            manifest =
                versionCode?.let {
                    UpdateManifest(
                        packageName = KIREI_BOX_PACKAGE,
                        versionCode = it,
                        versionName = versionName,
                        minSdk = 26,
                        abis = listOf("arm64-v8a"),
                        apkAsset = "app.apk",
                        sha256 = "a".repeat(64),
                        signingCertSha256 = "b".repeat(64),
                    )
                },
            fallbackVersionName = if (versionCode == null) versionName else "",
        )

    @Test
    fun installedAlpha14SeesNewerAlpha17() {
        val result =
            UpdateSelectionPolicy.select(
                installedVersionCode = 21,
                ignoredVersionCode = 0,
                releases = listOf(release("0.5.0-alpha.17", versionCode = null)),
                installedVersionName = "0.5.0-alpha.14",
            )
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals("0.5.0-alpha.17", (result as UpdateCheckResult.Available).release.versionName)
    }

    @Test
    fun installedAlpha14WithManifestCode21SeesNewerAlpha17Code25() {
        val result =
            UpdateSelectionPolicy.select(
                installedVersionCode = 21,
                ignoredVersionCode = 0,
                releases = listOf(release("0.5.0-alpha.17", versionCode = 25)),
                installedVersionName = "0.5.0-alpha.14",
            )
        assertTrue(result is UpdateCheckResult.Available)
    }

    @Test
    fun installedAlpha17DoesNotSeeItself() {
        val result =
            UpdateSelectionPolicy.select(
                installedVersionCode = 25,
                ignoredVersionCode = 0,
                releases = listOf(release("0.5.0-alpha.17", versionCode = 25)),
                installedVersionName = "0.5.0-alpha.17",
            )
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun ignoredNewerVersionIsNotOfferedAgain() {
        val result =
            UpdateSelectionPolicy.select(
                installedVersionCode = 21,
                ignoredVersionCode = 25,
                releases = listOf(release("0.5.0-alpha.17", versionCode = 25)),
                installedVersionName = "0.5.0-alpha.14",
            )
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    private companion object {
        const val KIREI_BOX_PACKAGE = "com.kireibox.app"
    }
}
