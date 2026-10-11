package com.kireibox.ui

/**
 * Pure pager window policy for the immersive gallery: one dot/bar per game up to seven,
 * with a stable seven-item window centered on the selection for longer libraries.
 */
object ImmersivePagerPolicy {
    const val MAX_VISIBLE = 7

    fun visibleCount(count: Int): Int =
        when {
            count <= 1 -> 0
            count <= MAX_VISIBLE -> count
            else -> MAX_VISIBLE
        }

    fun windowStart(
        count: Int,
        selected: Int
    ): Int {
        val visible = visibleCount(count)
        if (visible == 0) return 0
        if (visible == count) return 0
        val safeSelected = Math.floorMod(selected, count)
        return Math.floorMod(safeSelected - visible / 2, count)
    }

    fun visibleIndexes(
        count: Int,
        selected: Int
    ): List<Int> {
        val visible = visibleCount(count)
        if (visible == 0) return emptyList()
        if (visible == count) return (0 until count).toList()
        val start = windowStart(count, selected)
        return List(visible) { offset -> Math.floorMod(start + offset, count) }
    }

    fun visibleCenters(
        centers: List<HomePoint>,
        count: Int
    ): List<HomePoint> {
        if (centers.isEmpty() || count <= 0) return emptyList()
        val visible = count.coerceAtMost(centers.size)
        val centerX = (centers.first().x + centers.last().x) / 2f
        val centerY = centers[centers.lastIndex / 2].y
        val gap = centers.getOrNull(1)?.let { it.x - centers.first().x } ?: 0f
        return List(visible) { index ->
            HomePoint(
                centerX + (index - (visible - 1) / 2f) * gap,
                centerY,
            )
        }
    }
}

/** Converts pointer velocity into the gallery-track velocity used by [GalleryTrackController]. */
internal fun dragVelocitySlotsPerSecond(
    pointerVelocityPxPerSecond: Float,
    slotDistancePx: Float,
): Float =
    if (slotDistancePx > 0f) {
        -pointerVelocityPxPerSecond / slotDistancePx
    } else {
        0f
    }

internal data class ImmersiveBackdropTrack(
    val leftIndex: Int,
    val rightIndex: Int,
    val fraction: Float,
)

internal data class ImmersiveBackdropRetention(
    val indexes: List<Int>,
    val transitioning: Boolean,
)

internal fun resolveImmersiveBackdropTrack(
    visualPosition: Float,
    gameCount: Int,
): ImmersiveBackdropTrack {
    if (gameCount <= 0 || !visualPosition.isFinite()) {
        return ImmersiveBackdropTrack(-1, -1, 0f)
    }
    val leftPosition = kotlin.math.floor(visualPosition).toInt()
    return ImmersiveBackdropTrack(
        leftIndex = Math.floorMod(leftPosition, gameCount),
        rightIndex = Math.floorMod(leftPosition + 1, gameCount),
        fraction = (visualPosition - leftPosition).coerceIn(0f, 1f),
    )
}

internal fun resolveImmersiveBackdropRetention(
    visualPosition: Float,
    gameCount: Int,
): ImmersiveBackdropRetention {
    val track = resolveImmersiveBackdropTrack(visualPosition, gameCount)
    if (track.leftIndex < 0) return ImmersiveBackdropRetention(emptyList(), transitioning = false)
    val transitioning = track.leftIndex != track.rightIndex && track.fraction > 0f
    val indexes =
        if (transitioning) {
            listOf(track.leftIndex, track.rightIndex).distinct()
        } else {
            listOf(track.leftIndex)
        }
    return ImmersiveBackdropRetention(indexes, transitioning)
}

/**
 * r5: the games whose backdrops are prepared while the gallery rests on [index] — its real
 * neighbours (next first), wrapping on the circular track of five or more games and stopping at the
 * ends of a bounded small gallery. Never the resting game itself, never a duplicate.
 */
internal fun resolveImmersiveBackdropPrefetch(
    index: Int,
    gameCount: Int,
): List<Int> {
    if (gameCount < 2 || index !in 0 until gameCount) return emptyList()
    val neighbours =
        if (GalleryTrackBounds.isBounded(gameCount)) {
            listOf(index + 1, index - 1).filter { it in 0 until gameCount }
        } else {
            listOf(Math.floorMod(index + 1, gameCount), Math.floorMod(index - 1, gameCount))
        }
    return neighbours.distinct().filter { it != index }
}
