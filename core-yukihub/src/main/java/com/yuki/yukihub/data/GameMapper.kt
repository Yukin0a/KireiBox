package com.yuki.yukihub.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import java.util.Locale

/**
 * Row mapping and value normalization for the `games` table.
 *
 * Extracted from [GameRepository] so the SQLite row shape lives in one place: [GameRepository]
 * delegates here instead of owning serialization, keeping the repository focused on queries.
 */
internal object GameMapper {
    internal fun toValues(g: Game): ContentValues {
        val v = ContentValues()
        v.put("title", nvl(g.title))
        v.put("original_title", g.originalTitle)
        v.put("engine", (g.engine ?: EngineType.UNKNOWN).name)
        v.put("root_uri", nvl(g.rootUri))
        v.put("root_uri_key", GameRepository.normalizeRootUriKey(g.rootUri))
        v.put("entry_key", GameRepository.normalizeEntryKey(g.entryKey))
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
        v.put("nsfw", if (g.nsfw) 1 else 0)
        v.put("manual_order", g.manualOrder)
        return v
    }

    internal fun fromCursor(c: Cursor): Game {
        val g = Game()
        g.id = c.getLong(c.getColumnIndexOrThrow("id"))
        g.title = c.getString(c.getColumnIndexOrThrow("title"))
        g.originalTitle = c.getString(c.getColumnIndexOrThrow("original_title"))
        g.engine = EngineType.fromString(c.getString(c.getColumnIndexOrThrow("engine")))
        g.rootUri = c.getString(c.getColumnIndexOrThrow("root_uri"))
        g.entryKey = GameRepository.normalizeEntryKey(getStringOrNull(c, "entry_key"))
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
        g.nsfw = getIntOrDefault(c, "nsfw", 0) == 1
        g.manualOrder = getLongOrDefault(c, "manual_order", 0L)
        return g
    }

    internal fun getStringOrNull(
        c: Cursor,
        column: String,
    ): String? {
        val index = c.getColumnIndex(column)
        return if (index >= 0) c.getString(index) else null
    }

    internal fun getIntOrDefault(
        c: Cursor,
        column: String,
        def: Int,
    ): Int {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getInt(index) else def
    }

    internal fun getDoubleOrDefault(
        c: Cursor,
        column: String,
        def: Double,
    ): Double {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getDouble(index) else def
    }

    internal fun nextManualOrder(
        db: SQLiteDatabase,
        favorite: Boolean,
    ): Long =
        db.rawQuery(
            "SELECT IFNULL(MAX(manual_order),0) FROM games WHERE favorite=?",
            arrayOf(if (favorite) "1" else "0"),
        ).use { cursor ->
            val current = if (cursor.moveToFirst()) cursor.getLong(0).coerceAtLeast(0L) else 0L
            (current + 1024L).coerceAtLeast(1024L)
        }

    internal fun normalizeVndbBindingMode(value: String?): String =
        when (value?.uppercase(Locale.ROOT)) {
            "AUTO", "MANUAL", "REVIEW", "RETRY", "DISABLED" -> value.uppercase(Locale.ROOT)
            else -> "NONE"
        }

    internal fun getLongOrDefault(
        c: Cursor,
        column: String,
        def: Long,
    ): Long {
        val index = c.getColumnIndex(column)
        return if (index >= 0 && !c.isNull(index)) c.getLong(index) else def
    }

    internal fun normalizePlayStatus(status: String?): String {
        if (status == null) return "unplayed"
        // 注意：迁移至 Kotlin 时由 toLowerCase()（默认 Locale）改为 lowercase(Locale.ROOT)，
        // 修正土耳其 Locale 下 "I" → "ı" 的归一化缺陷（行为变化，等同 bug 修复）。
        val s = status.trim().lowercase(Locale.ROOT)
        if (s == "completed" || s == "played" || s == "done") return "completed"
        if (s == "playing" || s == "current") return "playing"
        return "unplayed"
    }

    internal fun nvl(value: String?): String {
        return value ?: ""
    }

    internal fun ensureSingleChangedRow(
        db: SQLiteDatabase,
        message: String,
    ) {
        val changed = db.rawQuery("SELECT changes()", null)
        changed.use {
            if (!it.moveToFirst() || it.getInt(0) != 1) throw IllegalStateException(message)
        }
    }
}
