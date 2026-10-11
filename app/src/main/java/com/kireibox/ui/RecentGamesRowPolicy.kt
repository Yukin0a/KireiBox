package com.kireibox.ui

/**
 * Responsive recent-games row sizing for constrained landscape surfaces. Every visible
 * item must be complete and touch-safe, and the row must never overflow its viewport, so
 * a second item can never be clipped to a sliver by a horizontal Row or LazyRow.
 */
object RecentGamesRowPolicy {
    const val ITEM_GAP_DP = 12f

    /** Minimum width that keeps cover + title column + play action complete. */
    const val MIN_ITEM_WIDTH_DP = 180f
    const val MIN_TOUCH_WIDTH_DP = 48f

    /** Largest item count (<= [maxItems]) that fits with the min card width. */
    fun itemCount(
        availableWidthDp: Float,
        maxItems: Int = 2
    ): Int {
        val cap = maxItems.coerceIn(1, 4)
        var count = cap
        while (count > 1) {
            if (fits(availableWidthDp, count)) return count
            count--
        }
        return 1
    }

    fun fits(
        availableWidthDp: Float,
        itemCount: Int
    ): Boolean {
        val count = itemCount.coerceAtLeast(1)
        if (count == 1) return availableWidthDp >= MIN_TOUCH_WIDTH_DP
        return availableWidthDp >= count * MIN_ITEM_WIDTH_DP + (count - 1) * ITEM_GAP_DP
    }

    /** Equal split of the viewport; the item-count guard keeps the result overflow-free. */
    fun cardWidthDp(
        availableWidthDp: Float,
        itemCount: Int
    ): Float {
        val count = itemCount.coerceAtLeast(1)
        if (count == 1) return availableWidthDp.coerceAtLeast(0f)
        val width = (availableWidthDp - (count - 1) * ITEM_GAP_DP) / count
        return width.coerceAtLeast(MIN_ITEM_WIDTH_DP)
    }
}
