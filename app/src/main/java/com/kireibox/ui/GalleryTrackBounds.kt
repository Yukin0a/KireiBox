package com.kireibox.ui

import com.kireibox.designsystem.AppleMotion

/**
 * Small galleries (r5, user request 「沉浸式不足五个封面的时候切换会很奇怪，会无中生有」).
 *
 * The immersive track is circular so five or more games scroll endlessly. With two to four games a
 * circular track has to show the same game twice: the card leaving on one side is the card entering
 * on the other, so a copy of it fades or pops into a side slot from nowhere. Those galleries are a
 * bounded track instead: positions 0 … n − 1, every visible card is a real, distinct game in its own
 * place, the side slots only ever hold real neighbours (the first game has no left neighbour, the
 * last no right one), and dragging past either end meets rubber-band resistance
 * ([AppleMotion.rubberband]) and springs back. The pager shows n dots either way. A single game is a
 * centred card that does not swipe; five or more keep the circular track unchanged.
 */
internal object GalleryTrackBounds {
    const val MAX_BOUNDED_COUNT = 4

    /** Rubber-band span in cards: a whole card dragged past the end moves it ≈ .29 card. */
    const val RUBBER_BAND_SPAN = .6f

    fun isBounded(count: Int): Boolean = count in 2..MAX_BOUNDED_COUNT

    /** A target index on a bounded track stays on a real game; circular tracks are unchanged. */
    fun clampIndex(
        index: Int,
        count: Int,
    ): Int = if (isBounded(count)) index.coerceIn(0, count - 1) else index

    /**
     * The visual position for a raw (finger) position: inside the bounds it is the raw position; past
     * an end only the rubber-banded overshoot shows.
     */
    fun rubberBanded(
        rawPosition: Float,
        count: Int,
    ): Float {
        val last = (count - 1).toFloat()
        val inside = !isBounded(count) || !rawPosition.isFinite() || rawPosition in 0f..last
        return when {
            inside -> rawPosition
            rawPosition < 0f -> -AppleMotion.rubberband(-rawPosition, RUBBER_BAND_SPAN, RUBBER_BAND_CONSTANT)
            else -> last + AppleMotion.rubberband(rawPosition - last, RUBBER_BAND_SPAN, RUBBER_BAND_CONSTANT)
        }
    }

    /**
     * The raw position whose rubber band is [visualPosition] (the inverse of [rubberBanded]), so a drag
     * that starts while the track is still springing back from an end continues without a jump.
     */
    fun unbanded(
        visualPosition: Float,
        count: Int,
    ): Float {
        val last = (count - 1).toFloat()
        val inside = !isBounded(count) || !visualPosition.isFinite() || visualPosition in 0f..last
        return when {
            inside -> visualPosition
            visualPosition < 0f -> -unbandedOvershoot(-visualPosition)
            else -> last + unbandedOvershoot(visualPosition - last)
        }
    }

    /** y = x·d·c / (d + c·x)  ⇔  x = y·d / (c·(d − y)); a band never reaches d, so cap just below it. */
    private fun unbandedOvershoot(shown: Float): Float {
        val capped = shown.coerceAtMost(RUBBER_BAND_SPAN * MAX_BANDED_SHARE)
        return capped * RUBBER_BAND_SPAN / (RUBBER_BAND_CONSTANT * (RUBBER_BAND_SPAN - capped))
    }

    /**
     * The position the backdrop crossfades from: on a bounded track an overscrolled end keeps its own
     * game's backdrop (a circular projection would fade in the other end's cover).
     */
    fun backdropPosition(
        visualPosition: Float,
        count: Int,
    ): Float = if (isBounded(count) && visualPosition.isFinite()) visualPosition.coerceIn(0f, (count - 1).toFloat()) else visualPosition

    /** The card index at [position] on a bounded track (nearest card, clamped to a real game). */
    fun activeIndex(
        position: Float,
        count: Int,
    ): Int = Math.round(position.takeIf { it.isFinite() } ?: 0f).coerceIn(0, (count - 1).coerceAtLeast(0))

    private const val RUBBER_BAND_CONSTANT = .55f
    private const val MAX_BANDED_SHARE = .98f
}
