package com.kireibox.ui

import com.yuki.yukihub.settings.LandscapeHomeStyle
import kotlin.math.abs

internal object FocusGalleryAutoAdvancePolicy {
    fun shouldRunTimer(
        gameCount: Int,
        galleryIdle: Boolean,
        isVisible: Boolean,
        isResumed: Boolean,
        isCardPressed: Boolean,
    ): Boolean = gameCount > 1 && galleryIdle && isVisible && isResumed && !isCardPressed
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
    ): FocusSwitchResult =
        when {
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

    fun progressFromDragDelta(
        deltaXDp: Float,
        direction: FocusDirection,
        slotDistanceDp: Float
    ): Float {
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
        val commit =
            if (moving) {
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

    fun navigationPlan(
        currentIndex: Int,
        targetIndex: Int,
        gameCount: Int
    ): FocusNavigationPlan? {
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
    ): Boolean =
        sourceSlot == FocusSlot.CENTER &&
            !isSettling &&
            progress <= .0001f &&
            infoAlpha >= .99f

    /** Two-game duplicate previews remain visual, but only LEFT exposes the switch action. */
    fun exposesSwitchAction(
        gameCount: Int,
        card: FocusCard,
        isSettling: Boolean
    ): Boolean {
        if (isSettling || gameCount <= 1) return false
        if (card.sourceSlot != FocusSlot.LEFT && card.sourceSlot != FocusSlot.RIGHT) return false
        return gameCount != 2 || card.sourceSlot == FocusSlot.LEFT
    }

    /** Position/scale settle threshold: <=1px position and <=.005 scale error. */
    fun isSettled(
        progress: Float,
        slotDistanceDp: Float
    ): Boolean {
        if (slotDistanceDp <= 0f) return progress >= 1f
        val positionErrorDp = slotDistanceDp * (1f - progress.coerceIn(0f, 1f))
        val scaleError = (1f - scaleFromProgress(progress)).coerceAtLeast(0f)
        return progress >= 1f || (positionErrorDp <= 1f && scaleError <= .005f)
    }

    private fun scaleFromProgress(progress: Float): Float =
        FocusGalleryGeometryPolicy.SIDE_SCALE +
            (1f - FocusGalleryGeometryPolicy.SIDE_SCALE) * progress.coerceIn(0f, 1f)
}

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
        val left =
            when (side) {
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
    ): Boolean =
        style != LandscapeHomeStyle.FOCUS_POSTER &&
            FocusGalleryInteractionPolicy.exposesSwitchAction(gameCount, card, isSettling)

    /** Overlay semantics: C only, idle frames only, duplicates deduplicated per side. */
    fun overlayExposesSwitchSemantics(
        style: LandscapeHomeStyle,
        gameCount: Int,
        card: FocusCard,
        isSettling: Boolean,
    ): Boolean =
        style == LandscapeHomeStyle.FOCUS_POSTER &&
            !isSettling &&
            FocusGalleryInteractionPolicy.exposesSwitchAction(gameCount, card, isSettling = false)
}
