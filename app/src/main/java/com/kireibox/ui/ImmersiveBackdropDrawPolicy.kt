package com.kireibox.ui

import com.yuki.yukihub.settings.ImmersiveBackdropStyle

/** Raw cover alpha of the tone backdrop (the long-standing faint cover behind the tint). */
internal const val IMMERSIVE_BACKDROP_BITMAP_ALPHA = .12f

/** Blurred cover alpha at medium intensity; FxLevel scales it to .45 / .55 / .65. */
internal const val IMMERSIVE_BACKDROP_BLUR_ALPHA = .55f

/** Under the aurora the blurred cover is a quieter base for the drifting blobs. */
internal const val IMMERSIVE_BACKDROP_AURORA_COVER_ALPHA = .34f

/** Alpha below which a bitmap layer has no useful visible contribution. */
internal const val IMMERSIVE_BACKDROP_BITMAP_VISIBILITY_EPSILON = .02f

/** Transition distance used to snap an endpoint and retire the predecessor layer. */
internal const val IMMERSIVE_BACKDROP_ENDPOINT_EPSILON = .001f

/**
 * Which cover pixels a backdrop style paints: none, the raw (氛围色's faint) cover, the tiny blurred
 * copy (封面虚化 / 流光极光) or 封面氛围's faint raw cover ([ATMOSPHERE]: alpha.16's, never softened).
 */
internal enum class ImmersiveBackdropCoverSource { NONE, RAW, BLURRED, ATMOSPHERE }

/** Whether the source is drawn from the precomputed blurred copy (off the main thread) instead of the decoded cover. */
internal fun ImmersiveBackdropCoverSource.needsBlurredCopy(): Boolean = this == ImmersiveBackdropCoverSource.RAW || this == ImmersiveBackdropCoverSource.BLURRED

/**
 * Draw decisions for the immersive backdrop; the live renderer (drawImmersiveBackdrop) consumes
 * exactly this plan.
 *
 * [composeCanvasLayerCount] counts Compose draw layers only. It documents that the whole backdrop
 * (cover, aurora, scrims, glow, grain and vignette) is drawn by one draw node; the optional
 * particle layer is a separate canvas composed only while an ambient effect is on.
 */
internal data class ImmersiveBackdropDrawPlan(
    val composeCanvasLayerCount: Int,
    val style: ImmersiveBackdropStyle,
    val coverSource: ImmersiveBackdropCoverSource,
    val leftBitmapAlpha: Float,
    val rightBitmapAlpha: Float,
    val drawLeftBitmap: Boolean,
    val drawRightBitmap: Boolean,
    val isAtStartEndpoint: Boolean,
    val isAtEndEndpoint: Boolean,
    val drawBlendRadial: Boolean,
    val drawAurora: Boolean,
    val drawHorizontalScrim: Boolean,
    val drawRadial: Boolean,
    val drawGlow: Boolean,
) {
    val bitmapLayerCount: Int
        get() = listOf(drawLeftBitmap, drawRightBitmap).count { it }

    /** The layer switches alone: they stay fixed while dragging (unlike the bitmap alphas). */
    fun layers(): ImmersiveBackdropLayers = ImmersiveBackdropLayers(style, drawBlendRadial, drawAurora, drawHorizontalScrim, drawRadial, drawGlow)
}

/** Which tint-dependent layers a backdrop paints; part of the brush cache key. */
internal data class ImmersiveBackdropLayers(
    val style: ImmersiveBackdropStyle,
    val blendRadial: Boolean,
    val aurora: Boolean,
    val horizontalScrim: Boolean,
    val radial: Boolean,
    val glow: Boolean,
)

/**
 * [style] is the already-degraded backdrop (see resolveImmersiveFx); TONE is the long-standing look:
 * a faint raw cover, a cover-tone blend radial, scrims, radial and glow. COVER_ZOOM (the default) is
 * the same layer stack with alpha.16's constants and the raw cover ([ImmersiveCoverAtmosphere]); the
 * two differ in how the brushes are tinted, not in which layers are drawn.
 */
internal fun immersiveBackdropDrawPlan(
    leftBitmapAvailable: Boolean,
    rightBitmapAvailable: Boolean,
    fraction: Float,
    reducedTransparency: Boolean,
    style: ImmersiveBackdropStyle = ImmersiveBackdropStyle.TONE,
): ImmersiveBackdropDrawPlan {
    val rawTransition = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val isAtStartEndpoint = rawTransition <= IMMERSIVE_BACKDROP_ENDPOINT_EPSILON
    val isAtEndEndpoint = rawTransition >= 1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON
    val transition = normalizeImmersiveBackdropTransition(rawTransition)
    val coverSource = immersiveBackdropCoverSource(style, reducedTransparency)
    val baseAlpha = immersiveBackdropCoverAlpha(style, coverSource)
    val leftAlpha = baseAlpha * (1f - transition)
    val rightAlpha = baseAlpha * transition
    val solid = style == ImmersiveBackdropStyle.SOLID

    return ImmersiveBackdropDrawPlan(
        composeCanvasLayerCount = 1,
        style = style,
        coverSource = coverSource,
        leftBitmapAlpha = leftAlpha,
        rightBitmapAlpha = rightAlpha,
        drawLeftBitmap = leftBitmapAvailable && !isAtEndEndpoint && shouldDrawImmersiveBackdropBitmap(leftAlpha),
        drawRightBitmap = rightBitmapAvailable && !isAtStartEndpoint && shouldDrawImmersiveBackdropBitmap(rightAlpha),
        isAtStartEndpoint = isAtStartEndpoint,
        isAtEndEndpoint = isAtEndEndpoint,
        drawBlendRadial = (style == ImmersiveBackdropStyle.TONE || style == ImmersiveBackdropStyle.COVER_ZOOM) && !reducedTransparency,
        drawAurora = style == ImmersiveBackdropStyle.AURORA && !reducedTransparency,
        drawHorizontalScrim = !solid,
        drawRadial = !solid,
        drawGlow = true,
    )
}

internal fun immersiveBackdropCoverSource(
    style: ImmersiveBackdropStyle,
    reducedTransparency: Boolean,
): ImmersiveBackdropCoverSource =
    when {
        reducedTransparency || style == ImmersiveBackdropStyle.SOLID -> ImmersiveBackdropCoverSource.NONE
        style == ImmersiveBackdropStyle.TONE -> ImmersiveBackdropCoverSource.RAW
        style == ImmersiveBackdropStyle.COVER_ZOOM -> ImmersiveBackdropCoverSource.ATMOSPHERE
        else -> ImmersiveBackdropCoverSource.BLURRED
    }

private fun immersiveBackdropCoverAlpha(
    style: ImmersiveBackdropStyle,
    source: ImmersiveBackdropCoverSource,
): Float =
    when {
        source == ImmersiveBackdropCoverSource.NONE -> 0f
        source == ImmersiveBackdropCoverSource.RAW || source == ImmersiveBackdropCoverSource.ATMOSPHERE -> IMMERSIVE_BACKDROP_BITMAP_ALPHA
        style == ImmersiveBackdropStyle.AURORA -> IMMERSIVE_BACKDROP_AURORA_COVER_ALPHA
        else -> IMMERSIVE_BACKDROP_BLUR_ALPHA
    }

internal fun shouldDrawImmersiveBackdropBitmap(alpha: Float): Boolean = alpha.isFinite() && alpha >= IMMERSIVE_BACKDROP_BITMAP_VISIBILITY_EPSILON

internal fun normalizeImmersiveBackdropTransition(fraction: Float): Float {
    val transition = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    return when {
        transition <= IMMERSIVE_BACKDROP_ENDPOINT_EPSILON -> 0f
        transition >= 1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON -> 1f
        else -> transition
    }
}

/**
 * Keeps side-card shadows off the hot path while easing the center shadow in from the
 * half-focused boundary. At focus 1 the caller's source elevation is preserved unchanged.
 */
internal fun immersiveCardShadowElevationPx(
    focusFraction: Float,
    sourceElevationPx: Float,
): Float {
    val focus = if (focusFraction.isFinite()) focusFraction.coerceIn(0f, 1f) else 0f
    if (focus < .5f) return 0f
    val centerProgress = ((focus - .5f) / .5f).coerceIn(0f, 1f)
    return sourceElevationPx.coerceAtLeast(0f) * centerProgress
}
