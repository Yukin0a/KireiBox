package com.kireibox.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubUpdateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: GitHubUpdateRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository =
            GitHubUpdateRepository(
                client = OkHttpClient(),
                releasesUrl = server.url("/repos/Yukin0a/KireiBox/releases?per_page=20"),
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun emptyPublicReleaseListReturnsNoPublishedReleaseAndStoresEtag() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "release-etag")
                    .setBody("[]"),
            )

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            assertEquals(UpdateCheckResult.NoPublishedRelease, response.result)
            assertEquals("release-etag", response.etag)
        }

    @Test
    fun readsPrereleaseManifestAndSelectsItsApkAsset() =
        runTest {
            val manifestUrl = server.url("/assets/kireibox-update.json")
            val apkUrl = server.url("/assets/KireiBox-alpha14.apk")
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[
                  {
                    "id": 200,
                    "tag_name": "v0.5.0-alpha.14",
                    "name": "Alpha 14",
                    "body": "修复和更新",
                    "html_url": "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.14",
                    "draft": false,
                    "prerelease": true,
                    "assets": [
                      {"name":"kireibox-update.json","browser_download_url":"$manifestUrl","size":512},
                      {"name":"KireiBox-alpha14.apk","browser_download_url":"$apkUrl","size":123456},
                      {"name":"KireiBox-alpha14.apk.sha256","browser_download_url":"$apkUrl.sha256","size":96}
                    ]
                  }
                ]""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson(versionCode = 20)))

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            val available = response.result as UpdateCheckResult.Available
            assertEquals(20, available.release.versionCode)
            assertEquals(apkUrl.toString(), available.release.apkUrl)
            assertEquals(123456L, available.release.apkSize)
        }

    @Test
    fun releaseWithoutChecksumSidecarIsSkipped() =
        runTest {
            val manifestUrl = server.url("/assets/kireibox-update.json")
            val apkUrl = server.url("/assets/KireiBox-alpha14.apk")
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{
                  "id": 201,
                  "tag_name": "v0.5.0-alpha.14",
                  "name": "Alpha 14",
                  "body": "missing checksum sidecar",
                  "html_url": "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.14",
                  "draft": false,
                  "prerelease": true,
                  "assets": [
                    {"name":"kireibox-update.json","browser_download_url":"$manifestUrl","size":512},
                    {"name":"KireiBox-alpha14.apk","browser_download_url":"$apkUrl","size":123456}
                  ]
                }]""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody(manifestJson(versionCode = 20)))

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            assertEquals(UpdateCheckResult.NoPublishedRelease, response.result)
        }

    @Test
    fun bareOfficialApkReleaseIsUsableAndComparedByTag() =
        runTest {
            val apkUrl = server.url("/assets/app-release.apk")
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{
                  "id": 190,
                  "tag_name": "v0.5.0-alpha.13",
                  "name": "0.5.0-alpha.13",
                  "body": "existing public release",
                  "html_url": "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.13",
                  "draft": false,
                  "prerelease": true,
                  "assets": [
                    {"name":"app-release.apk","browser_download_url":"$apkUrl","size":57766345}
                  ]
                }]""",
                ),
            )

            val response =
                repository.check(
                    installedVersionCode = 18,
                    ignoredVersionCode = 0,
                    etag = null,
                    installedVersionName = "0.5.0-alpha.12",
                )

            val release = (response.result as UpdateCheckResult.Available).release
            assertEquals("0.5.0-alpha.13", release.versionName)
            assertEquals("app-release.apk", release.apkAsset)
            assertEquals(false, release.hasStrictManifest)
        }

    @Test
    fun alpha15TreatsExistingAlpha13BareReleaseAsUpToDate() =
        runTest {
            val apkUrl = server.url("/assets/app-release.apk")
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[{"id":190,"tag_name":"v0.5.0-alpha.13","name":"Alpha 13","body":"",""" +
                        """"html_url":"https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.13",""" +
                        """"draft":false,"prerelease":true,"assets":[{"name":"app-release.apk","browser_download_url":"$apkUrl","size":12}]}]""",
                ),
            )

            val response = repository.check(22, 0, null, "0.5.0-alpha.15")

            assertEquals(UpdateCheckResult.UpToDate, response.result)
        }

    @Test
    fun persistedReleaseRestoresSelectionAfterProcessRestartAnd304() =
        runTest {
            val cached =
                UpdateRelease(
                    releaseId = 190,
                    tagName = "v0.5.0-alpha.13",
                    releaseName = "Alpha 13",
                    notes = "notes",
                    pageUrl = "https://github.com/Yukin0a/KireiBox/releases/tag/v0.5.0-alpha.13",
                    apkUrl = server.url("/assets/app-release.apk").toString(),
                    apkSize = 12,
                    apkAssetName = "app-release.apk",
                    fallbackVersionName = "0.5.0-alpha.13",
                )
            server.enqueue(MockResponse().setResponseCode(304))

            val response = repository.check(18, 0, "etag", "0.5.0-alpha.12", cached)

            assertTrue(response.result is UpdateCheckResult.Available)
            assertEquals(190L, response.cachedRelease?.releaseId)
        }

    @Test
    fun draftAndMalformedReleasesAreSkipped() =
        runTest {
            val manifestUrl = server.url("/assets/bad-manifest.json")
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """[
                  {"id":1,"tag_name":"draft","name":"draft","body":"","html_url":"https://example.com","draft":true,"prerelease":true,"assets":[]},
                  {"id":2,"tag_name":"bad","name":"bad","body":"","html_url":"https://example.com","draft":false,"prerelease":true,"assets":[
                    {"name":"kireibox-update.json","browser_download_url":"$manifestUrl","size":100}
                  ]}
                ]""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            assertEquals(UpdateCheckResult.NoPublishedRelease, response.result)
        }

    @Test
    fun conditionalRequestReturnsNotModifiedWithoutFetchingAssets() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(304))

            val response = repository.check(19, ignoredVersionCode = 0, etag = "old-etag")

            assertEquals(UpdateCheckResult.NotModified, response.result)
            assertEquals("old-etag", server.takeRequest().getHeader("If-None-Match"))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun conditionalRequestReusesResultCachedInCurrentProcess() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "same").setBody("[]"))
            server.enqueue(MockResponse().setResponseCode(304))

            val first = repository.check(19, ignoredVersionCode = 0, etag = null)
            val second = repository.check(19, ignoredVersionCode = 0, etag = first.etag)

            assertEquals(UpdateCheckResult.NoPublishedRelease, second.result)
        }

    @Test
    fun invalidReleasesJsonIsReportedAsFailure() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            assertTrue(response.result is UpdateCheckResult.Failed)
        }

    @Test
    fun rateLimitResponseIsReportedAsFailureWithoutThrowing() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(403).setBody("rate limited"))

            val response = repository.check(19, ignoredVersionCode = 0, etag = null)

            assertTrue(response.result is UpdateCheckResult.Failed)
        }

    private fun manifestJson(versionCode: Int) =
        """{
      "schemaVersion": 1,
      "packageName": "com.kireibox.app",
      "versionCode": $versionCode,
      "versionName": "0.5.0-alpha.14",
      "minSdk": 26,
      "abis": ["arm64-v8a"],
      "apkAsset": "KireiBox-alpha14.apk",
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "signingCertSha256": "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
    }"""
}
