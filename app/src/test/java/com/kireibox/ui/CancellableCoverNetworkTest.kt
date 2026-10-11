package com.kireibox.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class CancellableCoverNetworkTest {
    @Test
    fun `cancelling a slow preview deletes its temporary file and never caches a result`() =
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .body(Buffer().write(ByteArray(256 * 1024)))
                    .throttleBody(1024, 100, TimeUnit.MILLISECONDS)
                    .build(),
            )
            val directory = Files.createTempDirectory("cover-cancel-").toFile()
            val cache = mutableMapOf<String, String>()
            val coordinator =
                CoverLoadCoordinator<String, String>(
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
                    readCache = cache::get,
                    writeCache = cache::put,
                )

            try {
                runBlocking {
                    val job =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            coordinator.load("hero") {
                                downloadCoverPreview(
                                    client = OkHttpClient(),
                                    url = server.url("/slow").toString(),
                                    directory = directory,
                                    maxBytes = 1024 * 1024,
                                )?.let { "bitmap" }
                            }
                        }
                    assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                    while (directory.listFiles().isNullOrEmpty()) delay(10)
                    job.cancelAndJoin()
                    withTimeout(1_000) {
                        while (!directory.listFiles().isNullOrEmpty()) delay(10)
                    }
                }
                assertTrue(directory.listFiles().isNullOrEmpty())
                assertEquals(null, cache["hero"])
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun `direct cancellation of an active preview removes its temporary file`() =
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .body(Buffer().write(ByteArray(256 * 1024)))
                    .throttleBody(1024, 100, TimeUnit.MILLISECONDS)
                    .build(),
            )
            val directory = Files.createTempDirectory("cover-direct-cancel-").toFile()

            try {
                runBlocking {
                    val job =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            downloadCoverPreview(OkHttpClient(), server.url("/direct").toString(), directory, 1024 * 1024)
                        }
                    assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
                    while (directory.listFiles().isNullOrEmpty()) delay(10)
                    job.cancelAndJoin()
                    withTimeout(1_000) {
                        while (!directory.listFiles().isNullOrEmpty()) delay(10)
                    }
                }
            } finally {
                directory.deleteRecursively()
            }
        }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
