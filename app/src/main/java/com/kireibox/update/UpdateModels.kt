package com.kireibox.update

import org.json.JSONObject

internal const val KIREIBOX_PACKAGE_NAME = "com.kireibox.app"
internal const val UPDATE_MANIFEST_ASSET_NAME = "kireibox-update.json"

data class UpdateManifest(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val abis: List<String>,
    val apkAsset: String,
    val sha256: String,
    val signingCertSha256: String,
)

data class UpdateRelease(
    val releaseId: Long,
    val tagName: String,
    val releaseName: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkSize: Long,
    val manifest: UpdateManifest,
)

internal sealed interface UpdateCheckResult {
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object NoPublishedRelease : UpdateCheckResult
    data object NotModified : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data class Checking(val manual: Boolean) : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(
        val release: UpdateRelease,
        val bytesRead: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : UpdateUiState
    data class ReadyToInstall(val release: UpdateRelease, val apkPath: String) : UpdateUiState
    data class Error(val message: String, val release: UpdateRelease? = null) : UpdateUiState
}

internal object UpdateManifestCodec {
    private val sha256Pattern = Regex("^[0-9a-f]{64}$")

    fun parse(json: String): UpdateManifest {
        val value = JSONObject(json)
        require(value.optInt("schemaVersion", -1) == 1) { "不支持的更新清单版本" }
        val packageName = value.getString("packageName").trim()
        require(packageName == KIREIBOX_PACKAGE_NAME) { "更新包名不匹配" }
        val versionCode = value.getInt("versionCode")
        require(versionCode > 0) { "更新版本号无效" }
        val versionName = value.getString("versionName").trim()
        require(versionName.isNotEmpty()) { "更新版本名称为空" }
        val minSdk = value.getInt("minSdk")
        require(minSdk >= 1) { "更新最低系统版本无效" }
        val abiArray = value.getJSONArray("abis")
        val abis = buildList {
            for (index in 0 until abiArray.length()) {
                abiArray.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinct()
        require(abis.isNotEmpty()) { "更新 ABI 为空" }
        val apkAsset = value.getString("apkAsset").trim()
        require(apkAsset.endsWith(".apk", ignoreCase = true) && '/' !in apkAsset && '\\' !in apkAsset) {
            "更新 APK 资产名称无效"
        }
        val sha256 = normalizeDigest(value.getString("sha256"))
        require(sha256Pattern.matches(sha256)) { "更新 APK SHA-256 无效" }
        val signer = normalizeDigest(value.getString("signingCertSha256"))
        require(sha256Pattern.matches(signer)) { "更新签名 SHA-256 无效" }
        return UpdateManifest(
            packageName = packageName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            abis = abis,
            apkAsset = apkAsset,
            sha256 = sha256,
            signingCertSha256 = signer,
        )
    }

    fun normalizeDigest(value: String): String = value
        .trim()
        .removePrefix("sha256:")
        .replace(":", "")
        .lowercase()
}

internal object UpdateSelectionPolicy {
    fun select(
        installedVersionCode: Int,
        ignoredVersionCode: Int,
        releases: List<UpdateRelease>,
    ): UpdateCheckResult {
        if (releases.isEmpty()) return UpdateCheckResult.NoPublishedRelease
        val latest = releases.maxByOrNull { it.manifest.versionCode }
            ?: return UpdateCheckResult.NoPublishedRelease
        return if (
            latest.manifest.versionCode <= installedVersionCode ||
            latest.manifest.versionCode <= ignoredVersionCode
        ) {
            UpdateCheckResult.UpToDate
        } else {
            UpdateCheckResult.Available(latest)
        }
    }
}
