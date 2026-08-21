package com.kireibox.cover

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume

data class CoverDownloadProgress(val bytesRead: Long, val totalBytes: Long?)

internal class CoverDownloader(
    private val client: OkHttpClient,
    private val maxBytes: Long = 20L * 1024L * 1024L,
) {
    suspend fun download(
        url: String,
        tempFile: File,
        onProgress: (CoverDownloadProgress) -> Unit,
    ): Result<File> = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation {
            call.cancel()
            tempFile.delete()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                tempFile.delete()
                if (continuation.isActive) continuation.resume(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        check(it.isSuccessful) { "HTTP ${it.code}" }
                        val body = it.body ?: error("Cover response body is empty")
                        val totalBytes = body.contentLength().takeIf { length -> length >= 0L }
                        check(totalBytes == null || totalBytes <= maxBytes) { "Cover file exceeds size limit" }
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                var bytesRead = 0L
                                var lastReportedBytes = 0L
                                var lastReportedAt = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (!continuation.isActive) throw CancellationException("Cover download cancelled")
                                    bytesRead += count
                                    check(bytesRead <= maxBytes) { "Cover file exceeds size limit" }
                                    output.write(buffer, 0, count)
                                    val now = System.nanoTime()
                                    if (shouldReport(bytesRead, totalBytes, lastReportedBytes, now, lastReportedAt)) {
                                        onProgress(CoverDownloadProgress(bytesRead, totalBytes))
                                        lastReportedBytes = bytesRead
                                        lastReportedAt = now
                                    }
                                }
                                output.fd.sync()
                                if (continuation.isActive && bytesRead != lastReportedBytes) {
                                    onProgress(CoverDownloadProgress(bytesRead, totalBytes))
                                }
                            }
                        }
                    }
                    if (continuation.isActive) continuation.resume(Result.success(tempFile))
                } catch (error: Throwable) {
                    tempFile.delete()
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
            }
        })
    }

    private fun shouldReport(
        bytesRead: Long,
        totalBytes: Long?,
        lastReportedBytes: Long,
        nowNanos: Long,
        lastReportedAtNanos: Long,
    ): Boolean {
        if (lastReportedBytes == 0L) return true
        if (nowNanos - lastReportedAtNanos >= PROGRESS_INTERVAL_NANOS) return true
        return totalBytes != null && bytesRead - lastReportedBytes >= (totalBytes / 100L).coerceAtLeast(1L)
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        const val PROGRESS_INTERVAL_NANOS = 80_000_000L
    }
}
