package com.kireibox.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal data class UpdateCheckResponse(
    val result: UpdateCheckResult,
    val etag: String? = null,
    val cachedRelease: UpdateRelease? = null,
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
        installedVersionName: String = "",
        persistedRelease: UpdateRelease? = null,
        ignoredVersionName: String = "",
    ): UpdateCheckResponse =
        withContext(Dispatchers.IO) {
            runCatching {
                // Only skip pre-releases when we know the installed build is a stable one; if the
                // installed version cannot be parsed we keep the old behaviour rather than hiding releases.
                val installed = KireiVersion.parse(installedVersionName)
                val allowPrerelease = installed == null || installed.channel != null
                val request =
                    Request.Builder()
                        .url(releasesUrl)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "KireiBox-Android-Updater")
                        .apply { etag?.takeIf(String::isNotBlank)?.let { header("If-None-Match", it) } }
                        .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 304) {
                        val cached = trustedCachedRelease(persistedRelease)
                        val result =
                            cached?.let {
                                UpdateSelectionPolicy.select(
                                    installedVersionCode,
                                    ignoredVersionCode,
                                    listOf(it),
                                    installedVersionName,
                                    ignoredVersionName,
                                )
                            } ?: cachedResult ?: UpdateCheckResult.NotModified
                        cachedResult = result
                        return@use UpdateCheckResponse(result, etag, cached)
                    }
                    if (!response.isSuccessful) {
                        return@use UpdateCheckResponse(
                            UpdateCheckResult.Failed("GitHub 更新检查失败（HTTP ${response.code}）"),
                            response.header("ETag") ?: etag,
                        )
                    }
                    val releases = parseReleases(response.body.string(), allowPrerelease)
                    val result =
                        UpdateSelectionPolicy.select(
                            installedVersionCode = installedVersionCode,
                            ignoredVersionCode = ignoredVersionCode,
                            releases = releases,
                            installedVersionName = installedVersionName,
                            ignoredVersionName = ignoredVersionName,
                        )
                    cachedResult = result
                    val newest =
                        releases.maxWithOrNull(
                            compareBy<UpdateRelease> { KireiVersion.parse(it.versionName) }
                                .thenBy { it.versionCode ?: Int.MIN_VALUE },
                        )
                    UpdateCheckResponse(
                        result = result,
                        etag = response.header("ETag") ?: etag,
                        cachedRelease = newest,
                    )
                }
            }.getOrElse { error ->
                UpdateCheckResponse(
                    UpdateCheckResult.Failed(error.message?.takeIf(String::isNotBlank) ?: "无法连接 GitHub"),
                    etag,
                )
            }
        }

    private fun parseReleases(
        json: String,
        allowPrerelease: Boolean
    ): List<UpdateRelease> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val release = array.optJSONObject(index) ?: continue
                if (release.optBoolean("draft", false)) continue
                // Stable installs must not be pushed a pre-release; a pre-release install may take one.
                if (release.optBoolean("prerelease", false) && !allowPrerelease) continue
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
        val manifest = manifestUrl?.let(::fetchManifest)
        if (manifest != null) {
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
                apkAssetName = manifest.apkAsset,
            )
        }
        val fallbackVersion = KireiVersion.normalize(release.optString("tag_name")) ?: return null
        val fallbackApk = selectFallbackApk(assetByName) ?: return null
        return UpdateRelease(
            releaseId = release.optLong("id", 0L),
            tagName = release.optString("tag_name"),
            releaseName = release.optString("name").ifBlank { release.optString("tag_name") },
            notes = release.optString("body"),
            pageUrl = release.optString("html_url"),
            apkUrl = fallbackApk.value.first.toString(),
            apkSize = fallbackApk.value.second,
            manifest = null,
            apkAssetName = fallbackApk.key,
            fallbackVersionName = fallbackVersion,
        )
    }

    private fun selectFallbackApk(assets: Map<String, Pair<HttpUrl, Long>>): Map.Entry<String, Pair<HttpUrl, Long>>? {
        val apks =
            assets.entries.filter { (name, _) ->
                val lower = name.lowercase()
                lower.endsWith(".apk") && "debug" !in lower && "demo" !in lower
            }
        if (apks.isEmpty()) return null
        val arm64 = apks.filter { "arm64" in it.key.lowercase() }
        val candidates = arm64.ifEmpty { apks }
        if (candidates.size == 1) return candidates.single()
        return candidates.singleOrNull { it.key.equals("app-release.apk", ignoreCase = true) }
    }

    private fun fetchManifest(url: HttpUrl): UpdateManifest? =
        runCatching {
            val request =
                Request.Builder()
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

    private fun trustedCachedRelease(release: UpdateRelease?): UpdateRelease? {
        val cached = release ?: return null
        val url = cached.apkUrl.toHttpUrlOrNull() ?: return null
        if (!isTrustedAssetUrl(url)) return null
        if (!cached.apkAsset.endsWith(".apk", ignoreCase = true)) return null
        if (cached.manifest == null && KireiVersion.parse(cached.versionName) == null) return null
        return cached
    }

    private fun String.toHttpUrlOrNull(): HttpUrl? = runCatching { trim().toHttpUrl() }.getOrNull()
}
