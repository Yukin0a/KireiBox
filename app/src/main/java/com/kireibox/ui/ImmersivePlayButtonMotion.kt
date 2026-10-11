package com.kireibox.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the immersive play button is drawn for one frame, in window pixels.
 *
 * Portrait (r4): the button belongs to the hero card. [rect] is the resolved `layout.play` rect
 * carried along with the card it belongs to (same relative spot, scaled with the card), and the
 * renderer rotates it with the card's own rotation around the card's center ([pivotFractionX] /
 * [pivotFractionY], relative to [rect]), so it rides the gallery frames instead of floating still
 * above cards sliding underneath. Landscape keeps its pill at the static `layout.play` rect.
 */
internal data class ImmersivePlayButtonPose(
    val gameId: Long,
    val rect: HomeRect,
    val rotationZ: Float = 0f,
    val rotationY: Float = 0f,
    val pivotFractionX: Float = .5f,
    val pivotFractionY: Float = .5f,
    /** Card presence × distance from rest; the renderer composes nothing at 0. */
    val alpha: Float = 1f,
    /** Whether the reveal scale applies (portrait); the landscape pill never scales. */
    val ridesWithCard: Boolean = false,
    /** The ride-dependent visibility alone (1 at rest): the button also shrinks with it ([ImmersivePlayButtonMotion.presenceScale]). */
    val presence: Float = 1f,
)

/** The gallery state one play-button frame is resolved from. */
internal data class ImmersivePlayButtonTrack(
    val frames: List<ImmersiveGameFrame>,
    val layout: ImmersiveHomeLayout,
    val orientation: HomeOrientation,
    val visualPosition: Float,
    val targetPosition: Float,
    /** A finger (or the frozen debug fixture) holds the track: the button follows the nearest card. */
    val dragging: Boolean,
    /** No drag and no settle in flight: the only state in which the button is visible under reduced motion. */
    val settled: Boolean,
    val reducedMotion: Boolean,
)

/**
 * Play-button motion policy (r4, user request 5: the still portrait button looked detached).
 *
 * - The button belongs to one card: the card nearest the center while dragging, the destination
 *   card while the gallery settles (so a pager jump never flashes it on the cards passed on the way).
 * - It is fully visible within [FULL_UNTIL] slots of that card's rest position and gone at
 *   [HIDDEN_AT] (smoothstep in between): it fades while it rides the card away and fades in as
 *   the next card lands. Only the anchor card ever carries it, so at most one is visible. The
 *   window is short and the button shrinks as it fades ([presenceScale]), so a slow drag never
 *   leaves a half-transparent, grey-looking button over the cover (r4 QA).
 * - The reveal scale springs from [HIDDEN_SCALE] back to 1 once the gallery settles
 *   (AppleMotion.Spring.Emphasized) and retracts with AppleMotion.Spring.Press when a drag starts.
 * - Reduced motion: no ride-dependent fade and no scale; the button is simply shown while settled
 *   and hidden otherwise (an instant crossfade, like the cards themselves).
 */
internal object ImmersivePlayButtonMotion {
    const val FULL_UNTIL = .05f
    const val HIDDEN_AT = .20f
    const val HIDDEN_SCALE = .72f

    /** Visible share for a track [distance] (in card slots) from the anchor card's rest position. */
    fun presence(distance: Float): Float {
        val d = if (distance.isFinite()) abs(distance) else HIDDEN_AT
        val x = ((d - FULL_UNTIL) / (HIDDEN_AT - FULL_UNTIL)).coerceIn(0f, 1f)
        return 1f - x * x * (3f - 2f * x)
    }

    /** Virtual index of the card that carries the button. */
    fun anchorVirtualIndex(
        visualPosition: Float,
        targetPosition: Float,
        dragging: Boolean,
    ): Int {
        val position = if (dragging) visualPosition else targetPosition
        return if (position.isFinite()) position.roundToInt() else 0
    }

    /** Reveal-scale factor for a reveal progress in [0, 1]. */
    fun revealScale(reveal: Float): Float = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * reveal.coerceIn(0f, 1f)

    /** Scale that carries most of the ride fade: the button shrinks toward [HIDDEN_SCALE] as its presence drops. */
    fun presenceScale(presence: Float): Float = revealScale(presence)

    /**
     * Carries [rest] (the resolved `layout.play`, measured against the rest center card [restCard])
     * onto the live [card] frame: same relative spot, scaled with the card, rotating around the
     * card's center.
     */
    fun carried(
        rest: HomeRect,
        restCard: HomeRect,
        card: ImmersiveCardFrame,
    ): ImmersivePlayButtonPose {
        val live = card.rect
        val scale = if (restCard.w > 0f && live.w.isFinite()) live.w / restCard.w else 1f
        val x = live.x + (rest.x - restCard.x) * scale
        val y = live.y + (rest.y - restCard.y) * scale
        val w = (rest.w * scale).coerceAtLeast(1f)
        val h = (rest.h * scale).coerceAtLeast(1f)
        return ImmersivePlayButtonPose(
            gameId = 0L,
            rect = HomeRect(x, y, w, h),
            rotationZ = card.rotationZ,
            rotationY = card.rotationY,
            pivotFractionX = (live.x + live.w / 2f - x) / w,
            pivotFractionY = (live.y + live.h / 2f - y) / h,
            alpha = card.alpha,
            ridesWithCard = true,
        )
    }

    /** Portrait visibility of the anchor card's button for this frame. */
    fun visibility(track: ImmersivePlayButtonTrack): Float {
        if (track.reducedMotion) return if (track.settled) 1f else 0f
        val anchor = anchorVirtualIndex(track.visualPosition, track.targetPosition, track.dragging)
        return presence(track.visualPosition - anchor)
    }
}

/**
 * The play button for one gallery frame, or null when none is visible. Landscape returns the static
 * pill at `layout.play` for the active game; portrait carries the button on its anchor card.
 */
internal fun resolveImmersivePlayButtonPose(
    track: ImmersivePlayButtonTrack,
    activeGameId: Long?,
): ImmersivePlayButtonPose? =
    if (track.orientation == HomeOrientation.LANDSCAPE) {
        activeGameId?.let { ImmersivePlayButtonPose(gameId = it, rect = track.layout.play) }
    } else {
        portraitPlayButtonPose(track)
    }

private fun portraitPlayButtonPose(track: ImmersivePlayButtonTrack): ImmersivePlayButtonPose? {
    val anchor = ImmersivePlayButtonMotion.anchorVirtualIndex(track.visualPosition, track.targetPosition, track.dragging)
    val item = track.frames.firstOrNull { it.virtualIndex == anchor }
    val restCard = track.layout.cardSlots[ImmersiveSlot.CENTER]?.rect
    val visibility = ImmersivePlayButtonMotion.visibility(track)
    if (item == null || restCard == null || visibility <= 0f) return null
    val carried = ImmersivePlayButtonMotion.carried(track.layout.play, restCard, item.frame)
    return carried.copy(gameId = item.gameId, alpha = carried.alpha * visibility, presence = visibility).takeIf { it.alpha > MIN_VISIBLE_ALPHA }
}

private const val MIN_VISIBLE_ALPHA = .01f
