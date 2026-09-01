package com.kireibox.ui

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.max
import kotlin.math.min

data class CoverTone(
    val argb: Int,
    val saturation: Float,
    val value: Float,
    val usedFallback: Boolean,
)

/** Extracts a restrained cold accent for the immersive backdrop without owning image loading. */
object CoverToneExtractor {
    const val DEFAULT_FALLBACK_ARGB: Int = 0xFF5B5AC8.toInt()

    /**
     * Warm hues are red (crossing the 0°/360° seam) through orange to yellow.
     * Hues below this bound are warm.
     */
    private const val WARM_HUE_UPPER_BOUND = 70f

    /** Red also wraps the seam: hues at or above this bound are warm again. */
    private const val WARM_HUE_WRAP_BOUND = 345f

    /** Sampling cap per axis: at most 64x64 (4096) deterministic samples. */
    const val MAX_SAMPLE_DIMENSION = 64

    const val MAX_SAMPLES = MAX_SAMPLE_DIMENSION * MAX_SAMPLE_DIMENSION

    /** Bounded tone cache keyed by the existing bitmap cache key. */
    private val toneCache = ConcurrentCoverToneCache()

    fun fromImageBitmap(bitmap: ImageBitmap?, fallbackArgb: Int = DEFAULT_FALLBACK_ARGB): CoverTone {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return fallback(fallbackArgb)
        }
        val onePixel = IntArray(1)
        val pixels = sampleArgbPixels(bitmap.width, bitmap.height) { x, y, width, height ->
            bitmap.readPixels(
                buffer = onePixel,
                startX = x,
                startY = y,
                width = width,
                height = height,
                bufferOffset = 0,
                stride = width,
            )
            onePixel[0]
        }
        return fromArgbPixels(pixels, fallbackArgb)
    }

    /**
     * Cached tone lookup for the existing bitmap cache key: the same key returns the
     * same tone without recomputation, a changed key recomputes, and memory stays
     * bounded by [ConcurrentCoverToneCache]. Null bitmaps and computation fallbacks
     * are returned directly and never inserted, so a later real bitmap for the same
     * key still recomputes.
     */
    fun toneFor(
        cacheKey: String,
        bitmap: ImageBitmap?,
        fallbackArgb: Int = DEFAULT_FALLBACK_ARGB,
    ): CoverTone {
        if (bitmap == null) return fallback(fallbackArgb)
        toneCache.get(cacheKey)?.let { return it }
        val tone = fromImageBitmap(bitmap, fallbackArgb)
        if (tone.usedFallback) return tone
        toneCache.put(cacheKey, tone)
        return tone
    }

    fun clearCache() = toneCache.clear()

    /**
     * Bounded, thread-safe tone cache that never holds its lock while a tone is
     * computed: only lookups and inserts are synchronized, so a slow pixel read
     * for one key can never serialize another key's lookup, insert, or extraction.
     * Concurrent misses for the same key may compute redundantly; the last insert
     * wins and every caller still receives a valid tone. Fallback or failed
     * computations never reach [put].
     */
    private class ConcurrentCoverToneCache(
        private val capacity: Int = CoverBitmapCaches.TONE_CACHE_CAPACITY,
    ) {
        private val lock = Any()
        private val tones = object : LinkedHashMap<String, CoverTone>(capacity, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CoverTone>?): Boolean =
                size > capacity
        }

        fun get(key: String): CoverTone? = synchronized(lock) { tones[key] }

        fun put(key: String, tone: CoverTone) {
            synchronized(lock) { tones[key] = tone }
        }

        fun clear() = synchronized(lock) { tones.clear() }
    }

    /**
     * Deterministic stride-based sampling over the whole image, bounded to at most
     * [MAX_SAMPLES] samples (64x64). The stride derives only from the image
     * dimensions, so identical input always yields identical samples; (0, 0) is
     * always included.
     */
    internal fun sampleArgbPixels(width: Int, height: Int, argbAt: (Int, Int) -> Int): IntArray {
        if (width <= 0 || height <= 0) return IntArray(0)
        val stride = ((max(width, height) + MAX_SAMPLE_DIMENSION - 1) / MAX_SAMPLE_DIMENSION)
            .coerceAtLeast(1)
        val columns = (width + stride - 1) / stride
        val rows = (height + stride - 1) / stride
        val samples = IntArray(columns * rows)
        var cursor = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                samples[cursor++] = argbAt(x, y)
                x += stride
            }
            y += stride
        }
        return samples
    }

    /**
     * Edge-inclusive sampler for bitmap adapters. Every callback requests one pixel,
     * and each axis contributes at most 64 deterministic positions.
     */
    internal fun sampleArgbPixels(
        width: Int,
        height: Int,
        readPixel: (x: Int, y: Int, width: Int, height: Int) -> Int,
    ): IntArray {
        if (width <= 0 || height <= 0) return IntArray(0)
        val columns = min(width, MAX_SAMPLE_DIMENSION)
        val rows = min(height, MAX_SAMPLE_DIMENSION)
        return IntArray(columns * rows) { index ->
            val column = index % columns
            val row = index / columns
            val x = if (columns == 1) 0 else
                (column.toLong() * (width - 1L) / (columns - 1L)).toInt()
            val y = if (rows == 1) 0 else
                (row.toLong() * (height - 1L) / (rows - 1L)).toInt()
            readPixel(x, y, 1, 1)
        }
    }

    fun fromArgbPixels(pixels: IntArray, fallbackArgb: Int = DEFAULT_FALLBACK_ARGB): CoverTone {
        if (pixels.isEmpty()) return fallback(fallbackArgb)
        var totalWeight = 0f
        var red = 0f
        var green = 0f
        var blue = 0f
        pixels.forEach { argb ->
            val alpha = ((argb ushr 24) and 0xFF) / 255f
            if (alpha <= .02f) return@forEach
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val weight = alpha * (.35f + max(r, max(g, b)))
            red += r * weight
            green += g * weight
            blue += b * weight
            totalWeight += weight
        }
        if (totalWeight <= .001f) return fallback(fallbackArgb)

        var average = floatArrayOf(red / totalWeight, green / totalWeight, blue / totalWeight)
        val hsv = rgbToHsv(average[0], average[1], average[2])
        val warm = isWarmHue(hsv[0])
        val coolFallback = rgbToHsv(0x5B / 255f, 0x5A / 255f, 0xC8 / 255f)
        if (warm) {
            average = floatArrayOf(
                lerp(average[0], 0x5B / 255f, .75f),
                lerp(average[1], 0x5A / 255f, .75f),
                lerp(average[2], 0xC8 / 255f, .75f),
            )
        }
        val mixedHsv = rgbToHsv(average[0], average[1], average[2])
        val hue = if (warm) lerpHue(hsv[0], coolFallback[0], .75f) else hsv[0]
        val saturation = mixedHsv[1].coerceIn(.28f, .48f)
        val value = mixedHsv[2].coerceIn(.22f, .38f)
        val rgb = hsvToRgb(hue, saturation, value)
        val packed = 0xFF000000.toInt() or
            ((rgb[0] * 255f).toInt().coerceIn(0, 255) shl 16) or
            ((rgb[1] * 255f).toInt().coerceIn(0, 255) shl 8) or
            (rgb[2] * 255f).toInt().coerceIn(0, 255)
        return CoverTone(packed, saturation, value, usedFallback = false)
    }

    private fun fallback(argb: Int): CoverTone {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val hsv = rgbToHsv(r, g, b)
        return CoverTone(argb, hsv[1], hsv[2], usedFallback = true)
    }

    /**
     * Green, cyan, blue and magenta are cold hues and must never be blended
     * toward the cold purple fallback. Only red, orange and yellow are warm.
     */
    private fun isWarmHue(hue: Float): Boolean =
        hue < WARM_HUE_UPPER_BOUND || hue >= WARM_HUE_WRAP_BOUND

    private fun rgbToHsv(r: Float, g: Float, b: Float): FloatArray {
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val delta = max - min
        val hue = when {
            delta <= .0001f -> 0f
            max == r -> (60f * ((g - b) / delta)).let { if (it < 0f) it + 360f else it }
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        val saturation = if (max <= .0001f) 0f else delta / max
        return floatArrayOf(hue, saturation, max)
    }

    private fun hsvToRgb(hue: Float, saturation: Float, value: Float): FloatArray {
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val i = h.toInt()
        val f = h - i
        val p = value * (1f - saturation)
        val q = value * (1f - saturation * f)
        val t = value * (1f - saturation * (1f - f))
        return when (i) {
            0 -> floatArrayOf(value, t, p)
            1 -> floatArrayOf(q, value, p)
            2 -> floatArrayOf(p, value, t)
            3 -> floatArrayOf(p, q, value)
            4 -> floatArrayOf(t, p, value)
            else -> floatArrayOf(value, p, q)
        }
    }

    private fun lerpHue(start: Float, end: Float, amount: Float): Float {
        var delta = (end - start + 540f) % 360f - 180f
        if (delta == -180f) delta = 180f
        return (start + delta * amount + 360f) % 360f
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
}
