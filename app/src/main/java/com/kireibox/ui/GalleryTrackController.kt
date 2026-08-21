package com.kireibox.ui

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Immutable snapshot of the continuous gallery track. Positions are virtual card spans and are
 * deliberately unbounded for every library with at least two games. The visible game index is a
 * floor-modded projection of that virtual position; callers render the circular seam from the
 * same position instead of clamping it to a finite edge.
 */
data class GalleryTrackSnapshot(
    val visualPosition: Float,
    val targetPosition: Float,
    val velocity: Float,
    val activeGameIndex: Int,
    val isDragging: Boolean,
)

/** Normalized gesture input shared by portrait and landscape gallery surfaces. */
internal data class GalleryTrackGestureInput(
    val currentIndex: Int,
    val dragDistance: Float,
    val velocity: Float,
    val recentIntent: Int = 0,
)

/**
 * Pure physical shaping and release policy for the gallery track.
 *
 * All distances are card spans and all velocities are card spans per second. Keeping this
 * policy independent of Compose and orientation makes portrait and landscape gestures resolve
 * identically for identical normalized input.
 */
internal object GalleryTrackTargetPolicy {
    // A release moves at most one card per gesture; anything below the commit distance returns.
    const val MAX_FLING_CARDS = 1

    // In-bounds drags track the finger exactly 1:1.
    const val DRAG_BASE_GAIN = 1f
    const val DRAG_GAIN_PER_SPAN = 0f

    // Release thresholds are deliberately expressed in card spans, not pixels.
    const val COMMIT_DISTANCE = .28f
    const val VELOCITY_PROJECTION_SECONDS = .18f
    const val FAST_FLING_VELOCITY = .95f

    fun resistedDragPosition(
        anchorPosition: Float,
        rawDragDistance: Float,
        itemCount: Int,
    ): Float {
        if (itemCount <= 1) return 0f
        val anchor = anchorPosition.takeIf { it.isFinite() } ?: 0f
        val raw = rawDragDistance.takeIf { it.isFinite() } ?: 0f
        val magnitude = abs(raw)
        val gain = DRAG_BASE_GAIN + DRAG_GAIN_PER_SPAN * magnitude.coerceAtMost(1f)
        return anchor + directionOf(raw) * magnitude * gain
    }

    fun resolveTarget(
        input: GalleryTrackGestureInput,
        itemCount: Int,
        maxFlingCards: Int = MAX_FLING_CARDS,
    ): Int {
        if (itemCount <= 1) return 0
        require(maxFlingCards >= 1) { "maxFlingCards must be >= 1" }

        val current = input.currentIndex
        val distance = input.dragDistance.takeIf { it.isFinite() } ?: 0f
        val velocity = input.velocity.takeIf { it.isFinite() } ?: 0f
        val distanceDirection = directionOf(distance)
        val velocityDirection = directionOf(velocity)
        val recentDirection = input.recentIntent.coerceIn(-1, 1)

        val velocityProjection = abs(velocity) * VELOCITY_PROJECTION_SECONDS
        val velocityDominatesOppositeDrag = distanceDirection != 0 &&
            velocityDirection != 0 &&
            velocityDirection != distanceDirection &&
            abs(velocity) >= FAST_FLING_VELOCITY &&
            velocityProjection >= abs(distance)
        val recentReversal = distanceDirection != 0 &&
            recentDirection != 0 &&
            recentDirection != distanceDirection &&
            (velocityDirection == 0 || velocityDirection == recentDirection)

        // A release velocity can intentionally reverse a long drag, but only when it is a
        // genuine fling or the latest pointer intent agrees with it. A weak contradictory
        // sample cannot make the track jump behind the user's dominant drag direction.
        val direction = when {
            distanceDirection == 0 && velocityDirection != 0 -> velocityDirection
            velocityDominatesOppositeDrag -> velocityDirection
            recentReversal -> recentDirection
            distanceDirection != 0 -> distanceDirection
            recentDirection != 0 -> recentDirection
            else -> velocityDirection
        }
        if (direction == 0) return current

        // Do not reuse distance travelled in the old direction after a reversal. A fast release
        // from rest still counts as a fling, while a contradictory velocity contributes nothing
        // unless it selected the new direction above.
        val distanceInDirection = if (distanceDirection == direction) abs(distance) else 0f
        val projectedDistance = distanceInDirection + if (velocityDirection == direction) {
            velocityProjection
        } else 0f
        val fastFling = velocityDirection == direction && abs(velocity) >= FAST_FLING_VELOCITY
        if (projectedDistance < COMMIT_DISTANCE && !fastFling) return current

        val cards = ceil(projectedDistance).toInt().coerceIn(1, maxFlingCards)
        return current + direction * cards
    }

    private fun directionOf(value: Float): Int = when {
        value < 0f -> -1
        value > 0f -> 1
        else -> 0
    }
}

/**
 * Pure state machine for the immersive home gallery. A Compose Animatable can drive it:
 * animate visualPosition toward targetPosition and report each frame plus its velocity via
 * [onVisualFrame]. Clicks, pager jumps and drags all mutate the same continuous track.
 *
 * - Clicks detect the sign of (target - visual). Same-direction clicks accumulate one slot
 *   from the target, at most [MAX_CLICK_LEAD] slots ahead of the visual position. Opposite
 *   clicks take over from the nearest virtual card anchor.
 * - [jumpTo] selects the nearest virtual representative of the requested circular card.
 * - Dragging keeps a raw logical target on the unbounded track; release uses the raw distance and
 *   velocity projected in card spans and is capped at one card by
 *   [GalleryTrackTargetPolicy.MAX_FLING_CARDS].
 * - activeGameIndex switches atomically when the visual position crosses a half slot and is
 *   projected with floor-mod semantics.
 * - Zero or one game is inert and safe; there is no auto-carousel.
 */
class GalleryTrackController(
    gameCount: Int,
    initialIndex: Int = 0,
) {
    companion object {
        const val MAX_CLICK_LEAD = 3f
        const val DEFAULT_SETTLE_MS = 520
        const val MIN_SETTLE_MS = 160
        const val REDUCED_MOTION_MS = 160
    }

    var gameCount: Int = gameCount
        private set

    private var gameIds: List<Long> = emptyList()
    private var dragAnchorPosition = 0f
    private var dragAnchorIndex = 0
    private var rawDragTargetPosition = 0f
    private var recentDragIntent = 0

    private var state = GalleryTrackSnapshot(
        visualPosition = 0f,
        targetPosition = 0f,
        velocity = 0f,
        activeGameIndex = -1,
        isDragging = false,
    )

    init {
        require(gameCount >= 0) { "gameCount must be >= 0" }
        if (gameCount >= 1) {
            val index = initialIndex.coerceIn(0, gameCount - 1)
            state = GalleryTrackSnapshot(
                visualPosition = index.toFloat(),
                targetPosition = index.toFloat(),
                velocity = 0f,
                activeGameIndex = index,
                isDragging = false,
            )
        }
    }

    fun snapshot(): GalleryTrackSnapshot = state

    /**
     * Reconciles a changing ordered game list while retaining the currently centered game ID
     * whenever it still exists. This keeps selection stable when metadata, source ordering, or
     * manual gallery settings change without allowing a stale index to escape the new bounds.
     */
    fun syncGameIds(nextGameIds: List<Long>): GalleryTrackSnapshot {
        val currentId = gameIds.getOrNull(state.activeGameIndex)
        val fallbackIndex = state.activeGameIndex.coerceIn(0, (nextGameIds.size - 1).coerceAtLeast(0))
        val nextIndex = currentId?.let {
            nextGameIds.indexOf(it).takeIf { index -> index >= 0 }
        } ?: fallbackIndex
        gameCount = nextGameIds.size
        gameIds = nextGameIds.toList()
        val safeIndex = if (gameCount <= 1) {
            0
        } else {
            nearestVirtualPage(nextIndex, state.visualPosition)
        }
        state = GalleryTrackSnapshot(
            visualPosition = safeIndex.toFloat(),
            targetPosition = safeIndex.toFloat(),
            velocity = 0f,
            activeGameIndex = if (gameCount == 0) -1 else Math.floorMod(safeIndex, gameCount),
            isDragging = false,
        )
        dragAnchorPosition = state.visualPosition
        dragAnchorIndex = safeIndex
        rawDragTargetPosition = state.visualPosition
        recentDragIntent = 0
        return state
    }

    /** Side-card click; +1 advances, -1 goes back. No-op while dragging or with < 2 games. */
    fun stepBy(direction: Int): GalleryTrackSnapshot {
        if (gameCount < 2 || state.isDragging) return state
        require(direction == 1 || direction == -1) { "direction must be +1 or -1" }
        val delta = state.targetPosition - state.visualPosition
        val motionSign = when {
            delta > 0f -> 1
            delta < 0f -> -1
            else -> 0
        }
        val candidate = (if (motionSign != 0 && motionSign == direction) {
            state.targetPosition + direction
        } else {
            anchorOf(state.visualPosition) + direction
        })
        val lead = (candidate - state.visualPosition) * direction
        if (candidate == state.targetPosition || lead > MAX_CLICK_LEAD) return state
        state = state.copy(targetPosition = candidate)
        return state
    }

    /** Pager click: jump to the nearest virtual representative of the requested game index. */
    fun jumpTo(index: Int): GalleryTrackSnapshot {
        if (gameCount < 2 || state.isDragging) return state
        val candidate = nearestVirtualPage(index.coerceIn(0, gameCount - 1), state.visualPosition).toFloat()
        if (state.targetPosition == candidate) return state
        state = state.copy(targetPosition = candidate)
        return state
    }

    /** Drag starts: cancel any pending animation and take direct control. */
    fun beginDrag(): GalleryTrackSnapshot {
        if (gameCount < 2) return state
        dragAnchorPosition = state.visualPosition
        dragAnchorIndex = virtualIndexAt(dragAnchorPosition)
        rawDragTargetPosition = dragAnchorPosition
        recentDragIntent = 0
        state = state.copy(isDragging = true, targetPosition = state.visualPosition, velocity = 0f)
        return state
    }

    /** Applies a normalized delta to the raw target; the visual position is damped separately. */
    fun dragBy(delta: Float): GalleryTrackSnapshot {
        if (gameCount < 2 || !state.isDragging) return state
        val normalizedDelta = delta.takeIf { it.isFinite() } ?: 0f
        rawDragTargetPosition += normalizedDelta
        if (normalizedDelta != 0f) {
            recentDragIntent = when {
                normalizedDelta < 0f -> -1
                else -> 1
            }
        }
        val visualPosition = GalleryTrackTargetPolicy.resistedDragPosition(
            anchorPosition = dragAnchorPosition,
            rawDragDistance = rawDragTargetPosition - dragAnchorPosition,
            itemCount = gameCount,
        )
        state = state.copy(
            visualPosition = visualPosition,
            targetPosition = rawDragTargetPosition,
            activeGameIndex = activeIndexAt(visualPosition),
        )
        return state
    }

    /** Drag released with pointer velocity in slots/second; projects the unbounded target. */
    fun endDrag(flingVelocity: Float): GalleryTrackSnapshot {
        if (gameCount < 2 || !state.isDragging) return state
        val velocity = flingVelocity.takeIf { it.isFinite() } ?: 0f
        val target = GalleryTrackTargetPolicy.resolveTarget(
            input = GalleryTrackGestureInput(
                currentIndex = dragAnchorIndex,
                dragDistance = rawDragTargetPosition - dragAnchorPosition,
                velocity = velocity,
                recentIntent = recentDragIntent,
            ),
            itemCount = gameCount,
        )
        state = state.copy(
            targetPosition = target.toFloat(),
            velocity = velocity,
            isDragging = false,
        )
        return state
    }

    /** Cancels a drag and springs back to the card that owned the gesture. */
    fun cancelDrag(): GalleryTrackSnapshot {
        if (gameCount < 2 || !state.isDragging) return state
        state = state.copy(
            targetPosition = dragAnchorIndex.toFloat(),
            velocity = 0f,
            isDragging = false,
        )
        return state
    }

    /** Reports the animation frame value and velocity; activeGameIndex updates atomically. */
    fun onVisualFrame(position: Float, velocity: Float): GalleryTrackSnapshot {
        state = state.copy(
            visualPosition = position,
            velocity = velocity,
            activeGameIndex = activeIndexAt(position),
        )
        return state
    }

    /** Stops in place; the caller decides whether reduced motion uses a cross-fade instead. */
    fun cancelAnimation(): GalleryTrackSnapshot {
        state = state.copy(
            targetPosition = state.visualPosition,
            velocity = 0f,
            isDragging = false,
        )
        return state
    }

    /**
     * Suggestion for the caller (e.g. Compose Animatable): 520ms per slot, proportional to the
     * remaining distance, clamped to [MIN_SETTLE_MS, maxMs]. Reduced motion returns
     * [REDUCED_MOTION_MS]; this class never renders or animates anything itself.
     */
    fun settleDurationMs(maxMs: Int = DEFAULT_SETTLE_MS, reducedMotion: Boolean = false): Int {
        require(maxMs >= MIN_SETTLE_MS) { "maxMs must be >= $MIN_SETTLE_MS" }
        if (reducedMotion) return REDUCED_MOTION_MS
        val distance = abs(state.targetPosition - state.visualPosition)
        return (maxMs * distance).roundToInt().coerceIn(MIN_SETTLE_MS, maxMs)
    }

    private fun activeIndexAt(position: Float): Int = when (gameCount) {
        0 -> -1
        1 -> 0
        else -> Math.floorMod(floor(position + 0.5f).toInt(), gameCount)
    }

    private fun anchorOf(visual: Float): Float = virtualIndexAt(visual).toFloat()

    private fun virtualIndexAt(position: Float): Int = floor(
        (position.takeIf { it.isFinite() } ?: 0f) + .5f,
    ).toInt()

    private fun nearestVirtualPage(index: Int, referencePosition: Float): Int {
        if (gameCount <= 1) return 0
        val normalizedIndex = Math.floorMod(index, gameCount)
        val reference = referencePosition.takeIf { it.isFinite() } ?: 0f
        val revolution = floor((reference - normalizedIndex) / gameCount + .5f).toInt()
        return normalizedIndex + revolution * gameCount
    }
}
