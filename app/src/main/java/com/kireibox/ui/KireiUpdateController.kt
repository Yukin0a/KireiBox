package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.kireibox.update.UpdateCheckResult
import com.kireibox.update.UpdateDownloadProgress
import com.kireibox.update.UpdateInstalledVersion
import com.kireibox.update.UpdateReleaseCacheCodec
import com.kireibox.update.UpdateUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class KireiUpdateController(host: KireiViewModel) : KireiViewModelController(host) {
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var lastUpdateAttemptAt = 0L

    fun checkForUpdates(manual: Boolean = false) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob =
            viewModelScope.launch {
                val settings =
                    mutableState.value.settings ?: if (isTestMode) {
                        null
                    } else {
                        withContext(Dispatchers.IO) { core.settings.snapshot() }
                    }
                val now = System.currentTimeMillis()
                if (!manual && now - maxOf(lastUpdateAttemptAt, settings?.lastUpdateCheckAt ?: 0L) in 0 until 86_400_000L) return@launch
                lastUpdateAttemptAt = now
                mutableState.value = mutableState.value.copy(updateState = UpdateUiState.Checking(manual))
                // Persist the attempt before networking so restarts do not bypass the daily limit.
                writeUpdatePreferences(checkedAt = now)
                val ignoredVersion = settings?.ignoredUpdateVersionCode ?: 0
                val ignoredVersionName = settings?.ignoredUpdateVersionName.orEmpty()
                val etag = settings?.updateEtag?.takeIf(String::isNotBlank)
                val persistedRelease = UpdateReleaseCacheCodec.decode(settings?.updateReleaseCache)
                val response =
                    testUpdateCheck?.invoke(UpdateInstalledVersion.versionCode, ignoredVersion, etag)
                        ?: updateRepository.check(
                            installedVersionCode = UpdateInstalledVersion.versionCode,
                            ignoredVersionCode = ignoredVersion,
                            etag = etag,
                            installedVersionName = UpdateInstalledVersion.versionName,
                            persistedRelease = persistedRelease,
                            ignoredVersionName = ignoredVersionName,
                        )
                writeUpdatePreferences(
                    etag = response.etag,
                    checkedAt = System.currentTimeMillis(),
                    releaseCache =
                        when {
                            response.cachedRelease != null -> UpdateReleaseCacheCodec.encode(response.cachedRelease)
                            response.result == UpdateCheckResult.NoPublishedRelease -> ""
                            else -> null
                        },
                )
                mutableState.value =
                    when (val result = response.result) {
                        is UpdateCheckResult.Available ->
                            mutableState.value.copy(
                                updateState = UpdateUiState.Available(result.release),
                            )
                        UpdateCheckResult.UpToDate,
                        UpdateCheckResult.NotModified,
                        ->
                            mutableState.value.copy(
                                updateState = UpdateUiState.Idle,
                                notice = if (manual) "已是最新版" else mutableState.value.notice,
                            )
                        UpdateCheckResult.NoPublishedRelease ->
                            mutableState.value.copy(
                                updateState = UpdateUiState.Idle,
                                notice = if (manual) "GitHub 暂无公开版本" else mutableState.value.notice,
                            )
                        is UpdateCheckResult.Failed ->
                            mutableState.value.copy(
                                updateState = if (manual) UpdateUiState.Error(result.message) else UpdateUiState.Idle,
                            )
                    }
            }
    }

    /**
     * Remembers the offered version so it is never surfaced again. Persisted by name as well as
     * code because releases published without the signed update manifest carry no version code.
     */
    fun ignoreAvailableUpdate() {
        val release = (mutableState.value.updateState as? UpdateUiState.Available)?.release ?: return
        writeUpdatePreferences(
            ignoredVersion = release.versionCode,
            ignoredVersionName = release.versionName.takeIf(String::isNotBlank),
        )
        val settings = mutableState.value.settings
        mutableState.value =
            mutableState.value.copy(
                updateState = UpdateUiState.Idle,
                settings =
                    settings?.copy(
                        ignoredUpdateVersionCode = release.versionCode ?: settings.ignoredUpdateVersionCode,
                        ignoredUpdateVersionName = release.versionName,
                    ),
            )
    }

    fun downloadAvailableUpdate() {
        val release =
            when (val current = mutableState.value.updateState) {
                is UpdateUiState.Available -> current.release
                is UpdateUiState.Error -> current.release
                else -> null
            } ?: return
        updateDownloadJob?.cancel()
        mutableState.value =
            mutableState.value.copy(
                updateState = UpdateUiState.Downloading(release, 0L, release.apkSize, 0L),
            )
        updateDownloadJob =
            viewModelScope.launch {
                try {
                    val file =
                        testUpdateDownload?.invoke(release, ::applyUpdateDownloadProgress)
                            ?: updateDownloader.download(release, ::applyUpdateDownloadProgress)
                    val validation =
                        testUpdateValidate?.invoke(file, release)
                            ?: updateInstaller.validate(file, release)
                    mutableState.value =
                        when (validation) {
                            com.kireibox.update.UpdatePackageValidation.Valid ->
                                mutableState.value.copy(
                                    updateState = UpdateUiState.ReadyToInstall(release, file.absolutePath),
                                )
                            is com.kireibox.update.UpdatePackageValidation.Invalid -> {
                                file.delete()
                                mutableState.value.copy(updateState = UpdateUiState.Error(validation.message, release))
                            }
                        }
                } catch (cancelled: CancellationException) {
                    mutableState.value = mutableState.value.copy(updateState = UpdateUiState.Available(release))
                    throw cancelled
                } catch (error: Throwable) {
                    mutableState.value =
                        mutableState.value.copy(
                            updateState =
                                UpdateUiState.Error(
                                    error.message?.takeIf(String::isNotBlank) ?: "更新下载失败",
                                    release,
                                ),
                        )
                }
            }
    }

    fun cancelUpdateDownload() {
        val release = (mutableState.value.updateState as? UpdateUiState.Downloading)?.release ?: return
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        mutableState.value = mutableState.value.copy(updateState = UpdateUiState.Available(release))
    }

    fun discardReadyUpdate() {
        val ready = mutableState.value.updateState as? UpdateUiState.ReadyToInstall ?: return
        val file = File(ready.apkPath)
        val updateRoot = File(hostApplication.cacheDir, "updates").canonicalFile
        if (runCatching { file.canonicalFile.parentFile == updateRoot }.getOrDefault(false)) file.delete()
        mutableState.value = mutableState.value.copy(updateState = UpdateUiState.Idle)
    }

    private fun applyUpdateDownloadProgress(progress: UpdateDownloadProgress) {
        val current = mutableState.value.updateState as? UpdateUiState.Downloading ?: return
        mutableState.value =
            mutableState.value.copy(
                updateState =
                    current.copy(
                        bytesRead = progress.bytesRead,
                        totalBytes = progress.totalBytes,
                        bytesPerSecond = progress.bytesPerSecond,
                    ),
            )
    }

    fun dismissUpdateUi() {
        mutableState.value = mutableState.value.copy(updateState = UpdateUiState.Idle)
    }

    /**
     * Drops partial downloads and week-old packages so a stale 80 MB APK cannot linger in the
     * cache after the user abandons an update.
     */
    internal fun cleanupStaleUpdateFiles() {
        val directory = File(hostApplication.cacheDir, "updates")
        val files = directory.listFiles() ?: return
        val expiry = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        files.forEach { file ->
            if (!file.isFile) return@forEach
            if (file.name.endsWith(".part") || file.lastModified() < expiry) file.delete()
        }
    }

    private fun writeUpdatePreferences(
        ignoredVersion: Int? = null,
        ignoredVersionName: String? = null,
        etag: String? = null,
        checkedAt: Long? = null,
        releaseCache: String? = null,
    ) {
        val writer = testUpdatePreferenceWriter
        if (writer != null) {
            writer.invoke(ignoredVersion, etag, checkedAt)
            return
        }
        ignoredVersion?.let(core.settings::setIgnoredUpdateVersionCode)
        ignoredVersionName?.let(core.settings::setIgnoredUpdateVersionName)
        etag?.let(core.settings::setUpdateEtag)
        checkedAt?.let(core.settings::setLastUpdateCheckAt)
        releaseCache?.let(core.settings::setUpdateReleaseCache)
    }
}

// ---------------------------------------------------------------------------------------------
// Library reload
// ---------------------------------------------------------------------------------------------
