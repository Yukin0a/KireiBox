package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import kotlin.math.abs

/**
 * Alpha 8 Focus Gallery V2: a deterministic four-slot focus stage that replaces the
 * virtual/infinite pager reorder motion. Visible slots are LEFT, CENTER, RIGHT plus an
 * ENTERING cache slot. The current game index/ID commits only after the transition
 * completes; cards are keyed by slot identity so no list mutation or rebinding happens at
 * intermediate frames.
 */
internal enum class FocusSlot {
    LEFT,
    CENTER,
    RIGHT,
    ENTERING;

    val stageKey: String get() = "focus_slot_${name.lowercase()}"
}

internal enum class FocusDirection {
    /** Next game: incoming enters from the right, old center exits toward the left. */
    ADVANCE,
    /** Previous game: incoming enters from the left, old center exits toward the right. */
    BACK;

    val reversed: FocusDirection
        get() = if (this == ADVANCE) BACK else ADVANCE
}

internal enum class FocusCardRole {
    CENTER_TO_SIDE,
    SIDE_TO_CENTER,
    OPPOSITE_SIDE,
    ENTERING,
    EXITING,
}

internal data class FocusCard(
    val gameId: Long,
    val sourceSlot: FocusSlot,
    val targetSlot: FocusSlot,
    val role: FocusCardRole,
) {
    /** Card identity follows the slot, never the game, so slots never rebind mid-flight. */
    val key: String get() = sourceSlot.stageKey
}

/** Resolves policy index cards to real game IDs; used by the controller and UI. */
internal object FocusGalleryGameResolver {
    fun resolve(
        cards: List<FocusCard>,
        games: List<com.yuki.yukihub.model.Game>,
    ): List<FocusCard> {
        if (games.isEmpty()) return cards
        return cards.map { card ->
            card.copy(gameId = games[card.gameId.toInt().mod(games.size)].id)
        }
    }
}

internal data class FocusGallerySnapshot(
    val currentGameIndex: Int,
    val targetGameIndex: Int,
    val direction: FocusDirection,
    val progress: Float,
    val cards: List<FocusCard>,
    val focusedGameId: Long? = null,
    val isSettling: Boolean = false,
    /** Bumped by every successful commit; the auto timer restarts on this value. */
    val commitSequence: Long = 0L,
    /** Bumped by every explicit user tap; cancels and re-times the auto timer. */
    val manualInteractionSequence: Long = 0L,
)

/** Lift of focus transition state for the page-0 root backdrop crossfade. */
internal data class FocusGalleryBackdropState(
    val currentGameIndex: Int = 0,
    val targetGameIndex: Int = 0,
    val progress: Float = 0f,
)

/** Stable virtual indexing used by the pure mapping (2-5 games). */
internal object FocusGalleryIndexPolicy {
    fun previous(index: Int, gameCount: Int): Int {
        if (gameCount <= 1) return 0
        return ((index - 1).mod(gameCount))
    }

    fun next(index: Int, gameCount: Int): Int {
        if (gameCount <= 1) return 0
        return (index + 1).mod(gameCount)
    }

    fun afterNext(index: Int, gameCount: Int): Int = next(next(index, gameCount), gameCount)
    fun beforePrevious(index: Int, gameCount: Int): Int = previous(previous(index, gameCount), gameCount)
}

/** Pure slot mapping. The returned cards reference game IDs only; slots are keyed by identity. */
internal object FocusGalleryMappingPolicy {
    fun cards(
        gameCount: Int,
        currentGameIndex: Int,
        direction: FocusDirection,
    ): List<FocusCard> {
        if (gameCount <= 0) return emptyList()
        if (gameCount == 1) {
            return listOf(
                FocusCard(0L, FocusSlot.LEFT, FocusSlot.LEFT, FocusCardRole.OPPOSITE_SIDE),
                FocusCard(0L, FocusSlot.CENTER, FocusSlot.CENTER, FocusCardRole.SIDE_TO_CENTER),
                FocusCard(0L, FocusSlot.RIGHT, FocusSlot.RIGHT, FocusCardRole.OPPOSITE_SIDE),
                FocusCard(0L, FocusSlot.ENTERING, FocusSlot.ENTERING, FocusCardRole.ENTERING),
            )
        }
        val center = currentGameIndex.mod(gameCount)
        val prev = FocusGalleryIndexPolicy.previous(center, gameCount)
        val next = FocusGalleryIndexPolicy.next(center, gameCount)
        return when (direction) {
            FocusDirection.ADVANCE -> listOf(
                FocusCard(
                    gameId = prev.toLong(),
                    sourceSlot = FocusSlot.LEFT,
                    targetSlot = FocusSlot.LEFT,
                    role = FocusCardRole.EXITING,
                ),
                FocusCard(
                    gameId = center.toLong(),
                    sourceSlot = FocusSlot.CENTER,
                    targetSlot = FocusSlot.LEFT,
                    role = FocusCardRole.CENTER_TO_SIDE,
                ),
                FocusCard(
                    gameId = next.toLong(),
                    sourceSlot = FocusSlot.RIGHT,
                    targetSlot = FocusSlot.CENTER,
                    role = FocusCardRole.SIDE_TO_CENTER,
                ),
                FocusCard(
                    gameId = FocusGalleryIndexPolicy.afterNext(center, gameCount).toLong(),
                    sourceSlot = FocusSlot.ENTERING,
                    targetSlot = FocusSlot.RIGHT,
                    role = FocusCardRole.ENTERING,
                ),
            )
            FocusDirection.BACK -> listOf(
                FocusCard(
                    gameId = prev.toLong(),
                    sourceSlot = FocusSlot.LEFT,
                    targetSlot = FocusSlot.CENTER,
                    role = FocusCardRole.SIDE_TO_CENTER,
                ),
                FocusCard(
                    gameId = center.toLong(),
                    sourceSlot = FocusSlot.CENTER,
                    targetSlot = FocusSlot.RIGHT,
                    role = FocusCardRole.CENTER_TO_SIDE,
                ),
                FocusCard(
                    gameId = next.toLong(),
                    sourceSlot = FocusSlot.RIGHT,
                    targetSlot = FocusSlot.RIGHT,
                    role = FocusCardRole.EXITING,
                ),
                FocusCard(
                    gameId = FocusGalleryIndexPolicy.beforePrevious(center, gameCount).toLong(),
                    sourceSlot = FocusSlot.ENTERING,
                    targetSlot = FocusSlot.LEFT,
                    role = FocusCardRole.ENTERING,
                ),
            )
        }
    }

    /**
     * After settle the committed current index is the incoming game, so the next mapping
     * starts from a stable current game even when the library reorders.
     */
    fun committedCurrentIndex(
        currentGameIndex: Int,
        gameCount: Int,
        direction: FocusDirection,
    ): Int = when (direction) {
        FocusDirection.ADVANCE -> FocusGalleryIndexPolicy.next(currentGameIndex, gameCount)
        FocusDirection.BACK -> FocusGalleryIndexPolicy.previous(currentGameIndex, gameCount)
    }

    /** True only when progress reached the exact commit point. */
    fun shouldCommit(progress: Float): Boolean = progress >= 1f

}

internal data class FocusNavigationPlan(
    val direction: FocusDirection,
    val stepCount: Int,
    /** Exact destination index captured when the user action was issued. */
    val targetIndex: Int,
)

internal data class FocusSlotGeometry(
    val centerXDp: Float,
    val widthDp: Float,
    val heightDp: Float,
    val scale: Float,
    val alpha: Float,
    val saturation: Float,
    val radiusDp: Float,
    val zIndex: Float,
    val infoAlpha: Float,
    /** Arc lift above the resting baseline (positive = up). C is always 0. */
    val offsetYDp: Float = 0f,
    /** Arc y-rotation toward the center. C is always 0. */
    val rotationYDegrees: Float = 0f,
)

internal data class FocusGalleryGeometry(
    val centerWidthDp: Float,
    val sideWidthDp: Float,
    val centerHeightDp: Float,
    val sideHeightDp: Float,
    val bottomGapDp: Float,
    val leftSlotCenterXDp: Float,
    val rightSlotCenterXDp: Float,
    val centerSlotCenterXDp: Float,
    val exitLeftXDp: Float,
    val exitRightXDp: Float,
    val slotDistanceDp: Float,
    /** D arc lift in dp; 0 for C. */
    val arcLiftDp: Float = 0f,
    /** D arc rotation magnitude in degrees; 0 for C. */
    val arcRotationDegrees: Float = 0f,
)

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

    fun outerMarginDp(viewportWidthDp: Float, symmetricSafeGuardDp: Float): Float =
        maxOf(viewportWidthDp * OUTER_EDGE_FRACTION, symmetricSafeGuardDp.coerceAtLeast(0f) + OUTER_EDGE_SAFE_DP)

    /** D side-card visual left edge after the SIDE_SCALE shrink (stage-relative dp). */
    fun sideVisualLeftDp(geometry: FocusGalleryGeometry): Float =
        geometry.leftSlotCenterXDp - geometry.sideWidthDp * SIDE_SCALE / 2f

    /** D side-card visual right edge after the SIDE_SCALE shrink (stage-relative dp). */
    fun sideVisualRightDp(geometry: FocusGalleryGeometry): Float =
        geometry.leftSlotCenterXDp + geometry.sideWidthDp * SIDE_SCALE / 2f

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
                centerWidth = fittedWidth
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
        val leftCenter = when (style) {
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

    private fun sideSign(slot: FocusSlot): Float = when (slot) {
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
    fun arcLiftDp(card: FocusCard, progress: Float, geometry: FocusGalleryGeometry): Float {
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
    fun rotationYDegrees(card: FocusCard, progress: Float, geometry: FocusGalleryGeometry): Float {
        if (geometry.arcRotationDegrees <= 0f) return 0f
        val side = when (card.role) {
            FocusCardRole.SIDE_TO_CENTER -> card.sourceSlot
            FocusCardRole.CENTER_TO_SIDE,
            FocusCardRole.ENTERING,
            -> card.targetSlot
            FocusCardRole.OPPOSITE_SIDE,
            FocusCardRole.EXITING,
            -> card.sourceSlot
        }
        val amount = when (card.role) {
            FocusCardRole.SIDE_TO_CENTER -> 1f - progress.coerceIn(0f, 1f)
            FocusCardRole.CENTER_TO_SIDE -> progress.coerceIn(0f, 1f)
            else -> 1f
        }
        return sideSign(side) * geometry.arcRotationDegrees * amount
    }

    private fun slotCenterXDp(slot: FocusSlot, geometry: FocusGalleryGeometry): Float = when (slot) {
        FocusSlot.LEFT -> geometry.leftSlotCenterXDp
        FocusSlot.CENTER -> geometry.centerSlotCenterXDp
        FocusSlot.RIGHT -> geometry.rightSlotCenterXDp
        FocusSlot.ENTERING -> geometry.centerSlotCenterXDp
    }

    private fun slotWidthDp(slot: FocusSlot, geometry: FocusGalleryGeometry): Float = when (slot) {
        FocusSlot.CENTER -> geometry.centerWidthDp
        FocusSlot.LEFT,
        FocusSlot.RIGHT,
        FocusSlot.ENTERING,
        -> geometry.sideWidthDp
    }

    private fun slotHeightDp(slot: FocusSlot, geometry: FocusGalleryGeometry): Float = when (slot) {
        FocusSlot.CENTER -> geometry.centerHeightDp
        FocusSlot.LEFT,
        FocusSlot.RIGHT,
        FocusSlot.ENTERING,
        -> geometry.sideHeightDp
    }

    fun widthDp(card: FocusCard, progress: Float, geometry: FocusGalleryGeometry): Float {
        if (card.role == FocusCardRole.ENTERING || card.role == FocusCardRole.EXITING) {
            return geometry.sideWidthDp
        }
        val p = progress.coerceIn(0f, 1f)
        val source = slotWidthDp(card.sourceSlot, geometry)
        val target = slotWidthDp(card.targetSlot, geometry)
        return source + (target - source) * p
    }

    /** Card height interpolates between its source and target slot heights. */
    fun heightDp(card: FocusCard, progress: Float, geometry: FocusGalleryGeometry): Float {
        if (card.role == FocusCardRole.ENTERING || card.role == FocusCardRole.EXITING) {
            return geometry.sideHeightDp
        }
        val p = progress.coerceIn(0f, 1f)
        val source = slotHeightDp(card.sourceSlot, geometry)
        val target = slotHeightDp(card.targetSlot, geometry)
        return source + (target - source) * p
    }

    fun centerXDp(card: FocusCard, progress: Float, geometry: FocusGalleryGeometry): Float {
        if (card.role == FocusCardRole.EXITING) {
            val exit = if (card.sourceSlot == FocusSlot.LEFT) geometry.exitLeftXDp else geometry.exitRightXDp
            val origin = slotCenterXDp(card.sourceSlot, geometry)
            return origin + (exit - origin) * progress.coerceIn(0f, 1f)
        }
        if (card.role == FocusCardRole.ENTERING) {
            val entryOrigin = if (card.targetSlot == FocusSlot.RIGHT) {
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

    private fun focusAmount(progress: Float, role: FocusCardRole): Float = when (role) {
        FocusCardRole.SIDE_TO_CENTER -> progress.coerceIn(0f, 1f)
        FocusCardRole.CENTER_TO_SIDE -> 1f - progress.coerceIn(0f, 1f)
        FocusCardRole.OPPOSITE_SIDE -> 0f
        FocusCardRole.ENTERING,
        FocusCardRole.EXITING,
        -> 0f
    }

    fun scaleAt(card: FocusCard, progress: Float): Float {
        val focus = focusAmount(progress, card.role)
        return SIDE_SCALE + (1f - SIDE_SCALE) * focus
    }

    fun alphaAt(card: FocusCard, progress: Float): Float = when (card.role) {
        // Edge cards stay effectively invisible while only a clipped sliver is on-screen.
        // The smooth bands remain continuous but avoid the old 30/60/75% edge fragments.
        FocusCardRole.ENTERING -> SIDE_ALPHA * smoothStep(EDGE_ENTER_FADE_START, 1f, progress)
        FocusCardRole.EXITING -> SIDE_ALPHA * (1f - smoothStep(0f, EDGE_EXIT_FADE_END, progress))
        FocusCardRole.OPPOSITE_SIDE -> SIDE_ALPHA
        else -> {
            val focus = focusAmount(progress, card.role)
            SIDE_ALPHA + (1f - SIDE_ALPHA) * focus
        }
    }

    fun saturationAt(card: FocusCard, progress: Float): Float {
        val focus = focusAmount(progress, card.role)
        return SIDE_SATURATION + (1f - SIDE_SATURATION) * focus
    }

    fun radiusAt(card: FocusCard, progress: Float): Float {
        val focus = focusAmount(progress, card.role)
        return SIDE_RADIUS_DP + (CENTER_RADIUS_DP - SIDE_RADIUS_DP) * focus
    }

    /**
     * z-index contract: the resting center card (CENTER_TO_SIDE at progress 0) must stay
     * above every side card even when C-style cards overlap it, and the incoming
     * SIDE_TO_CENTER card takes over once it crosses the animation midpoint.
     */
    fun zIndexAt(card: FocusCard, progress: Float): Float = when (card.role) {
        FocusCardRole.SIDE_TO_CENTER -> 2f + 2f * progress.coerceIn(0f, 1f)
        FocusCardRole.CENTER_TO_SIDE -> 3f - progress.coerceIn(0f, 1f)
        FocusCardRole.ENTERING -> 1f
        FocusCardRole.EXITING -> 0f
        FocusCardRole.OPPOSITE_SIDE -> 0f
    }

    fun infoAlphaAt(card: FocusCard, progress: Float): Float = when (card.role) {
        FocusCardRole.OPPOSITE_SIDE -> 0f
        FocusCardRole.ENTERING -> 0f
        FocusCardRole.EXITING -> 0f
        FocusCardRole.SIDE_TO_CENTER -> {
            val p = progress.coerceIn(0f, 1f)
            ((p - METADATA_FADE_IN_START) / (METADATA_FADE_IN_END - METADATA_FADE_IN_START)).coerceIn(0f, 1f)
        }
        FocusCardRole.CENTER_TO_SIDE -> {
            val p = progress.coerceIn(0f, 1f)
            (1f - p / METADATA_FADE_OUT_END).coerceIn(0f, 1f)
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
            FocusCardRole.CENTER_TO_SIDE -> FocusSlotGeometry(
                centerXDp = geometry.centerSlotCenterXDp,
                widthDp = geometry.centerWidthDp,
                heightDp = geometry.centerHeightDp,
                scale = 1f,
                alpha = 1f - p,
                saturation = 1f,
                radiusDp = CENTER_RADIUS_DP,
                zIndex = zIndexAt(card, p),
                infoAlpha = 1f - p,
                offsetYDp = 0f,
                rotationYDegrees = 0f,
            )
            FocusCardRole.SIDE_TO_CENTER -> FocusSlotGeometry(
                centerXDp = geometry.centerSlotCenterXDp,
                widthDp = geometry.centerWidthDp,
                heightDp = geometry.centerHeightDp,
                scale = 1f,
                alpha = p,
                saturation = 1f,
                radiusDp = CENTER_RADIUS_DP,
                zIndex = zIndexAt(card, p),
                infoAlpha = p,
                offsetYDp = 0f,
                rotationYDegrees = 0f,
            )
            FocusCardRole.ENTERING -> FocusSlotGeometry(
                centerXDp = slotCenterXDp(card.targetSlot, geometry),
                widthDp = geometry.sideWidthDp,
                heightDp = geometry.sideHeightDp,
                scale = SIDE_SCALE,
                alpha = SIDE_ALPHA * p,
                saturation = SIDE_SATURATION,
                radiusDp = SIDE_RADIUS_DP,
                zIndex = 1f,
                infoAlpha = 0f,
                offsetYDp = geometry.arcLiftDp,
                rotationYDegrees = rotationYDegrees(card, 1f, geometry),
            )
            FocusCardRole.EXITING -> FocusSlotGeometry(
                centerXDp = slotCenterXDp(card.sourceSlot, geometry),
                widthDp = geometry.sideWidthDp,
                heightDp = geometry.sideHeightDp,
                scale = SIDE_SCALE,
                alpha = SIDE_ALPHA * (1f - p),
                saturation = SIDE_SATURATION,
                radiusDp = SIDE_RADIUS_DP,
                zIndex = 0f,
                infoAlpha = 0f,
                offsetYDp = geometry.arcLiftDp,
                rotationYDegrees = rotationYDegrees(card, 0f, geometry),
            )
            FocusCardRole.OPPOSITE_SIDE -> FocusSlotGeometry(
                centerXDp = slotCenterXDp(card.sourceSlot, geometry),
                widthDp = geometry.sideWidthDp,
                heightDp = geometry.sideHeightDp,
                scale = SIDE_SCALE,
                alpha = SIDE_ALPHA,
                saturation = SIDE_SATURATION,
                radiusDp = SIDE_RADIUS_DP,
                zIndex = 0f,
                infoAlpha = 0f,
                offsetYDp = geometry.arcLiftDp,
                rotationYDegrees = rotationYDegrees(card, 0f, geometry),
            )
        }
    }

    private fun smoothStep(start: Float, end: Float, value: Float): Float {
        if (end <= start) return if (value >= end) 1f else 0f
        val x = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
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
}

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
    fun stageAlpha(style: LandscapeHomeStyle, baseAlpha: Float): Float =
        if (style == LandscapeHomeStyle.IMMERSIVE_COVER) boostDAlpha(baseAlpha) else baseAlpha

    /**
     * D-only card border alpha: strong at the side (infoAlpha 0) and softened at the
     * center so the arc flow never draws a hard ring around the focused card.
     */
    fun outlineAlpha(infoAlpha: Float, darkTheme: Boolean): Float {
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
    fun ownsOuterArtwork(style: LandscapeHomeStyle): Boolean =
        style == LandscapeHomeStyle.IMMERSIVE_COVER

    /**
     * Outer stage card y offset (dp, negative = up). D merges the bottom gap into the
     * outer offset and then drops the inner gap to 0, so the artwork box occupies the full
     * card geometry. C keeps the identity offset (-arc lift only, always 0 for the stacked
     * poster) and the inner bottom gap unchanged, locking the existing C layout.
     */
    fun outerOffsetY(style: LandscapeHomeStyle, arcLift: Float, bottomGap: Float): Float =
        when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> -(arcLift + bottomGap)
            LandscapeHomeStyle.FOCUS_POSTER -> -arcLift
        }

    /** Inner page bottom gap: 0 for D (already merged into the outer offset), unchanged for C. */
    fun innerBottomGap(style: LandscapeHomeStyle, bottomGap: Float): Float =
        when (style) {
            LandscapeHomeStyle.IMMERSIVE_COVER -> 0f
            LandscapeHomeStyle.FOCUS_POSTER -> bottomGap
        }
}

internal data class FocusDragDecision(
    val targetProgress: Float,
    val direction: FocusDirection,
    val settleToCommitted: Boolean,
)

internal object FocusGalleryAutoAdvancePolicy {
    fun shouldRunTimer(
        gameCount: Int,
        galleryIdle: Boolean,
        isVisible: Boolean,
        isResumed: Boolean,
        isCardPressed: Boolean,
    ): Boolean = gameCount > 1 && galleryIdle && isVisible && isResumed && !isCardPressed
}

/** Outcome of an explicit side-card tap resolved by [FocusSwitchPolicy]. */
internal enum class FocusSwitchResult {
    /** A new transition to the tapped game was started. */
    NAVIGATED,
    /** The tapped game is already the destination of the in-flight transition. */
    CONTINUING,
    /** Idle and already centered on the tapped game; nothing changed. */
    STAYED,
    /** The tapped game could not be resolved. */
    IGNORED,
}

/**
 * Pure decision for an explicit side-card/dot tap. The controller resolves the tapped
 * game id to an index synchronously and asks this policy which action to take, so a tap
 * always targets the card the user actually saw and never depends on a stale commit.
 */
internal object FocusSwitchPolicy {
    fun decide(
        currentGameIndex: Int,
        targetGameIndex: Int,
        isSettling: Boolean,
        settlingTargetIndex: Int,
    ): FocusSwitchResult = when {
        targetGameIndex < 0 -> FocusSwitchResult.IGNORED
        isSettling && settlingTargetIndex == targetGameIndex -> FocusSwitchResult.CONTINUING
        isSettling && currentGameIndex == targetGameIndex -> FocusSwitchResult.STAYED
        !isSettling && currentGameIndex == targetGameIndex -> FocusSwitchResult.STAYED
        else -> FocusSwitchResult.NAVIGATED
    }
}

/**
 * Pure drag/settle contract shared by manual drag, side tap and auto advance. Drag maps
 * 1:1 to page progress inside the [0, 1] bounds and the release settles to exactly one
 * page (0 = return to the anchor, 1 = commit the neighbor), so a gesture can never cross
 * more than one page; cyclic content keeps the hard in-bounds stop and gets no
 * finite-edge rubber-band.
 */
internal object FocusGalleryInteractionPolicy {
    /** Distance threshold: releasing at >= 0.28 page commits without a fast fling. */
    const val CLAIM_PROGRESS = .28f
    /** Velocity threshold in pages per second: faster flings commit on release. */
    const val CLAIM_VELOCITY_PAGES_PER_SECOND = .95f

    fun progressFromDragDelta(deltaXDp: Float, direction: FocusDirection, slotDistanceDp: Float): Float {
        val normalized = if (slotDistanceDp > 0f) deltaXDp / slotDistanceDp else 0f
        return when (direction) {
            FocusDirection.ADVANCE -> -normalized.coerceIn(-1f, 1f)
            FocusDirection.BACK -> normalized.coerceIn(-1f, 1f)
        }
    }

    fun settle(
        progress: Float,
        velocityPagesPerSecond: Float,
        requestedDirection: FocusDirection,
    ): FocusDragDecision {
        val moving = abs(velocityPagesPerSecond) >= CLAIM_VELOCITY_PAGES_PER_SECOND
        val commit = if (moving) {
            // ADVANCE is a leftward drag (negative x); BACK is rightward (positive x).
            (velocityPagesPerSecond < 0f) == (requestedDirection == FocusDirection.ADVANCE)
        } else {
            progress >= CLAIM_PROGRESS
        }
        return FocusDragDecision(
            targetProgress = if (commit) 1f else 0f,
            direction = requestedDirection,
            settleToCommitted = commit,
        )
    }

    fun canCommit(progress: Float): Boolean = progress >= 1f

    fun navigationPlan(currentIndex: Int, targetIndex: Int, gameCount: Int): FocusNavigationPlan? {
        if (gameCount <= 1) return null
        val current = currentIndex.mod(gameCount)
        val target = targetIndex.mod(gameCount)
        if (current == target) return null
        val advanceSteps = (target - current).mod(gameCount)
        val backSteps = (current - target).mod(gameCount)
        return if (backSteps < advanceSteps) {
            FocusNavigationPlan(FocusDirection.BACK, backSteps, target)
        } else {
            FocusNavigationPlan(FocusDirection.ADVANCE, advanceSteps, target)
        }
    }

    fun centerActionEnabled(
        sourceSlot: FocusSlot,
        progress: Float,
        isSettling: Boolean,
        infoAlpha: Float,
    ): Boolean = sourceSlot == FocusSlot.CENTER &&
        !isSettling &&
        progress <= .0001f &&
        infoAlpha >= .99f

    /** Two-game duplicate previews remain visual, but only LEFT exposes the switch action. */
    fun exposesSwitchAction(gameCount: Int, card: FocusCard, isSettling: Boolean): Boolean {
        if (isSettling || gameCount <= 1) return false
        if (card.sourceSlot != FocusSlot.LEFT && card.sourceSlot != FocusSlot.RIGHT) return false
        return gameCount != 2 || card.sourceSlot == FocusSlot.LEFT
    }

    /** Position/scale settle threshold: <=1px position and <=.005 scale error. */
    fun isSettled(progress: Float, slotDistanceDp: Float): Boolean {
        if (slotDistanceDp <= 0f) return progress >= 1f
        val positionErrorDp = slotDistanceDp * (1f - progress.coerceIn(0f, 1f))
        val scaleError = (1f - scaleFromProgress(progress)).coerceAtLeast(0f)
        return progress >= 1f || (positionErrorDp <= 1f && scaleError <= .005f)
    }

    private fun scaleFromProgress(progress: Float): Float =
        FocusGalleryGeometryPolicy.SIDE_SCALE +
            (1f - FocusGalleryGeometryPolicy.SIDE_SCALE) * progress.coerceIn(0f, 1f)
}

internal data class FocusHitTargetBounds(
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * C-style transparent side hit targets and visible-strip geometry.
 *
 * The C side cards tuck behind the center card, so only the strip outside the center's
 * left/right edge is actually tappable. The strip itself must stay visually recognizable
 * (>= 52dp, target 56dp after the SIDE_SCALE shrink), and the transparent hit target adds
 * a stable, at-least-48dp-wide tap area that never overlaps the center card's artwork or
 * play button. D-style cards are fully visible at the edges and keep direct card clicks,
 * so no overlay is ever produced for [LandscapeHomeStyle.IMMERSIVE_COVER].
 */
internal object FocusGalleryHitTargetPolicy {
    const val MIN_HIT_WIDTH_DP = 48f
    const val MIN_VISIBLE_SIDE_WIDTH_DP = 52f
    const val TARGET_VISIBLE_SIDE_WIDTH_DP = 56f

    /** Actual visible C side-strip width after the rendered SIDE_SCALE shrink. */
    fun visibleSideWidthDp(geometry: FocusGalleryGeometry): Float {
        val centerLeft = geometry.centerSlotCenterXDp - geometry.centerWidthDp / 2f
        val sideVisualLeft =
            geometry.leftSlotCenterXDp - geometry.sideWidthDp * FocusGalleryGeometryPolicy.SIDE_SCALE / 2f
        return (centerLeft - sideVisualLeft).coerceAtLeast(0f)
    }

    /**
     * C-only, idle-only transparent hit target for one visible side strip. Returns null for
     * D, for non-side slots, and for any transition frame (progress > 0 or settling) so a
     * tap can never fire while cards are moving. Bounds are stage-relative dp: horizontally
     * they end exactly at the center card edge and vertically they cover the scaled side
     * card, whose bottom sits at [FocusGalleryGeometry.centerHeightDp].
     */
    fun sideHitBounds(
        side: FocusSlot,
        geometry: FocusGalleryGeometry,
        style: LandscapeHomeStyle,
        progress: Float,
        isSettling: Boolean,
    ): FocusHitTargetBounds? {
        if (style != LandscapeHomeStyle.FOCUS_POSTER) return null
        if (isSettling || progress > .0001f) return null
        if (side != FocusSlot.LEFT && side != FocusSlot.RIGHT) return null
        val hitWidth = visibleSideWidthDp(geometry).coerceAtLeast(MIN_HIT_WIDTH_DP)
        val visualHeight = geometry.sideHeightDp * FocusGalleryGeometryPolicy.SIDE_SCALE
        val bottom = geometry.centerHeightDp
        val top = (bottom - visualHeight).coerceAtLeast(0f)
        val centerLeft = geometry.centerSlotCenterXDp - geometry.centerWidthDp / 2f
        val centerRight = geometry.centerSlotCenterXDp + geometry.centerWidthDp / 2f
        val left = when (side) {
            FocusSlot.LEFT -> centerLeft - hitWidth
            FocusSlot.RIGHT -> centerRight
            else -> return null
        }
        return FocusHitTargetBounds(
            leftDp = left,
            topDp = top,
            widthDp = hitWidth,
            heightDp = visualHeight,
        )
    }
}

/**
 * Single owner for the "switch to this game" accessibility action on the C/D focus stage.
 *
 * C renders a transparent hit overlay above the tucked side cards, so the overlay owns the
 * switch semantics and the underlying card never exposes it (otherwise a screen reader
 * would announce the same duplicated candidate twice). D side cards are fully visible and
 * keep their direct card click, so only the card exposes the action and no overlay exists.
 */
internal object FocusGallerySemanticsPolicy {
    /** Underlying side card semantics: D only. C clears them and keeps pointer behavior. */
    fun cardExposesSwitchSemantics(
        style: LandscapeHomeStyle,
        gameCount: Int,
        card: FocusCard,
        isSettling: Boolean,
    ): Boolean = style != LandscapeHomeStyle.FOCUS_POSTER &&
        FocusGalleryInteractionPolicy.exposesSwitchAction(gameCount, card, isSettling)

    /** Overlay semantics: C only, idle frames only, duplicates deduplicated per side. */
    fun overlayExposesSwitchSemantics(
        style: LandscapeHomeStyle,
        gameCount: Int,
        card: FocusCard,
        isSettling: Boolean,
    ): Boolean = style == LandscapeHomeStyle.FOCUS_POSTER &&
        !isSettling &&
        FocusGalleryInteractionPolicy.exposesSwitchAction(gameCount, card, isSettling = false)
}
