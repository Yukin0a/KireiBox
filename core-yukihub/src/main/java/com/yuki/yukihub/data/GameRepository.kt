package com.yuki.yukihub.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.data.backup.GameBackupCodec
import com.yuki.yukihub.data.db.GameIdentityKey
import com.yuki.yukihub.data.game.GameQueryRepository
import com.yuki.yukihub.data.play.PlayStatsRepository
import com.yuki.yukihub.model.Game
import org.json.JSONArray

/**
 * Facade over the core data stores. The public API is unchanged (callers and tests keep working);
 * the implementation is split across focused repositories:
 *
 * - [GameQueryRepository] — game CRUD and lookup queries
 * - [PlaySessionRepository] — play sessions
 * - [PlayStatsRepository] — play-statistics aggregation
 * - [GameBackupCodec] — JSON export/import and aggregate recompute
 * - [GameIdentityKey] — identity key normalization (also exposed via the companion)
 */
class GameRepository(context: Context) {
    private val helper: YukiDatabaseHelper = YukiDatabaseHelper(context.applicationContext)
    private val play = PlaySessionRepository(helper)
    private val query = GameQueryRepository(helper)
    private val stats = PlayStatsRepository(helper)
    private val backup = GameBackupCodec(helper, query)

    // --- Play sessions: delegated to PlaySessionRepository, API kept stable for callers. ---

    fun startPlaySession(
        gameId: Long,
        start: Long,
        launchType: String?,
    ): Long = play.startPlaySession(gameId, start, launchType)

    fun markPlaySessionLaunched(
        sessionId: Long,
        launchedAt: Long,
    ): Boolean = play.markPlaySessionLaunched(sessionId, launchedAt)

    fun cancelPlaySession(sessionId: Long) = play.cancelPlaySession(sessionId)

    fun finishPlaySession(
        sessionId: Long,
        end: Long,
        minDuration: Long,
        maxDuration: Long,
    ) = play.finishPlaySession(sessionId, end, minDuration, maxDuration)

    fun finishPlaySessionWithDuration(
        sessionId: Long,
        end: Long,
        recordedDuration: Long,
        minDuration: Long,
        maxDuration: Long,
    ): Boolean = play.finishPlaySessionWithDuration(sessionId, end, recordedDuration, minDuration, maxDuration)

    fun finishUnfinishedPlaySessions(
        end: Long,
        minDuration: Long,
        maxDuration: Long,
    ) = play.finishUnfinishedPlaySessions(end, minDuration, maxDuration)

    fun finishUnfinishedPlaySessions(
        end: Long,
        minDuration: Long,
        maxDuration: Long,
        exceptSessionId: Long,
    ) = play.finishUnfinishedPlaySessions(end, minDuration, maxDuration, exceptSessionId)

    fun findLatestOpenPlaySession(): PlaySessionRepository.PlayActivity? = play.findLatestOpenPlaySession()

    fun getOpenPlaySessions(): List<PlaySessionRepository.PlayActivity> = play.getOpenPlaySessions()

    fun deleteOpenPlaySessions(): Int = play.deleteOpenPlaySessions()

    fun deleteOpenPlaySession(sessionId: Long): Int = play.deleteOpenPlaySession(sessionId)

    fun addPlayTime(
        gameId: Long,
        start: Long,
        end: Long,
        duration: Long,
    ) = play.addPlayTime(gameId, start, end, duration)

    fun addManualPlayTime(
        gameId: Long,
        duration: Long,
    ) = play.addManualPlayTime(gameId, duration)

    fun addManualPlayTime(
        gameId: Long,
        duration: Long,
        end: Long,
    ) = play.addManualPlayTime(gameId, duration, end)

    fun setManualPlayTimeForGame(
        gameId: Long,
        totalDuration: Long,
    ) = play.setManualPlayTimeForGame(gameId, totalDuration)

    fun getPlayDurationsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Map<String, Long> = play.getPlayDurationsBetween(startInclusive, endExclusive)

    fun getRecentPlayActivities(limit: Int): List<PlaySessionRepository.PlayActivity> = play.getRecentPlayActivities(limit)

    fun getPlayActivitiesBetween(
        startInclusive: Long,
        endExclusive: Long,
        limit: Int,
    ): List<PlaySessionRepository.PlayActivity> = play.getPlayActivitiesBetween(startInclusive, endExclusive, limit)

    // --- Play statistics: delegated to PlayStatsRepository. ---

    /** Daily play duration including zero-value days; used by the Home trend widget. */
    @JvmOverloads
    fun dailyPlayTrend(
        days: Int,
        now: Long = System.currentTimeMillis(),
    ): List<PlayTrendPoint> = stats.dailyPlayTrend(days, now)

    // --- Game CRUD / queries: delegated to GameQueryRepository. ---

    fun getAll(): List<Game> = query.getAll()

    fun getAll(orderBy: String?): List<Game> = query.getAll(orderBy)

    fun findById(id: Long): Game? = query.findById(id)

    fun findAnyByRootKey(rootUriKey: String?): Game? = query.findAnyByRootKey(rootUriKey)

    fun findByIdentity(
        rootUri: String?,
        entryKey: String?,
    ): Game? = query.findByIdentity(rootUri, entryKey)

    fun insert(game: Game): Long = query.insert(game)

    fun insert(
        game: Game,
        db: SQLiteDatabase,
    ): Long = query.insert(game, db)

    fun existsByRootUri(rootUri: String?): Boolean = query.existsByRootUri(rootUri)

    fun getRootUriSet(): Set<String> = query.getRootUriSet()

    fun getRootUriKeySet(): Set<String> = query.getRootUriKeySet()

    fun insertIfNotExists(game: Game): Long = query.insertIfNotExists(game)

    fun findScannedMatch(scanned: Game?): Game? = query.findScannedMatch(scanned)

    fun bindScannedLocation(
        existing: Game?,
        scanned: Game?,
    ): Int = query.bindScannedLocation(existing, scanned)

    fun update(game: Game): Int = query.update(game)

    fun update(
        game: Game,
        db: SQLiteDatabase,
    ): Int = query.update(game, db)

    fun gamesUnderRoot(
        rootUri: String,
        includeHidden: Boolean = false,
    ): List<Game> = query.gamesUnderRoot(rootUri, includeHidden)

    fun setGamesHidden(
        ids: Collection<Long>,
        hidden: Boolean,
    ): Int = query.setGamesHidden(ids, hidden)

    fun reorderManual(
        favorite: Boolean,
        orderedIds: List<Long>,
    ) = query.reorderManual(favorite, orderedIds)

    fun reorderManualGroups(
        favoriteIds: List<Long>,
        regularIds: List<Long>,
    ) = query.reorderManualGroups(favoriteIds, regularIds)

    fun appendManualOrder(
        gameId: Long,
        favorite: Boolean,
    ): Boolean = query.appendManualOrder(gameId, favorite)

    fun updateCoverBindingIfUnconfigured(
        gameId: Long,
        mode: String,
        score: Double,
    ): Boolean = query.updateCoverBindingIfUnconfigured(gameId, mode, score)

    fun loadAllIncludingHidden(): List<Game> = query.loadAllIncludingHidden()

    fun delete(id: Long): Int = query.delete(id)

    fun isEmpty(): Boolean = query.isEmpty()

    fun deleteSampleGames(): Int = query.deleteSampleGames()

    // --- JSON export/import + play-stat recompute: delegated to GameBackupCodec. ---

    fun exportGamesJson(): JSONArray = backup.exportGamesJson()

    @Throws(Exception::class)
    fun exportPlaySessionsJson(): JSONArray = backup.exportPlaySessionsJson()

    @Throws(Exception::class)
    fun importGamesJson(arr: JSONArray?): Int = backup.importGamesJson(arr)

    @Throws(Exception::class)
    fun importGamesJson(
        db: SQLiteDatabase,
        arr: JSONArray?,
    ): Int = backup.importGamesJson(db, arr)

    @Throws(Exception::class)
    fun importPlaySessionsJson(arr: JSONArray?): Int = backup.importPlaySessionsJson(arr)

    @Throws(Exception::class)
    fun importPlaySessionsJson(
        db: SQLiteDatabase,
        arr: JSONArray?,
    ): Int = backup.importPlaySessionsJson(db, arr)

    fun recalculatePlayStats() = backup.recalculatePlayStats(helper.writableDatabase)

    companion object {
        @JvmStatic
        fun normalizeRootUriKey(value: String?): String = GameIdentityKey.normalizeRootUriKey(value)

        @JvmStatic
        fun normalizeEntryKey(value: String?): String = GameIdentityKey.normalizeEntryKey(value)

        @JvmStatic
        fun gameIdentityKey(game: Game?): String = GameIdentityKey.gameIdentityKey(game)

        @JvmStatic
        fun gameIdentityKey(
            rootUri: String?,
            entryKey: String?,
        ): String = GameIdentityKey.gameIdentityKey(rootUri, entryKey)
    }
}
