package com.yuki.yukihub.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.InputStream
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 简易图片加载器：在 IO 线程解码，主线程回填 ImageView。
 * 通过 WeakHashMap 跟踪每个 ImageView 最近一次请求 id，避免复用错位。
 */
object SafeImageLoader {
    fun interface Callback {
        fun onResult(success: Boolean)
    }

    private val REQUEST_IDS = AtomicLong()
    private val ACTIVE_REQUESTS: WeakHashMap<ImageView, Long> = WeakHashMap()

    @JvmStatic
    fun loadUri(imageView: ImageView?, uriText: String?, callback: Callback?): Boolean {
        if (imageView == null) return false
        val requestId = REQUEST_IDS.incrementAndGet()
        synchronized(ACTIVE_REQUESTS) { ACTIVE_REQUESTS[imageView] = requestId }
        imageView.setImageDrawable(null)
        // 严格等价原 Java: uriText == null || uriText.trim().isEmpty()
        // 不使用 isNullOrBlank() —— 后者按 Char.isWhitespace() 判定 Unicode 空白(如全角空格/NBSP)，
        // 而 Java trim() 仅去 <= U+0020；保留原语义以避免边界差异
        if (uriText == null || uriText.trim().isEmpty()) return false
        val context = imageView.context.applicationContext
        val requestedWidth = imageView.width
        val requestedHeight = imageView.height
        val uri: Uri = try {
            Uri.parse(uriText.trim())
        } catch (ignored: Throwable) {
            return false
        }
        AppExecutors.runOnIo {
            val bitmap = decodeSampled(context, uri, requestedWidth, requestedHeight)
            RxMainScheduler.post {
                val current: Boolean
                synchronized(ACTIVE_REQUESTS) {
                    val active = ACTIVE_REQUESTS[imageView]
                    current = active != null && active == requestId
                    if (current) ACTIVE_REQUESTS.remove(imageView)
                }
                if (!current) {
                    bitmap?.recycle()
                    return@post
                }
                if (bitmap != null) imageView.setImageBitmap(bitmap) else imageView.setImageDrawable(null)
                callback?.onResult(bitmap != null)
            }
        }
        return true
    }

    private fun decodeSampled(
        context: android.content.Context,
        uri: Uri,
        requestedWidth: Int,
        requestedHeight: Int
    ): Bitmap? {
        val targetWidth = if (requestedWidth > 0) requestedWidth else 512
        val targetHeight = if (requestedHeight > 0) requestedHeight else 512
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val pixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
            if (pixels > 100_000_000L) return null
            var sample = 1
            while (bounds.outWidth / sample > targetWidth * 2 ||
                bounds.outHeight / sample > targetHeight * 2
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, sample) }
            context.contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (ignored: Throwable) {
            null
        }
    }
}
