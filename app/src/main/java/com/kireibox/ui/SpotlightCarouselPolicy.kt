package com.kireibox.ui

/** Pure carousel behavior, kept separate from Compose timing and lifecycle wiring. */
internal object SpotlightCarouselPolicy {
    const val AUTO_ADVANCE_MILLIS = 6_000L
    const val ANIMATE_MILLIS = 480

    /**
     * PagerState.isScrollInProgress is also true during animateScrollToPage. Only a direct
     * pointer drag should pause and restart the user's six-second reading interval.
     */
    fun timerPausedForPager(userDragActive: Boolean, scrollInProgress: Boolean): Boolean =
        userDragActive && scrollInProgress

    /** Keeps a programmatic page animation from cancelling its own timer coroutine mid-flight. */
    fun timerAnchorPage(currentPage: Int, settledPage: Int): Int =
        if (currentPage == settledPage) currentPage else settledPage

    fun shouldAutoAdvance(
        pageCount: Int,
        elapsedMillis: Long,
        isVisible: Boolean,
        isResumed: Boolean,
        isDragging: Boolean,
    ): Boolean = pageCount > 1 &&
        elapsedMillis >= AUTO_ADVANCE_MILLIS &&
        isVisible &&
        isResumed &&
        !isDragging

    fun nextPage(currentPage: Int, pageCount: Int): Int =
        if (pageCount <= 1) 0 else (currentPage + 1).floorMod(pageCount)

    fun prefetchIndexes(currentPage: Int, pageCount: Int): List<Int> = when {
        pageCount <= 0 -> emptyList()
        pageCount == 1 -> listOf(0)
        else -> listOf(currentPage.floorMod(pageCount), nextPage(currentPage, pageCount))
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}

/**
 * Circular virtual-index mapping for the landscape hero gallery. 2-5 games loop through a
 * large virtual page space so the pager can show the previous/next game on both sides and
 * advance in either direction; two games display the alternate game on both previews.
 */
internal object SpotlightCircularCarouselPolicy {
    const val VIRTUAL_BASE_MULTIPLIER = 1000

    fun virtualPageCount(gameCount: Int): Int = when {
        gameCount <= 0 -> 0
        gameCount == 1 -> 1
        else -> gameCount * VIRTUAL_BASE_MULTIPLIER
    }

    fun startVirtualPage(gameCount: Int, initialIndex: Int): Int = when {
        gameCount <= 0 -> 0
        gameCount == 1 -> 0
        else -> VIRTUAL_BASE_MULTIPLIER * gameCount / 2 + initialIndex.floorMod(gameCount)
    }

    fun gameIndexForVirtualPage(virtualPage: Int, gameCount: Int): Int = when {
        gameCount <= 0 -> 0
        else -> virtualPage.floorMod(gameCount)
    }

    /** Nearest virtual page (either direction) whose game index matches [gameIndex]. */
    fun nearestVirtualPage(gameIndex: Int, pageCount: Int, fromPage: Int): Int {
        if (pageCount <= 1) return 0
        val targetIndex = gameIndex.floorMod(pageCount)
        val currentIndex = fromPage.floorMod(pageCount)
        val forward = (targetIndex - currentIndex).floorMod(pageCount)
        val backward = forward - pageCount
        return fromPage + (if (kotlin.math.abs(backward) < forward) backward else forward)
    }

    private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor
}
