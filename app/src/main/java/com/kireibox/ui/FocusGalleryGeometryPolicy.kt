package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle

/**
 * Pure geometry for the focus stage. All values derive from the viewport, the cutout-safe
 * guard and the Dock top so both rotations and cutout sides stay symmetric. D is a 16:9
 * arc Cover Flow (center width ~46-50vw capped by the height budget, side cards at
 * 0.42 of the center width — after SIDE_SCALE/arc projection ≈14.5vw — lifted and rotated
 * toward the center). C keeps the original 9:16 stacked poster with a 56dp visible side
 * strip and zero arc.
 */
internal object FocusGalleryGeometryPolicy {
    const val OUTER_EDGE_FRACTION = .04f
    const val OUTER_EDGE_SAFE_DP = 10f
    const val CENTER_MAX_HEIGHT_DP = 420f

    /** C side-card height relative to the center card. */
    const val SIDE_HEIGHT_FRACTION_C = .80f

    /** C target visible side-strip width after the SIDE_SCALE shrink is applied. */
    const val C_SIDE_VISIBLE_WIDTH_DP = 56f

    /** D center-card width: ~46-50vw on Pixel 8, capped by the 16:9 height budget. */
    const val D_CENTER_WIDTH_FRACTION = .48f
    const val D_CENTER_MIN_WIDTH_DP = 300f
    const val D_CENTER_MAX_WIDTH_DP = 440f

    /** D 侧卡宽 = 中心卡宽 × 0.42（投影后约 14.5vw），使 Pixel 8 稳定帧侧卡与中心卡保留明确约 40dp 间隙。 */
    const val D_SIDE_WIDTH_FRACTION = .42f

    /** D arc: side cards lift ~16-24dp and rotate +/-12-16 degrees toward the center. */
    const val D_ARC_LIFT_DP = 24f
    const val D_ARC_ROTATION_DEGREES = 16f
    const val D_ASPECT_WIDTH = 16f
    const val D_ASPECT_HEIGHT = 9f

    /** width:height = 16:9 for D arc Cover Flow cards. */
    val D_WIDTH_TO_HEIGHT_RATIO: Float = D_ASPECT_WIDTH / D_ASPECT_HEIGHT
    const val DOCK_GAP_DP = 12f
    const val SIDE_SCALE = .78f

    /** Minimum gap between a scaled D side card's inner edge and the center card. */
    const val D_INNER_GAP_MIN_DP = 10f
    const val SIDE_ALPHA = .58f
    const val SIDE_SATURATION = .76f
    const val CENTER_RADIUS_DP = 26f
    const val SIDE_RADIUS_DP = 22f
    const val METADATA_FADE_OUT_END = .45f
    const val METADATA_FADE_IN_START = .35f
    const val METADATA_FADE_IN_END = .85f
    const val BACKDROP_FADE_START = .15f
    const val BACKDROP_FADE_END = .85f
    const val EDGE_ENTER_FADE_START = .62f
    const val EDGE_EXIT_FADE_END = .42f

    fun outerMarginDp(
        viewportWidthDp: Float,
        symmetricSafeGuardDp: Float
    ): Float = maxOf(viewportWidthDp * OUTER_EDGE_FRACTION, symmetricSafeGuardDp.coerceAtLeast(0f) + OUTER_EDGE_SAFE_DP)

    /** D side-card visual left edge after the SIDE_SCALE shrink (stage-relative dp). */
    fun sideVisualLeftDp(geometry: FocusGalleryGeometry): Float = geometry.leftSlotCenterXDp - geometry.sideWidthDp * SIDE_SCALE / 2f

    /** D side-card visual right edge after the SIDE_SCALE shrink (stage-relative dp). */
    fun sideVisualRightDp(geometry: FocusGalleryGeometry): Float = geometry.leftSlotCenterXDp + geometry.sideWidthDp * SIDE_SCALE / 2f

    /** Gap between the center card's left edge and the scaled left side card's inner edge. */
    fun sideInnerGapDp(geometry: FocusGalleryGeometry): Float =
        (geometry.centerSlotCenterXDp - geometry.centerWidthDp / 2f - sideVisualRightDp(geometry))
            .coerceAtLeast(0f)

    fun geometry(
        viewportWidthDp: Float,
        symmetricSafeGuardDp: Float,
        dockTopDp: Float,
        stageHeightDp: Float,
        style: LandscapeHomeStyle = LandscapeHomeStyle.IMMERSIVE_COVER,
    ): FocusGalleryGeometry {
        val availableHeight = (dockTopDp - DOCK_GAP_DP).coerceAtLeast(0f)
        val centerWidth: Float
        val centerHeight: Float
        val sideWidth: Float
        val sideHeight: Float
        val arcLift: Float
        val arcRotation: Float
        when (style) {
            // D: 16:9 arc Cover Flow. The center width is min(~48vw, the 16:9 width the
            // height budget can hold), so the card is as large as the ratio allows and is
            // always fully contained between the header lane and the Dock top. The min
            // width clamp can never push the card past the height budget: on extremely
            // short windows the height-derived width wins and the card shrinks below the
            // nominal minimum instead of overflowing.
            LandscapeHomeStyle.IMMERSIVE_COVER -> {
                val widthByViewport = viewportWidthDp * D_CENTER_WIDTH_FRACTION
                val widthByHeight = availableHeight * D_WIDTH_TO_HEIGHT_RATIO
                val fittedWidth = minOf(widthByViewport, widthByHeight)
                centerWidth =
                    fittedWidth
                        .coerceAtMost(D_CENTER_MAX_WIDTH_DP)
                        .coerceAtLeast(minOf(D_CENTER_MIN_WIDTH_DP, fittedWidth))
                centerHeight = centerWidth / D_WIDTH_TO_HEIGHT_RATIO
                sideWidth = centerWidth * D_SIDE_WIDTH_FRACTION
                sideHeight = sideWidth / D_WIDTH_TO_HEIGHT_RATIO
                arcLift = D_ARC_LIFT_DP
                arcRotation = D_ARC_ROTATION_DEGREES
            }
            // C: 9:16 stacked poster. Center height is driven by the actual space between
            // the stage top (below the header) and the Dock top minus the fixed gap,
            // bounded by a sensible maximum; widths follow height * 9/16. No arc.
            LandscapeHomeStyle.FOCUS_POSTER -> {
                centerHeight = availableHeight.coerceAtMost(CENTER_MAX_HEIGHT_DP)
                centerWidth = PortraitCoverPolicy.widthForHeight(centerHeight)
                sideHeight = centerHeight * SIDE_HEIGHT_FRACTION_C
                sideWidth = PortraitCoverPolicy.widthForHeight(sideHeight)
                arcLift = 0f
                arcRotation = 0f
            }
        }
        val outer = outerMarginDp(viewportWidthDp, symmetricSafeGuardDp)
        val center = viewportWidthDp / 2f
        val leftCenter =
            when (style) {
                // C: stacked focus - the side card tucks behind the center card but must keep a
                // recognizable strip visible. The strip width is measured after the SIDE_SCALE
                // shrink (scale is applied around the card center), so the slot position is
                // derived from the visible half-width instead of the unscaled card edge.
                LandscapeHomeStyle.FOCUS_POSTER -> {
                    val centerLeft = center - centerWidth / 2f
                    val slot = centerLeft + sideWidth * SIDE_SCALE / 2f - C_SIDE_VISIBLE_WIDTH_DP
                    slot.coerceAtLeast(sideWidth / 2f)
                }
                // D: edge spread - the slot center is derived from the scaled half-width, so
                // after the SIDE_SCALE shrink the visible outer edge sits exactly on the safe
                // margin (outer) and the visible inner edge lands at outer + sideWidth*SIDE_SCALE,
                // leaving a real gap to the center card instead of an overlapping inner edge.
                LandscapeHomeStyle.IMMERSIVE_COVER -> outer + sideWidth * SIDE_SCALE / 2f
            }
        val rightCenter = viewportWidthDp - leftCenter
        val slotDistance = (center - leftCenter).coerceAtLeast(0f)
        val bottomGap = (stageHeightDp - centerHeight).coerceAtLeast(0f)
        return FocusGalleryGeometry(
            centerWidthDp = centerWidth,
            sideWidthDp = sideWidth,
            centerHeightDp = centerHeight,
            sideHeightDp = sideHeight,
            bottomGapDp = bottomGap,
            leftSlotCenterXDp = leftCenter,
            rightSlotCenterXDp = rightCenter,
            centerSlotCenterXDp = center,
            exitLeftXDp = -sideWidth / 2f - outer,
            exitRightXDp = viewportWidthDp + sideWidth / 2f + outer,
            slotDistanceDp = slotDistance,
            arcLiftDp = arcLift,
            arcRotationDegrees = arcRotation,
        )
    }

    /** Old center metadata fades out by .45 while the new center fades in .35-.85. */
    fun metadataCrossfade(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        val out = (1f - p / METADATA_FADE_OUT_END).coerceIn(0f, 1f)
        val input = ((p - METADATA_FADE_IN_START) / (METADATA_FADE_IN_END - METADATA_FADE_IN_START)).coerceIn(0f, 1f)
        return out to input
    }

    /** Background crossfade weight of the incoming game; 0 = old cover, 1 = new cover. */
    fun backdropProgress(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return ((p - BACKDROP_FADE_START) / (BACKDROP_FADE_END - BACKDROP_FADE_START)).coerceIn(0f, 1f)
    }

    fun arcLiftDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float = FocusGalleryGeometryProjectionPolicy.arcLiftDp(card, progress, geometry)

    fun rotationYDegrees(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float = FocusGalleryGeometryProjectionPolicy.rotationYDegrees(card, progress, geometry)

    fun widthDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float = FocusGalleryGeometryProjectionPolicy.widthDp(card, progress, geometry)

    fun heightDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float = FocusGalleryGeometryProjectionPolicy.heightDp(card, progress, geometry)

    fun centerXDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float = FocusGalleryGeometryProjectionPolicy.centerXDp(card, progress, geometry)

    fun scaleAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.scaleAt(card, progress)

    fun alphaAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.alphaAt(card, progress)

    fun saturationAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.saturationAt(card, progress)

    fun radiusAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.radiusAt(card, progress)

    fun zIndexAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.zIndexAt(card, progress)

    fun infoAlphaAt(
        card: FocusCard,
        progress: Float
    ): Float = FocusGalleryGeometryProjectionPolicy.infoAlphaAt(card, progress)

    fun cardGeometry(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry,
        reducedMotion: Boolean = false,
    ): FocusSlotGeometry = FocusGalleryGeometryProjectionPolicy.cardGeometry(card, progress, geometry, reducedMotion)
}
