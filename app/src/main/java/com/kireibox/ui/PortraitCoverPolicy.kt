package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle

/**
 * Pure cover-layout policy for the Alpha 8 C/D landscape home. C cards stay 9:16 portrait
 * while D cards are 16:9 landscape, so the shared helpers expose both ratios and both the
 * crop and the fit (adaptive foreground) layouts.
 *
 * Crop semantics scale the source by the larger of the two axis ratios so the image always
 * fills the whole rounded card; the overflow is centered-cropped on the other axis. Fit
 * semantics keep the complete source centered inside the target box (used only as the
 * adaptive foreground on top of a blurred crop fill, never on its own).
 */
internal object PortraitCoverPolicy {
    const val WIDTH_DIVIDEND = 9f
    const val HEIGHT_DIVISOR = 16f

    /** width:height = 9:16. */
    const val WIDTH_TO_HEIGHT_RATIO = WIDTH_DIVIDEND / HEIGHT_DIVISOR
    const val HEIGHT_TO_WIDTH_RATIO = HEIGHT_DIVISOR / WIDTH_DIVIDEND

    fun widthForHeight(heightDp: Float): Float = heightDp * WIDTH_TO_HEIGHT_RATIO

    fun heightForWidth(widthDp: Float): Float = widthDp * HEIGHT_TO_WIDTH_RATIO

    /**
     * ContentScale.Fit layout: the whole source stays visible inside the target, scaled by
     * the smaller axis ratio and centered. The caller is expected to layer this over a
     * blurred crop fill so the empty margins never show as black bars.
     */
    fun fitLayout(
        srcWidthPx: Int,
        srcHeightPx: Int,
        targetWidthPx: Float,
        targetHeightPx: Float,
    ): CoverLayout {
        if (srcWidthPx <= 0 || srcHeightPx <= 0 || targetWidthPx <= 0f || targetHeightPx <= 0f) {
            return CoverLayout(
                contentWidthPx = 0f,
                contentHeightPx = 0f,
                offsetXPx = 0f,
                offsetYPx = 0f,
            )
        }
        val scale =
            minOf(
                targetWidthPx / srcWidthPx,
                targetHeightPx / srcHeightPx,
            )
        val contentWidthPx = srcWidthPx * scale
        val contentHeightPx = srcHeightPx * scale
        return CoverLayout(
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            offsetXPx = (targetWidthPx - contentWidthPx) / 2f,
            offsetYPx = (targetHeightPx - contentHeightPx) / 2f,
        )
    }

    /**
     * Fill/crop layout for a single cover layer. [srcWidthPx]/[srcHeightPx] are the decoded
     * bitmap dimensions and [targetWidthPx]/[targetHeightPx] are the card box. The returned
     * content is always at least as large as the target on both axes (Crop), and the
     * offsets center the overflow. [hasContent] is false only when the source is unknown;
     * the caller then renders the engine theme placeholder.
     */
    data class CoverLayout(
        val contentWidthPx: Float,
        val contentHeightPx: Float,
        val offsetXPx: Float,
        val offsetYPx: Float,
    ) {
        val hasContent: Boolean get() = contentWidthPx > 0f && contentHeightPx > 0f
    }

    fun layout(
        srcWidthPx: Int,
        srcHeightPx: Int,
        targetWidthPx: Float,
        targetHeightPx: Float,
    ): CoverLayout {
        if (srcWidthPx <= 0 || srcHeightPx <= 0 || targetWidthPx <= 0f || targetHeightPx <= 0f) {
            return CoverLayout(
                contentWidthPx = 0f,
                contentHeightPx = 0f,
                offsetXPx = 0f,
                offsetYPx = 0f,
            )
        }
        val scale =
            maxOf(
                targetWidthPx / srcWidthPx,
                targetHeightPx / srcHeightPx,
            )
        val contentWidthPx = srcWidthPx * scale
        val contentHeightPx = srcHeightPx * scale
        return CoverLayout(
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            offsetXPx = (targetWidthPx - contentWidthPx) / 2f,
            offsetYPx = (targetHeightPx - contentHeightPx) / 2f,
        )
    }
}

/**
 * Adaptive cover rendering mode for the C/D hero cards.
 *
 * When the source aspect closely matches the style's target aspect the card draws one
 * full-bleed Crop layer (the established single-cover look). When the mismatch is severe
 * (>= [MISMATCH_THRESHOLD]) the same bitmap is used twice but as two visually distinct
 * layers: a blurred, scaled, dimmed crop fill that guarantees full bleed, plus a
 * `ContentScale.Fit` foreground that shows the complete artwork without cropping or
 * stretching. This is the "half image" fix: a wide source inside a 9:16 C card and a
 * portrait source inside a 16:9 D card both show the full cover instead of a centered
 * sliver with gradient/black padding.
 */
internal enum class FocusCoverMode {
    CROP,
    ADAPTIVE_BLUR_FILL,
}

internal data class FocusCoverPlan(
    val mode: FocusCoverMode,
    val fill: PortraitCoverPolicy.CoverLayout,
    /** Non-null only in [FocusCoverMode.ADAPTIVE_BLUR_FILL]. */
    val foreground: PortraitCoverPolicy.CoverLayout? = null,
    val ratioMismatch: Float,
) {
    val hasContent: Boolean get() = fill.hasContent
}

internal data class FocusCoverAdaptiveBlend(
    val sharpCropAlpha: Float,
    val blurredFillAlpha: Float,
    val fitAlpha: Float,
    val overlayStrength: Float,
)

internal object FocusCoverArtworkPolicy {
    /** Aspect mismatch above this value switches to blur-fill + full Fit foreground. */
    const val MISMATCH_THRESHOLD = 1.35f
    const val FILL_BLUR_DP = 12f
    const val FILL_SCALE = 1.04f
    const val FILL_SATURATION = .84f
    const val FILL_DARK_ALPHA = .26f
    const val FILL_LIGHT_ALPHA = .36f

    fun adaptiveBlend(focusAlpha: Float): FocusCoverAdaptiveBlend {
        val p = focusAlpha.coerceIn(0f, 1f)
        return FocusCoverAdaptiveBlend(
            sharpCropAlpha = 1f - p,
            blurredFillAlpha = p,
            fitAlpha = p,
            overlayStrength = p,
        )
    }

    /** Target card aspect (width/height) for the requested style. */
    fun targetAspect(style: LandscapeHomeStyle): Float =
        when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> FocusGalleryGeometryPolicy.D_WIDTH_TO_HEIGHT_RATIO
            LandscapeHomeStyle.FOCUS_POSTER -> PortraitCoverPolicy.WIDTH_TO_HEIGHT_RATIO
        }

    /**
     * Pure decision for one hero card. [targetWidthPx]/[targetHeightPx] are the actual
     * rounded-card box in px; the returned plan drives both the fill and the optional Fit
     * foreground in the composable.
     */
    fun plan(
        srcWidthPx: Int,
        srcHeightPx: Int,
        targetWidthPx: Float,
        targetHeightPx: Float,
        style: LandscapeHomeStyle,
    ): FocusCoverPlan {
        if (srcWidthPx <= 0 || srcHeightPx <= 0 || targetWidthPx <= 0f || targetHeightPx <= 0f) {
            return FocusCoverPlan(
                mode = FocusCoverMode.CROP,
                fill = PortraitCoverPolicy.CoverLayout(0f, 0f, 0f, 0f),
                foreground = null,
                ratioMismatch = 0f,
            )
        }
        val sourceAspect = srcWidthPx.toFloat() / srcHeightPx.toFloat()
        val targetAspect = targetAspect(style)
        val mismatch = maxOf(sourceAspect / targetAspect, targetAspect / sourceAspect)
        val fill = PortraitCoverPolicy.layout(srcWidthPx, srcHeightPx, targetWidthPx, targetHeightPx)
        if (mismatch <= MISMATCH_THRESHOLD) {
            return FocusCoverPlan(
                mode = FocusCoverMode.CROP,
                fill = fill,
                foreground = null,
                ratioMismatch = mismatch,
            )
        }
        return FocusCoverPlan(
            mode = FocusCoverMode.ADAPTIVE_BLUR_FILL,
            fill = fill,
            foreground = PortraitCoverPolicy.fitLayout(srcWidthPx, srcHeightPx, targetWidthPx, targetHeightPx),
            ratioMismatch = mismatch,
        )
    }
}
