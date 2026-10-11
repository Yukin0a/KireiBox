package com.kireibox.ui

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

internal data class FocusDragDecision(
    val targetProgress: Float,
    val direction: FocusDirection,
    val settleToCommitted: Boolean,
)

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

internal data class FocusHitTargetBounds(
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)
