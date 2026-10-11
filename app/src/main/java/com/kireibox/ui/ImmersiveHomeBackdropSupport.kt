package com.kireibox.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

internal data class HeroCoverVisual(
    val bitmap: ImageBitmap?,
    val tone: CoverTone,
    val coverCacheKey: String?,
    /**
     * The precomputed copy the active backdrop paints, built off the main thread from [bitmap]: the
     * tiny blurred cover (封面虚化 / 流光极光, 氛围色 on dark pages; [ImmersiveBlurSpec]).
     */
    val blurred: ImageBitmap? = null,
)

/**
 * Runs the bounded tone extraction away from Compose's composition dispatcher.
 * The suspending boundary is also the lifecycle boundary used by [produceState]:
 * a changed bitmap/cache key or a disposed hero cancels the pending computation
 * before it can publish a stale tone.
 */
internal suspend fun computeImmersiveTone(
    cacheKey: String,
    bitmap: ImageBitmap?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    toneFor: suspend (String, ImageBitmap?) -> CoverTone = { key, image ->
        CoverToneExtractor.toneFor(key, image)
    },
): CoverTone =
    withContext(dispatcher) {
        coroutineContext.ensureActive()
        val tone = toneFor(cacheKey, bitmap)
        coroutineContext.ensureActive()
        tone
    }

/**
 * How a backdrop copy is made: downsampled to [shortEdgePx] (long edge ≤ [longEdgePx]), then
 * [passes] box blurs of [radius]. [id] keys the cache.
 */
internal data class ImmersiveBlurSpec(
    val id: String,
    val shortEdgePx: Int,
    val longEdgePx: Int,
    val radius: Int,
    val passes: Int,
)

/** The tiny 64 px blur of 封面虚化, 流光极光 and 氛围色's dark-page cover, at the intensity's radius. */
internal fun immersiveBlurSpec(level: FxLevel): ImmersiveBlurSpec = ImmersiveBlurSpec(level.storedValue, BLUR_SHORT_EDGE_PX, BLUR_LONG_EDGE_PX, immersiveBlurRadius(level), passes = 2)

/** Cache key of a backdrop copy of a cover (the cover key already carries the NSFW blur flag). */
internal fun immersiveBlurCacheKey(
    coverKey: String,
    spec: ImmersiveBlurSpec,
): String = "$coverKey|blur|${spec.id}"

/**
 * Blurs [bitmap] for the backdrop off the main thread (plan §6.1: downsampled, then cached). The
 * already NSFW-blurred cover is the input, so the backdrop never reveals a hidden cover.
 */
internal suspend fun computeImmersiveBlur(
    cacheKey: String,
    bitmap: ImageBitmap,
    spec: ImmersiveBlurSpec,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): ImageBitmap =
    withContext(dispatcher) {
        ImmersiveBackdropBlurCache.get(cacheKey)?.let { return@withContext it.asImageBitmap() }
        coroutineContext.ensureActive()
        val blurred = blurredBackdropBitmap(bitmap.asAndroidBitmap(), spec)
        coroutineContext.ensureActive()
        ImmersiveBackdropBlurCache.put(cacheKey, blurred)
        blurred.asImageBitmap()
    }

/**
 * Byte-bounded LRU of blurred backdrop copies, 2 MiB in total: tiny covers are 64 × 114 × 4 B ≈ 29 KiB,
 * so dozens fit. Evicted bitmaps are never recycled: a backdrop still drawing one keeps it alive until
 * it is collected, exactly like the other cover caches.
 */
internal object ImmersiveBackdropBlurCache : LruCache<String, Bitmap>(2 * 1024) {
    override fun sizeOf(
        key: String,
        value: Bitmap,
    ): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)
}

private const val BLUR_SHORT_EDGE_PX = 64
private const val BLUR_LONG_EDGE_PX = 128

/** Aspect-preserving sample size: the short edge becomes ≤ [shortEdgePx] (64) and the long edge ≤ [longEdgePx] (128). */
internal fun immersiveBlurSampleSize(
    width: Int,
    height: Int,
    shortEdgePx: Int = BLUR_SHORT_EDGE_PX,
    longEdgePx: Int = BLUR_LONG_EDGE_PX,
): IntSize {
    if (width <= 0 || height <= 0) return IntSize(1, 1)
    val scale =
        minOf(
            1f,
            shortEdgePx / minOf(width, height).toFloat(),
            longEdgePx / maxOf(width, height).toFloat(),
        )
    return IntSize((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1))
}

internal fun immersiveBlurRadius(level: FxLevel): Int =
    when (level) {
        FxLevel.LOW -> 1
        FxLevel.MEDIUM -> 2
        FxLevel.HIGH -> 3
    }

private fun blurredBackdropBitmap(
    source: Bitmap,
    spec: ImmersiveBlurSpec,
): Bitmap {
    val target = immersiveBlurSampleSize(source.width, source.height, spec.shortEdgePx, spec.longEdgePx)
    val scaled = Bitmap.createScaledBitmap(source, target.width, target.height, true)
    var pixels = IntArray(target.width * target.height)
    scaled.getPixels(pixels, 0, target.width, 0, 0, target.width, target.height)
    // The scaled copy is private to this call; the source belongs to the cover caches.
    if (scaled !== source) scaled.recycle()
    // Repeated box passes approximate a Gaussian closely enough for a stretched backdrop.
    repeat(spec.passes) { pixels = boxBlurArgb(pixels, target.width, target.height, spec.radius) }
    return Bitmap.createBitmap(pixels, target.width, target.height, Bitmap.Config.ARGB_8888)
}

/** Separable box blur over packed ARGB pixels (edges clamp). */
internal fun boxBlurArgb(
    source: IntArray,
    width: Int,
    height: Int,
    radius: Int,
): IntArray {
    if (radius <= 0 || width <= 0 || height <= 0) return source.copyOf()
    val horizontal = IntArray(source.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            horizontal[y * width + x] = averageArgb(source, radius) { d -> y * width + (x + d).coerceIn(0, width - 1) }
        }
    }
    val output = IntArray(source.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            output[y * width + x] = averageArgb(horizontal, radius) { d -> (y + d).coerceIn(0, height - 1) * width + x }
        }
    }
    return output
}

private inline fun averageArgb(
    pixels: IntArray,
    radius: Int,
    index: (Int) -> Int,
): Int {
    var a = 0
    var r = 0
    var g = 0
    var b = 0
    for (d in -radius..radius) {
        val color = pixels[index(d)]
        a += color ushr 24
        r += color ushr 16 and 0xFF
        g += color ushr 8 and 0xFF
        b += color and 0xFF
    }
    val count = radius * 2 + 1
    return (a / count shl 24) or (r / count shl 16) or (g / count shl 8) or (b / count)
}

internal fun DrawScope.drawCroppedBackdropBitmap(
    bitmap: ImageBitmap,
    alpha: Float,
) {
    if (bitmap.width <= 0 || bitmap.height <= 0 || size.width <= 0f || size.height <= 0f) return
    val destinationWidth = size.width.roundToInt().coerceAtLeast(1)
    val destinationHeight = size.height.roundToInt().coerceAtLeast(1)
    val destinationAspect = size.width / size.height
    val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val sourceWidth: Int
    val sourceHeight: Int
    val sourceLeft: Int
    val sourceTop: Int
    if (bitmapAspect > destinationAspect) {
        sourceHeight = bitmap.height
        sourceWidth = (bitmap.height * destinationAspect).roundToInt().coerceIn(1, bitmap.width)
        sourceLeft = ((bitmap.width - sourceWidth) / 2).coerceAtLeast(0)
        sourceTop = 0
    } else {
        sourceWidth = bitmap.width
        sourceHeight = (bitmap.width / destinationAspect).roundToInt().coerceIn(1, bitmap.height)
        sourceLeft = 0
        sourceTop = ((bitmap.height - sourceHeight) / 2).coerceAtLeast(0)
    }
    drawImage(
        image = bitmap,
        srcOffset = IntOffset(sourceLeft, sourceTop),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(destinationWidth, destinationHeight),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

/** Brush inputs that stay fixed while a swipe is dragged: everything except the tints and the fraction. */
internal data class ImmersiveBackdropLook(
    val layers: ImmersiveBackdropLayers,
    val fx: ResolvedImmersiveFx,
    val palette: ImmersivePalette,
    val center: HomeRect,
    val scale: Float,
)

/** Everything one backdrop frame needs; built in composition, drawn by [drawImmersiveBackdrop]. */
internal data class ImmersiveBackdropScene(
    val plan: ImmersiveBackdropDrawPlan,
    val look: ImmersiveBackdropLook,
    val leftTint: Color,
    val rightTint: Color,
    /** Normalized transition: the right-hand tint's share of the tint layers. */
    val fraction: Float,
    val leftBitmap: ImageBitmap?,
    val rightBitmap: ImageBitmap?,
)

/**
 * Brushes of one tint at one size. A swipe crossfades the left and right sets by alpha instead of
 * rebuilding gradients for a blended color on every drag frame (plan §6.5: no per-frame shaders).
 */
internal class ImmersiveBackdropBrushes(
    private val look: ImmersiveBackdropLook,
    private val tint: Color,
    private val size: Size,
) {
    private val palette = look.palette
    private val intensity = look.fx.intensity.backdropIntensityScale()
    private val reduced = look.fx.reducedTransparency
    private val contrast = look.fx.increasedContrast
    private val layers = look.layers
    private val center = look.center
    private val atmosphere = layers.style == ImmersiveBackdropStyle.COVER_ZOOM

    val base: Color = if (layers.style == ImmersiveBackdropStyle.SOLID && palette.isDark) KireiTokens.Immersive.SolidDark else palette.base
    val blendRadial: Brush? = if (layers.blendRadial) radial(tint, palette.blendAlpha * intensity, 900f * look.scale) else null
    val aurora: ImmersiveAuroraBrushes? =
        if (layers.aurora) ImmersiveAuroraBrushes(size, immersiveAuroraColors(tint, palette), intensity * immersiveAuroraAlphaScale(palette.isDark)) else null
    val scrim: Brush? =
        if (layers.horizontalScrim) {
            Brush.horizontalGradient(
                listOf(
                    if (contrast) palette.scrimStart.copy(alpha = .99f) else palette.scrimStart,
                    if (contrast) palette.scrimMid.copy(alpha = .84f) else palette.scrimMid,
                    Color.Transparent,
                ),
            )
        } else {
            null
        }

    // The wide radial is the room's atmosphere: accent-led on dark pages (r4 · V1), the tint on light ones;
    // 封面氛围 keeps alpha.16's raw cover tone.
    val radial: Brush? =
        if (layers.radial) {
            radial(
                if (atmosphere) tint else immersiveAtmosphereTint(tint, palette, look.fx.dynamicTint),
                (if (reduced) palette.radialReducedAlpha else palette.radialAlpha) * intensity,
                1500f * look.scale,
            )
        } else {
            null
        }

    /**
     * 氛围色's faint cover fades into the base over the lower screen on dark pages (r4 · V2): a
     * base-colored ramp drawn right over the cover, so it masks only the cover pixels.
     */
    val coverFloor: Brush? =
        if (palette.isDark && layers.style == ImmersiveBackdropStyle.TONE && !reduced) {
            Brush.verticalGradient(
                0f to base.copy(alpha = 0f),
                ImmersiveDarkDepth.COVER_FLOOR_START to base.copy(alpha = 0f),
                1f to base.copy(alpha = ImmersiveDarkDepth.COVER_FLOOR_ALPHA),
                startY = 0f,
                endY = size.height,
            )
        } else {
            null
        }

    /** 封面氛围's glow: alpha.16's oval under the centre card ([ImmersiveCoverAtmosphereGlow]); null for the other styles. */
    val glowOval: ImmersiveCoverAtmosphereGlow? = if (atmosphere) ImmersiveCoverAtmosphereGlow(center) else null

    /** Center-card floor glow: an ellipse under the card bottom, feathered to zero on every edge (accent-leaning in dark, r4). */
    val glowCenter = Offset(center.x + center.w * .5f, center.y + center.h * .95f)
    val glowRadius = center.w * 1.12f
    val glowSquash = (center.h * .17f / glowRadius.coerceAtLeast(1f)).coerceIn(.01f, 1f)
    val glow: Brush? =
        if (layers.glow && look.fx.glow) {
            val glowAlpha = ((if (reduced) palette.glowReducedAlpha else palette.glowAlpha) * intensity).coerceIn(0f, 1f)
            if (glowOval != null) {
                // alpha.16: no explicit centre, so the gradient is centred on the canvas and clipped by the oval.
                Brush.radialGradient(listOf(tint.copy(alpha = glowAlpha), Color.Transparent), radius = glowOval.radius)
            } else {
                Brush.radialGradient(
                    immersiveGlowTint(tint, palette).let { glowTint -> listOf(glowTint.copy(alpha = glowAlpha), glowTint.copy(alpha = 0f)) },
                    center = glowCenter,
                    radius = glowRadius,
                )
            }
        } else {
            null
        }
    val grainAlpha: Float = immersiveGrainAlpha(palette.isDark, intensity)

    val vignette: Brush? = if (look.fx.vignette) immersiveVignetteBrush(size, palette.isDark, immersiveVignetteAlpha(palette.isDark, contrast, intensity)) else null

    fun matches(
        look: ImmersiveBackdropLook,
        tint: Color,
        size: Size,
    ): Boolean = this.tint == tint && this.size == size && this.look == look

    private fun radial(
        color: Color,
        alpha: Float,
        radius: Float,
    ): Brush = Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent), radius = radius)
}

/** The brush sets one frame draws: the left (settled) tone and, mid-swipe, the incoming one. */
internal class ImmersiveBackdropFrameBrushes {
    var left: ImmersiveBackdropBrushes? = null
        internal set
    var right: ImmersiveBackdropBrushes? = null
        internal set
}

/**
 * Least-recently-used brush sets for the tones on screen (left, right and the one just left
 * behind), so a drag frame resolves its brushes with a few equality checks and no allocation.
 */
internal class ImmersiveBackdropBrushCache {
    private val entries = arrayOfNulls<ImmersiveBackdropBrushes>(CAPACITY)
    private val lastUsed = LongArray(CAPACITY)
    private var clock = 0L
    private val frame = ImmersiveBackdropFrameBrushes()

    fun frame(
        scene: ImmersiveBackdropScene,
        size: Size,
    ): ImmersiveBackdropFrameBrushes {
        frame.left = brushes(scene.look, scene.leftTint, size)
        frame.right = if (scene.fraction > 0f && scene.rightTint != scene.leftTint) brushes(scene.look, scene.rightTint, size) else null
        return frame
    }

    private fun brushes(
        look: ImmersiveBackdropLook,
        tint: Color,
        size: Size,
    ): ImmersiveBackdropBrushes {
        clock += 1
        var oldest = 0
        for (index in entries.indices) {
            val entry = entries[index]
            if (entry != null && entry.matches(look, tint, size)) {
                lastUsed[index] = clock
                return entry
            }
            if (lastUsed[index] < lastUsed[oldest]) oldest = index
        }
        return ImmersiveBackdropBrushes(look, tint, size).also {
            entries[oldest] = it
            lastUsed[oldest] = clock
        }
    }

    private companion object {
        const val CAPACITY = 3
    }
}

/**
 * The whole backdrop in one draw pass, in the order the draw plan describes. Tint layers of the
 * outgoing and incoming cover crossfade by the swipe fraction.
 */
internal fun DrawScope.drawImmersiveBackdrop(
    scene: ImmersiveBackdropScene,
    brushes: ImmersiveBackdropFrameBrushes,
    seconds: Float,
    parallax: ImmersiveParallaxState,
) {
    val left = brushes.left ?: return
    val right = brushes.right
    val t = if (right == null) 0f else scene.fraction
    drawRect(color = left.base)
    drawImmersiveCoverLayers(scene, seconds, parallax)
    drawImmersiveCoverOverlays(scene, left)
    drawCrossfaded(left.blendRadial, right?.blendRadial, t)
    left.aurora?.let { drawImmersiveAurora(it, seconds, 1f - t) }
    right?.aurora?.let { drawImmersiveAurora(it, seconds, t) }
    left.scrim?.let { drawRect(brush = it) }
    drawCrossfaded(left.radial, right?.radial, t)
    left.glow?.let { drawImmersiveBackdropGlow(it, left, 1f - t) }
    right?.glow?.let { drawImmersiveBackdropGlow(it, right, t) }
    if (scene.look.fx.grain) drawRect(brush = ImmersiveGrainTexture.brush, alpha = left.grainAlpha)
    left.vignette?.let { drawRect(brush = it) }
}

/** What lies directly over the cover pixels: 氛围色's floor, or the wash over blurred artwork. */
private fun DrawScope.drawImmersiveCoverOverlays(
    scene: ImmersiveBackdropScene,
    left: ImmersiveBackdropBrushes,
) {
    if (scene.leftBitmap != null || scene.rightBitmap != null) left.coverFloor?.let { drawRect(brush = it) }
    // A base-colored wash over blurred artwork: in the light theme it keeps the art a pastel tint of
    // the light page instead of a dim grey-blue room (spec §2.4 light base); in the dark theme it
    // keeps the art deep and moody instead of a washed-out haze (r4, ImmersiveDarkDepth).
    val wash = immersiveImageryWashAlpha(scene.look.palette.isDark, scene.plan.coverSource, scene.look.fx.intensity)
    if (wash > 0f) drawRect(color = left.base, alpha = wash)
}

/** Full-size tint layer of the outgoing ([left], 1 - t) and incoming ([right], t) tone. */
private fun DrawScope.drawCrossfaded(
    left: Brush?,
    right: Brush?,
    t: Float,
) {
    if (left != null && t < 1f) drawRect(brush = left, alpha = 1f - t)
    if (right != null && t > 0f) drawRect(brush = right, alpha = t)
}

/**
 * The center-card glow (plan §6.1 光晕), tinted by the cover tone: a radial gradient centered on
 * the glow and squashed into an ellipse, so it fades out on all sides instead of an oval clipping
 * an off-center gradient into a hard "horizon" edge. 封面氛围 draws alpha.16's clipped oval instead.
 */
private fun DrawScope.drawImmersiveBackdropGlow(
    brush: Brush,
    brushes: ImmersiveBackdropBrushes,
    alpha: Float,
) {
    if (alpha <= 0f) return
    brushes.glowOval?.let { oval ->
        drawOval(brush = brush, topLeft = oval.topLeft, size = oval.size, alpha = alpha)
        return
    }
    scale(1f, brushes.glowSquash, pivot = brushes.glowCenter) {
        drawCircle(brush = brush, radius = brushes.glowRadius, center = brushes.glowCenter, alpha = alpha)
    }
}

/** Share of the light base washed over blurred covers in the light theme. */
internal const val IMMERSIVE_LIGHT_COVER_WASH = .38f

/**
 * Cover crossfade with Ken Burns (1.05 ↔ 1.12 over 24 s) and gyroscope pan. The layer is scaled
 * just enough that the largest pan can never reveal the base color at the screen edge.
 */
private fun DrawScope.drawImmersiveCoverLayers(
    scene: ImmersiveBackdropScene,
    seconds: Float,
    parallax: ImmersiveParallaxState,
) {
    val plan = scene.plan
    val left = scene.leftBitmap?.takeIf { plan.drawLeftBitmap }
    val right = scene.rightBitmap?.takeIf { plan.drawRightBitmap }
    if (left == null && right == null) return
    // Intensity scales the cover like every other backdrop layer (r4: 简约's 低 also quiets the faint
    // raw cover); dark pages additionally hold cover pixels back (ImmersiveDarkDepth).
    val alphaScale = scene.look.fx.intensity.backdropIntensityScale() * immersiveImageryAlphaScale(scene.look.palette.isDark, plan.coverSource)
    val kenBurns = scene.look.fx.kenBurns
    val parallaxPx = if (scene.look.fx.gyroParallax) IMMERSIVE_PARALLAX_BACKDROP_DP.dp.toPx() else 0f
    val kenBurnsPanPx = if (kenBurns) size.width * KEN_BURNS_PAN_FRACTION else 0f
    val maxPanPx = parallaxPx + kenBurnsPanPx
    val overscan = if (maxPanPx > 0f) 1f + 2f * maxPanPx / size.minDimension + .004f else 1f
    val scale = maxOf(overscan, if (kenBurns) immersiveKenBurnsScale(seconds) else 1f)
    val kenBurnsShift = if (kenBurns) (immersiveTriangleWave(seconds + KEN_BURNS_PERIOD_SECONDS / 4f, KEN_BURNS_PERIOD_SECONDS) - .5f) * 2f * kenBurnsPanPx else 0f
    withTransform({
        translate(parallax.x * parallaxPx + kenBurnsShift, parallax.y * parallaxPx)
        scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
    }) {
        left?.let { drawCroppedBackdropBitmap(it, plan.leftBitmapAlpha * alphaScale) }
        right?.let { drawCroppedBackdropBitmap(it, plan.rightBitmapAlpha * alphaScale) }
    }
}

private const val KEN_BURNS_PAN_FRACTION = .012f
