package com.yuki.yukihub.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.data.YukiDatabaseHelper
import com.yuki.yukihub.metadata.YukiMetadataController

/** NextMoe's per-work limit; larger local totals are written as this cap. */
const val NEXTMOE_MAX_MINUTES = 60_000

/** One game's sync row for the current account (absent fields = never resolved). */
data class PlaytimeLedgerRow(
    val workId: String? = null,
    val candidateId: String? = null,
    val candidateTitle: String? = null,
    val uploadedMinutes: Int = 0,
    val uploadedWorkId: String? = null,
    val staleWorkId: String? = null,
    val rejected: String? = null,
    /** The manual NextMoe pick this binding came from; stays put when NextMoe merges the work into another id. */
    val boundFrom: String? = null,
)

/** A game with at least one played minute, with its ledger row under the current account (null = never seen). */
data class PlaytimeGame(
    val gameId: Long,
    val title: String,
    val localMinutes: Int,
    val row: PlaytimeLedgerRow?,
)

/** Counts for the settings status line. */
data class PlaytimeLedgerSummary(
    val synced: Int = 0,
    val waiting: Int = 0,
    val needsConfirm: Int = 0,
    val rejected: Int = 0,
)

/** Where sync state lives; [NextmoePlaytimeLedger] in the app, an in-memory fake in tests. */
interface NextmoePlaytimeStore {
    /** Every game with ≥ 1 played minute, with its row for [account]. */
    fun games(account: String): List<PlaytimeGame>

    /** Every library game, played here or not; pulling from NextMoe also looks at games with no local time. */
    fun libraryGames(account: String): List<PlaytimeGame>

    /** Raises [gameId]'s local total to [minutes] when it is lower; never lowers it. */
    fun raiseLocal(
        gameId: Long,
        minutes: Int,
    )

    /** Local identity evidence for a game; reads only the database and preferences. */
    fun binding(gameId: Long): GameBinding

    fun save(
        gameId: Long,
        account: String,
        row: PlaytimeLedgerRow,
    )

    /** Forgets [gameId]'s resolution so the next sync decides again (e.g. after the user picks another work). */
    fun forgetResolution(
        gameId: Long,
        account: String,
    ) {
        val row = games(account).firstOrNull { it.gameId == gameId }?.row ?: return
        save(gameId, account, PlaytimeLedgerRow(uploadedMinutes = row.uploadedMinutes, uploadedWorkId = row.uploadedWorkId, staleWorkId = row.staleWorkId))
    }
}

/**
 * Playtime sync state, one row per game (`nextmoe_playtime_sync`, DB v22). What to upload is derived
 * from `games.total_play_time` at sync time, so nothing in the play/settlement path writes here and
 * any change to a game's total (sessions, backup restore) is picked up. Rows belong to an account
 * (the NextMoe `sub`): another account's rows read as absent and are overwritten.
 */
class NextmoePlaytimeLedger(context: Context) : NextmoePlaytimeStore {
    private val app = context.applicationContext
    private val helper by lazy { YukiDatabaseHelper(app) }
    private val metadataController by lazy { YukiMetadataController(app) }

    override fun games(account: String): List<PlaytimeGame> = query(account, minPlayTimeMs = 60_000L)

    override fun libraryGames(account: String): List<PlaytimeGame> = query(account, minPlayTimeMs = 0L)

    override fun raiseLocal(
        gameId: Long,
        minutes: Int,
    ) {
        // No session row: an imported total must not show up in the play-history trend or ranking.
        helper.writableDatabase.execSQL(
            "UPDATE games SET total_play_time = MAX(IFNULL(total_play_time, 0), ?), updated_at = ? WHERE id = ?",
            arrayOf<Any>(minutes * 60_000L, System.currentTimeMillis(), gameId),
        )
    }

    private fun query(
        account: String,
        minPlayTimeMs: Long,
    ): List<PlaytimeGame> =
        helper.readableDatabase.rawQuery(
            "SELECT g.id, g.title, g.total_play_time, s.work_id, s.candidate_id, s.candidate_title, s.uploaded_minutes, " +
                "s.uploaded_work_id, s.stale_work_id, s.rejected, s.game_id, s.bound_from " +
                "FROM games g LEFT JOIN nextmoe_playtime_sync s ON s.game_id = g.id AND s.account_id = ? " +
                // Inlined, not bound: a bound arg is TEXT and never compares as a number against an expression.
                "WHERE IFNULL(g.total_play_time, 0) >= $minPlayTimeMs ORDER BY g.id",
            arrayOf(account),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val row =
                        if (c.isNull(10)) {
                            null
                        } else {
                            PlaytimeLedgerRow(
                                workId = c.getStringOrNull(3),
                                candidateId = c.getStringOrNull(4),
                                candidateTitle = c.getStringOrNull(5),
                                uploadedMinutes = c.getInt(6),
                                uploadedWorkId = c.getStringOrNull(7),
                                staleWorkId = c.getStringOrNull(8),
                                rejected = c.getStringOrNull(9),
                                boundFrom = c.getStringOrNull(11),
                            )
                        }
                    add(PlaytimeGame(c.getLong(0), c.getString(1).orEmpty(), localMinutes(c.getLong(2)), row))
                }
            }
        }

    override fun binding(gameId: Long): GameBinding {
        val ids = mutableMapOf<String, String>()
        helper.readableDatabase.rawQuery(
            "SELECT source, source_id FROM metadata_cache WHERE game_id = ? AND IFNULL(source_id, '') <> ''",
            arrayOf(gameId.toString()),
        ).use { c -> while (c.moveToNext()) ids[c.getString(0)] = c.getString(1) }
        val mode =
            helper.readableDatabase.rawQuery("SELECT vndb_binding_mode FROM games WHERE id = ?", arrayOf(gameId.toString()))
                .use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }
        return GameBinding(
            gameId = gameId,
            visibleSource = metadataController.visibleSource(gameId)?.key,
            bindingMode = mode,
            nextmoeId = ids["nextmoe"],
            vndbId = ids["vndb"],
            bangumiId = ids["bangumi"] ?: ids["bangumi_mirror"],
        )
    }

    override fun save(
        gameId: Long,
        account: String,
        row: PlaytimeLedgerRow,
    ) {
        val values =
            ContentValues().apply {
                put("game_id", gameId)
                put("account_id", account)
                put("work_id", row.workId)
                put("candidate_id", row.candidateId)
                put("candidate_title", row.candidateTitle)
                put("uploaded_minutes", row.uploadedMinutes)
                put("uploaded_work_id", row.uploadedWorkId)
                put("stale_work_id", row.staleWorkId)
                put("rejected", row.rejected)
                put("bound_from", row.boundFrom)
            }
        helper.writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val TABLE = "nextmoe_playtime_sync"

        fun localMinutes(totalPlayTimeMs: Long): Int = (totalPlayTimeMs / 60_000L).coerceIn(0L, NEXTMOE_MAX_MINUTES.toLong()).toInt()

        /** Status-line counts derived from the rows [games] returned. */
        fun summarize(games: List<PlaytimeGame>): PlaytimeLedgerSummary {
            var synced = 0
            var waiting = 0
            var confirm = 0
            var rejected = 0
            games.forEach { game ->
                val row = game.row
                when {
                    row?.rejected != null -> rejected++
                    row != null && row.workId == null -> confirm++
                    row?.workId != null && game.localMinutes <= row.uploadedMinutes -> synced++
                    else -> waiting++
                }
            }
            return PlaytimeLedgerSummary(synced, waiting, confirm, rejected)
        }

        private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    }
}
