package com.kireibox.update

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateDownloadTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamsApkToPrivateDirectoryAndReportsProgress() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("hello update"))
            val progress = mutableListOf<UpdateDownloadProgress>()
            val downloader =
                UpdateDownloader(
                    client = OkHttpClient(),
                    directory = temporaryFolder.newFolder("updates"),
                    maxBytes = 1024,
                )

            val file = downloader.download(release(), progress::add)

            assertEquals("hello update", file.readText())
            assertTrue(progress.isNotEmpty())
            assertEquals(12L, progress.last().bytesRead)
            assertEquals(12L, progress.last().totalBytes)
            assertFalse(file.name.endsWith(".part"))
        }

    @Test
    fun checksumMismatchDeletesPartialAndFinalFiles() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("tampered"))
            val directory = temporaryFolder.newFolder("checksum")
            val downloader = UpdateDownloader(OkHttpClient(), directory, maxBytes = 1024)

            val result = runCatching { downloader.download(release(), {}) }

            assertTrue(result.isFailure)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun oversizedResponseIsRejectedBeforeWriting() =
        runTest {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("x".repeat(64)),
            )
            val directory = temporaryFolder.newFolder("oversized")
            val downloader = UpdateDownloader(OkHttpClient(), directory, maxBytes = 32)

            val result = runCatching { downloader.download(release(), {}) }

            assertTrue(result.isFailure)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun cancellationDeletesPartialAndFinalFiles() =
        runBlocking {
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("hello update".repeat(256))
                    .throttleBody(1, 20, java.util.concurrent.TimeUnit.MILLISECONDS),
            )
            val directory = temporaryFolder.newFolder("cancelled")
            val downloader = UpdateDownloader(OkHttpClient(), directory, maxBytes = 16 * 1024)
            val progressSeen = CompletableDeferred<Unit>()
            val job =
                launch {
                    runCatching {
                        downloader.download(release()) { progressSeen.complete(Unit) }
                    }
                }

            withTimeout(5_000) { progressSeen.await() }
            job.cancelAndJoin()

            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }

    private fun release() =
        UpdateRelease(
            releaseId = 20,
            tagName = "v0.5.0-alpha.14",
            releaseName = "Alpha 14",
            notes = "notes",
            pageUrl = "https://example.com/release",
            apkUrl = server.url("/KireiBox-alpha14.apk").toString(),
            apkSize = 12,
            manifest =
                UpdateManifest(
                    packageName = KIREIBOX_PACKAGE_NAME,
                    versionCode = 20,
                    versionName = "0.5.0-alpha.14",
                    minSdk = 26,
                    abis = listOf("arm64-v8a"),
                    apkAsset = "KireiBox-alpha14.apk",
                    sha256 = "ed70ed98d448ff57ee2b137e52bfcc54729095de8dd049f8432c1bbc06581687",
                    signingCertSha256 = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
                ),
        )
}
