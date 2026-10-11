package com.kireibox.cover

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class CoverDownloaderTest {
    @Test
    fun knownLengthReportsMonotonicProgressAndFinalTotal() =
        withServer { server ->
            val bytes = "known-length-cover".encodeToByteArray()
            server.enqueue(MockResponse.Builder().body(Buffer().write(bytes)).build())
            val target = temporaryFile()
            val progress = mutableListOf<CoverDownloadProgress>()

            val result = runBlocking { downloader().download(server.url("/cover").toString(), target) { progress += it } }

            assertTrue(result.isSuccess)
            assertEquals(bytes.toList(), target.readBytes().toList())
            assertTrue(progress.zipWithNext().all { (before, after) -> after.bytesRead >= before.bytesRead })
            assertEquals(bytes.size.toLong(), progress.last().bytesRead)
            assertEquals(bytes.size.toLong(), progress.last().totalBytes)
        }

    @Test
    fun unknownLengthKeepsTotalNull() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().chunkedBody(Buffer().writeUtf8("streamed-cover"), 3).build())
            val target = temporaryFile()
            val progress = mutableListOf<CoverDownloadProgress>()

            val result = runBlocking { downloader().download(server.url("/cover").toString(), target) { progress += it } }

            assertTrue(result.isSuccess)
            assertTrue(progress.isNotEmpty())
            assertTrue(progress.all { it.totalBytes == null })
        }

    @Test
    fun responseLargerThanTwentyMiBIsRejectedAndPartFileIsDeleted() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().body(Buffer().write(ByteArray(20 * 1024 * 1024 + 1))).build())
            val target = temporaryFile()

            val result = runBlocking { downloader().download(server.url("/cover").toString(), target) {} }

            assertTrue(result.isFailure)
            assertFalse(target.exists())
        }

    @Test
    fun httpFailureDeletesPartFile() =
        withServer { server ->
            server.enqueue(MockResponse.Builder().code(404).build())
            val target = temporaryFile()

            val result = runBlocking { downloader().download(server.url("/missing").toString(), target) {} }

            assertTrue(result.isFailure)
            assertFalse(target.exists())
        }

    @Test
    fun coroutineCancellationCancelsDownloadAndDeletesPartFile() =
        withServer { server ->
            server.enqueue(
                MockResponse.Builder()
                    .body(Buffer().write(ByteArray(256 * 1024)))
                    .throttleBody(1024, 100, TimeUnit.MILLISECONDS)
                    .build()
            )
            val target = temporaryFile()

            runBlocking {
                val job = launch { downloader().download(server.url("/slow").toString(), target) {} }
                while (!target.exists() || target.length() == 0L) delay(10)
                job.cancel()
                job.join()
            }

            assertFalse(target.exists())
        }

    @Test
    fun cancellationObservedImmediatelyAfterReadDeletesPartFileAfterStreamsClose() {
        val target = deleteOnSecondAttemptFile()
        lateinit var downloadJob: Job
        val client = cancellationAfterFirstReadClient { downloadJob.cancel() }

        runBlocking {
            downloadJob =
                launch {
                    CoverDownloader(client).download("https://covers.invalid/race", target) {}
                }
            downloadJob.join()
        }

        assertFalse(target.exists())
        assertEquals(2, target.deleteAttempts)
    }

    private fun downloader() = CoverDownloader(OkHttpClient())

    private fun cancellationAfterFirstReadClient(onFirstReadReturned: () -> Unit): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(
                    object : ResponseBody() {
                        override fun contentLength(): Long = 1L

                        override fun contentType() = null

                        override fun source(): BufferedSource =
                            object : ForwardingSource(Buffer().writeUtf8("x")) {
                                private var cancelled = false

                                override fun read(
                                    sink: Buffer,
                                    byteCount: Long
                                ): Long {
                                    val count = super.read(sink, byteCount)
                                    if (count > 0L && !cancelled) {
                                        cancelled = true
                                        onFirstReadReturned()
                                    }
                                    return count
                                }
                            }.buffer()
                    }
                )
                .build()
        }.build()

    private fun temporaryFile(): File = File.createTempFile("cover-downloader-", ".part").also { it.delete() }

    private fun deleteOnSecondAttemptFile(): DeleteOnSecondAttemptFile {
        val path = File.createTempFile("cover-downloader-", ".part").also { it.delete() }.path
        return DeleteOnSecondAttemptFile(path)
    }

    private class DeleteOnSecondAttemptFile(path: String) : File(path) {
        var deleteAttempts = 0
            private set

        override fun delete(): Boolean {
            deleteAttempts += 1
            return deleteAttempts > 1 && super.delete()
        }
    }

    private fun withServer(block: (MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            server.start()
            block(server)
        }
    }
}
