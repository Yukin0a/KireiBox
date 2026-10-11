package com.yuki.yukihub.data.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.data.GameMapper
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.YukiDatabaseHelper
import com.yuki.yukihub.data.game.GameQueryRepository
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * JSON export/import for games and play sessions, plus the aggregate play-stat recompute.
 *
 * Extracted from [GameRepository]; GameRepository keeps its public methods and delegates here so
 * callers are unaffected. The smart-merge rules (newer local edits win, aggregates never shrink)
 * live with the codec that owns the on-the-wire shape.
 */
internal class GameBackupCodec(
    private val helper: YukiDatabaseHelper,
    private val repo: GameQueryRepository,
) {
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
                o.put("entry_key", GameMapper.getStringOrNull(it, "entry_key") ?: "")
                o.put("cover_uri", it.getString(it.getColumnIndexOrThrow("cover_uri")))
                o.put("cover_persist_uri", GameMapper.getStringOrNull(it, "cover_persist_uri"))
                o.put("cover_source_type", GameMapper.getIntOrDefault(it, "cover_source_type", 0))
                o.put("vndb_binding_mode", GameMapper.normalizeVndbBindingMode(GameMapper.getStringOrNull(it, "vndb_binding_mode")))
                o.put("vndb_match_score", GameMapper.getDoubleOrDefault(it, "vndb_match_score", 0.0))
                o.put("emulator_package", it.getString(it.getColumnIndexOrThrow("emulator_package")))
                o.put("launch_target", GameMapper.getStringOrNull(it, "launch_target"))
                o.put("description", it.getString(it.getColumnIndexOrThrow("description")))
                o.put("tags", it.getString(it.getColumnIndexOrThrow("tags")))
                o.put("play_status", GameMapper.normalizePlayStatus(GameMapper.getStringOrNull(it, "play_status")))
                o.put("total_play_time", it.getLong(it.getColumnIndexOrThrow("total_play_time")))
                o.put("last_played_at", it.getLong(it.getColumnIndexOrThrow("last_played_at")))
                o.put("playtime_reset_at", GameMapper.getLongOrDefault(it, "playtime_reset_at", 0L))
                o.put("created_at", it.getLong(it.getColumnIndexOrThrow("created_at")))
                o.put("updated_at", it.getLong(it.getColumnIndexOrThrow("updated_at")))
                o.put("hidden", it.getInt(it.getColumnIndexOrThrow("hidden")) == 1)
                o.put("favorite", it.getInt(it.getColumnIndexOrThrow("favorite")) == 1)
                o.put("nsfw", GameMapper.getIntOrDefault(it, "nsfw", 0) == 1)
                o.put("manual_order", GameMapper.getLongOrDefault(it, "manual_order", 0L))
                arr.put(o)
            }
        }
        return arr
    }

    @Throws(Exception::class)
    fun exportPlaySessionsJson(): JSONArray {
        val arr = JSONArray()
        val db = helper.readableDatabase
        val c =
            db.rawQuery(
                "SELECT ps.*, g.root_uri, g.entry_key AS game_entry_key, g.title AS game_title, " +
                    "g.engine AS game_engine, g.emulator_package AS game_emulator_package " +
                    "FROM play_sessions ps LEFT JOIN games g ON g.id=ps.game_id " +
                    "WHERE IFNULL(ps.deleted,0)=0 AND " +
                    "COALESCE(ps.end_time,ps.start_time,0)>=IFNULL(g.playtime_reset_at,0) " +
                    "ORDER BY ps.start_time ASC",
                null,
            )
        c.use {
            while (it.moveToNext()) {
                val o = JSONObject()
                o.put("session_uuid", GameMapper.getStringOrNull(it, "session_uuid"))
                o.put("game_local_id", it.getLong(it.getColumnIndexOrThrow("game_id")))
                o.put("game_root_uri", GameMapper.getStringOrNull(it, "root_uri"))
                o.put("game_entry_key", GameMapper.getStringOrNull(it, "game_entry_key") ?: "")
                o.put("game_title", GameMapper.getStringOrNull(it, "game_title"))
                o.put("game_engine", GameMapper.getStringOrNull(it, "game_engine"))
                o.put("game_emulator_package", GameMapper.getStringOrNull(it, "game_emulator_package"))
                o.put("start_time", it.getLong(it.getColumnIndexOrThrow("start_time")))
                val endIdx = it.getColumnIndex("end_time")
                if (endIdx >= 0 && !it.isNull(endIdx)) o.put("end_time", it.getLong(endIdx))
                o.put("duration", it.getLong(it.getColumnIndexOrThrow("duration")))
                o.put("launched_at", GameMapper.getLongOrDefault(it, "launched_at", 0L))
                o.put("launch_type", GameMapper.getStringOrNull(it, "launch_type"))
                o.put("device_id", GameMapper.getStringOrNull(it, "device_id"))
                o.put("created_at", GameMapper.getLongOrDefault(it, "created_at", it.getLong(it.getColumnIndexOrThrow("start_time"))))
                o.put("updated_at", GameMapper.getLongOrDefault(it, "updated_at", it.getLong(it.getColumnIndexOrThrow("start_time"))))
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
    fun importGamesJson(
        db: SQLiteDatabase,
        arr: JSONArray?,
    ): Int {
        if (arr == null) return 0
        var changed = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rootUri = o.optString("root_uri", "").trim()
            val incomingEntryKey = GameRepository.normalizeEntryKey(o.optString("entry_key", ""))
            var g = repo.findBySyncIdentity(db, o, rootUri, incomingEntryKey)
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
                g.vndbBindingMode = GameMapper.normalizeVndbBindingMode(o.optString("vndb_binding_mode", g.vndbBindingMode))
                g.vndbMatchScore = o.optDouble("vndb_match_score", g.vndbMatchScore)
                g.emulatorPackage = o.optString("emulator_package", g.emulatorPackage ?: "")
                g.launchTarget = o.optString("launch_target", g.launchTarget ?: "")
                g.description = o.optString("description", g.description ?: "")
                g.tags = o.optString("tags", g.tags ?: "")
                g.playStatus = GameMapper.normalizePlayStatus(o.optString("play_status", g.playStatus))
                g.hidden = o.optBoolean("hidden", g.hidden)
                g.favorite = o.optBoolean("favorite", g.favorite)
                g.nsfw = o.optBoolean("nsfw", g.nsfw)
                g.manualOrder = Math.max(0L, o.optLong("manual_order", g.manualOrder))
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
                if (db.update("games", GameMapper.toValues(g), "id=?", arrayOf(g.id.toString())) <= 0) {
                    throw IllegalStateException("更新云端游戏失败: ${g.id}")
                }
            } else {
                val id = db.insertWithOnConflict("games", null, GameMapper.toValues(g), SQLiteDatabase.CONFLICT_IGNORE)
                if (id > 0) {
                    g.id = id
                } else if (rootUri.isNotEmpty()) {
                    // Another scan/import won the same URI identity concurrently.
                    // Reapply this already-merged snapshot to that canonical row.
                    val concurrent =
                        repo.findByIdentity(db, rootUri, incomingEntryKey)
                            ?: throw IllegalStateException("并发插入后无法找到云端游戏: ${g.title}")
                    g.id = concurrent.id
                    if (db.update("games", GameMapper.toValues(g), "id=?", arrayOf(g.id.toString())) != 1) {
                        throw IllegalStateException("并发合并云端游戏失败: ${g.title}")
                    }
                } else {
                    throw IllegalStateException("插入云端游戏失败: ${g.title}")
                }
            }
            if (resetAdvanced && g.id > 0) {
                db.delete(
                    "play_sessions",
                    "game_id=? AND (COALESCE(end_time,start_time,0) <= ?)",
                    arrayOf(g.id.toString(), g.playtimeResetAt.toString()),
                )
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
    fun importPlaySessionsJson(
        db: SQLiteDatabase,
        arr: JSONArray?,
    ): Int {
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
            val entryKey = GameRepository.normalizeEntryKey(o.optString("game_entry_key", ""))
            var g: Game? = repo.findByIdentity(db, rootUri, entryKey)
            if (g == null) {
                val identity = JSONObject()
                identity.put("root_uri", rootUri)
                identity.put("title", o.optString("game_title", ""))
                identity.put("engine", o.optString("game_engine", ""))
                identity.put("emulator_package", o.optString("game_emulator_package", ""))
                identity.put("entry_key", entryKey)
                g = repo.findBySyncIdentity(db, identity, rootUri, entryKey)
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

    /**
     * Preserve imported/manual aggregate play time when the synced snapshot only contains a
     * limited tail of play_sessions. Explicit clear/manual reset is represented by a newer
     * playtime_reset_at plus the aggregate value imported in [importGamesJson], so this method
     * must never blindly lower totals.
     */
    internal fun recalculatePlayStats(db: SQLiteDatabase) {
        db.execSQL(
            "UPDATE games SET " +
                "total_play_time=MAX(IFNULL(total_play_time,0)," +
                "IFNULL((SELECT SUM(duration) FROM play_sessions WHERE game_id=games.id " +
                "AND end_time IS NOT NULL AND IFNULL(deleted,0)=0 " +
                "AND end_time>=IFNULL(games.playtime_reset_at,0)),0)), " +
                "last_played_at=MAX(IFNULL(last_played_at,0)," +
                "IFNULL((SELECT MAX(end_time) FROM play_sessions WHERE game_id=games.id " +
                "AND end_time IS NOT NULL AND IFNULL(deleted,0)=0 " +
                "AND end_time>=IFNULL(games.playtime_reset_at,0)),0))",
        )
    }
}
