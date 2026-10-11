package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle

/**
 * Pure D-only readability lift applied at the Compose graphicsLayer, never inside the
 * geometry/state-machine alpha. C (FOCUS_POSTER) keeps the raw cardGeometry alpha
 * unchanged; D is the only style that calls [stageAlpha].
 *
 * Luna V3 root cause: the parent side alpha (.58) also multiplies the border, so the
 * D side card reads like an 80px strip instead of its real ~389x278 bounds. Lifting the
 * rendered alpha to .84 at the resting side and keeping the border alpha dynamic restores
 * card definition without touching geometry, interaction, adaptive cover blend,
 * cameraDistance, arc lift, or the C path.
 */
internal object FocusGalleryVisualPolicy {
    /** D resting side-card rendered alpha after the Compose-layer boost. */
    const val D_SIDE_ALPHA = .84f

    /** D border outline alpha at the side (infoAlpha = 0). */
    const val D_OUTLINE_ALPHA_DARK_SIDE = .56f
    const val D_OUTLINE_ALPHA_LIGHT_SIDE = .42f

    /** D border outline alpha at the center (infoAlpha = 1). */
    const val D_OUTLINE_ALPHA_DARK_CENTER = .20f
    const val D_OUTLINE_ALPHA_LIGHT_CENTER = .14f

    /**
     * Maps the policy alpha to the D rendered alpha: 0 -> 0, SIDE_ALPHA (.58) -> .84,
     * 1 -> 1, linear in [0, .58] and [.58, 1], clamped and monotonic. ENTERING/EXITING
     * stay continuous from 0 and the resting/commit endpoints stay continuous with the
     * idle frame because both map through the same monotonic curve.
     */
    fun boostDAlpha(baseAlpha: Float): Float {
        val clamped = baseAlpha.coerceIn(0f, 1f)
        return if (clamped <= FocusGalleryGeometryPolicy.SIDE_ALPHA) {
            clamped * (D_SIDE_ALPHA / FocusGalleryGeometryPolicy.SIDE_ALPHA)
        } else {
            D_SIDE_ALPHA +
                (clamped - FocusGalleryGeometryPolicy.SIDE_ALPHA) *
                ((1f - D_SIDE_ALPHA) / (1f - FocusGalleryGeometryPolicy.SIDE_ALPHA))
        }
    }

    /** Rendered stage alpha: only D applies the boost; C keeps the original alpha. */
    fun stageAlpha(
        style: LandscapeHomeStyle,
        baseAlpha: Float
    ): Float = if (style == LandscapeHomeStyle.IMMERSIVE_COVER) boostDAlpha(baseAlpha) else baseAlpha

    /**
     * D-only card border alpha: strong at the side (infoAlpha 0) and softened at the
     * center so the arc flow never draws a hard ring around the focused card.
     */
    fun outlineAlpha(
        infoAlpha: Float,
        darkTheme: Boolean
    ): Float {
        val p = infoAlpha.coerceIn(0f, 1f)
        return if (darkTheme) {
            D_OUTLINE_ALPHA_DARK_SIDE +
                (D_OUTLINE_ALPHA_DARK_CENTER - D_OUTLINE_ALPHA_DARK_SIDE) * p
        } else {
            D_OUTLINE_ALPHA_LIGHT_SIDE +
                (D_OUTLINE_ALPHA_LIGHT_CENTER - D_OUTLINE_ALPHA_LIGHT_SIDE) * p
        }
    }

    /**
     * D (IMMERSIVE_COVER) owns the card artwork and style border at the FocusGalleryStage
     * outer card layer; C keeps both inside LandscapeSpotlightPage. Moving the artwork to
     * the outer card bypasses the nested-page clipping that collapsed the D side card into
     * an ~80px strip while the semantics bounds still reported the real ~389x278 geometry.
     */
    fun ownsOuterArtwork(style: LandscapeHomeStyle): Boolean = style == LandscapeHomeStyle.IMMERSIVE_COVER

    /**
     * Outer stage card y offset (dp, negative = up). D merges the bottom gap into the
     * outer offset and then drops the inner gap to 0, so the artwork box occupies the full
     * card geometry. C keeps the identity offset (-arc lift only, always 0 for the stacked
     * poster) and the inner bottom gap unchanged, locking the existing C layout.
     */
    fun outerOffsetY(
        style: LandscapeHomeStyle,
        arcLift: Float,
        bottomGap: Float
    ): Float =
        when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> -(arcLift + bottomGap)
            LandscapeHomeStyle.FOCUS_POSTER -> -arcLift
        }

    /** Inner page bottom gap: 0 for D (already merged into the outer offset), unchanged for C. */
    fun innerBottomGap(
        style: LandscapeHomeStyle,
        bottomGap: Float
    ): Float =
        when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> 0f
            LandscapeHomeStyle.FOCUS_POSTER -> bottomGap
        }
}
