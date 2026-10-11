package com.kireibox.ui

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Downloads a bounded preview into [directory], deleting its temporary file on every non-success path. */
internal suspend fun downloadCoverPreview(
    client: OkHttpClient,
    url: String,
    directory: File,
    maxBytes: Long,
    sourceKey: String = "",
): File? {
    return suspendCancellableCoroutine { continuation ->
        // Per-source image-host policy: VNDB needs its cookie, Bangumi needs the mirror,
        // and both need a browser UA instead of the metadata API's identity.
        val request =
            com.yuki.yukihub.metadata.policy.CoverImagePolicy.apply(
                Request.Builder().url(url).build(),
                com.yuki.yukihub.metadata.policy.CoverSourceKey.from(sourceKey),
            )
        val call = client.newCall(request)
        val temporary = AtomicReference<File?>(null)
        continuation.invokeOnCancellation {
            call.cancel()
            temporary.get()?.delete()
        }
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {
                    temporary.get()?.delete()
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    try {
                        response.use {
                            continuation.ensureActive()
                            val body = it.body
                            if (!it.isSuccessful || body.contentLength() > maxBytes) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            if (!directory.isDirectory && !directory.mkdirs()) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            temporary.set(File.createTempFile("preview-", ".img", directory))
                            body.byteStream().use { input ->
                                temporary.get()!!.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var total = 0L
                                    while (true) {
                                        continuation.ensureActive()
                                        val count = input.read(buffer)
                                        continuation.ensureActive()
                                        if (count <= 0) break
                                        total += count
                                        if (total > maxBytes) {
                                            temporary.get()?.delete()
                                            if (continuation.isActive) continuation.resume(null)
                                            return
                                        }
                                        continuation.ensureActive()
                                        output.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        val completed = temporary.get() ?: return
                        if (continuation.isActive) {
                            continuation.resume(completed) { _, deliveredFile, _ -> deliveredFile.delete() }
                        } else {
                            completed.delete()
                        }
                    } catch (_: CancellationException) {
                        temporary.get()?.delete()
                    } catch (error: Throwable) {
                        temporary.get()?.delete()
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        )
    }
}

private const val BUFFER_SIZE = 16 * 1024

private fun CancellableContinuation<*>.ensureActive() {
    if (!isActive) throw CancellationException("Cover download cancelled")
}
