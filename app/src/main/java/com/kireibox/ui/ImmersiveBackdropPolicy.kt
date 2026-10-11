package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle

data class ImmersiveGradientStop(
    val offsetDp: Float,
    val alpha: Float,
    val colorToken: String,
)

data class ImmersiveBackdropPlan(
    val drawBackdrop: Boolean,
    val topGradientStops: List<ImmersiveGradientStop>,
    val drawHorizontalScrim: Boolean,
    val drawBottomScrim: Boolean,
    val fixedBackdropAlphaDim: Boolean,
    val static: Boolean,
    val blurRadiusDp: Float,
    val saturation: Float,
    val fullOverlayAlpha: Float,
    val staticCoverAlpha: Float,
)

/**
 * Pure rendering contract for the landscape V3 immersive cover. The cover is a low-frequency
 * atmosphere: cropped, ~18dp blur, ~0.85 saturation, slightly scaled for the blur edge, with a
 * theme-adaptive full-screen overlay and a top safe-area gradient so the hidden status bar and
 * header lane keep text readable. Dark mode uses Black 0.62 plus a Black 0.84-to-transparent top
 * gradient; light mode uses White 0.72 plus White 0.90-to-transparent. Both fade with the page-0
 * backdrop progress. Reduced transparency drops blur and crossfade and renders a static ~0.21
 * cover over the theme background.
 */
object ImmersiveBackdropPolicy {
    const val LIGHT_START = "white"
    const val DARK_START = "black"
    const val TRANSPARENT = "transparent"
    const val BACKDROP_BLUR_DP = 18f
    const val BACKDROP_SATURATION = .85f
    const val BACKDROP_SCALE = 1.06f
    const val STATIC_COVER_ALPHA = .21f
    const val DARK_FULL_OVERLAY_ALPHA = .62f
    const val LIGHT_FULL_OVERLAY_ALPHA = .72f

    /** C backdrop overlays are slightly lighter than D but keep text readable. */
    const val C_DARK_FULL_OVERLAY_ALPHA = .55f
    const val C_LIGHT_FULL_OVERLAY_ALPHA = .66f
    const val DARK_TOP_GRADIENT_START_ALPHA = .84f
    const val LIGHT_TOP_GRADIENT_START_ALPHA = .90f
    const val TOP_GRADIENT_EXTENT_DP = 132f

    /**
     * Both hero styles (D 16:9 arc Cover Flow and C 9:16 stacked poster) render the
     * current-game cover backdrop on page 0; the component page fades it out via the pager
     * progress.
     */
    fun shouldRender(
        style: LandscapeHomeStyle,
        heroVisible: Boolean
    ): Boolean =
        heroVisible && (
            style == LandscapeHomeStyle.IMMERSIVE_COVER ||
                style == LandscapeHomeStyle.FOCUS_POSTER
        )

    fun plan(
        style: LandscapeHomeStyle,
        reducedTransparency: Boolean,
        darkTheme: Boolean,
    ): ImmersiveBackdropPlan {
        if (style != LandscapeHomeStyle.IMMERSIVE_COVER &&
            style != LandscapeHomeStyle.FOCUS_POSTER
        ) {
            return ImmersiveBackdropPlan(
                drawBackdrop = false,
                topGradientStops = emptyList(),
                drawHorizontalScrim = false,
                drawBottomScrim = false,
                fixedBackdropAlphaDim = false,
                static = reducedTransparency,
                blurRadiusDp = 0f,
                saturation = 1f,
                fullOverlayAlpha = 0f,
                staticCoverAlpha = 0f,
            )
        }
        val start = if (darkTheme) DARK_START else LIGHT_START
        val startAlpha =
            if (darkTheme) {
                DARK_TOP_GRADIENT_START_ALPHA
            } else {
                LIGHT_TOP_GRADIENT_START_ALPHA
            }
        val fullOverlayAlpha =
            if (reducedTransparency) {
                0f
            } else if (darkTheme) {
                if (style == LandscapeHomeStyle.FOCUS_POSTER) {
                    C_DARK_FULL_OVERLAY_ALPHA
                } else {
                    DARK_FULL_OVERLAY_ALPHA
                }
            } else {
                if (style == LandscapeHomeStyle.FOCUS_POSTER) {
                    C_LIGHT_FULL_OVERLAY_ALPHA
                } else {
                    LIGHT_FULL_OVERLAY_ALPHA
                }
            }
        return ImmersiveBackdropPlan(
            drawBackdrop = true,
            topGradientStops =
                if (reducedTransparency) {
                    emptyList()
                } else {
                    listOf(
                        ImmersiveGradientStop(0f, startAlpha, start),
                        ImmersiveGradientStop(TOP_GRADIENT_EXTENT_DP, 0f, TRANSPARENT),
                    )
                },
            drawHorizontalScrim = false,
            drawBottomScrim = false,
            fixedBackdropAlphaDim = false,
            static = reducedTransparency,
            blurRadiusDp = if (reducedTransparency) 0f else BACKDROP_BLUR_DP,
            saturation = if (reducedTransparency) 1f else BACKDROP_SATURATION,
            fullOverlayAlpha = fullOverlayAlpha,
            staticCoverAlpha = STATIC_COVER_ALPHA,
        )
    }
}
