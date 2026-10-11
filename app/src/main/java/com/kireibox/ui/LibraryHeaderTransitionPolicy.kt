package com.kireibox.ui

/**
 * Library header geometry. Both orientations draw the shared KireiLargeTopBar over the grid, so
 * cards scroll under its glass: portrait scrolls a large title away into the bar, landscape keeps
 * one compact bar and only fades its glass in once the first row passes under it.
 */
object LibraryHeaderTransitionPolicy {
    /** The compact bar (`KireiTopBarDefaults.CompactHeight`) the landscape grid starts below. */
    const val TOOLBAR_HEIGHT_DP = 52f

    /** Landscape: scroll distance over which the bar's glass fades in as the first row passes under it. */
    const val GLASS_FADE_DP = 16f

    /**
     * 0 at rest, 1 once the grid scrolled [rangePx] or its first item is gone. The caller passes the
     * grid's scroll position, so the result can be read in a draw scope without recomposing.
     */
    fun scrollFraction(
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffsetPx: Int,
        rangePx: Float,
    ): Float {
        if (firstVisibleItemIndex > 0) return 1f
        return if (rangePx <= 0f) 0f else (firstVisibleItemScrollOffsetPx / rangePx).coerceIn(0f, 1f)
    }
}
