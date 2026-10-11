package com.kireibox.ui

import android.app.Application
import com.kireibox.update.UpdateCheckResponse
import com.kireibox.update.UpdateCheckResult
import com.kireibox.update.UpdateManifest
import com.kireibox.update.UpdatePackageValidation
import com.kireibox.update.UpdateRelease
import com.kireibox.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KireiViewModelUpdateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `available update becomes visible and ignore persists exact version`() =
        runTest {
            var ignoredVersion = 0
            val release = release(20)
            val vm =
                viewModel(
                    result = UpdateCheckResult.Available(release),
                    preferenceWriter = { ignored, _, _ -> ignored?.let { ignoredVersion = it } },
                )

            vm.checkForUpdates(manual = false)

            assertEquals(release, (vm.state.value.updateState as UpdateUiState.Available).release)
            vm.ignoreAvailableUpdate()
            assertEquals(20, ignoredVersion)
            assertEquals(UpdateUiState.Idle, vm.state.value.updateState)
        }

    @Test
    fun `manual empty result reports no public version while automatic result stays silent`() =
        runTest {
            val automatic = viewModel(UpdateCheckResult.NoPublishedRelease)
            automatic.checkForUpdates(manual = false)
            assertEquals(UpdateUiState.Idle, automatic.state.value.updateState)
            assertEquals(null, automatic.state.value.notice)

            val manual = viewModel(UpdateCheckResult.NoPublishedRelease)
            manual.checkForUpdates(manual = true)
            assertEquals(UpdateUiState.Idle, manual.state.value.updateState)
            assertTrue(manual.state.value.notice.orEmpty().contains("暂无公开版本"))
        }

    @Test
    fun `verified download becomes ready for system installer`() =
        runTest {
            val release = release(20)
            val apk = temporaryFolder.newFile("update.apk").apply { writeText("fixture") }
            val vm =
                KireiViewModel(
                    application = Application(),
                    testReloadAction = {},
                    testUpdateCheck = { _, _, _ ->
                        UpdateCheckResponse(UpdateCheckResult.Available(release), "etag")
                    },
                    testUpdatePreferenceWriter = { _, _, _ -> },
                    testUpdateDownload = { _, onProgress ->
                        onProgress(com.kireibox.update.UpdateDownloadProgress(7, 7, 7))
                        apk
                    },
                    testUpdateValidate = { _, _ -> UpdatePackageValidation.Valid },
                )

            vm.checkForUpdates()
            vm.downloadAvailableUpdate()

            val ready = vm.state.value.updateState as UpdateUiState.ReadyToInstall
            assertEquals(apk.absolutePath, ready.apkPath)
        }

    @Test
    fun `automatic failure is silent and only checked once per day while manual bypasses`() =
        runTest {
            var attempts = 0
            val vm =
                KireiViewModel(
                    application = Application(),
                    testReloadAction = {},
                    testUpdateCheck = { _, _, _ ->
                        attempts++
                        UpdateCheckResponse(UpdateCheckResult.NoPublishedRelease, null)
                    },
                    testUpdatePreferenceWriter = { _, _, _ -> },
                )
            vm.checkForUpdates(false)
            vm.checkForUpdates(false)
            assertEquals(1, attempts)
            assertEquals(null, vm.state.value.notice)
            vm.checkForUpdates(true)
            assertEquals(2, attempts)
        }

    private fun viewModel(
        result: UpdateCheckResult,
        preferenceWriter: (Int?, String?, Long?) -> Unit = { _, _, _ -> },
    ) = KireiViewModel(
        application = Application(),
        testReloadAction = {},
        testUpdateCheck = { _, _, _ -> UpdateCheckResponse(result, "etag") },
        testUpdatePreferenceWriter = preferenceWriter,
    )

    private fun release(versionCode: Int) =
        UpdateRelease(
            releaseId = versionCode.toLong(),
            tagName = "v0.5.0-alpha.14",
            releaseName = "Alpha 14",
            notes = "notes",
            pageUrl = "https://example.com",
            apkUrl = "https://example.com/update.apk",
            apkSize = 1024,
            manifest =
                UpdateManifest(
                    packageName = "com.kireibox.app",
                    versionCode = versionCode,
                    versionName = "0.5.0-alpha.14",
                    minSdk = 26,
                    abis = listOf("arm64-v8a"),
                    apkAsset = "update.apk",
                    sha256 = "1".repeat(64),
                    signingCertSha256 = "2".repeat(64),
                ),
        )
}
