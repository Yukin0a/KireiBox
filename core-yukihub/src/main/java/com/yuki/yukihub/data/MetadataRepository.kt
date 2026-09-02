package com.yuki.yukihub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.metadata.VnMetadata
import com.yuki.yukihub.metadata.CoverSourceMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * 元数据缓存仓库，操作 metadata_cache 表。
 * 提供 YukiHub 五个来源的 get/save/clear，以及 JSON 导入导出（含冲突检测）。
 */
class MetadataRepository(context: Context) {

    private val helper = YukiDatabaseHelper(context.applicationContext)

    fun getVndb(gameId: Long): VnMetadata? = getMetadata(gameId, "vndb")

    fun saveVndb(gameId: Long, data: VnMetadata?) = saveMetadata(gameId, "vndb", data)

    fun saveVndb(gameId: Long, data: VnMetadata?, db: SQLiteDatabase) =
        saveMetadata(gameId, "vndb", data, db)

    fun getBangumi(gameId: Long): VnMetadata? = getMetadata(gameId, "bangumi")

    fun saveBangumi(gameId: Long, data: VnMetadata?) = saveMetadata(gameId, "bangumi", data)

    fun getBangumiMirror(gameId: Long): VnMetadata? = getMetadata(gameId, "bangumi_mirror")

    fun saveBangumiMirror(gameId: Long, data: VnMetadata?) = saveMetadata(gameId, "bangumi_mirror", data)

    fun getYmgal(gameId: Long): VnMetadata? = getMetadata(gameId, "ymgal")

    fun saveYmgal(gameId: Long, data: VnMetadata?) = saveMetadata(gameId, "ymgal", data)

    fun getHikarinagi(gameId: Long): VnMetadata? = getMetadata(gameId, "hikarinagi")

    fun get(gameId: Long, source: CoverSourceMode): VnMetadata? = getMetadata(gameId, source.key)

    fun saveHikarinagi(gameId: Long, data: VnMetadata?) = saveMetadata(gameId, "hikarinagi", data)

    /** Returns the source whose cached metadata was most recently bound or refreshed. */
    fun getMostRecentlyUpdatedSource(gameId: Long): String {
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT source FROM metadata_cache WHERE game_id=? " +
                    "AND source IN ('vndb','bangumi','bangumi_mirror','ymgal','hikarinagi') " +
                    "ORDER BY updated_at DESC LIMIT 1",
            arrayOf(gameId.toString())
        )
        return c.use { if (it.moveToFirst()) it.getString(0) else "" }
    }

    @Throws(Exception::class)
    fun exportMetadataJson(): JSONArray {
        val arr = JSONArray()
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT m.game_id,g.root_uri,g.entry_key,m.source,m.source_id,m.json,m.updated_at " +
                    "FROM metadata_cache m LEFT JOIN games g ON g.id=m.game_id " +
                    "ORDER BY m.updated_at ASC",
            null
        )
        c.use {
            while (it.moveToNext()) {
                val o = JSONObject()
                o.put("game_local_id", it.getLong(0))
                o.put("game_root_uri", it.getString(1))
                o.put("game_entry_key", it.getString(2) ?: "")
                o.put("source", it.getString(3))
                o.put("source_id", it.getString(4))
                o.put("json", it.getString(5))
                o.put("updated_at", it.getLong(6))
                arr.put(o)
            }
        }
        return arr
    }

    @Throws(Exception::class)
    fun importMetadataJson(arr: JSONArray?): Int =
        importMetadataJson(helper.writableDatabase, arr)

    @Throws(Exception::class)
    fun importMetadataJson(db: SQLiteDatabase, arr: JSONArray?): Int {
        if (arr == null) return 0
        var changed = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val rootUri = o.optString("game_root_uri", "")
            var gameId = findGameIdByIdentity(db, rootUri, o.optString("game_entry_key", ""))
            if (gameId <= 0 && rootUri.trim().isEmpty()) {
                gameId = findGameIdByLocalId(db, o.optLong("game_local_id", -1))
            }
            val source = o.optString("source", "")
            val json = o.optString("json", "")
            if (gameId <= 0 || source.isEmpty() || json.isEmpty()) continue
            val incomingUpdatedAt = o.optLong("updated_at", System.currentTimeMillis())

            // 冲突检测：如果本地已有更新版本则跳过
            val shouldSkip = db.rawQuery(
                "SELECT updated_at FROM metadata_cache WHERE game_id=? AND source=? LIMIT 1",
                arrayOf(gameId.toString(), source)
            ).use { it.moveToFirst() && incomingUpdatedAt < it.getLong(0) }
            if (shouldSkip) continue

            val v = ContentValues().apply {
                put("game_id", gameId)
                put("source", source)
                put("source_id", o.optString("source_id", ""))
                put("json", json)
                put("updated_at", incomingUpdatedAt)
            }
            if (db.insertWithOnConflict("metadata_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE) <= 0) {
                throw IllegalStateException("导入元数据失败: game=$gameId, source=$source")
            }
            changed++
        }
        return changed
    }

    fun clearVndb(gameId: Long) = clearMetadata(gameId, "vndb")

    fun clearBangumi(gameId: Long) = clearMetadata(gameId, "bangumi")

    fun clearBangumiMirror(gameId: Long) = clearMetadata(gameId, "bangumi_mirror")

    fun clearYmgal(gameId: Long) = clearMetadata(gameId, "ymgal")

    fun clearHikarinagi(gameId: Long) = clearMetadata(gameId, "hikarinagi")

    // ===== private helpers =====

    private fun getMetadata(gameId: Long, source: String): VnMetadata? {
        val db = helper.readableDatabase
        val c = db.rawQuery(
            "SELECT json FROM metadata_cache WHERE game_id=? AND source=? LIMIT 1",
            arrayOf(gameId.toString(), source)
        )
        return c.use {
            if (it.moveToFirst()) VnMetadata.fromJson(it.getString(0)) else null
        }
    }

    private fun saveMetadata(gameId: Long, source: String, data: VnMetadata?) {
        saveMetadata(gameId, source, data, helper.writableDatabase)
    }

    private fun saveMetadata(
        gameId: Long,
        source: String,
        data: VnMetadata?,
        db: SQLiteDatabase
    ) {
        if (gameId <= 0 || data == null) return
        val v = ContentValues().apply {
            put("game_id", gameId)
            put("source", source)
            put("source_id", data.id)
            put("json", data.toJson().toString())
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("metadata_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun clearMetadata(gameId: Long, source: String) {
        val db = helper.writableDatabase
        db.delete("metadata_cache", "game_id=? AND source=?", arrayOf(gameId.toString(), source))
    }

    private fun findGameIdByRootUri(db: SQLiteDatabase, rootUri: String?): Long {
        return findGameIdByIdentity(db, rootUri, "")
    }

    private fun findGameIdByIdentity(db: SQLiteDatabase, rootUri: String?, entryKey: String?): Long {
        if (rootUri == null || rootUri.trim().isEmpty()) return -1
        val key = GameRepository.normalizeRootUriKey(rootUri)
        if (key.isEmpty()) return -1
        val entry = GameRepository.normalizeEntryKey(entryKey)
        val c = db.rawQuery(
            "SELECT id FROM games WHERE root_uri_key=? AND entry_key=? LIMIT 1",
            arrayOf(key, entry)
        )
        return c.use { if (it.moveToFirst()) it.getLong(0) else -1 }
    }

    private fun findGameIdByLocalId(db: SQLiteDatabase, localId: Long): Long {
        if (localId <= 0) return -1
        val c = db.rawQuery("SELECT id FROM games WHERE id=? LIMIT 1", arrayOf(localId.toString()))
        return c.use { if (it.moveToFirst()) it.getLong(0) else -1 }
    }
}
