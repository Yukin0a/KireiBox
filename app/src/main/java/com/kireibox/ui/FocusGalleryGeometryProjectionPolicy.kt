package com.kireibox.ui

internal object FocusGalleryGeometryProjectionPolicy {
    private fun sideSign(slot: FocusSlot): Float =
        when (slot) {
            FocusSlot.LEFT -> -1f
            FocusSlot.RIGHT -> 1f
            FocusSlot.CENTER,
            FocusSlot.ENTERING,
            -> 0f
        }

    /**
     * Arc lift above the resting baseline: 0 for the center card, ~20dp for D side slots,
     * and always 0 for C. SIDE_TO_CENTER un-lifts and CENTER_TO_SIDE lifts with the same
     * shared progress so no frame ever teleports.
     */
    fun arcLiftDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float {
        if (geometry.arcLiftDp <= 0f) return 0f
        return when (card.role) {
            FocusCardRole.SIDE_TO_CENTER -> geometry.arcLiftDp * (1f - progress.coerceIn(0f, 1f))
            FocusCardRole.CENTER_TO_SIDE -> geometry.arcLiftDp * progress.coerceIn(0f, 1f)
            FocusCardRole.OPPOSITE_SIDE,
            FocusCardRole.ENTERING,
            FocusCardRole.EXITING,
            -> geometry.arcLiftDp
        }
    }

    /**
     * Arc y-rotation toward the center: LEFT = -degrees, RIGHT = +degrees, center = 0.
     * The sign mirrors under both rotations and stays continuous at every sampled
     * progress; C is always 0.
     */
    fun rotationYDegrees(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float {
        if (geometry.arcRotationDegrees <= 0f) return 0f
        val side =
            when (card.role) {
                FocusCardRole.SIDE_TO_CENTER -> card.sourceSlot
                FocusCardRole.CENTER_TO_SIDE,
                FocusCardRole.ENTERING,
                -> card.targetSlot
                FocusCardRole.OPPOSITE_SIDE,
                FocusCardRole.EXITING,
                -> card.sourceSlot
            }
        val amount =
            when (card.role) {
                FocusCardRole.SIDE_TO_CENTER -> 1f - progress.coerceIn(0f, 1f)
                FocusCardRole.CENTER_TO_SIDE -> progress.coerceIn(0f, 1f)
                else -> 1f
            }
        return sideSign(side) * geometry.arcRotationDegrees * amount
    }

    private fun slotCenterXDp(
        slot: FocusSlot,
        geometry: FocusGalleryGeometry
    ): Float =
        when (slot) {
            FocusSlot.LEFT -> geometry.leftSlotCenterXDp
            FocusSlot.CENTER -> geometry.centerSlotCenterXDp
            FocusSlot.RIGHT -> geometry.rightSlotCenterXDp
            FocusSlot.ENTERING -> geometry.centerSlotCenterXDp
        }

    private fun slotWidthDp(
        slot: FocusSlot,
        geometry: FocusGalleryGeometry
    ): Float =
        when (slot) {
            FocusSlot.CENTER -> geometry.centerWidthDp
            FocusSlot.LEFT,
            FocusSlot.RIGHT,
            FocusSlot.ENTERING,
            -> geometry.sideWidthDp
        }

    private fun slotHeightDp(
        slot: FocusSlot,
        geometry: FocusGalleryGeometry
    ): Float =
        when (slot) {
            FocusSlot.CENTER -> geometry.centerHeightDp
            FocusSlot.LEFT,
            FocusSlot.RIGHT,
            FocusSlot.ENTERING,
            -> geometry.sideHeightDp
        }

    fun widthDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float {
        if (card.role == FocusCardRole.ENTERING || card.role == FocusCardRole.EXITING) {
            return geometry.sideWidthDp
        }
        val p = progress.coerceIn(0f, 1f)
        val source = slotWidthDp(card.sourceSlot, geometry)
        val target = slotWidthDp(card.targetSlot, geometry)
        return source + (target - source) * p
    }

    /** Card height interpolates between its source and target slot heights. */
    fun heightDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float {
        if (card.role == FocusCardRole.ENTERING || card.role == FocusCardRole.EXITING) {
            return geometry.sideHeightDp
        }
        val p = progress.coerceIn(0f, 1f)
        val source = slotHeightDp(card.sourceSlot, geometry)
        val target = slotHeightDp(card.targetSlot, geometry)
        return source + (target - source) * p
    }

    fun centerXDp(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry
    ): Float {
        if (card.role == FocusCardRole.EXITING) {
            val exit = if (card.sourceSlot == FocusSlot.LEFT) geometry.exitLeftXDp else geometry.exitRightXDp
            val origin = slotCenterXDp(card.sourceSlot, geometry)
            return origin + (exit - origin) * progress.coerceIn(0f, 1f)
        }
        if (card.role == FocusCardRole.ENTERING) {
            val entryOrigin =
                if (card.targetSlot == FocusSlot.RIGHT) {
                    geometry.exitRightXDp
                } else {
                    geometry.exitLeftXDp
                }
            val target = slotCenterXDp(card.targetSlot, geometry)
            return entryOrigin + (target - entryOrigin) * progress.coerceIn(0f, 1f)
        }
        val source = slotCenterXDp(card.sourceSlot, geometry)
        val target = slotCenterXDp(card.targetSlot, geometry)
        return source + (target - source) * progress.coerceIn(0f, 1f)
    }

    private fun focusAmount(
        progress: Float,
        role: FocusCardRole
    ): Float =
        when (role) {
            FocusCardRole.SIDE_TO_CENTER -> progress.coerceIn(0f, 1f)
            FocusCardRole.CENTER_TO_SIDE -> 1f - progress.coerceIn(0f, 1f)
            FocusCardRole.OPPOSITE_SIDE -> 0f
            FocusCardRole.ENTERING,
            FocusCardRole.EXITING,
            -> 0f
        }

    fun scaleAt(
        card: FocusCard,
        progress: Float
    ): Float {
        val focus = focusAmount(progress, card.role)
        return FocusGalleryGeometryPolicy.SIDE_SCALE + (1f - FocusGalleryGeometryPolicy.SIDE_SCALE) * focus
    }

    fun alphaAt(
        card: FocusCard,
        progress: Float
    ): Float =
        when (card.role) {
            // Edge cards stay effectively invisible while only a clipped sliver is on-screen.
            // The smooth bands remain continuous but avoid the old 30/60/75% edge fragments.
            FocusCardRole.ENTERING -> FocusGalleryGeometryPolicy.SIDE_ALPHA * smoothStep(FocusGalleryGeometryPolicy.EDGE_ENTER_FADE_START, 1f, progress)
            FocusCardRole.EXITING -> FocusGalleryGeometryPolicy.SIDE_ALPHA * (1f - smoothStep(0f, FocusGalleryGeometryPolicy.EDGE_EXIT_FADE_END, progress))
            FocusCardRole.OPPOSITE_SIDE -> FocusGalleryGeometryPolicy.SIDE_ALPHA
            else -> {
                val focus = focusAmount(progress, card.role)
                FocusGalleryGeometryPolicy.SIDE_ALPHA + (1f - FocusGalleryGeometryPolicy.SIDE_ALPHA) * focus
            }
        }

    fun saturationAt(
        card: FocusCard,
        progress: Float
    ): Float {
        val focus = focusAmount(progress, card.role)
        return FocusGalleryGeometryPolicy.SIDE_SATURATION + (1f - FocusGalleryGeometryPolicy.SIDE_SATURATION) * focus
    }

    fun radiusAt(
        card: FocusCard,
        progress: Float
    ): Float {
        val focus = focusAmount(progress, card.role)
        return FocusGalleryGeometryPolicy.SIDE_RADIUS_DP + (FocusGalleryGeometryPolicy.CENTER_RADIUS_DP - FocusGalleryGeometryPolicy.SIDE_RADIUS_DP) * focus
    }

    /**
     * z-index contract: the resting center card (CENTER_TO_SIDE at progress 0) must stay
     * above every side card even when C-style cards overlap it, and the incoming
     * SIDE_TO_CENTER card takes over once it crosses the animation midpoint.
     */
    fun zIndexAt(
        card: FocusCard,
        progress: Float
    ): Float =
        when (card.role) {
            FocusCardRole.SIDE_TO_CENTER -> 2f + 2f * progress.coerceIn(0f, 1f)
            FocusCardRole.CENTER_TO_SIDE -> 3f - progress.coerceIn(0f, 1f)
            FocusCardRole.ENTERING -> 1f
            FocusCardRole.EXITING -> 0f
            FocusCardRole.OPPOSITE_SIDE -> 0f
        }

    fun infoAlphaAt(
        card: FocusCard,
        progress: Float
    ): Float =
        when (card.role) {
            FocusCardRole.OPPOSITE_SIDE -> 0f
            FocusCardRole.ENTERING -> 0f
            FocusCardRole.EXITING -> 0f
            FocusCardRole.SIDE_TO_CENTER -> {
                val p = progress.coerceIn(0f, 1f)
                ((p - FocusGalleryGeometryPolicy.METADATA_FADE_IN_START) / (FocusGalleryGeometryPolicy.METADATA_FADE_IN_END - FocusGalleryGeometryPolicy.METADATA_FADE_IN_START)).coerceIn(0f, 1f)
            }
            FocusCardRole.CENTER_TO_SIDE -> {
                val p = progress.coerceIn(0f, 1f)
                (1f - p / FocusGalleryGeometryPolicy.METADATA_FADE_OUT_END).coerceIn(0f, 1f)
            }
        }

    fun cardGeometry(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry,
        reducedMotion: Boolean = false,
    ): FocusSlotGeometry {
        // Reduced motion only changes the handoff frames. The idle frame (progress 0) keeps
        // the normal slot geometry so previews rest at their edges and the center card stays
        // on top of tucked C-style side cards.
        if (reducedMotion && progress > 0f) return reducedMotionGeometry(card, progress, geometry)
        return fullMotionGeometry(card, progress, geometry)
    }

    private fun fullMotionGeometry(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry,
    ): FocusSlotGeometry {
        return FocusSlotGeometry(
            centerXDp = centerXDp(card, progress, geometry),
            widthDp = widthDp(card, progress, geometry),
            heightDp = heightDp(card, progress, geometry),
            scale = scaleAt(card, progress),
            alpha = alphaAt(card, progress),
            saturation = saturationAt(card, progress),
            radiusDp = radiusAt(card, progress),
            zIndex = zIndexAt(card, progress),
            infoAlpha = infoAlphaAt(card, progress),
            offsetYDp = arcLiftDp(card, progress, geometry),
            rotationYDegrees = rotationYDegrees(card, progress, geometry),
        )
    }

    /**
     * Reduced motion is an opacity-only focus handoff. Every card keeps one fixed position,
     * width and scale for the whole 160 ms transition; commit performs the direct slot switch.
     */
    private fun reducedMotionGeometry(
        card: FocusCard,
        progress: Float,
        geometry: FocusGalleryGeometry,
    ): FocusSlotGeometry {
        val p = progress.coerceIn(0f, 1f)
        return when (card.role) {
            FocusCardRole.CENTER_TO_SIDE ->
                FocusSlotGeometry(
                    centerXDp = geometry.centerSlotCenterXDp,
                    widthDp = geometry.centerWidthDp,
                    heightDp = geometry.centerHeightDp,
                    scale = 1f,
                    alpha = 1f - p,
                    saturation = 1f,
                    radiusDp = FocusGalleryGeometryPolicy.CENTER_RADIUS_DP,
                    zIndex = zIndexAt(card, p),
                    infoAlpha = 1f - p,
                    offsetYDp = 0f,
                    rotationYDegrees = 0f,
                )
            FocusCardRole.SIDE_TO_CENTER ->
                FocusSlotGeometry(
                    centerXDp = geometry.centerSlotCenterXDp,
                    widthDp = geometry.centerWidthDp,
                    heightDp = geometry.centerHeightDp,
                    scale = 1f,
                    alpha = p,
                    saturation = 1f,
                    radiusDp = FocusGalleryGeometryPolicy.CENTER_RADIUS_DP,
                    zIndex = zIndexAt(card, p),
                    infoAlpha = p,
                    offsetYDp = 0f,
                    rotationYDegrees = 0f,
                )
            FocusCardRole.ENTERING ->
                FocusSlotGeometry(
                    centerXDp = slotCenterXDp(card.targetSlot, geometry),
                    widthDp = geometry.sideWidthDp,
                    heightDp = geometry.sideHeightDp,
                    scale = FocusGalleryGeometryPolicy.SIDE_SCALE,
                    alpha = FocusGalleryGeometryPolicy.SIDE_ALPHA * p,
                    saturation = FocusGalleryGeometryPolicy.SIDE_SATURATION,
                    radiusDp = FocusGalleryGeometryPolicy.SIDE_RADIUS_DP,
                    zIndex = 1f,
                    infoAlpha = 0f,
                    offsetYDp = geometry.arcLiftDp,
                    rotationYDegrees = rotationYDegrees(card, 1f, geometry),
                )
            FocusCardRole.EXITING ->
                FocusSlotGeometry(
                    centerXDp = slotCenterXDp(card.sourceSlot, geometry),
                    widthDp = geometry.sideWidthDp,
                    heightDp = geometry.sideHeightDp,
                    scale = FocusGalleryGeometryPolicy.SIDE_SCALE,
                    alpha = FocusGalleryGeometryPolicy.SIDE_ALPHA * (1f - p),
                    saturation = FocusGalleryGeometryPolicy.SIDE_SATURATION,
                    radiusDp = FocusGalleryGeometryPolicy.SIDE_RADIUS_DP,
                    zIndex = 0f,
                    infoAlpha = 0f,
                    offsetYDp = geometry.arcLiftDp,
                    rotationYDegrees = rotationYDegrees(card, 0f, geometry),
                )
            FocusCardRole.OPPOSITE_SIDE ->
                FocusSlotGeometry(
                    centerXDp = slotCenterXDp(card.sourceSlot, geometry),
                    widthDp = geometry.sideWidthDp,
                    heightDp = geometry.sideHeightDp,
                    scale = FocusGalleryGeometryPolicy.SIDE_SCALE,
                    alpha = FocusGalleryGeometryPolicy.SIDE_ALPHA,
                    saturation = FocusGalleryGeometryPolicy.SIDE_SATURATION,
                    radiusDp = FocusGalleryGeometryPolicy.SIDE_RADIUS_DP,
                    zIndex = 0f,
                    infoAlpha = 0f,
                    offsetYDp = geometry.arcLiftDp,
                    rotationYDegrees = rotationYDegrees(card, 0f, geometry),
                )
        }
    }

    private fun smoothStep(
        start: Float,
        end: Float,
        value: Float
    ): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val x = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
