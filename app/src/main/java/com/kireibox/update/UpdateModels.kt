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
    val manifest: UpdateManifest? = null,
    val apkAssetName: String = manifest?.apkAsset.orEmpty(),
    val fallbackVersionName: String = "",
) {
    val versionName: String get() = manifest?.versionName ?: fallbackVersionName
    val versionCode: Int? get() = manifest?.versionCode
    val apkAsset: String get() = manifest?.apkAsset ?: apkAssetName
    val hasStrictManifest: Boolean get() = manifest != null
}

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

internal data class KireiVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val channel: String?,
    val channelNumber: Int,
) : Comparable<KireiVersion> {
    override fun compareTo(other: KireiVersion): Int {
        compareValuesBy(this, other, KireiVersion::major, KireiVersion::minor, KireiVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        val rank = channelRank(channel).compareTo(channelRank(other.channel))
        return if (rank != 0) rank else channelNumber.compareTo(other.channelNumber)
    }

    companion object {
        private val pattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(alpha|beta|rc)\\.(\\d+))?$", RegexOption.IGNORE_CASE)

        fun parse(value: String?): KireiVersion? {
            val match = pattern.matchEntire(value?.trim().orEmpty()) ?: return null
            return KireiVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                channel = match.groupValues[4].lowercase().ifBlank { null },
                channelNumber = match.groupValues[5].toIntOrNull() ?: 0,
            )
        }

        fun normalize(value: String?): String? = parse(value)?.let { version ->
            buildString {
                append(version.major).append('.').append(version.minor).append('.').append(version.patch)
                version.channel?.let { append('-').append(it).append('.').append(version.channelNumber) }
            }
        }

        private fun channelRank(channel: String?): Int = when (channel) {
            "alpha" -> 0
            "beta" -> 1
            "rc" -> 2
            null -> 3
            else -> -1
        }
    }
}

internal object UpdateReleaseCacheCodec {
    fun encode(release: UpdateRelease?): String {
        if (release == null) return ""
        return JSONObject().apply {
            put("releaseId", release.releaseId)
            put("tagName", release.tagName)
            put("releaseName", release.releaseName)
            put("notes", release.notes)
            put("pageUrl", release.pageUrl)
            put("apkUrl", release.apkUrl)
            put("apkSize", release.apkSize)
            put("apkAssetName", release.apkAssetName)
            put("fallbackVersionName", release.fallbackVersionName)
            release.manifest?.let { manifest ->
                put("manifest", JSONObject().apply {
                    put("schemaVersion", 1)
                    put("packageName", manifest.packageName)
                    put("versionCode", manifest.versionCode)
                    put("versionName", manifest.versionName)
                    put("minSdk", manifest.minSdk)
                    put("abis", org.json.JSONArray(manifest.abis))
                    put("apkAsset", manifest.apkAsset)
                    put("sha256", manifest.sha256)
                    put("signingCertSha256", manifest.signingCertSha256)
                })
            }
        }.toString()
    }

    fun decode(value: String?): UpdateRelease? = runCatching {
        if (value.isNullOrBlank()) return null
        val json = JSONObject(value)
        val manifest = json.optJSONObject("manifest")?.let { UpdateManifestCodec.parse(it.toString()) }
        val fallbackVersion = json.optString("fallbackVersionName")
        require(manifest != null || KireiVersion.parse(fallbackVersion) != null)
        UpdateRelease(
            releaseId = json.getLong("releaseId"),
            tagName = json.getString("tagName"),
            releaseName = json.getString("releaseName"),
            notes = json.optString("notes"),
            pageUrl = json.getString("pageUrl"),
            apkUrl = json.getString("apkUrl"),
            apkSize = json.optLong("apkSize", 0L).coerceAtLeast(0L),
            manifest = manifest,
            apkAssetName = json.optString("apkAssetName"),
            fallbackVersionName = fallbackVersion,
        ).also { require(it.apkAsset.endsWith(".apk", ignoreCase = true)) }
    }.getOrNull()
}

internal object UpdateSelectionPolicy {
    fun select(
        installedVersionCode: Int,
        ignoredVersionCode: Int,
        releases: List<UpdateRelease>,
        installedVersionName: String = "",
    ): UpdateCheckResult {
        if (releases.isEmpty()) return UpdateCheckResult.NoPublishedRelease
        val installedSemantic = KireiVersion.parse(installedVersionName)
        val latest = releases.maxWithOrNull(
            compareBy<UpdateRelease> { KireiVersion.parse(it.versionName) }
                .thenBy { it.versionCode ?: Int.MIN_VALUE },
        )
            ?: return UpdateCheckResult.NoPublishedRelease
        val newer = latest.manifest?.let {
            it.versionCode > installedVersionCode && it.versionCode > ignoredVersionCode
        } ?: run {
            val releaseSemantic = KireiVersion.parse(latest.versionName)
            releaseSemantic != null && installedSemantic != null && releaseSemantic > installedSemantic
        }
        return if (!newer) {
            UpdateCheckResult.UpToDate
        } else {
            UpdateCheckResult.Available(latest)
        }
    }
}
