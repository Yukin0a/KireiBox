package com.kireibox.ui

/**
 * Alpha 8 V3 hero gallery geometry: center ~31vw clamped 280-330dp, side ~18vw clamped
 * 156-196dp, side-card centers near 13.5vw/86.5vw with an outer margin of max(cutout-safe
 * + 10dp, ~4.5vw). All geometry is derived in one place and is symmetric under both
 * rotations because it consumes the larger physical side guard for both edges. Width,
 * scale, alpha, saturation and radius interpolate continuously with the pager offset so
 * side-to-center motion never snaps or pops.
 */
object LandscapeHeroSizingPolicy {
    const val CENTER_FRACTION = .275f
    const val CENTER_MIN_WIDTH_DP = 244f
    const val CENTER_MAX_WIDTH_DP = 294f
    const val SIDE_FRACTION = .145f
    const val SIDE_MIN_WIDTH_DP = 132f
    const val SIDE_MAX_WIDTH_DP = 176f
    const val SIDE_CENTER_FRACTION = .11f
    const val OUTER_MARGIN_FRACTION = .04f
    const val OUTER_MARGIN_SAFE_DP = 10f
    const val SIDE_ALPHA = .60f
    const val SIDE_SCALE = .78f
    const val SIDE_BLUR_DP = 1f
    const val SIDE_SATURATION = .72f
    const val CENTER_RADIUS_DP = 26f
    const val SIDE_RADIUS_DP = 22f

    /** Center card width is ~31% of the effective viewport width, clamped to 280-330dp. */
    fun centerCardWidthDp(viewportWidthDp: Float): Float {
        return (viewportWidthDp * CENTER_FRACTION).coerceIn(CENTER_MIN_WIDTH_DP, CENTER_MAX_WIDTH_DP)
    }

    fun sideCardWidthDp(viewportWidthDp: Float): Float = (viewportWidthDp * SIDE_FRACTION).coerceIn(SIDE_MIN_WIDTH_DP, SIDE_MAX_WIDTH_DP)

    /**
     * The larger physical side guard is mirrored to both edges so left and right rotation
     * produce the same geometry. [symmetricSafeGuardDp] is expected to already be mirrored.
     */
    fun outerMarginDp(
        viewportWidthDp: Float,
        symmetricSafeGuardDp: Float
    ): Float = maxOf(viewportWidthDp * OUTER_MARGIN_FRACTION, symmetricSafeGuardDp.coerceAtLeast(0f) + OUTER_MARGIN_SAFE_DP)

    /** Left side-card center: ~13.5vw, pushed in only when the cutout-safe margin requires it. */
    fun sideCenterLeftDp(
        viewportWidthDp: Float,
        outerMarginDp: Float,
        sideWidthDp: Float,
    ): Float = maxOf(viewportWidthDp * SIDE_CENTER_FRACTION, outerMarginDp + sideWidthDp / 2f)

    fun sideCenterRightDp(
        viewportWidthDp: Float,
        outerMarginDp: Float,
        sideWidthDp: Float,
    ): Float = viewportWidthDp - sideCenterLeftDp(viewportWidthDp, outerMarginDp, sideWidthDp)

    /** Distance between the center slot and an adjacent side slot. */
    fun slotCenterDistanceDp(
        viewportWidthDp: Float,
        sideCenterLeftDp: Float
    ): Float = (viewportWidthDp / 2f - sideCenterLeftDp).coerceAtLeast(0f)

    fun pageSpacingDp(
        centerWidthDp: Float,
        slotCenterDistanceDp: Float
    ): Float = (slotCenterDistanceDp - centerWidthDp).coerceAtLeast(0f)

    /** 1 when the card is the center, 0 when it is fully a side preview. */
    fun centerFocus(pageOffset: Float): Float = (1f - pageOffset.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    fun cardWidthDpAt(
        centerWidthDp: Float,
        sideWidthDp: Float,
        pageOffset: Float,
    ): Float = sideWidthDp + (centerWidthDp - sideWidthDp) * centerFocus(pageOffset)

    fun cardAlphaAt(pageOffset: Float): Float = SIDE_ALPHA + (1f - SIDE_ALPHA) * centerFocus(pageOffset)

    fun cardScaleAt(pageOffset: Float): Float = SIDE_SCALE + (1f - SIDE_SCALE) * centerFocus(pageOffset)

    fun cardSaturationAt(pageOffset: Float): Float = SIDE_SATURATION + (1f - SIDE_SATURATION) * centerFocus(pageOffset)

    fun cardRadiusAt(pageOffset: Float): Float = SIDE_RADIUS_DP + (CENTER_RADIUS_DP - SIDE_RADIUS_DP) * centerFocus(pageOffset)

    /** Title/play information fades in only as focus approaches the center. */
    fun infoAlphaAt(pageOffset: Float): Float = ((centerFocus(pageOffset) - .5f) / .5f).coerceIn(0f, 1f)

    /** Input gate: the play button is only actionable when the card is visually near center. */
    fun isActionableCenter(pageOffset: Float): Boolean = centerFocus(pageOffset) >= .5f
}
