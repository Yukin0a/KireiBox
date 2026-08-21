package com.kireibox.ui

internal const val IMMERSIVE_BACKDROP_BITMAP_ALPHA = .12f
/** Alpha below which a bitmap layer has no useful visible contribution. */
internal const val IMMERSIVE_BACKDROP_BITMAP_VISIBILITY_EPSILON = .02f
/** Transition distance used to snap an endpoint and retire the predecessor layer. */
internal const val IMMERSIVE_BACKDROP_ENDPOINT_EPSILON = .001f

/**
 * Draw decisions for the immersive backdrop.
 *
 * [composeCanvasLayerCount] counts Compose Canvas composables only. It documents that the
 * backdrop is composed by one Canvas layer; it makes no claim about the number of GPU draw
 * calls produced by the renderer.
 */
internal data class ImmersiveBackdropDrawPlan(
    val composeCanvasLayerCount: Int,
    val leftBitmapAlpha: Float,
    val rightBitmapAlpha: Float,
    val drawLeftBitmap: Boolean,
    val drawRightBitmap: Boolean,
    val isAtStartEndpoint: Boolean,
    val isAtEndEndpoint: Boolean,
    val drawBlendRadial: Boolean,
    val drawHorizontalScrim: Boolean,
    val drawRadial: Boolean,
    val drawGlow: Boolean,
) {
    val bitmapLayerCount: Int
        get() = listOf(drawLeftBitmap, drawRightBitmap).count { it }
}

internal fun immersiveBackdropDrawPlan(
    leftBitmapAvailable: Boolean,
    rightBitmapAvailable: Boolean,
    fraction: Float,
    reducedTransparency: Boolean,
): ImmersiveBackdropDrawPlan {
    val rawTransition = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val isAtStartEndpoint = rawTransition <= IMMERSIVE_BACKDROP_ENDPOINT_EPSILON
    val isAtEndEndpoint = rawTransition >= 1f - IMMERSIVE_BACKDROP_ENDPOINT_EPSILON
    val transition = normalizeImmersiveBackdropTransition(rawTransition)
    val leftAlpha = if (reducedTransparency) 0f else {
        IMMERSIVE_BACKDROP_BITMAP_ALPHA * (1f - transition)
    }
    val rightAlpha = if (reducedTransparency) 0f else {
        IMMERSIVE_BACKDROP_BITMAP_ALPHA * transition
    }

    return ImmersiveBackdropDrawPlan(
        composeCanvasLayerCount = 1,
        leftBitmapAlpha = leftAlpha,
        rightBitmapAlpha = rightAlpha,
        drawLeftBitmap = leftBitmapAvailable && !isAtEndEndpoint && shouldDrawImmersiveBackdropBitmap(leftAlpha),
        drawRightBitmap = rightBitmapAvailable && !isAtStartEndpoint && shouldDrawImmersiveBackdropBitmap(rightAlpha),
        isAtStartEndpoint = isAtStartEndpoint,
        isAtEndEndpoint = isAtEndEndpoint,
        drawBlendRadial = !reducedTransparency,
        drawHorizontalScrim = true,
        drawRadial = true,
        drawGlow = true,
    )
}

internal fun shouldDrawImmersiveBackdropBitmap(alpha: Float): Boolean =
    alpha.isFinite() && alpha >= IMMERSIVE_BACKDROP_BITMAP_VISIBILITY_EPSILON

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
