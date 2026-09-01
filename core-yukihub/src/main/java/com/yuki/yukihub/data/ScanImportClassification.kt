package com.yuki.yukihub.data

/**
 * Decides whether one scan result counts as a newly-added game.
 *
 * `importScan` only records a game in `ScanImportSummary.addedGames` when the
 * database actually created a new row. The update path (`existingId > 0`) and
 * `CONFLICT_IGNORE` conflicts (`insertedId <= 0`) must never count as "new",
 * otherwise every auto-refresh reports all existing games as newly added.
 */
internal object ScanImportClassification {
    /**
     * Returns the row id to record as newly added, or null when the result was
     * already present or the insert was ignored.
     */
    fun addedIdFor(existingId: Long, insertedId: Long): Long? =
        if (existingId <= 0L && insertedId > 0L) insertedId else null
}
