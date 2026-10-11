package com.kireibox.ui

/** Resolves policy index cards to real game IDs; used by the controller and UI. */
internal object FocusGalleryGameResolver {
    fun resolve(
        cards: List<FocusCard>,
        games: List<com.yuki.yukihub.model.Game>,
    ): List<FocusCard> {
        if (games.isEmpty()) return cards
        return cards.map { card ->
            card.copy(gameId = games[card.gameId.toInt().mod(games.size)].id)
        }
    }
}

/** Stable virtual indexing used by the pure mapping (2-5 games). */
internal object FocusGalleryIndexPolicy {
    fun previous(
        index: Int,
        gameCount: Int
    ): Int {
        if (gameCount <= 1) return 0
        return ((index - 1).mod(gameCount))
    }

    fun next(
        index: Int,
        gameCount: Int
    ): Int {
        if (gameCount <= 1) return 0
        return (index + 1).mod(gameCount)
    }

    fun afterNext(
        index: Int,
        gameCount: Int
    ): Int = next(next(index, gameCount), gameCount)

    fun beforePrevious(
        index: Int,
        gameCount: Int
    ): Int = previous(previous(index, gameCount), gameCount)
}

/** Pure slot mapping. The returned cards reference game IDs only; slots are keyed by identity. */
internal object FocusGalleryMappingPolicy {
    fun cards(
        gameCount: Int,
        currentGameIndex: Int,
        direction: FocusDirection,
    ): List<FocusCard> {
        if (gameCount <= 0) return emptyList()
        if (gameCount == 1) {
            return listOf(
                FocusCard(0L, FocusSlot.LEFT, FocusSlot.LEFT, FocusCardRole.OPPOSITE_SIDE),
                FocusCard(0L, FocusSlot.CENTER, FocusSlot.CENTER, FocusCardRole.SIDE_TO_CENTER),
                FocusCard(0L, FocusSlot.RIGHT, FocusSlot.RIGHT, FocusCardRole.OPPOSITE_SIDE),
                FocusCard(0L, FocusSlot.ENTERING, FocusSlot.ENTERING, FocusCardRole.ENTERING),
            )
        }
        val center = currentGameIndex.mod(gameCount)
        val prev = FocusGalleryIndexPolicy.previous(center, gameCount)
        val next = FocusGalleryIndexPolicy.next(center, gameCount)
        return when (direction) {
            FocusDirection.ADVANCE ->
                listOf(
                    FocusCard(
                        gameId = prev.toLong(),
                        sourceSlot = FocusSlot.LEFT,
                        targetSlot = FocusSlot.LEFT,
                        role = FocusCardRole.EXITING,
                    ),
                    FocusCard(
                        gameId = center.toLong(),
                        sourceSlot = FocusSlot.CENTER,
                        targetSlot = FocusSlot.LEFT,
                        role = FocusCardRole.CENTER_TO_SIDE,
                    ),
                    FocusCard(
                        gameId = next.toLong(),
                        sourceSlot = FocusSlot.RIGHT,
                        targetSlot = FocusSlot.CENTER,
                        role = FocusCardRole.SIDE_TO_CENTER,
                    ),
                    FocusCard(
                        gameId = FocusGalleryIndexPolicy.afterNext(center, gameCount).toLong(),
                        sourceSlot = FocusSlot.ENTERING,
                        targetSlot = FocusSlot.RIGHT,
                        role = FocusCardRole.ENTERING,
                    ),
                )
            FocusDirection.BACK ->
                listOf(
                    FocusCard(
                        gameId = prev.toLong(),
                        sourceSlot = FocusSlot.LEFT,
                        targetSlot = FocusSlot.CENTER,
                        role = FocusCardRole.SIDE_TO_CENTER,
                    ),
                    FocusCard(
                        gameId = center.toLong(),
                        sourceSlot = FocusSlot.CENTER,
                        targetSlot = FocusSlot.RIGHT,
                        role = FocusCardRole.CENTER_TO_SIDE,
                    ),
                    FocusCard(
                        gameId = next.toLong(),
                        sourceSlot = FocusSlot.RIGHT,
                        targetSlot = FocusSlot.RIGHT,
                        role = FocusCardRole.EXITING,
                    ),
                    FocusCard(
                        gameId = FocusGalleryIndexPolicy.beforePrevious(center, gameCount).toLong(),
                        sourceSlot = FocusSlot.ENTERING,
                        targetSlot = FocusSlot.LEFT,
                        role = FocusCardRole.ENTERING,
                    ),
                )
        }
    }

    /**
     * After settle the committed current index is the incoming game, so the next mapping
     * starts from a stable current game even when the library reorders.
     */
    fun committedCurrentIndex(
        currentGameIndex: Int,
        gameCount: Int,
        direction: FocusDirection,
    ): Int =
        when (direction) {
            FocusDirection.ADVANCE -> FocusGalleryIndexPolicy.next(currentGameIndex, gameCount)
            FocusDirection.BACK -> FocusGalleryIndexPolicy.previous(currentGameIndex, gameCount)
        }

    /** True only when progress reached the exact commit point. */
    fun shouldCommit(progress: Float): Boolean = progress >= 1f
}
