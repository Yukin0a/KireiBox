package com.kireibox.ui

import androidx.compose.ui.graphics.FilterQuality

internal enum class CoverImageRole { THUMBNAIL, LIBRARY_CARD, HERO, IMMERSIVE_BACKDROP }

internal data class CoverPixelSize(val widthPx: Int, val heightPx: Int)

internal data class CoverImageRequest(
    val uri: String,
    val role: CoverImageRole,
    val targetWidthPx: Int,
    val targetHeightPx: Int,
    val cacheKey: String,
)

/** Limits launcher cover work so rapid scrolling cannot fan out large bitmap allocations. */
internal object CoverImagePolicy {
    const val TARGET_WIDTH_PX = 256
    const val TARGET_HEIGHT_PX = 384
    const val BITMAP_CACHE_KIB = CoverBitmapCaches.ORDINARY_KIB
    const val LIBRARY_CARD_MAX_WIDTH_PX = 1_080
    const val LIBRARY_CARD_MAX_HEIGHT_PX = 1_620
    const val LIBRARY_CARD_BITMAP_CACHE_KIB = CoverBitmapCaches.LIBRARY_CARD_KIB
    const val HERO_BITMAP_CACHE_KIB = CoverBitmapCaches.HERO_KIB
    const val HERO_MAX_WIDTH_PX = 1_080
    const val HERO_MAX_HEIGHT_PX = 1_620
    /** Immersive backdrop caps both dimensions near 720 so the short edge never exceeds it. */
    const val BACKDROP_MAX_EDGE_PX = 720
    const val BACKDROP_BITMAP_CACHE_KIB = CoverBitmapCaches.BACKDROP_KIB
    const val MAX_PARALLEL_DECODES = 2
    const val MAX_NETWORK_BYTES = 8L * 1024L * 1024L
    private const val MAX_SOURCE_PIXELS = 60_000_000L

    fun request(
        uri: String,
        role: CoverImageRole,
        componentWidthPx: Int,
        componentHeightPx: Int,
    ): CoverImageRequest {
        val targetWidth = when (role) {
            CoverImageRole.THUMBNAIL -> TARGET_WIDTH_PX
            CoverImageRole.LIBRARY_CARD -> componentWidthPx.coerceIn(1, LIBRARY_CARD_MAX_WIDTH_PX)
            CoverImageRole.HERO -> componentWidthPx.coerceIn(1, HERO_MAX_WIDTH_PX)
            CoverImageRole.IMMERSIVE_BACKDROP -> componentWidthPx.coerceIn(1, BACKDROP_MAX_EDGE_PX)
        }
        val targetHeight = when (role) {
            CoverImageRole.THUMBNAIL -> TARGET_HEIGHT_PX
            CoverImageRole.LIBRARY_CARD -> componentHeightPx.coerceIn(1, LIBRARY_CARD_MAX_HEIGHT_PX)
            CoverImageRole.HERO -> componentHeightPx.coerceIn(1, HERO_MAX_HEIGHT_PX)
            CoverImageRole.IMMERSIVE_BACKDROP -> componentHeightPx.coerceIn(1, BACKDROP_MAX_EDGE_PX)
        }
        return CoverImageRequest(
            uri = uri,
            role = role,
            targetWidthPx = targetWidth,
            targetHeightPx = targetHeight,
            // The key must identify the exact clamped target: sampleSize() decides
            // from these same dims, so a coarser bucket would let one slot serve
            // bitmaps decoded at a different resolution (review regression).
            cacheKey = "$uri|$role|${targetWidth}x${targetHeight}",
        )
    }

    fun sampleSize(width: Int, height: Int): Int? = sampleSize(width, height, CoverImageRole.THUMBNAIL)

    fun sampleSize(width: Int, height: Int, role: CoverImageRole): Int? {
        val (targetWidth, targetHeight) = when (role) {
            CoverImageRole.THUMBNAIL -> TARGET_WIDTH_PX to TARGET_HEIGHT_PX
            CoverImageRole.LIBRARY_CARD -> LIBRARY_CARD_MAX_WIDTH_PX to LIBRARY_CARD_MAX_HEIGHT_PX
            CoverImageRole.HERO -> HERO_MAX_WIDTH_PX to HERO_MAX_HEIGHT_PX
            CoverImageRole.IMMERSIVE_BACKDROP -> BACKDROP_MAX_EDGE_PX to BACKDROP_MAX_EDGE_PX
        }
        return sampleSize(width, height, targetWidth, targetHeight)
    }

    fun sampleSize(width: Int, height: Int, request: CoverImageRequest): Int? = when (request.role) {
        CoverImageRole.LIBRARY_CARD -> coveringSampleSize(
            width,
            height,
            request.targetWidthPx,
            request.targetHeightPx,
        )
        else -> sampleSize(width, height, request.targetWidthPx, request.targetHeightPx)
    }

    fun libraryCardScaledSize(
        decodedWidthPx: Int,
        decodedHeightPx: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): CoverPixelSize? {
        if (
            decodedWidthPx <= 0 || decodedHeightPx <= 0 ||
            targetWidthPx <= 0 || targetHeightPx <= 0
        ) return null
        val scale = maxOf(
            targetWidthPx.toDouble() / decodedWidthPx,
            targetHeightPx.toDouble() / decodedHeightPx,
        ).coerceAtMost(1.0)
        return CoverPixelSize(
            widthPx = kotlin.math.ceil(decodedWidthPx * scale).toInt().coerceAtLeast(1),
            heightPx = kotlin.math.ceil(decodedHeightPx * scale).toInt().coerceAtLeast(1),
        )
    }

    fun filterQuality(role: CoverImageRole): FilterQuality =
        if (role == CoverImageRole.LIBRARY_CARD) FilterQuality.High else FilterQuality.Low

    /**
     * Target envelopes are downsampling ceilings only: [sampleSize] returns the
     * smallest power of two that keeps the decoded size within the envelope, and
     * never returns a value that enlarges the source. Sources already at or below
     * the envelope decode at native resolution (sample size 1), so small covers
     * are never upscaled by the decode pipeline.
     */
    private fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int? {
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (width / sample > targetWidth || height / sample > targetHeight) {
            if (sample > Int.MAX_VALUE / 2) return null
            sample *= 2
        }
        return sample
    }

    private fun coveringSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int? {
        if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0) return null
        if (width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (sample <= Int.MAX_VALUE / 2) {
            val next = sample * 2
            if (width / next < targetWidth || height / next < targetHeight) break
            sample = next
        }
        return sample
    }

    fun acceptsNetworkLength(contentLength: Long): Boolean =
        contentLength < 0L || contentLength <= MAX_NETWORK_BYTES
}
