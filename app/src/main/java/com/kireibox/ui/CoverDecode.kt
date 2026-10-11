package com.kireibox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.role
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

// Cover bitmap caches, decoding pipeline and the rememberCover entry point.

internal object CoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.THUMBNAIL)) {
    override fun sizeOf(
        key: String,
        value: Bitmap
    ): Int = value.allocationByteCount / 1024
}

internal object LibraryCardCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.LIBRARY_CARD)) {
    override fun sizeOf(
        key: String,
        value: Bitmap
    ): Int = value.allocationByteCount / 1024
}

internal object HeroCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.HERO)) {
    override fun sizeOf(
        key: String,
        value: Bitmap
    ): Int = value.allocationByteCount / 1024
}

internal object BackdropCoverBitmapCache : LruCache<String, Bitmap>(Alpha11KireiBoxAppIntegration.cacheBudgetKib(CoverImageRole.IMMERSIVE_BACKDROP)) {
    override fun sizeOf(
        key: String,
        value: Bitmap
    ): Int = value.allocationByteCount / 1024
}

internal val CoverDecodeSemaphore = Semaphore(CoverDecodePolicy.MAX_PARALLEL_DECODES)

internal val CoverLoadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
internal val CoverLoads =
    CoverLoadCoordinator<CoverImageRequest, Bitmap>(
        scope = CoverLoadScope,
        readCache = ::cachedCover,
        writeCache = ::putCachedCover,
    )

internal val CoverPreviewClient =
    okhttp3.OkHttpClient.Builder()
        .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

internal suspend fun releaseLauncherCoverResources() {
    CoverLoads.cancelAll()
    CoverPreviewClient.dispatcher.cancelAll()
    CoverPreviewClient.connectionPool.evictAll()
    CoverBitmapCache.evictAll()
    LibraryCardCoverBitmapCache.evictAll()
    HeroCoverBitmapCache.evictAll()
    BackdropCoverBitmapCache.evictAll()
    ImmersiveBackdropBlurCache.evictAll()
    CoverToneExtractor.clearCache()
}

@Composable
internal fun rememberCover(
    uri: String?,
    role: CoverImageRole = CoverImageRole.THUMBNAIL,
    componentWidthPx: Int = CoverDecodePolicy.TARGET_WIDTH_PX,
    componentHeightPx: Int = CoverDecodePolicy.TARGET_HEIGHT_PX,
    blurred: Boolean = false,
    sourceKey: String = "",
): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val request =
        remember(uri, role, componentWidthPx, componentHeightPx, blurred, sourceKey) {
            uri?.takeIf { it.isNotBlank() }?.let {
                CoverDecodePolicy.request(it, role, componentWidthPx, componentHeightPx, blurred, sourceKey)
            }
        }

    data class LoadedCover(val displayKey: CoverDisplayKey, val bitmap: ImageBitmap)
    val loaded =
        produceState<LoadedCover?>(null, request?.cacheKey) {
            if (value?.displayKey != request?.displayKey) value = null
            value =
                withContext(Dispatchers.IO) {
                    if (request == null) return@withContext null
                    val decoded =
                        CoverLoads.load(request) {
                            CoverDecodeSemaphore.withPermit { decodeSampledCover(context, request) }
                        } ?: return@withContext null
                    LoadedCover(request.displayKey, decoded.asImageBitmap())
                }
        }.value
    return loaded?.takeIf { it.displayKey == request?.displayKey }?.bitmap
}

internal fun coverCacheKey(
    uri: String?,
    role: CoverImageRole,
    componentWidthPx: Int,
    componentHeightPx: Int,
    blurred: Boolean = false,
): String? =
    uri?.takeIf { it.isNotBlank() }?.let {
        CoverDecodePolicy.request(it, role, componentWidthPx, componentHeightPx, blurred).cacheKey
    }

@Composable
internal fun HeroCoverPrefetch(
    pages: List<Game>,
    currentPage: Int,
    componentWidthPx: Int,
    componentHeightPx: Int,
) {
    val context = LocalContext.current.applicationContext
    val nsfwBlurEnabled = LocalNsfwBlurEnabled.current
    LaunchedEffect(pages, currentPage, componentWidthPx, componentHeightPx, nsfwBlurEnabled) {
        withContext(Dispatchers.IO) {
            SpotlightCarouselPolicy.prefetchIndexes(currentPage, pages.size).drop(1).forEach { index ->
                val source = pages[index].coverUri ?: return@forEach
                val request =
                    CoverDecodePolicy.request(
                        uri = source,
                        role = CoverImageRole.HERO,
                        componentWidthPx = componentWidthPx,
                        componentHeightPx = componentHeightPx,
                        blurred = pages[index].nsfw && nsfwBlurEnabled,
                    )
                CoverLoads.load(request) {
                    CoverDecodeSemaphore.withPermit { decodeSampledCover(context, request) }
                }
            }
        }
    }
}

internal fun cachedCover(request: CoverImageRequest): Bitmap? =
    when (request.role) {
        CoverImageRole.THUMBNAIL -> CoverBitmapCache.get(request.cacheKey)
        CoverImageRole.LIBRARY_CARD -> LibraryCardCoverBitmapCache.get(request.cacheKey)
        CoverImageRole.HERO -> HeroCoverBitmapCache.get(request.cacheKey)
        CoverImageRole.IMMERSIVE_BACKDROP -> BackdropCoverBitmapCache.get(request.cacheKey)
    }

internal fun putCachedCover(
    request: CoverImageRequest,
    bitmap: Bitmap
) {
    when (request.role) {
        CoverImageRole.THUMBNAIL -> CoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.LIBRARY_CARD -> LibraryCardCoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.HERO -> HeroCoverBitmapCache.put(request.cacheKey, bitmap)
        CoverImageRole.IMMERSIVE_BACKDROP -> BackdropCoverBitmapCache.put(request.cacheKey, bitmap)
    }
}

internal fun requiresContentResolverStream(scheme: String?): Boolean = scheme == "content" || scheme == "android.resource"

internal fun constrainDecodedCover(
    bitmap: Bitmap?,
    request: CoverImageRequest
): Bitmap? {
    if (bitmap == null || request.role != CoverImageRole.LIBRARY_CARD) return bitmap
    val size =
        CoverDecodePolicy.libraryCardScaledSize(
            decodedWidthPx = bitmap.width,
            decodedHeightPx = bitmap.height,
            targetWidthPx = request.targetWidthPx,
            targetHeightPx = request.targetHeightPx,
        ) ?: return bitmap
    if (size.widthPx == bitmap.width && size.heightPx == bitmap.height) return bitmap
    val scaled = Bitmap.createScaledBitmap(bitmap, size.widthPx, size.heightPx, true)
    if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
    return scaled
}

internal fun finishDecodedCover(
    context: Context,
    bitmap: Bitmap?,
    request: CoverImageRequest
): Bitmap? {
    val constrained = constrainDecodedCover(bitmap, request) ?: return null
    return if (request.blurred) CoverBlurRenderer.apply(context, constrained) else constrained
}

internal suspend fun decodeSampledCover(
    context: Context,
    request: CoverImageRequest
): Bitmap? {
    return try {
        currentCoroutineContext().ensureActive()
        val uri = android.net.Uri.parse(request.uri)
        if (uri.scheme == "http" || uri.scheme == "https") {
            return decodeNetworkCover(context, request)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (requiresContentResolverStream(uri.scheme)) {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } else {
            BitmapFactory.decodeFile(uri.path ?: request.uri, bounds)
        }
        currentCoroutineContext().ensureActive()
        val sample = CoverDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight, request) ?: return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val bitmap =
            if (requiresContentResolverStream(uri.scheme)) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            } else {
                BitmapFactory.decodeFile(uri.path ?: request.uri, options)
            }
        currentCoroutineContext().ensureActive()
        finishDecodedCover(context, bitmap, request)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }
}

internal suspend fun decodeNetworkCover(
    context: Context,
    coverRequest: CoverImageRequest
): Bitmap? {
    val temporary =
        downloadCoverPreview(
            client = CoverPreviewClient,
            url = coverRequest.uri,
            directory = File(context.cacheDir, "cover-previews"),
            maxBytes = CoverDecodePolicy.MAX_NETWORK_BYTES,
            sourceKey = coverRequest.sourceKey,
        ) ?: return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temporary.path, bounds)
        currentCoroutineContext().ensureActive()
        val sample = CoverDecodePolicy.sampleSize(bounds.outWidth, bounds.outHeight, coverRequest) ?: return null
        val bitmap =
            BitmapFactory.decodeFile(
                temporary.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        currentCoroutineContext().ensureActive()
        finishDecodedCover(context, bitmap, coverRequest)
    } finally {
        temporary.delete()
    }
}
