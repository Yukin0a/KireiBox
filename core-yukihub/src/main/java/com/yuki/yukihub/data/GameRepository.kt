package com.yuki.yukihub.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game

import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID

class GameRepository(context: Context) {
    private val helper: YukiDatabaseHelper = YukiDatabaseHelper(context.applicationContext)

    fun getAll(): List<Game> {
        return getAll("last_played_at DESC, created_at DESC")
    }

    fun getAll(orderBy: String?): List<Game> {
        val list = ArrayList<Game>()
        val db = helper.readableDatabase
        val c = db.query(
            "games", null, "hidden=0", null, null, null,
            if (orderBy == null || orderBy.trim().isEmpty()) "last_played_at DESC, created_at DESC" else orderBy
        )
        c.use {
            while (it.moveToNext()) list.add(fromCursor(it))
        }
        return list
    }

    /** Daily play duration including zero-value days; used by the Home trend widget. */
    @JvmOverloads
    fun dailyPlayTrend(days: Int, now: Long = System.currentTimeMillis()): List<PlayTrendPoint> {
        val start = PlayTrendAggregator.rangeStart(days, now)
        val samples = ArrayList<PlaySessionSample>()
        helper.readableDatabase.rawQuery(
            "SELECT COALESCE(end_time,start_time),duration,game_id,IFNULL(launched_at,0) FROM play_sessions " +
                "WHERE IFNULL(deleted,0)=0 AND " +
                "((duration>0 AND COALESCE(end_time,start_time)>=?) OR launched_at>=?)",
            arrayOf(start.toString(), start.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                samples += PlaySessionSample(
                    timestamp = cursor.getLong(0),
                    duration = cursor.getLong(1),
                    gameId = cursor.getLong(2),
                    launchedAt = cursor.getLong(3)
                )
            }
        }
        return PlayTrendAggregator.aggregate(samples, days, now)
    }

    fun findById(id: Long): Game? {
        if (id <= 0) return null
        val db = helper.readableDatabase
        val c = db.query("games", null, "id=?", arrayOf(id.toString()), null, null, null)
        return c.use {
            if (it.moveToNext()) fromCursor(it) else null
        }
    }

    fun findAnyByRootKey(rootUriKey: String?): Game? {
        if (rootUriKey.isNullOrBlank()) return null
        val db = helper.readableDatabase
        // Prefer the legacy/scanner identity when a root also owns explicit XP3
        // cards.  Callers using this compatibility lookup should not receive an
        // arbitrary entry card just because SQLite returned it first.
        db.query(
            "games", null, "root_uri_key=? AND entry_key=''", arrayOf(rootUriKey), null, null,
            "updated_at DESC", "1"
        ).use { if (it.moveToFirst()) return fromCursor(it) }
        return db.query(
            "games", null, "root_uri_key=?", arrayOf(rootUriKey), null, null, "updated_at DESC", "1"
        ).use { if (it.moveToFirst()) fromCursor(it) else null }
    }

    fun findByIdentity(rootUri: String?, entryKey: String?): Game? {
        val rootKey = normalizeRootUriKey(rootUri)
        if (rootKey.isBlank()) return null
        val entry = normalizeEntryKey(entryKey)
        return helper.readableDatabase.query(
            "games", null, "root_uri_key=? AND entry_key=?",
            arrayOf(rootKey, entry), null, null, "updated_at DESC", "1"
        ).use { if (it.moveToFirst()) fromCursor(it) else null }
    }

    /**
     * 插入游戏记录，返回新行 id。
     *
     * 契约变化（相对 Java 原版）：
     * - Java 原版：game 为 null 抛 IllegalArgumentException；rootUri 非空约束在 SQL 层。
     * - Kotlin 版：game 参数声明为非空类型，Java 调用方传 null 将由 Kotlin 运行时抛 NPE
     *   而非 IllegalArgumentException。
     * 当前所有 Java 调用方（如 ImporterService）均传非空 Game，无实际触发路径。
     */
    fun insert(game: Game): Long {
        val db = helper.writableDatabase
        return insert(game, db)
    }

    /**
     * 使用外部传入的 SQLiteDatabase 写入，确保调用方的事务边界生效。
     * 用于跨服务事务场景（如 ImporterService.importSelected 包裹的事务），
     * 避免新 Helper 实例拿到独立连接导致 rollback 时行残留。
     *
     * 契约变化（相对 Java 原版）：与 [insert] 同——game 参数声明为非空类型，
     * Java 调用方传 null 将由 Kotlin 运行时抛 NPE 而非 IllegalArgumentException。
     * 当前所有 Java 调用方均传非空 Game，无实际触发路径。
     */
    fun insert(game: Game, db: SQLiteDatabase): Long {
        val now = System.currentTimeMillis()
        game.createdAt = if (game.createdAt == 0L) now else game.createdAt
        game.updatedAt = now
        val id = db.insert("games", null, toValues(game))
        game.id = id
        return id
    }

    fun existsByRootUri(rootUri: String?): Boolean {
        if (rootUri == null || rootUri.trim().isEmpty()) return false
        val db = helper.readableDatabase
        val key = normalizeRootUriKey(rootUri)
        val c = db.rawQuery("SELECT 1 FROM games WHERE root_uri_key=? LIMIT 1", arrayOf(key))
        return c.use { it.moveToFirst() }
    }

    fun getRootUriSet(): Set<String> {
        val set = HashSet<String>()
        val db = helper.readableDatabase
        val c = db.rawQuery("SELECT root_uri FROM games WHERE root_uri IS NOT NULL AND root_uri != ''", null)
        c.use {
            while (it.moveToNext()) set.add(it.getString(0))
        }
        return set
    }

    fun getRootUriKeySet(): Set<String> {
        val set = HashSet<String>()
        val db = helper.readableDatabase
        val c = db.rawQuery("SELECT root_uri_key FROM games WHERE root_uri_key != ''", null)
        c.use {
            while (it.moveToNext()) set.add(it.getString(0))
        }
        return set
    }

    /**
     * 若同 root_uri_key 已存在则跳过插入（CONFLICT_IGNORE），返回新行 id 或 -1（已存在/失败）。
     *
     * 契约变化（相对 Java 原版）：
     * - Java 原版：game 为 null 抛 IllegalArgumentException；rootUri 为空返回 -1。
     * - Kotlin 版：game 参数声明为非空类型，Java 调用方传 null 将由 Kotlin 运行时抛 NPE
     *   而非 IllegalArgumentException；rootUri 为空仍返回 -1，与原版一致。
     * 当前所有 Java 调用方均传非空 Game，无实际触发路径。
     */
    fun insertIfNotExists(game: Game): Long {
        val rootUri = game.rootUri
        if (rootUri == null || rootUri.trim().isEmpty()) return -1
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        game.createdAt = if (game.createdAt == 0L) now else game.createdAt
        game.updatedAt = now
        val id = db.insertWithOnConflict("games", null, toValues(game), SQLiteDatabase.CONFLICT_IGNORE)
        if (id > 0) game.id = id
        // CONFLICT_IGNORE makes the scan/import identity check atomic.  Keep the
        // established -1 "already present" contract for callers.
        return id
    }

    /**
     * 查找扫描结果对应的现有游戏。云端恢复后的 SAF URI 可能因重新授权或换设备而变化，
     * 因此在完整 URI 之外，允许用同引擎下唯一的路径末段或标题进行保守匹配。
     */
    fun findScannedMatch(scanned: Game?): Game? {
        if (scanned == null) return null
        val exact = findByRootUri(scanned.rootUri)
        if (exact != null) return exact

        val games = allIncludingHidden
        val scannedLeaf = portableRootLeaf(scanned.rootUri)
        val leafMatch = uniqueScannedMatch(games, scanned, scannedLeaf, true)
        if (leafMatch != null) return leafMatch
        return uniqueScannedMatch(games, scanned, normalizeIdentityText(scanned.title), false)
    }

    /** 更新云端恢复卡片的本机扫描路径，保留游玩统计、封面和用户编辑字段。 */
    fun bindScannedLocation(existing: Game?, scanned: Game?): Int {
        if (existing == null || existing.id <= 0 || scanned == null) return 0
        val savedKrkrTarget = existing.launchTarget?.takeIf {
            (existing.engine == EngineType.KIRIKIRI || scanned.engine == EngineType.KIRIKIRI) &&
                it.endsWith(".xp3", ignoreCase = true)
        }
        existing.rootUri = scanned.rootUri
        if (scanned.engine != null && scanned.engine != EngineType.UNKNOWN) existing.engine = scanned.engine
        val scannedLaunchTarget = scanned.launchTarget
        if (savedKrkrTarget != null) {
            existing.launchTarget = savedKrkrTarget
        } else if (scannedLaunchTarget != null && scannedLaunchTarget.trim().isNotEmpty()) {
            existing.launchTarget = scannedLaunchTarget
        }
        val scannedEmulatorPackage = scanned.emulatorPackage
        if (scannedEmulatorPackage != null && scannedEmulatorPackage.trim().isNotEmpty()) existing.emulatorPackage = scannedEmulatorPackage
        return update(existing)
    }

    fun update(game: Game): Int {
        val db = helper.writableDatabase
        game.updatedAt = System.currentTimeMillis()
        return db.update("games", toValues(game), "id=?", arrayOf(game.id.toString()))
    }

    fun update(game: Game, db: SQLiteDatabase): Int {
        game.updatedAt = System.currentTimeMillis()
        return db.update("games", toValues(game), "id=?", arrayOf(game.id.toString()))
    }

    fun loadAllIncludingHidden(): List<Game> = allIncludingHidden

    fun delete(id: Long): Int {
        val db = helper.writableDatabase
        return db.delete("games", "id=?", arrayOf(id.toString()))
    }

    fun startPlaySession(gameId: Long, start: Long, launchType: String?): Long {
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
    fun markPlaySessionLaunched(sessionId: Long, launchedAt: Long): Boolean {
        if (sessionId <= 0L) return false
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            val gameId = db.rawQuery(
                "SELECT game_id FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                arrayOf(sessionId.toString())
            ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
            if (gameId <= 0L) return false
            val sessionValues = ContentValues().apply {
                put("launched_at", launchedAt)
                put("updated_at", launchedAt)
                put("dirty", 1)
            }
            if (db.update("play_sessions", sessionValues, "id=?", arrayOf(sessionId.toString())) != 1) {
                return false
            }
            db.execSQL(
                "UPDATE games SET last_played_at=MAX(IFNULL(last_played_at,0),?),updated_at=? WHERE id=?",
                arrayOf<Any>(launchedAt, launchedAt, gameId)
            )
            ensureSingleChangedRow(db, "记录最近启动时找不到游戏: $gameId")
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

    fun finishPlaySession(sessionId: Long, end: Long, minDuration: Long, maxDuration: Long) {
        if (sessionId <= 0) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            var gameId = 0L
            var start = 0L
            val c = db.rawQuery(
                "SELECT game_id,start_time,IFNULL(launched_at,0) FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                arrayOf(sessionId.toString())
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
                    val values = ContentValues().apply {
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
            db.execSQL("UPDATE games SET total_play_time = total_play_time + ?, last_played_at = MAX(IFNULL(last_played_at,0), ?), updated_at = ? WHERE id = ?",
                arrayOf<Any>(duration, end, end, gameId))
            ensureSingleChangedRow(db, "结算游玩会话时找不到游戏: $gameId")
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
        maxDuration: Long
    ): Boolean {
        if (sessionId <= 0L) return false
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val row = db.rawQuery(
                "SELECT game_id,start_time,IFNULL(launched_at,0) FROM play_sessions WHERE id=? AND end_time IS NULL LIMIT 1",
                arrayOf(sessionId.toString())
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
                    val values = ContentValues().apply {
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
            val values = ContentValues().apply {
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
                arrayOf<Any>(duration, safeEnd, safeEnd, gameId)
            )
            ensureSingleChangedRow(db, "结算游玩会话时找不到游戏: $gameId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return true
    }

    fun finishUnfinishedPlaySessions(end: Long, minDuration: Long, maxDuration: Long) {
        finishUnfinishedPlaySessions(end, minDuration, maxDuration, -1L)
    }

    fun finishUnfinishedPlaySessions(end: Long, minDuration: Long, maxDuration: Long, exceptSessionId: Long) {
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
        val c = db.rawQuery(
            "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type " +
                "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                "WHERE ps.end_time IS NULL AND IFNULL(ps.deleted,0)=0 " +
                "ORDER BY ps.start_time DESC LIMIT 1", null)
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
            null
        ).use {
            while (it.moveToNext()) {
                out += PlayActivity(
                    sessionId = it.getLong(0),
                    sessionUuid = it.getString(1),
                    gameId = it.getLong(2),
                    gameTitle = it.getString(3)?.takeIf(String::isNotBlank) ?: "未命名游戏",
                    startTime = it.getLong(4),
                    endTime = 0L,
                    duration = it.getLong(6),
                    launchType = it.getString(7)
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

    fun addPlayTime(gameId: Long, start: Long, end: Long, duration: Long) {
        var dur = duration
        if (dur <= 0) dur = Math.max(0L, end - start)
        addManualPlayTime(gameId, dur, if (end <= 0) System.currentTimeMillis() else end)
    }

    fun addManualPlayTime(gameId: Long, duration: Long) {
        addManualPlayTime(gameId, duration, System.currentTimeMillis())
    }

    fun addManualPlayTime(gameId: Long, duration: Long, end: Long) {
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
            db.execSQL("UPDATE games SET total_play_time = total_play_time + ?, last_played_at = MAX(IFNULL(last_played_at,0), ?), updated_at = ? WHERE id = ?",
                arrayOf<Any>(duration, safeEnd, now, gameId))
            ensureSingleChangedRow(db, "累计手动游玩时长时找不到游戏: $gameId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setManualPlayTimeForGame(gameId: Long, totalDuration: Long) {
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
        var playStatus: String? = null
    )

    fun getPlayDurationsBetween(startInclusive: Long, endExclusive: Long): Map<String, Long> {
        val result = LinkedHashMap<String, Long>()
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT g.title, SUM(ps.duration) FROM play_sessions ps " +
                "JOIN games g ON g.id=ps.game_id " +
                "WHERE ps.end_time IS NOT NULL AND ps.end_time>=? AND ps.end_time<? AND IFNULL(ps.deleted,0)=0 " +
                "GROUP BY ps.game_id ORDER BY MAX(ps.end_time) DESC",
            arrayOf(startInclusive.toString(), endExclusive.toString()))
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
        val c = db.rawQuery(
            "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type,g.play_status " +
                "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                "WHERE ps.end_time IS NOT NULL AND IFNULL(ps.deleted,0)=0 " +
                "ORDER BY ps.end_time DESC LIMIT ?",
            arrayOf(Math.max(1, limit).toString()))
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
                a.playStatus = normalizePlayStatus(it.getString(8))
                val title = a.gameTitle
            if (title == null || title.trim().isEmpty()) {
                a.gameTitle = "未命名游戏"
            }
                list.add(a)
            }
        }
        return list
    }

    fun getPlayActivitiesBetween(startInclusive: Long, endExclusive: Long, limit: Int): List<PlayActivity> {
        val list = ArrayList<PlayActivity>()
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT ps.id,ps.session_uuid,ps.game_id,g.title,ps.start_time,ps.end_time,ps.duration,ps.launch_type,g.play_status " +
                "FROM play_sessions ps JOIN games g ON g.id=ps.game_id " +
                "WHERE ps.end_time IS NOT NULL AND ps.end_time>=? AND ps.end_time<? AND IFNULL(ps.deleted,0)=0 " +
                "ORDER BY ps.end_time DESC LIMIT ?",
            arrayOf(startInclusive.toString(), endExclusive.toString(), Math.max(1, limit).toString()))
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
                a.playStatus = normalizePlayStatus(it.getString(8))
                val title = a.gameTitle
            if (title == null || title.trim().isEmpty()) {
                a.gameTitle = "未命名游戏"
            }
                list.add(a)
            }
        }
        return list
    }


    @Throws(Exception::class)
    fun exportGamesJson(): JSONArray {
        val arr = JSONArray()
        val db = helper.readableDatabase
        val c = db.query("games", null, null, null, null, null, "id ASC")
        c.use {
            while (it.moveToNext()) {
                val o = JSONObject()
                val id = it.getLong(it.getColumnIndexOrThrow("id"))
                o.put("local_id", id)
                o.put("title", it.getString(it.getColumnIndexOrThrow("title")))
                o.put("original_title", it.getString(it.getColumnIndexOrThrow("original_title")))
                o.put("engine", it.getString(it.getColumnIndexOrThrow("engine")))
                o.put("root_uri", it.getString(it.getColumnIndexOrThrow("root_uri")))
                o.put("entry_key", getStringOrNull(it, "entry_key") ?: "")
                o.put("cover_uri", it.getString(it.getColumnIndexOrThrow("cover_uri")))
                o.put("cover_persist_uri", getStringOrNull(it, "cover_persist_uri"))
                o.put("cover_source_type", getIntOrDefault(it, "cover_source_type", 0))
                o.put("vndb_binding_mode", normalizeVndbBindingMode(getStringOrNull(it, "vndb_binding_mode")))
                o.put("vndb_match_score", getDoubleOrDefault(it, "vndb_match_score", 0.0))
                o.put("emulator_package", it.getString(it.getColumnIndexOrThrow("emulator_package")))
                o.put("launch_target", getStringOrNull(it, "launch_target"))
                o.put("description", it.getString(it.getColumnIndexOrThrow("description")))
                o.put("tags", it.getString(it.getColumnIndexOrThrow("tags")))
                o.put("play_status", normalizePlayStatus(getStringOrNull(it, "play_status")))
                o.put("total_play_time", it.getLong(it.getColumnIndexOrThrow("total_play_time")))
                o.put("last_played_at", it.getLong(it.getColumnIndexOrThrow("last_played_at")))
                o.put("playtime_reset_at", getLongOrDefault(it, "playtime_reset_at", 0L))
                o.put("created_at", it.getLong(it.getColumnIndexOrThrow("created_at")))
                o.put("updated_at", it.getLong(it.getColumnIndexOrThrow("updated_at")))
                o.put("hidden", it.getInt(it.getColumnIndexOrThrow("hidden")) == 1)
                o.put("favorite", it.getInt(it.getColumnIndexOrThrow("favorite")) == 1)
                arr.put(o)
            }
        }
        return arr
    }

    @Throws(Exception::class)
    fun exportPlaySessionsJson(): JSONArray {
        val arr = JSONArray()
        val db = helper.readableDatabase
        val c = db.rawQuery("SELECT ps.*, g.root_uri, g.entry_key AS game_entry_key, g.title AS game_title, g.engine AS game_engine, g.emulator_package AS game_emulator_package FROM play_sessions ps LEFT JOIN games g ON g.id=ps.game_id WHERE IFNULL(ps.deleted,0)=0 AND COALESCE(ps.end_time,ps.start_time,0)>=IFNULL(g.playtime_reset_at,0) ORDER BY ps.start_time ASC", null)
        c.use {
            while (it.moveToNext()) {
                val o = JSONObject()
                o.put("session_uuid", getStringOrNull(it, "session_uuid"))
                o.put("game_local_id", it.getLong(it.getColumnIndexOrThrow("game_id")))
                o.put("game_root_uri", getStringOrNull(it, "root_uri"))
                o.put("game_entry_key", getStringOrNull(it, "game_entry_key") ?: "")
                o.put("game_title", getStringOrNull(it, "game_title"))
                o.put("game_engine", getStringOrNull(it, "game_engine"))
                o.put("game_emulator_package", getStringOrNull(it, "game_emulator_package"))
                o.put("start_time", it.getLong(it.getColumnIndexOrThrow("start_time")))
                val endIdx = it.getColumnIndex("end_time")
                if (endIdx >= 0 && !it.isNull(endIdx)) o.put("end_time", it.getLong(endIdx))
                o.put("duration", it.getLong(it.getColumnIndexOrThrow("duration")))
                o.put("launched_at", getLongOrDefault(it, "launched_at", 0L))
                o.put("launch_type", getStringOrNull(it, "launch_type"))
                o.put("device_id", getStringOrNull(it, "device_id"))
                o.put("created_at", getLongOrDefault(it, "created_at", it.getLong(it.getColumnIndexOrThrow("start_time"))))
                o.put("updated_at", getLongOrDefault(it, "updated_at", it.getLong(it.getColumnIndexOrThrow("start_time"))))
                arr.put(o)
            }
        }
        return arr
    }

    @Throws(Exception::class)
    fun importGamesJson(arr: JSONArray?): Int {
        return importGamesJson(helper.writableDatabase, arr)
    }

    @Throws(Exception::class)
    fun importGamesJson(db: SQLiteDatabase, arr: JSONArray?): Int {
        if (arr == null) return 0
        var changed = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rootUri = o.optString("root_uri", "").trim()
            val incomingEntryKey = normalizeEntryKey(o.optString("entry_key", ""))
            var g = findBySyncIdentity(db, o, rootUri, incomingEntryKey)
            val exists = g != null
            if (g == null) g = Game()
            val existingUpdatedAt = if (exists) Math.max(0L, g.updatedAt) else 0L
            val incomingUpdatedAt = Math.max(0L, o.optLong("updated_at", existingUpdatedAt))
            val applyDetails = !exists || incomingUpdatedAt >= existingUpdatedAt
            if (applyDetails) {
                g.title = o.optString("title", g.title ?: "未命名游戏")
                g.originalTitle = o.optString("original_title", g.originalTitle ?: "")
                g.engine = EngineType.fromString(o.optString("engine", g.engine?.name ?: EngineType.UNKNOWN.name))
                val gRootUri = g.rootUri
                if (rootUri.isNotEmpty() || gRootUri == null || gRootUri.trim().isEmpty()) g.rootUri = rootUri
                g.entryKey = incomingEntryKey
                g.coverUri = o.optString("cover_uri", g.coverUri ?: "")
                g.coverPersistUri = o.optString("cover_persist_uri", g.coverPersistUri ?: "")
                g.coverSourceType = o.optInt("cover_source_type", g.coverSourceType)
                g.vndbBindingMode = normalizeVndbBindingMode(o.optString("vndb_binding_mode", g.vndbBindingMode))
                g.vndbMatchScore = o.optDouble("vndb_match_score", g.vndbMatchScore)
                g.emulatorPackage = o.optString("emulator_package", g.emulatorPackage ?: "")
                g.launchTarget = o.optString("launch_target", g.launchTarget ?: "")
                g.description = o.optString("description", g.description ?: "")
                g.tags = o.optString("tags", g.tags ?: "")
                g.playStatus = normalizePlayStatus(o.optString("play_status", g.playStatus))
                g.hidden = o.optBoolean("hidden", g.hidden)
                g.favorite = o.optBoolean("favorite", g.favorite)
            } else {
                // Older card metadata must not overwrite newer local edits, but it may
                // still fill identity fields that are missing locally.
                val rootUriValue = g.rootUri
                if ((rootUriValue == null || rootUriValue.trim().isEmpty()) && rootUri.isNotEmpty()) g.rootUri = rootUri
            }
            val titleValue = g.title
            if (titleValue == null || titleValue.trim().isEmpty()) g.title = "未命名游戏"
            if (g.engine == null) g.engine = EngineType.UNKNOWN
            val incomingResetAt = Math.max(0L, o.optLong("playtime_reset_at", 0L))
            val incomingTotalPlayTime = Math.max(0L, o.optLong("total_play_time", 0L))
            val incomingLastPlayedAt = Math.max(0L, o.optLong("last_played_at", 0L))
            val resetAdvanced = incomingResetAt > g.playtimeResetAt
            if (resetAdvanced) {
                // A newer reset/manual-set on another device is an explicit operation;
                // accept its aggregate value and discard older local sessions below.
                g.playtimeResetAt = incomingResetAt
                g.totalPlayTime = incomingTotalPlayTime
                g.lastPlayedAt = incomingLastPlayedAt
            } else if (incomingResetAt == g.playtimeResetAt) {
                // Total play time is cumulative. During smart merge or normal download,
                // never let an older/empty device overwrite a larger aggregate with 0.
                g.totalPlayTime = Math.max(g.totalPlayTime, incomingTotalPlayTime)
                g.lastPlayedAt = Math.max(g.lastPlayedAt, incomingLastPlayedAt)
            } else {
                // Local reset is newer. Ignore incoming aggregate to avoid resurrecting
                // play time that was intentionally cleared locally. Newer sessions, if any,
                // can still be imported by importPlaySessionsJson() when they pass resetAt.
            }
            g.createdAt = o.optLong("created_at", g.createdAt)
            g.updatedAt = Math.max(g.updatedAt, o.optLong("updated_at", g.updatedAt))
            if (g.createdAt <= 0) g.createdAt = System.currentTimeMillis()
            if (g.updatedAt <= 0) g.updatedAt = g.createdAt
            if (exists) {
                if (db.update("games", toValues(g), "id=?", arrayOf(g.id.toString())) <= 0) {
                    throw IllegalStateException("更新云端游戏失败: ${g.id}")
                }
            } else {
                val id = db.insertWithOnConflict("games", null, toValues(g), SQLiteDatabase.CONFLICT_IGNORE)
                if (id > 0) {
                    g.id = id
                } else if (rootUri.isNotEmpty()) {
                    // Another scan/import won the same URI identity concurrently.
                    // Reapply this already-merged snapshot to that canonical row.
                    val concurrent = findByIdentity(db, rootUri, incomingEntryKey)
                        ?: throw IllegalStateException("并发插入后无法找到云端游戏: ${g.title}")
                    g.id = concurrent.id
                    if (db.update("games", toValues(g), "id=?", arrayOf(g.id.toString())) != 1) {
                        throw IllegalStateException("并发合并云端游戏失败: ${g.title}")
                    }
                } else {
                    throw IllegalStateException("插入云端游戏失败: ${g.title}")
                }
            }
            if (resetAdvanced && g.id > 0) {
                db.delete("play_sessions", "game_id=? AND (COALESCE(end_time,start_time,0) <= ?)", arrayOf(g.id.toString(), g.playtimeResetAt.toString()))
            }
            changed++
        }
        recalculatePlayStats(db)
        return changed
    }

    @Throws(Exception::class)
    fun importPlaySessionsJson(arr: JSONArray?): Int {
        return importPlaySessionsJson(helper.writableDatabase, arr)
    }

    @Throws(Exception::class)
    fun importPlaySessionsJson(db: SQLiteDatabase, arr: JSONArray?): Int {
        if (arr == null) return 0
        var changed = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var uuid = o.optString("session_uuid", "").trim()
            if (uuid.isEmpty()) uuid = UUID.randomUUID().toString()
            val dup = db.rawQuery("SELECT id,updated_at FROM play_sessions WHERE session_uuid=? LIMIT 1", arrayOf(uuid))
            var existingSessionId = -1L
            var existingUpdatedAt = 0L
            dup.use {
                if (it.moveToFirst()) {
                    existingSessionId = it.getLong(0)
                    existingUpdatedAt = it.getLong(1)
                }
            }
            val rootUri = o.optString("game_root_uri", "").trim()
            val entryKey = normalizeEntryKey(o.optString("game_entry_key", ""))
            var g: Game? = findByIdentity(db, rootUri, entryKey)
            if (g == null) {
                val identity = JSONObject()
                identity.put("root_uri", rootUri)
                identity.put("title", o.optString("game_title", ""))
                identity.put("engine", o.optString("game_engine", ""))
                identity.put("emulator_package", o.optString("game_emulator_package", ""))
                identity.put("entry_key", entryKey)
                g = findBySyncIdentity(db, identity, rootUri, entryKey)
            }
            if (g == null || g.id <= 0) continue
            val endTime = if (o.has("end_time") && !o.isNull("end_time")) o.optLong("end_time") else 0L
            val startTime = o.optLong("start_time", 0L)
            val sessionTime = if (endTime > 0) endTime else startTime
            if (g.playtimeResetAt > 0 && sessionTime > 0 && sessionTime < g.playtimeResetAt) continue
            val incomingUpdatedAt = o.optLong("updated_at", System.currentTimeMillis())
            if (existingSessionId > 0 && incomingUpdatedAt < existingUpdatedAt) continue
            val v = ContentValues()
            v.put("game_id", g.id)
            v.put("start_time", o.optLong("start_time", 0))
            if (o.has("end_time") && !o.isNull("end_time")) v.put("end_time", o.optLong("end_time")) else v.putNull("end_time")
            val duration = Math.max(0L, o.optLong("duration", 0))
            v.put("duration", duration)
            v.put("launched_at", o.optLong("launched_at", if (duration > 0L) sessionTime else 0L))
            v.put("launch_type", o.optString("launch_type", "external"))
            v.put("session_uuid", uuid)
            v.put("device_id", o.optString("device_id", "imported"))
            v.put("created_at", o.optLong("created_at", o.optLong("start_time", 0)))
            v.put("updated_at", incomingUpdatedAt)
            v.put("dirty", 1)
            v.put("deleted", 0)
            if (existingSessionId > 0) {
                if (db.update("play_sessions", v, "id=?", arrayOf(existingSessionId.toString())) <= 0) {
                    throw IllegalStateException("更新云端游玩记录失败: $uuid")
                }
            } else {
                if (db.insert("play_sessions", null, v) <= 0) {
                    throw IllegalStateException("插入云端游玩记录失败: $uuid")
                }
            }
            changed++
        }
        recalculatePlayStats(db)
        return changed
    }

    fun recalculatePlayStats() {
        recalculatePlayStats(helper.writableDatabase)
    }

    private fun recalculatePlayStats(db: SQLiteDatabase) {
        // Preserve imported/manual aggregate play time when the synced snapshot only
        // contains a limited tail of play_sessions. Explicit clear/manual reset is
        // represented by a newer playtime_reset_at plus the aggregate value imported
        // in importGamesJson(), so this method must never blindly lower totals.
        db.execSQL("UPDATE games SET total_play_time=MAX(IFNULL(total_play_time,0),IFNULL((SELECT SUM(duration) FROM play_sessions WHERE game_id=games.id AND end_time IS NOT NULL AND IFNULL(deleted,0)=0 AND end_time>=IFNULL(games.playtime_reset_at,0)),0)), last_played_at=MAX(IFNULL(last_played_at,0),IFNULL((SELECT MAX(end_time) FROM play_sessions WHERE game_id=games.id AND end_time IS NOT NULL AND IFNULL(deleted,0)=0 AND end_time>=IFNULL(games.playtime_reset_at,0)),0))")
    }

    private fun findByRootUri(rootUri: String?): Game? {
        return findByRootUri(helper.readableDatabase, rootUri)
    }

    private fun findByRootUri(db: SQLiteDatabase, rootUri: String?): Game? {
        if (rootUri == null || rootUri.trim().isEmpty()) return null
        val key = normalizeRootUriKey(rootUri)
        val c = db.query(
            "games", null, "root_uri_key=? AND entry_key=''", arrayOf(key),
            null, null, "updated_at DESC", "1"
        )
        return c.use {
            if (it.moveToFirst()) fromCursor(it) else null
        }
    }

    private fun findByIdentity(db: SQLiteDatabase, rootUri: String?, entryKey: String?): Game? {
        val key = normalizeRootUriKey(rootUri)
        if (key.isBlank()) return null
        val entry = normalizeEntryKey(entryKey)
        return db.query(
            "games", null, "root_uri_key=? AND entry_key=?", arrayOf(key, entry),
            null, null, "updated_at DESC", "1"
        ).use { if (it.moveToFirst()) fromCursor(it) else null }
    }

    private val allIncludingHidden: List<Game>
        get() {
            val list = ArrayList<Game>()
            val db = helper.readableDatabase
            val c = db.query("games", null, null, null, null, null, "updated_at DESC")
            c.use {
                while (it.moveToNext()) list.add(fromCursor(it))
            }
            return list
        }

    private fun uniqueScannedMatch(games: List<Game>?, scanned: Game, key: String, byLeaf: Boolean): Game? {
        if (games == null || key.isEmpty()) return null
        var match: Game? = null
        for (candidate in games) {
            if (candidate.entryKey.isNotBlank()) continue
            if (candidate.engine != scanned.engine) continue
            val same: Boolean = if (byLeaf) {
                key == portableRootLeaf(candidate.rootUri)
            } else {
                key == normalizeIdentityText(candidate.title) || key == normalizeIdentityText(candidate.originalTitle)
            }
            if (!same) continue
            if (match != null && match.id != candidate.id) return null
            match = candidate
        }
        return match
    }

    private fun findBySyncIdentity(
        db: SQLiteDatabase,
        o: JSONObject?,
        rootUri: String,
        entryKey: String = normalizeEntryKey(o?.optString("entry_key", ""))
    ): Game? {
        val byRoot = findByIdentity(db, rootUri, entryKey)
        if (byRoot != null) return byRoot
        if (o == null) return null
        return findByTitleForEmptyRoot(db, o.optString("title", "").trim())
            ?.takeIf { normalizeEntryKey(it.entryKey) == normalizeEntryKey(entryKey) }
    }

    private fun findByTitleForEmptyRoot(title: String): Game? {
        return findByTitleForEmptyRoot(helper.readableDatabase, title)
    }

    private fun findByTitleForEmptyRoot(db: SQLiteDatabase, title: String?): Game? {
        if (title == null || title.trim().isEmpty()) return null
        val c = db.query("games", null,
            "IFNULL(root_uri,'')='' AND IFNULL(title,'')=?",
            arrayOf(title.trim()),
            null, null, "updated_at DESC", "1")
        return c.use {
            if (!it.moveToFirst()) null else fromCursor(it)
        }
    }

    fun isEmpty(): Boolean {
        val db = helper.readableDatabase
        val c = db.rawQuery("SELECT COUNT(*) FROM games", null)
        return c.use { it.moveToFirst() && it.getInt(0) == 0 }
    }

    fun deleteSampleGames(): Int {
        val db = helper.writableDatabase
        return db.delete("games", "root_uri LIKE ?", arrayOf("sample://%"))
    }

    private fun toValues(g: Game): ContentValues {
        val v = ContentValues()
        v.put("title", nvl(g.title))
        v.put("original_title", g.originalTitle)
        v.put("engine", (g.engine ?: EngineType.UNKNOWN).name)
        v.put("root_uri", nvl(g.rootUri))
        v.put("root_uri_key", normalizeRootUriKey(g.rootUri))
        v.put("entry_key", normalizeEntryKey(g.entryKey))
        v.put("cover_uri", g.coverUri)
        v.put("cover_persist_uri", g.coverPersistUri)
        v.put("cover_source_type", g.coverSourceType)
        v.put("vndb_binding_mode", normalizeVndbBindingMode(g.vndbBindingMode))
        v.put("vndb_match_score", g.vndbMatchScore)
        v.put("emulator_package", g.emulatorPackage)
        v.put("launch_target", g.launchTarget?.takeIf { it.isNotEmpty() } ?: "[游戏目录]")
        v.put("description", g.description)
        v.put("tags", g.tags)
        v.put("play_status", normalizePlayStatus(g.playStatus))
        v.put("total_play_time", g.totalPlayTime)
        v.put("last_played_at", g.lastPlayedAt)
        v.put("playtime_reset_at", g.playtimeResetAt)
        v.put("created_at", g.createdAt)
        v.put("updated_at", g.updatedAt)
        v.put("hidden", if (g.hidden) 1 else 0)
        v.put("favorite", if (g.favorite) 1 else 0)
        return v
    }

    private fun fromCursor(c: Cursor): Game {
        val g = Game()
        g.id = c.getLong(c.getColumnIndexOrThrow("id"))
        g.title = c.getString(c.getColumnIndexOrThrow("title"))
        g.originalTitle = c.getString(c.getColumnIndexOrThrow("original_title"))
        g.engine = EngineType.fromString(c.getString(c.getColumnIndexOrThrow("engine")))
        g.rootUri = c.getString(c.getColumnIndexOrThrow("root_uri"))
        g.entryKey = normalizeEntryKey(getStringOrNull(c, "entry_key"))
        g.coverUri = c.getString(c.getColumnIndexOrThrow("cover_uri"))
        g.coverPersistUri = getStringOrNull(c, "cover_persist_uri")
        g.coverSourceType = getIntOrDefault(c, "cover_source_type", 0)
        g.vndbBindingMode = normalizeVndbBindingMode(getStringOrNull(c, "vndb_binding_mode"))
        g.vndbMatchScore = getDoubleOrDefault(c, "vndb_match_score", 0.0)
        g.emulatorPackage = c.getString(c.getColumnIndexOrThrow("emulator_package"))
        g.launchTarget = getStringOrNull(c, "launch_target")
        if (g.launchTarget.isNullOrEmpty()) g.launchTarget = "[游戏目录]"
        g.description = c.getString(c.getColumnIndexOrThrow("description"))
        g.tags = c.getString(c.getColumnIndexOrThrow("tags"))
        g.playStatus = normalizePlayStatus(getStringOrNull(c, "play_status"))
        g.totalPlayTime = c.getLong(c.getColumnIndexOrThrow("total_play_time"))
        g.lastPlayedAt = c.getLong(c.getColumnIndexOrThrow("last_played_at"))
        g.playtimeResetAt = getLongOrDefault(c, "playtime_reset_at", 0L)
        g.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
        g.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
        g.hidden = c.getInt(c.getColumnIndexOrThrow("hidden")) == 1
        val favoriteIndex = c.getColumnIndex("favorite")
        g.favorite = favoriteIndex >= 0 && !c.isNull(favoriteIndex) && c.getInt(favoriteIndex) == 1
        return g
    }

    private fun getStringOrNull(c: Cursor, column: String): String? {
        val index = c.getColumnIndex(column)
        return if (index >= 0) c.getString(index) else null
    }

    private fun getIntOrDefault(c: Cursor, column: String, def: Int): Int {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getInt(index) else def
    }

    private fun getDoubleOrDefault(c: Cursor, column: String, def: Double): Double {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getDouble(index) else def
    }

    private fun normalizeVndbBindingMode(value: String?): String = when (value?.uppercase(Locale.ROOT)) {
        "AUTO", "MANUAL", "REVIEW", "RETRY", "DISABLED" -> value.uppercase(Locale.ROOT)
        else -> "NONE"
    }

    private fun getLongOrDefault(c: Cursor, column: String, def: Long): Long {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getLong(index) else def
    }

    private fun normalizePlayStatus(status: String?): String {
        if (status == null) return "unplayed"
        // 注意：迁移至 Kotlin 时由 toLowerCase()（默认 Locale）改为 lowercase(Locale.ROOT)，
        // 修正土耳其 Locale 下 "I" → "ı" 的归一化缺陷（行为变化，等同 bug 修复）。
        val s = status.trim().lowercase(Locale.ROOT)
        if (s == "completed" || s == "played" || s == "done") return "completed"
        if (s == "playing" || s == "current") return "playing"
        return "unplayed"
    }

    private fun nvl(value: String?): String {
        return value ?: ""
    }

    private fun ensureSingleChangedRow(db: SQLiteDatabase, message: String) {
        val changed = db.rawQuery("SELECT changes()", null)
        changed.use {
            if (!it.moveToFirst() || it.getInt(0) != 1) throw IllegalStateException(message)
        }
    }

    companion object {
        @JvmStatic
        fun normalizeRootUriKey(value: String?): String {
            if (value == null) return ""
            var s = value.trim()
            if (s.startsWith("file://")) s = s.substring("file://".length)
            try {
                if (s.startsWith("content://")) {
                    val uri = android.net.Uri.parse(s)
                    var docId: String? = null
                    try { docId = android.provider.DocumentsContract.getDocumentId(uri) } catch (ignored: Throwable) { }
                    if (docId == null || docId.isEmpty()) {
                        try { docId = android.provider.DocumentsContract.getTreeDocumentId(uri) } catch (ignored: Throwable) { }
                    }
                    if (docId != null && docId.isNotEmpty()) s = android.net.Uri.decode(docId)
                }
            } catch (ignored: Throwable) { }
            while (s.contains("//")) s = s.replace("//", "/")
            while (s.endsWith("/") && s.length > 1) s = s.substring(0, s.length - 1)
            return s.lowercase(Locale.ROOT)
        }

        @JvmStatic
        fun normalizeEntryKey(value: String?): String {
            if (value.isNullOrBlank()) return ""
            if (value.contains("://")) return ""
            val parts = value.trim().replace('\\', '/').split('/')
            val safe = ArrayList<String>(parts.size)
            for (part in parts) {
                val segment = part.trim()
                if (segment.isEmpty() || segment == ".") continue
                if (segment == ".." || segment.contains('\u0000')) return ""
                safe += segment
            }
            return safe.joinToString("/").lowercase(Locale.ROOT)
        }

        @JvmStatic
        fun gameIdentityKey(game: Game?): String {
            if (game == null) return ""
            return gameIdentityKey(game.rootUri, game.entryKey)
        }

        @JvmStatic
        fun gameIdentityKey(rootUri: String?, entryKey: String?): String {
            val root = normalizeRootUriKey(rootUri)
            val entry = normalizeEntryKey(entryKey)
            return if (entry.isBlank()) root else "$root#entry=$entry"
        }

        private fun portableRootLeaf(value: String?): String {
            val key = normalizeRootUriKey(value)
            if (key.isEmpty()) return ""
            val slash = key.lastIndexOf('/')
            var leaf = if (slash >= 0) key.substring(slash + 1) else key
            val colon = leaf.lastIndexOf(':')
            if (colon >= 0 && colon < leaf.length - 1) leaf = leaf.substring(colon + 1)
            return normalizeIdentityText(leaf)
        }

        private fun normalizeIdentityText(value: String?): String {
            if (value == null) return ""
            return value.trim().replace("\\s+".toRegex(), " ").lowercase(Locale.ROOT)
        }
    }
}
