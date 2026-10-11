package com.yuki.yukihub.data

import android.content.ContentValues
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Play-session and play-statistics persistence, split out of [GameRepository].
 *
 * GameRepository keeps the public API and delegates here, so callers (and tests) are unaffected
 * while the SQLite session/stat queries live in one focused class.
 */
class PlaySessionRepository(
    private val helper: YukiDatabaseHelper,
) {
    /** SQLiteDatabase access for callers that already opened a transaction. */
    private val writable get() = helper.writableDatabase

    fun startPlaySession(
        gameId: Long,
        start: Long,
        launchType: String?,
    ): Long {
        val db = helper.writableDatabase
        val session = ContentValues()
        session.put("game_id", gameId)
        session.put("start_time", start)
        session.putNull("end_time")
        session.put("duration", 0L)
        session.put("launched_at", 0L)
        session.put("launch_type", if (launchType == null) "external" else launchType)
        session.put("session_uuid", UUID.randomUUID().toString())
        session.put("device_id", "local")
        session.put("created_at", start)
        session.put("updated_at", start)
        session.put("dirty", 1)
        session.put("deleted", 0)
        return db.insert("play_sessions", null, session)
    }

    /** Marks a successfully dispatched launch before the launcher can be killed. */
    fun markPlaySessionLaunched(
        sessionId: Long,
        launchedAt: Long,
    ): Boolean {
        if (sessionId <= 0L) return false
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            val gameId =
                db.rawQuery(
                    "SELECT game_id FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                    arrayOf(sessionId.toString()),
                ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
            if (gameId <= 0L) return false
            val sessionValues =
                ContentValues().apply {
                    put("launched_at", launchedAt)
                    put("updated_at", launchedAt)
                    put("dirty", 1)
                }
            if (db.update("play_sessions", sessionValues, "id=?", arrayOf(sessionId.toString())) != 1) {
                return false
            }
            db.execSQL(
                "UPDATE games SET last_played_at=MAX(IFNULL(last_played_at,0),?),updated_at=? WHERE id=?",
                arrayOf<Any>(launchedAt, launchedAt, gameId),
            )
            GameMapper.ensureSingleChangedRow(db, "记录最近启动时找不到游戏: $gameId")
            db.setTransactionSuccessful()
            true
        } finally {
            db.endTransaction()
        }
    }

    fun cancelPlaySession(sessionId: Long) {
        if (sessionId <= 0) return
        val db = helper.writableDatabase
        db.delete("play_sessions", "id=? AND (end_time IS NULL OR duration=0)", arrayOf(sessionId.toString()))
    }

    fun finishPlaySession(
        sessionId: Long,
        end: Long,
        minDuration: Long,
        maxDuration: Long,
    ) {
        if (sessionId <= 0) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            var gameId = 0L
            var start = 0L
            val c =
                db.rawQuery(
                    "SELECT game_id,start_time,IFNULL(launched_at,0) FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                    arrayOf(sessionId.toString()),
                )
            var launchedAt = 0L
            c.use {
                if (!it.moveToFirst()) return
                gameId = it.getLong(0)
                start = it.getLong(1)
                launchedAt = it.getLong(2)
            }
            val rawDuration = Math.max(0L, end - start)
            if (rawDuration < minDuration) {
                if (launchedAt > 0L) {
                    val values =
                        ContentValues().apply {
                            put("end_time", end.coerceAtLeast(start))
                            put("duration", 0L)
                            put("updated_at", end.coerceAtLeast(start))
                            put("dirty", 1)
                        }
                    if (db.update("play_sessions", values, "id=?", arrayOf(sessionId.toString())) != 1) {
                        throw IllegalStateException("保留短时启动记录失败: $sessionId")
                    }
                } else if (db.delete("play_sessions", "id=?", arrayOf(sessionId.toString())) != 1) {
                    throw IllegalStateException("删除无效游玩会话失败: $sessionId")
                }
                db.setTransactionSuccessful()
                return
            }
            val duration = Math.min(rawDuration, maxDuration)
            val values = ContentValues()
            values.put("end_time", end)
            values.put("duration", duration)
            values.put("updated_at", end)
            values.put("dirty", 1)
            if (db.update("play_sessions", values, "id=?", arrayOf(sessionId.toString())) != 1) {
                throw IllegalStateException("结算游玩会话失败: $sessionId")
            }
            // Keep the increment in SQL so a stale Game object cannot overwrite it.
            db.execSQL(
                "UPDATE games SET total_play_time = total_play_time + ?, last_played_at = MAX(IFNULL(last_played_at,0), ?), updated_at = ? WHERE id = ?",
                arrayOf<Any>(duration, end, end, gameId),
            )
            GameMapper.ensureSingleChangedRow(db, "结算游玩会话时找不到游戏: $gameId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Finalizes an engine-owned heartbeat journal without counting wall-clock downtime. */
    fun finishPlaySessionWithDuration(
        sessionId: Long,
        end: Long,
        recordedDuration: Long,
        minDuration: Long,
        maxDuration: Long,
    ): Boolean {
        if (sessionId <= 0L) return false
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val row =
                db.rawQuery(
                    "SELECT game_id,start_time,IFNULL(launched_at,0) FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                    arrayOf(sessionId.toString()),
                ).use {
                    if (it.moveToFirst()) Triple(it.getLong(0), it.getLong(1), it.getLong(2)) else null
                } ?: return false
            val gameId = row.first
            val start = row.second
            val launchedAt = row.third
            val safeEnd = maxOf(start, end)
            val duration = recordedDuration.coerceAtLeast(0L).coerceAtMost(maxDuration)
            if (duration < minDuration) {
                if (launchedAt > 0L) {
                    val values =
                        ContentValues().apply {
                            put("end_time", safeEnd)
                            put("duration", 0L)
                            put("updated_at", safeEnd)
                            put("dirty", 1)
                        }
                    if (db.update("play_sessions", values, "id=?", arrayOf(sessionId.toString())) != 1) {
                        throw IllegalStateException("保留短时启动记录失败: $sessionId")
                    }
                } else if (db.delete("play_sessions", "id=?", arrayOf(sessionId.toString())) != 1) {
                    throw IllegalStateException("删除无效游玩会话失败: $sessionId")
                }
                db.setTransactionSuccessful()
                return true
            }
            val values =
                ContentValues().apply {
                    put("end_time", safeEnd)
                    put("duration", duration)
                    put("updated_at", safeEnd)
                    put("dirty", 1)
                }
            if (db.update("play_sessions", values, "id=?", arrayOf(sessionId.toString())) != 1) {
                throw IllegalStateException("结算游玩会话失败: $sessionId")
            }
            db.execSQL(
                "UPDATE games SET total_play_time=total_play_time+?,last_played_at=MAX(IFNULL(last_played_at,0),?),updated_at=? WHERE id=?",
                arrayOf<Any>(duration, safeEnd, safeEnd, gameId),
            )
            GameMapper.ensureSingleChangedRow(db, "结算游玩会话时找不到游戏: $gameId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    fun finishUnfinishedPlaySessions(
        end: Long,
        minDuration: Long,
        maxDuration: Long,
    ) {
        finishUnfinishedPlaySessions(end, minDuration, maxDuration, -1L)
    }

    fun finishUnfinishedPlaySessions(
        end: Long,
        minDuration: Long,
        maxDuration: Long,
        exceptSessionId: Long,
    ) {
        val db = helper.writableDatabase
        val ids = ArrayList<Long>()
        val c = db.rawQuery("SELECT id FROM play_sessions WHERE end_time IS NULL ORDER BY start_time ASC", null)
        c.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                if (id != exceptSessionId) ids.add(id)
            }
        }
        for (id in ids) finishPlaySession(id, end, minDuration, maxDuration)
    }

    fun findLatestOpenPlaySession(): PlayActivity? {
        val db = helper.readableDatabase
        val c =
            db.rawQuery(
                "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type " +
                    "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                    "WHERE ps.end_time IS NULL AND IFNULL(ps.deleted,0)=0 " +
                    "ORDER BY ps.start_time DESC LIMIT 1",
                null,
            )
        return c.use {
            if (!it.moveToFirst()) return null
            val a = PlayActivity()
            a.sessionId = it.getLong(0)
            a.sessionUuid = it.getString(1)
            a.gameId = it.getLong(2)
            a.gameTitle = it.getString(3)
            a.startTime = it.getLong(4)
            a.endTime = 0L
            a.duration = it.getLong(6)
            a.launchType = it.getString(7)
            val title = a.gameTitle
            if (title == null || title.trim().isEmpty()) {
                a.gameTitle = "未命名游戏"
            }
            a
        }
    }

    fun getOpenPlaySessions(): List<PlayActivity> {
        val out = ArrayList<PlayActivity>()
        helper.readableDatabase.rawQuery(
            "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type " +
                "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                "WHERE ps.end_time IS NULL AND IFNULL(ps.deleted,0)=0 ORDER BY ps.start_time ASC",
            null,
        ).use {
            while (it.moveToNext()) {
                out +=
                    PlayActivity(
                        sessionId = it.getLong(0),
                        sessionUuid = it.getString(1),
                        gameId = it.getLong(2),
                        gameTitle = it.getString(3)?.takeIf(String::isNotBlank) ?: "未命名游戏",
                        startTime = it.getLong(4),
                        endTime = 0L,
                        duration = it.getLong(6),
                        launchType = it.getString(7),
                    )
            }
        }
        return out
    }

    fun deleteOpenPlaySessions(): Int {
        val db = helper.writableDatabase
        return db.delete("play_sessions", "end_time IS NULL", null)
    }

    fun deleteOpenPlaySession(sessionId: Long): Int {
        if (sessionId <= 0) return 0
        val db = helper.writableDatabase
        return db.delete("play_sessions", "id=? AND end_time IS NULL", arrayOf(sessionId.toString()))
    }

    fun addPlayTime(
        gameId: Long,
        start: Long,
        end: Long,
        duration: Long,
    ) {
        var dur = duration
        if (dur <= 0) dur = Math.max(0L, end - start)
        addManualPlayTime(gameId, dur, if (end <= 0) System.currentTimeMillis() else end)
    }

    fun addManualPlayTime(
        gameId: Long,
        duration: Long,
    ) {
        addManualPlayTime(gameId, duration, System.currentTimeMillis())
    }

    fun addManualPlayTime(
        gameId: Long,
        duration: Long,
        end: Long,
    ) {
        if (gameId <= 0 || duration <= 0) return
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        val safeEnd = if (end <= 0) now else end
        val start = Math.max(0L, safeEnd - duration)
        val session = ContentValues()
        session.put("game_id", gameId)
        session.put("start_time", start)
        session.put("end_time", safeEnd)
        session.put("duration", duration)
        session.put("launched_at", 0L)
        session.put("launch_type", "manual")
        session.put("session_uuid", UUID.randomUUID().toString())
        session.put("device_id", "local")
        session.put("created_at", now)
        session.put("updated_at", now)
        session.put("dirty", 1)
        session.put("deleted", 0)
        db.beginTransaction()
        try {
            if (db.insert("play_sessions", null, session) <= 0) {
                throw IllegalStateException("写入手动游玩记录失败: $gameId")
            }
            db.execSQL(
                "UPDATE games SET total_play_time = total_play_time + ?, last_played_at = MAX(IFNULL(last_played_at,0), ?), updated_at = ? WHERE id = ?",
                arrayOf<Any>(duration, safeEnd, now, gameId),
            )
            GameMapper.ensureSingleChangedRow(db, "累计手动游玩时长时找不到游戏: $gameId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setManualPlayTimeForGame(
        gameId: Long,
        totalDuration: Long,
    ) {
        if (gameId <= 0) return
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        val safeDuration = Math.max(0L, totalDuration)
        db.beginTransaction()
        try {
            db.delete("play_sessions", "game_id=?", arrayOf(gameId.toString()))
            var lastPlayed = 0L
            if (safeDuration > 0) {
                lastPlayed = now
                val start = Math.max(0L, now - safeDuration)
                val session = ContentValues()
                session.put("game_id", gameId)
                session.put("start_time", start)
                session.put("end_time", now)
                session.put("duration", safeDuration)
                session.put("launched_at", 0L)
                session.put("launch_type", "manual")
                session.put("session_uuid", UUID.randomUUID().toString())
                session.put("device_id", "local")
                session.put("created_at", now)
                session.put("updated_at", now)
                session.put("dirty", 1)
                session.put("deleted", 0)
                if (db.insert("play_sessions", null, session) <= 0) {
                    throw IllegalStateException("写入手动总时长记录失败: $gameId")
                }
            }
            val v = ContentValues()
            v.put("total_play_time", safeDuration)
            v.put("last_played_at", lastPlayed)
            v.put("playtime_reset_at", now)
            v.put("updated_at", now)
            if (db.update("games", v, "id=?", arrayOf(gameId.toString())) != 1) {
                throw IllegalStateException("设置手动总时长时找不到游戏: $gameId")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    data class PlayActivity(
        var sessionId: Long = 0L,
        var sessionUuid: String? = null,
        var gameId: Long = 0L,
        var gameTitle: String? = null,
        var startTime: Long = 0L,
        var endTime: Long = 0L,
        var duration: Long = 0L,
        var launchType: String? = null,
        var playStatus: String? = null,
    )

    fun getPlayDurationsBetween(
        startInclusive: Long,
        endExclusive: Long,
    ): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val db = helper.readableDatabase
        val c =
            db.rawQuery(
                "SELECT g.title, SUM(ps.duration) FROM play_sessions ps " +
                    "JOIN games g ON g.id=ps.game_id " +
                    "WHERE ps.end_time IS NOT NULL AND ps.end_time>=? AND ps.end_time<? AND IFNULL(ps.deleted,0)=0 " +
                    "GROUP BY ps.game_id ORDER BY MAX(ps.end_time) DESC",
                arrayOf(startInclusive.toString(), endExclusive.toString()),
            )
        c.use {
            while (it.moveToNext()) {
                val title = it.getString(0)
                val duration = it.getLong(1)
                result.put(if (title == null || title.trim().isEmpty()) "未命名游戏" else title, duration)
            }
        }
        return result
    }

    fun getRecentPlayActivities(limit: Int): List<PlayActivity> {
        val list = ArrayList<PlayActivity>()
        val db = helper.readableDatabase
        val c =
            db.rawQuery(
                "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type,g.play_status " +
                    "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                    "WHERE ps.end_time IS NOT NULL AND IFNULL(ps.deleted,0)=0 " +
                    "ORDER BY ps.end_time DESC LIMIT ?",
                arrayOf(Math.max(1, limit).toString()),
            )
        c.use {
            while (it.moveToNext()) {
                val a = PlayActivity()
                a.sessionId = it.getLong(0)
                a.sessionUuid = it.getString(1)
                a.gameId = it.getLong(2)
                a.gameTitle = it.getString(3)
                a.startTime = it.getLong(4)
                a.endTime = it.getLong(5)
                a.duration = it.getLong(6)
                a.launchType = it.getString(7)
                a.playStatus = GameMapper.normalizePlayStatus(it.getString(8))
                val title = a.gameTitle
                if (title == null || title.trim().isEmpty()) {
                    a.gameTitle = "未命名游戏"
                }
                list.add(a)
            }
        }
        return list
    }

    fun getPlayActivitiesBetween(
        startInclusive: Long,
        endExclusive: Long,
        limit: Int,
    ): List<PlayActivity> {
        val list = ArrayList<PlayActivity>()
        val db = helper.readableDatabase
        val c =
            db.rawQuery(
                "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type,g.play_status " +
                    "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                    "WHERE ps.end_time IS NOT NULL AND ps.end_time>=? AND ps.end_time<? AND IFNULL(ps.deleted,0)=0 " +
                    "ORDER BY ps.end_time DESC LIMIT ?",
                arrayOf(startInclusive.toString(), endExclusive.toString(), Math.max(1, limit).toString()),
            )
        c.use {
            while (it.moveToNext()) {
                val a = PlayActivity()
                a.sessionId = it.getLong(0)
                a.sessionUuid = it.getString(1)
                a.gameId = it.getLong(2)
                a.gameTitle = it.getString(3)
                a.startTime = it.getLong(4)
                a.endTime = it.getLong(5)
                a.duration = it.getLong(6)
                a.launchType = it.getString(7)
                a.playStatus = GameMapper.normalizePlayStatus(it.getString(8))
                val title = a.gameTitle
                if (title == null || title.trim().isEmpty()) {
                    a.gameTitle = "未命名游戏"
                }
                list.add(a)
            }
        }
        return list
    }
}
