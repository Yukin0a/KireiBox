package com.yuki.yukihub.data

/**
 * Decides whether an auto-refresh should permanently delete a card whose
 * directory no longer exists. Only visible cards are considered; anything
 * hidden is a deliberate in-app removal and must survive refresh.
 */
internal object ScanPrunePolicy {
    fun shouldPrune(
        seen: Boolean,
        resolves: Boolean,
        hidden: Boolean,
    ): Boolean = !hidden && !seen && !resolves
}
