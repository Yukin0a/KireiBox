package com.kireibox.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

internal data class UpdateCheckResponse(
    val result: UpdateCheckResult,
    val etag: String? = null,
)

internal class GitHubUpdateRepository(
    private val client: OkHttpClient,
    private val releasesUrl: HttpUrl =
        "https://api.github.com/repos/Yukin0a/KireiBox/releases?per_page=20".toHttpUrl(),
) {
    private var cachedResult: UpdateCheckResult? = null

    suspend fun check(
        installedVersionCode: Int,
        ignoredVersionCode: Int,
        etag: String?,
    ): UpdateCheckResponse = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releasesUrl)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "KireiBox-Android-Updater")
                .apply { etag?.takeIf(String::isNotBlank)?.let { header("If-None-Match", it) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 304) {
                    return@use UpdateCheckResponse(cachedResult ?: UpdateCheckResult.NotModified, etag)
                }
                if (!response.isSuccessful) {
                    return@use UpdateCheckResponse(
                        UpdateCheckResult.Failed("GitHub 更新检查失败（HTTP ${response.code}）"),
                        response.header("ETag") ?: etag,
                    )
                }
                val releases = parseReleases(response.body.string())
                val result = UpdateSelectionPolicy.select(
                        installedVersionCode = installedVersionCode,
                        ignoredVersionCode = ignoredVersionCode,
                        releases = releases,
                    )
                cachedResult = result
                UpdateCheckResponse(
                    result = result,
                    etag = response.header("ETag") ?: etag,
                )
            }
        }.getOrElse { error ->
            UpdateCheckResponse(
                UpdateCheckResult.Failed(error.message?.takeIf(String::isNotBlank) ?: "无法连接 GitHub"),
                etag,
            )
        }
    }

    private fun parseReleases(json: String): List<UpdateRelease> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val release = array.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                parseRelease(release)?.let(::add)
            }
        }
    }

    private fun parseRelease(release: JSONObject): UpdateRelease? {
        val assets = release.optJSONArray("assets") ?: return null
        var manifestUrl: HttpUrl? = null
        val assetByName = linkedMapOf<String, Pair<HttpUrl, Long>>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name").trim()
            val url = asset.optString("browser_download_url").toHttpUrlOrNull() ?: continue
            if (!isTrustedAssetUrl(url)) continue
            assetByName[name] = url to asset.optLong("size", 0L).coerceAtLeast(0L)
            if (name == UPDATE_MANIFEST_ASSET_NAME) manifestUrl = url
        }
        val manifest = manifestUrl?.let(::fetchManifest) ?: return null
        val apk = assetByName[manifest.apkAsset] ?: return null
        if (assetByName["${manifest.apkAsset}.sha256"] == null) return null
        return UpdateRelease(
            releaseId = release.optLong("id", 0L),
            tagName = release.optString("tag_name"),
            releaseName = release.optString("name").ifBlank { release.optString("tag_name") },
            notes = release.optString("body"),
            pageUrl = release.optString("html_url"),
            apkUrl = apk.first.toString(),
            apkSize = apk.second,
            manifest = manifest,
        )
    }

    private fun fetchManifest(url: HttpUrl): UpdateManifest? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "KireiBox-Android-Updater")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            UpdateManifestCodec.parse(response.body.string())
        }
    }.getOrNull()

    private fun isTrustedAssetUrl(url: HttpUrl): Boolean {
        if (releasesUrl.host == "localhost" || releasesUrl.host == "127.0.0.1") {
            return url.host == releasesUrl.host
        }
        return url.isHttps && (url.host == "github.com" || url.host.endsWith(".githubusercontent.com"))
    }

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { trim().toHttpUrl() }.getOrNull()
}
