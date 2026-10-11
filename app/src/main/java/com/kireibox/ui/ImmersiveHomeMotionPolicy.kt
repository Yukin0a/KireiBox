package com.kireibox.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

internal data class ImmersiveGameFrame(
    val gameId: Long,
    /** Absolute position on the unbounded virtual track; never use gameId as the render key. */
    val virtualIndex: Int,
    val slot: ImmersiveSlot,
    val frame: ImmersiveCardFrame,
)

/**
 * One latest-wins lane for all Animatable mutations belonging to the gallery track. Pointer
 * samples cancel stale snaps and release/sync operations can await that cancellation before
 * taking ownership of the animation.
 */
internal class SerializedTrackMutationRunner(
    private val scope: CoroutineScope,
) {
    private val stateLock = Any()
    private val ownedJobs = LinkedHashSet<Job>()
    private var activeJob: Job? = null
    private var disposed = false

    fun launchLatest(block: suspend () -> Unit): Job {
        synchronized(stateLock) {
            if (disposed) return Job().apply { cancel() }

            val previous = activeJob
            previous?.cancel()
            val next =
                scope.launch(start = CoroutineStart.LAZY) {
                    previous?.join()
                    block()
                }
            ownedJobs += next
            activeJob = next
            next.invokeOnCompletion {
                synchronized(stateLock) {
                    ownedJobs.remove(next)
                    if (activeJob === next) activeJob = null
                }
            }
            next.start()
            return next
        }
    }

    suspend fun cancelAndJoin() {
        val active = synchronized(stateLock) { activeJob } ?: return
        active.cancelAndJoin()
        synchronized(stateLock) {
            ownedJobs.remove(active)
            if (activeJob === active) activeJob = null
        }
    }

    fun dispose() {
        val jobs =
            synchronized(stateLock) {
                if (disposed) return
                disposed = true
                activeJob = null
                ownedJobs.toList().also { ownedJobs.clear() }
            }
        jobs.forEach { it.cancel() }
    }

    suspend fun replaceAndJoin(block: suspend () -> Unit) {
        launchLatest(block).join()
    }
}

internal fun shouldStartImmersiveSettle(
    isDragging: Boolean,
    frozen: Boolean,
    itemCount: Int,
): Boolean = !isDragging && !frozen && itemCount >= 2

/**
 * Gaps (in slots) below this are snapped instead of animated, and the same epsilon decides whether
 * the gallery still counts as settling. A shared value matters: a release a fraction of a pixel from
 * rest must not leave the track "settling" forever (that hid the portrait play button).
 */
internal const val IMMERSIVE_SETTLE_EPSILON = .001f

internal fun isImmersiveGallerySettling(
    isDragging: Boolean,
    targetPosition: Float,
    visualPosition: Float,
): Boolean = !isDragging && abs(targetPosition - visualPosition) > IMMERSIVE_SETTLE_EPSILON

/** Settle velocity cap, in slots per second per slot still to travel (below the spring's overshoot point). */
internal const val IMMERSIVE_SETTLE_MAX_VELOCITY_PER_SLOT = 12f

/**
 * Initial velocity of the settle spring. A running animation keeps its own velocity; otherwise the
 * track's last velocity (the finger's release fling, or the last frame of an interrupted settle) is
 * handed over — only toward the target and capped by the remaining gap so the no-bounce spring never
 * overshoots — so the cards keep moving with the finger instead of stopping dead and speeding up again.
 */
internal fun immersiveSettleInitialVelocity(
    start: Float,
    target: Float,
    runningVelocity: Float,
    trackVelocity: Float,
): Float {
    if (runningVelocity != 0f && runningVelocity.isFinite()) return runningVelocity
    val gap = target - start
    val release = trackVelocity.takeIf { it.isFinite() } ?: 0f
    val cap = abs(gap) * IMMERSIVE_SETTLE_MAX_VELOCITY_PER_SLOT
    return if (gap * release > 0f) release.coerceIn(-cap, cap) else 0f
}

/**
 * Circular-track adapter used by the Compose renderer. Every rendered card owns an absolute
 * virtual index. The index is mapped to a game id only at the data boundary, so a five-game
 * library can render the card leaving through the left edge while the next one enters through the
 * right edge. One game and two to four games are resolved by [resolveSmallImmersiveFrames] (r5:
 * a bounded track, so no game is ever shown twice).
 */
internal fun resolveImmersiveFrames(
    visualPosition: Float,
    layout: ImmersiveHomeLayout,
    gameIds: List<Long>,
    orientation: HomeOrientation,
    reducedMotion: Boolean = false,
): List<ImmersiveGameFrame> {
    if (gameIds.isEmpty()) return emptyList()
    val safePosition = visualPosition.takeIf { it.isFinite() } ?: 0f
    val small = resolveSmallImmersiveFrames(safePosition, layout, gameIds, orientation, reducedMotion)
    if (small != null) return small
    val currentVirtual = floor(safePosition).toInt()
    val fraction = safePosition - floor(safePosition)
    if (reducedMotion && fraction > .0001f) {
        return reducedMotionImmersiveFrames(currentVirtual, fraction, layout, gameIds)
    }

    // Five or more games: portrait shows one neighbour per side, landscape two (r5: smaller galleries
    // no longer reach this circular path, so no radius has to avoid showing a game twice).
    val compact = orientation == HomeOrientation.PORTRAIT
    val radius = if (compact) 1 else 2
    val isTransitioning = fraction > .0001f
    val firstVirtual = currentVirtual - radius
    val lastVirtual = currentVirtual + radius + if (isTransitioning) 1 else 0

    return (firstVirtual..lastVirtual).mapNotNull { virtualIndex ->
        val positioned = frameAtVirtualOffset(virtualIndex - safePosition, layout, compact)
        if (positioned.frame.alpha <= .0001f) {
            null
        } else {
            ImmersiveGameFrame(
                gameId = gameIds[Math.floorMod(virtualIndex, gameIds.size)],
                virtualIndex = virtualIndex,
                slot = positioned.slot,
                frame = positioned.frame,
            )
        }
    }
}

/**
 * Galleries below five games (r5, [GalleryTrackBounds]); null for the circular track.
 * - One game: a single centred card at any position (it does not swipe).
 * - Two to four games: a bounded track. Each game is one card at its own index, placed by its
 *   offset from [position] on the same slot geometry as the circular track (portrait: neighbours
 *   at the near slots; landscape: up to two per side), so side slots only hold real neighbours and
 *   a card enters by sliding out from behind its neighbour — never as a second copy of a game. Past
 *   either end the cards follow the rubber band; reduced motion crossfades within the bounds.
 */
private fun resolveSmallImmersiveFrames(
    position: Float,
    layout: ImmersiveHomeLayout,
    gameIds: List<Long>,
    orientation: HomeOrientation,
    reducedMotion: Boolean,
): List<ImmersiveGameFrame>? {
    val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER)
    return when {
        gameIds.size == 1 -> listOf(ImmersiveGameFrame(gameIds.single(), 0, ImmersiveSlot.CENTER, center))
        !GalleryTrackBounds.isBounded(gameIds.size) -> null
        reducedMotion && position - floor(position) > .0001f -> {
            val clamped = position.coerceIn(0f, gameIds.size - 1f)
            val current = floor(clamped).toInt()
            if (clamped - current > .0001f) reducedMotionImmersiveFrames(current, clamped - current, layout, gameIds) else boundedImmersiveFrames(clamped, layout, gameIds, orientation)
        }
        else -> boundedImmersiveFrames(position, layout, gameIds, orientation)
    }
}

private fun boundedImmersiveFrames(
    position: Float,
    layout: ImmersiveHomeLayout,
    gameIds: List<Long>,
    orientation: HomeOrientation,
): List<ImmersiveGameFrame> {
    val compact = orientation == HomeOrientation.PORTRAIT
    return gameIds.mapIndexedNotNull { index, gameId ->
        val positioned = frameAtVirtualOffset(index - position, layout, compact)
        if (positioned.frame.alpha <= .0001f) null else ImmersiveGameFrame(gameId, index, positioned.slot, positioned.frame)
    }
}

/** Reduced motion: the card at [currentVirtual] and the next one crossfade in place at the centre. */
private fun reducedMotionImmersiveFrames(
    currentVirtual: Int,
    fraction: Float,
    layout: ImmersiveHomeLayout,
    gameIds: List<Long>,
): List<ImmersiveGameFrame> {
    val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER)
    return listOf(
        ImmersiveGameFrame(
            gameId = gameIds[Math.floorMod(currentVirtual, gameIds.size)],
            virtualIndex = currentVirtual,
            slot = ImmersiveSlot.CENTER,
            frame = center.copy(alpha = 1f - fraction),
        ),
        ImmersiveGameFrame(
            gameId = gameIds[Math.floorMod(currentVirtual + 1, gameIds.size)],
            virtualIndex = currentVirtual + 1,
            slot = ImmersiveSlot.CENTER,
            frame = center.copy(alpha = fraction, zIndex = center.zIndex + .01f),
        ),
    )
}

private data class PositionedImmersiveFrame(
    val slot: ImmersiveSlot,
    val frame: ImmersiveCardFrame,
)

private fun frameAtVirtualOffset(
    offset: Float,
    layout: ImmersiveHomeLayout,
    compact: Boolean,
): PositionedImmersiveFrame {
    if (compact) {
        return when {
            offset <= -2f ->
                PositionedImmersiveFrame(
                    ImmersiveSlot.EXIT_LEFT,
                    syntheticEdgeFrame(layout, ImmersiveSlot.EXIT_LEFT),
                )
            offset < -1f -> betweenSlots(layout, ImmersiveSlot.FAR_LEFT, ImmersiveSlot.NEAR_LEFT, offset + 2f)
            offset < 0f -> betweenSlots(layout, ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.CENTER, offset + 1f)
            offset < 1f -> betweenSlots(layout, ImmersiveSlot.CENTER, ImmersiveSlot.NEAR_RIGHT, offset)
            offset < 2f -> betweenSlots(layout, ImmersiveSlot.NEAR_RIGHT, ImmersiveSlot.FAR_RIGHT, offset - 1f)
            else ->
                PositionedImmersiveFrame(
                    ImmersiveSlot.ENTER_RIGHT,
                    syntheticEdgeFrame(layout, ImmersiveSlot.ENTER_RIGHT),
                )
        }
    }

    return when {
        offset <= -3f ->
            PositionedImmersiveFrame(
                ImmersiveSlot.EXIT_LEFT,
                syntheticEdgeFrame(layout, ImmersiveSlot.EXIT_LEFT),
            )
        offset < -2f -> betweenSlots(layout, ImmersiveSlot.EXIT_LEFT, ImmersiveSlot.FAR_LEFT, offset + 3f)
        offset < -1f -> betweenSlots(layout, ImmersiveSlot.FAR_LEFT, ImmersiveSlot.NEAR_LEFT, offset + 2f)
        offset < 0f -> betweenSlots(layout, ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.CENTER, offset + 1f)
        offset < 1f -> betweenSlots(layout, ImmersiveSlot.CENTER, ImmersiveSlot.NEAR_RIGHT, offset)
        offset < 2f -> betweenSlots(layout, ImmersiveSlot.NEAR_RIGHT, ImmersiveSlot.FAR_RIGHT, offset - 1f)
        offset < 3f -> betweenSlots(layout, ImmersiveSlot.FAR_RIGHT, ImmersiveSlot.ENTER_RIGHT, offset - 2f)
        else ->
            PositionedImmersiveFrame(
                ImmersiveSlot.ENTER_RIGHT,
                syntheticEdgeFrame(layout, ImmersiveSlot.ENTER_RIGHT),
            )
    }
}

/** Extra sideways travel (in centre-card widths) of a portrait card halfway between the centre and a side slot. */
internal const val PORTRAIT_SHUFFLE_SWING = .22f

/** Extra lift (in centre-card heights) at the same point: the card rises out of the stack as it passes. */
internal const val PORTRAIT_SHUFFLE_LIFT = .035f

/** Shrink at the same point, so both passing covers stay on screen. */
internal const val PORTRAIT_SHUFFLE_SHRINK = .06f

/** Extra 3D turn (degrees) at the same point, toward the side the card is travelling to. */
internal const val PORTRAIT_SHUFFLE_TURN_DEG = 9f

/**
 * 切换方式「交错」 (optional portrait effect, [ResolvedImmersiveFx.cardSwitch]; the default 「缓和」 is the
 * straight switch). On the way between the centre and a side slot each card swings out along an
 * arc ([PORTRAIT_SHUFFLE_SWING]), rises, shrinks a little and turns toward its side, so the leaving
 * and the arriving card pass beside each other instead of overlapping, then tuck back in. The bump
 * is zero at both slots, so resting frames are unchanged. Applied to the frames of
 * [resolveImmersiveFrames] at the same [visualPosition]; not for reduced-motion crossfade frames.
 */
internal fun List<ImmersiveGameFrame>.withPortraitShuffle(
    visualPosition: Float,
    layout: ImmersiveHomeLayout,
): List<ImmersiveGameFrame> {
    val position = visualPosition.takeIf { it.isFinite() } ?: return this
    val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
    return map { item ->
        val offset = item.virtualIndex - position
        val bump = if (abs(offset) < 1f) sin(PI.toFloat() * abs(offset)) else 0f
        if (bump <= 0f) {
            item
        } else {
            val side = if (offset < 0f) -1f else 1f
            val frame = item.frame
            val shrink = 1f - bump * PORTRAIT_SHUFFLE_SHRINK
            val w = frame.rect.w * shrink
            val h = frame.rect.h * shrink
            item.copy(
                frame =
                    frame.copy(
                        rect =
                            HomeRect(
                                x = frame.rect.x + (frame.rect.w - w) / 2f + side * bump * center.w * PORTRAIT_SHUFFLE_SWING,
                                y = frame.rect.y + (frame.rect.h - h) / 2f - bump * center.h * PORTRAIT_SHUFFLE_LIFT,
                                w = w,
                                h = h,
                            ),
                        rotationY = frame.rotationY + side * bump * PORTRAIT_SHUFFLE_TURN_DEG,
                    ),
            )
        }
    }
}

private fun betweenSlots(
    layout: ImmersiveHomeLayout,
    from: ImmersiveSlot,
    to: ImmersiveSlot,
    progress: Float,
): PositionedImmersiveFrame {
    val safeProgress = progress.coerceIn(0f, 1f)
    val fromFrame = layout.cardSlots[from] ?: syntheticEdgeFrame(layout, from)
    val toFrame = layout.cardSlots[to] ?: syntheticEdgeFrame(layout, to)
    return PositionedImmersiveFrame(
        slot = if (safeProgress < .5f) from else to,
        frame = interpolate(fromFrame, toFrame, safeProgress),
    )
}

private fun syntheticEdgeFrame(
    layout: ImmersiveHomeLayout,
    slot: ImmersiveSlot
): ImmersiveCardFrame {
    val sourceSlot = if (slot == ImmersiveSlot.EXIT_LEFT) ImmersiveSlot.FAR_LEFT else ImmersiveSlot.FAR_RIGHT
    val source = layout.cardSlots[sourceSlot] ?: layout.cardSlots.getValue(ImmersiveSlot.CENTER)
    val center = layout.cardSlots.getValue(ImmersiveSlot.CENTER).rect
    val shift = center.w + source.rect.w
    return source.copy(
        rect = source.rect.copy(x = source.rect.x + if (slot == ImmersiveSlot.EXIT_LEFT) -shift else shift),
        alpha = 0f,
        saturation = source.saturation.coerceAtMost(.5f),
        elevationPx = 0f,
        zIndex = 0f,
    )
}

private fun interpolate(
    start: ImmersiveCardFrame,
    end: ImmersiveCardFrame,
    progress: Float
): ImmersiveCardFrame {
    fun value(
        a: Float,
        b: Float
    ) = a + (b - a) * progress
    return ImmersiveCardFrame(
        rect =
            HomeRect(
                value(start.rect.x, end.rect.x),
                value(start.rect.y, end.rect.y),
                value(start.rect.w, end.rect.w),
                value(start.rect.h, end.rect.h),
            ),
        rotationZ = value(start.rotationZ, end.rotationZ),
        rotationY = value(start.rotationY, end.rotationY),
        alpha = value(start.alpha, end.alpha),
        saturation = value(start.saturation, end.saturation),
        elevationPx = value(start.elevationPx, end.elevationPx),
        zIndex = value(start.zIndex, end.zIndex),
        focusFraction = value(start.focusFraction, end.focusFraction),
        depth = value(start.depth, end.depth),
    )
}
