package com.kireibox.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.floor

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
            val next = scope.launch(start = CoroutineStart.LAZY) {
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
        val jobs = synchronized(stateLock) {
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
 * Circular-track adapter used by the Compose renderer. Every rendered card owns an absolute
 * virtual index. The index is mapped to a game id only at the data boundary, so a two-item
 * library can intentionally render two virtual instances of the same game while one instance
 * leaves through the left edge and the next enters through the right edge.
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
    val currentVirtual = floor(safePosition).toInt()
    val fraction = safePosition - floor(safePosition)
    if (gameIds.size == 1) {
        return listOf(
            ImmersiveGameFrame(
                gameId = gameIds.single(),
                virtualIndex = 0,
                slot = ImmersiveSlot.CENTER,
                frame = layout.cardSlots.getValue(ImmersiveSlot.CENTER),
            ),
        )
    }
    if (reducedMotion && fraction > .0001f && gameIds.size > 1) {
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

    val compact = orientation == HomeOrientation.PORTRAIT || gameIds.size <= 3
    val leftRadius = if (compact) 1 else 2
    val rightRadius = if (!compact && gameIds.size == 4) 1 else if (compact) 1 else 2
    val isTransitioning = fraction > .0001f
    val firstVirtual = currentVirtual - leftRadius
    val lastVirtual = currentVirtual + rightRadius + if (isTransitioning) 1 else 0

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
            offset <= -2f -> PositionedImmersiveFrame(
                ImmersiveSlot.EXIT_LEFT,
                syntheticEdgeFrame(layout, ImmersiveSlot.EXIT_LEFT),
            )
            offset < -1f -> betweenSlots(layout, ImmersiveSlot.FAR_LEFT, ImmersiveSlot.NEAR_LEFT, offset + 2f)
            offset < 0f -> betweenSlots(layout, ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.CENTER, offset + 1f)
            offset < 1f -> betweenSlots(layout, ImmersiveSlot.CENTER, ImmersiveSlot.NEAR_RIGHT, offset)
            offset < 2f -> betweenSlots(layout, ImmersiveSlot.NEAR_RIGHT, ImmersiveSlot.FAR_RIGHT, offset - 1f)
            else -> PositionedImmersiveFrame(
                ImmersiveSlot.ENTER_RIGHT,
                syntheticEdgeFrame(layout, ImmersiveSlot.ENTER_RIGHT),
            )
        }
    }

    return when {
        offset <= -3f -> PositionedImmersiveFrame(
            ImmersiveSlot.EXIT_LEFT,
            syntheticEdgeFrame(layout, ImmersiveSlot.EXIT_LEFT),
        )
        offset < -2f -> betweenSlots(layout, ImmersiveSlot.EXIT_LEFT, ImmersiveSlot.FAR_LEFT, offset + 3f)
        offset < -1f -> betweenSlots(layout, ImmersiveSlot.FAR_LEFT, ImmersiveSlot.NEAR_LEFT, offset + 2f)
        offset < 0f -> betweenSlots(layout, ImmersiveSlot.NEAR_LEFT, ImmersiveSlot.CENTER, offset + 1f)
        offset < 1f -> betweenSlots(layout, ImmersiveSlot.CENTER, ImmersiveSlot.NEAR_RIGHT, offset)
        offset < 2f -> betweenSlots(layout, ImmersiveSlot.NEAR_RIGHT, ImmersiveSlot.FAR_RIGHT, offset - 1f)
        offset < 3f -> betweenSlots(layout, ImmersiveSlot.FAR_RIGHT, ImmersiveSlot.ENTER_RIGHT, offset - 2f)
        else -> PositionedImmersiveFrame(
            ImmersiveSlot.ENTER_RIGHT,
            syntheticEdgeFrame(layout, ImmersiveSlot.ENTER_RIGHT),
        )
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

private fun syntheticEdgeFrame(layout: ImmersiveHomeLayout, slot: ImmersiveSlot): ImmersiveCardFrame {
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

private fun interpolate(start: ImmersiveCardFrame, end: ImmersiveCardFrame, progress: Float): ImmersiveCardFrame {
    fun value(a: Float, b: Float) = a + (b - a) * progress
    return ImmersiveCardFrame(
        rect = HomeRect(
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
    )
}
