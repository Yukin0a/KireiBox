package com.kireibox.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// Immersive home backdrop: hero cover loading (NSFW-aware) and the single-canvas backdrop layer.
// rememberHeroCover and ImmersiveBackdrop stay adjacent and in this order (NsfwCoverWiringTest slices between them).

/**
 * Hero covers of the retained backdrop track, one keyed group per game. When a swipe settles and
 * the right-hand game becomes the left one, its loaded bitmap, tone and blurred copy move with it,
 * so the slot never shows the previous game's backdrop (or a bare base) for a frame.
 */
@Composable
internal fun rememberRetainedHeroCovers(
    games: List<Game>,
    retention: ImmersiveBackdropRetention,
    widthPx: Int,
    heightPx: Int,
): List<HeroCoverVisual?> =
    retention.indexes.map { index ->
        val game = games.getOrNull(index)
        key(game?.id) { rememberHeroCover(game, widthPx, heightPx) }
    }

@Composable
internal fun rememberHeroCover(
    game: Game?,
    widthPx: Int,
    heightPx: Int,
): HeroCoverVisual? {
    if (game == null) return null
    val uri = game.coverUri
    val blurred = game.nsfw && LocalNsfwBlurEnabled.current
    val cacheKey =
        remember(uri, widthPx, heightPx, blurred) {
            coverCacheKey(uri, CoverImageRole.IMMERSIVE_BACKDROP, widthPx, heightPx, blurred)
        }
    val bitmap =
        rememberCover(
            uri = uri,
            role = CoverImageRole.IMMERSIVE_BACKDROP,
            componentWidthPx = widthPx,
            componentHeightPx = heightPx,
            blurred = blurred,
        )
    val toneCacheKey = cacheKey ?: "immersive-backdrop-fallback"
    // produceState keeps its last value when its keys change, so the produced tone carries the key
    // and bitmap it was computed for and is only used while they still match (never a stale tone).
    val produced by produceState<KeyedCoverTone?>(initialValue = null, toneCacheKey, bitmap) {
        value = KeyedCoverTone(toneCacheKey, bitmap, computeImmersiveTone(toneCacheKey, bitmap))
    }
    val tone = produced?.takeIf { it.key == toneCacheKey && it.bitmap === bitmap }?.tone ?: CoverToneExtractor.cachedTone(toneCacheKey, bitmap)
    val backdropBlur = rememberBackdropBlur(toneCacheKey, bitmap)
    return remember(cacheKey, bitmap, tone, backdropBlur) {
        HeroCoverVisual(bitmap = bitmap, tone = tone, coverCacheKey = cacheKey, blurred = backdropBlur)
    }
}

/** Draws the resolved atmosphere as one draw node without relying on platform blur modifiers. */
@Composable
internal fun ImmersiveBackdrop(
    leftHero: HeroCoverVisual?,
    rightHero: HeroCoverVisual?,
    fraction: Float,
    layout: ImmersiveHomeLayout,
    reducedTransparency: Boolean,
    scale: Float,
    density: Density,
    palette: ImmersivePalette,
) {
    // An empty gallery has no cover to show: 封面氛围 draws the tone backdrop's accent stage there.
    val fx = LocalImmersiveFx.current.reconciled(reducedMotion = false, reducedTransparency = reducedTransparency).forHero(leftHero != null)
    val clock = LocalImmersiveMotionClock.current
    val parallax = LocalImmersiveParallax.current
    val transition = normalizeImmersiveBackdropTransition(fraction)
    // 封面氛围 is alpha.16 verbatim: the raw cover tone and alpha.16's tokens. Plan §6.1 氛围色: the tint
    // is the cover tone (neutral without a cover); the accent is at most a faint hint, so no preset
    // washes the whole home in the raw accent. An empty gallery on a dark page keeps alpha.17's
    // restrained accent stage instead of a grey room (r4).
    val atmosphere = fx.backdrop == ImmersiveBackdropStyle.COVER_ZOOM
    val backdropPalette = if (atmosphere) palette.withCoverAtmosphere() else palette
    val leftTint =
        when {
            leftHero == null -> immersiveEmptyGalleryTint(palette, fx.dynamicTint)
            atmosphere -> immersiveCoverAtmosphereTint(leftHero.tone)
            else -> immersiveBackdropTint(leftHero.tone, palette, fx.dynamicTint)
        }
    val rightTint =
        rightHero?.let { if (atmosphere) immersiveCoverAtmosphereTint(it.tone) else immersiveBackdropTint(it.tone, palette, fx.dynamicTint) } ?: leftTint
    val coverSource = immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency)
    val softCover = immersiveSoftensRawCover(palette.isDark)
    val leftBitmap = leftHero.backdropBitmap(coverSource, softCover)
    val rightBitmap = rightHero.backdropBitmap(coverSource, softCover)
    val plan =
        immersiveBackdropDrawPlan(
            leftBitmapAvailable = leftBitmap != null,
            rightBitmapAvailable = rightBitmap != null,
            fraction = transition,
            reducedTransparency = fx.reducedTransparency,
            style = fx.backdrop,
        )
    val scene =
        ImmersiveBackdropScene(
            plan = plan,
            // No hero (an empty gallery): the light page draws no floor glow under a card that is not
            // there; the dark page keeps it as the accent "stage" under the empty-state content (r4).
            look =
                ImmersiveBackdropLook(
                    plan.layers().let { if (leftHero == null && !palette.isDark) it.copy(glow = false) else it },
                    fx,
                    backdropPalette,
                    layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect,
                    scale
                ),
            leftTint = leftTint,
            rightTint = rightTint,
            fraction = transition,
            leftBitmap = leftBitmap,
            rightBitmap = rightBitmap,
        )
    val brushCache = remember { ImmersiveBackdropBrushCache() }
    Spacer(
        Modifier.fillMaxSize().zIndex(0f).drawWithCache {
            // Drag frames recompose this node, but brushes come from the per-tint cache: a frame
            // only re-resolves them, it never rebuilds shaders unless a tone, the look or the size changed.
            val brushes = brushCache.frame(scene, size)
            // Only the frame clock and the tilt are read here, so animation frames re-draw
            // with cached brushes instead of recomposing.
            onDrawBehind { drawImmersiveBackdrop(scene, brushes, clock.seconds, parallax) }
        },
    )
}

/** Blends two cover tones; the transition fraction is clamped and non-finite values snap to the left tone. */
internal fun blendImmersiveColors(
    left: Color,
    right: Color,
    fraction: Float,
): Color {
    val t = fraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    return Color(
        red = left.red + (right.red - left.red) * t,
        green = left.green + (right.green - left.green) * t,
        blue = left.blue + (right.blue - left.blue) * t,
        alpha = 1f,
    )
}

/** [softCover]: 氛围色's faint cover comes from the blurred copy (dark pages, r4 · V2) and is skipped until it exists. */
private fun HeroCoverVisual?.backdropBitmap(
    source: ImmersiveBackdropCoverSource,
    softCover: Boolean,
): ImageBitmap? =
    when (source) {
        ImmersiveBackdropCoverSource.NONE -> null
        ImmersiveBackdropCoverSource.RAW -> if (softCover) this?.blurred else this?.bitmap
        ImmersiveBackdropCoverSource.ATMOSPHERE -> this?.bitmap
        ImmersiveBackdropCoverSource.BLURRED -> this?.blurred
    }

/** A tone together with the cache key and bitmap it was computed from. */
private class KeyedCoverTone(
    val key: String,
    val bitmap: ImageBitmap?,
    val tone: CoverTone,
)

/** A blurred copy together with the blur key it was computed for. */
private class KeyedBlur(
    val key: String,
    val image: ImageBitmap,
)

/**
 * The blurred copy is only computed when the resolved backdrop paints it: BLUR_COVER and AURORA, and
 * 氛围色, whose faint cover is the blurred copy on dark pages (r4 · V2; tiny and cached). 封面氛围
 * draws the decoded cover itself and needs no copy. A cached copy is shown in the same frame; a new
 * one is built on Dispatchers.Default — never on the main thread, on every API level. The produced
 * copy is keyed, so a hero whose cover changed never keeps drawing the previous cover's blur.
 */
@Composable
private fun rememberBackdropBlur(
    coverKey: String,
    bitmap: ImageBitmap?,
): ImageBitmap? {
    val fx = LocalImmersiveFx.current
    val wanted = immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency).needsBlurredCopy()
    val spec = immersiveBlurSpec(fx.intensity)
    val blurKey = remember(coverKey, spec) { immersiveBlurCacheKey(coverKey, spec) }
    val cached = remember(blurKey, bitmap, wanted) { if (wanted && bitmap != null) ImmersiveBackdropBlurCache.get(blurKey)?.asImageBitmap() else null }
    val produced by produceState<KeyedBlur?>(initialValue = null, blurKey, bitmap, wanted) {
        value = if (wanted && bitmap != null) KeyedBlur(blurKey, cached ?: computeImmersiveBlur(blurKey, bitmap, spec)) else null
    }
    if (!wanted || bitmap == null) return null
    return produced?.takeIf { it.key == blurKey }?.image ?: cached
}

/**
 * r5: while the gallery rests, the backdrops of the resting game's neighbours ([indexes]) are
 * prepared off the main thread — the decoded cover in the backdrop cache, its tone, and the blurred
 * copy in the blur cache when the backdrop paints one — so the next swipe crossfades straight into
 * the neighbour's cover instead of the bare base while it decodes. Each cover is requested exactly
 * as [rememberHeroCover] requests it (same NSFW blur flag, same cache keys).
 */
@Composable
internal fun ImmersiveBackdropPrefetch(
    games: List<Game>,
    indexes: List<Int>,
    widthPx: Int,
    heightPx: Int,
) {
    val context = LocalContext.current.applicationContext
    val nsfwBlurEnabled = LocalNsfwBlurEnabled.current
    val fx = LocalImmersiveFx.current
    val spec = if (immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency).needsBlurredCopy()) immersiveBlurSpec(fx.intensity) else null
    val neighbours = indexes.mapNotNull(games::getOrNull)
    LaunchedEffect(neighbours, widthPx, heightPx, nsfwBlurEnabled, spec) {
        neighbours.forEach { game ->
            val blurred = game.nsfw && nsfwBlurEnabled
            val uri = game.coverUri?.takeIf { it.isNotBlank() } ?: return@forEach
            val request = CoverDecodePolicy.request(uri, CoverImageRole.IMMERSIVE_BACKDROP, widthPx, heightPx, blurred = blurred)
            val bitmap =
                withContext(Dispatchers.IO) {
                    CoverLoads.load(request) { CoverDecodeSemaphore.withPermit { decodeSampledCover(context, request) } }
                }?.asImageBitmap() ?: return@forEach
            computeImmersiveTone(request.cacheKey, bitmap)
            spec?.let { computeImmersiveBlur(immersiveBlurCacheKey(request.cacheKey, it), bitmap, it) }
        }
    }
}
