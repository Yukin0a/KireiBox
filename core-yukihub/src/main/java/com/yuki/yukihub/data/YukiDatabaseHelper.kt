package com.yuki.yukihub.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yuki.yukihub.settings.YukiSettingsStore
import com.yuki.yukihub.storage.StorageRootIdentity

class YukiDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        const val DB_NAME = "kireibox.db"
        // Keep this monotonic for the production package.  The launcher now uses the
        // same applicationId as existing installs; lowering this value makes SQLite
        // reject those installs before the launcher can load any data.
        const val DB_VERSION = 20
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Foreign-key declarations are otherwise only documentation in SQLite.  This
        // must run before every create/upgrade/open, not only when the schema changes.
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE games (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "original_title TEXT," +
                "engine TEXT NOT NULL," +
                "root_uri TEXT NOT NULL," +
                "root_uri_key TEXT NOT NULL DEFAULT ''," +
                "entry_key TEXT NOT NULL DEFAULT ''," +
                "cover_uri TEXT," +
                "cover_persist_uri TEXT," +
                "cover_source_type INTEGER DEFAULT 0," +
                "vndb_binding_mode TEXT NOT NULL DEFAULT 'NONE'," +
                "vndb_match_score REAL NOT NULL DEFAULT 0," +
                "emulator_package TEXT," +
                "launch_target TEXT DEFAULT 'data.xp3'," +
                "description TEXT," +
                "tags TEXT," +
                "play_status TEXT DEFAULT 'unplayed'," +
                "total_play_time INTEGER DEFAULT 0," +
                "last_played_at INTEGER DEFAULT 0," +
                "playtime_reset_at INTEGER DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "hidden INTEGER DEFAULT 0," +
                "favorite INTEGER DEFAULT 0," +
                "manual_order INTEGER NOT NULL DEFAULT 0" +
                ",location_verified_at INTEGER NOT NULL DEFAULT 0" +
                ")")
        db.execSQL("CREATE TABLE play_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "game_id INTEGER NOT NULL," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER," +
                "duration INTEGER DEFAULT 0," +
                "launched_at INTEGER NOT NULL DEFAULT 0," +
                "launch_type TEXT," +
                "session_uuid TEXT," +
                "device_id TEXT," +
                "created_at INTEGER DEFAULT 0," +
                "updated_at INTEGER DEFAULT 0," +
                "dirty INTEGER DEFAULT 1," +
                "deleted INTEGER DEFAULT 0," +
                "FOREIGN KEY(game_id) REFERENCES games(id) ON DELETE CASCADE" +
                ")")
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT)")
        createMetadataCacheTable(db)
        try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_play_sessions_uuid ON play_sessions(session_uuid)") } catch (ignored: Exception) {}
        createGameIdentityIndex(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN cover_persist_uri TEXT")
            safeAlter(db, "ALTER TABLE games ADD COLUMN cover_source_type INTEGER DEFAULT 0")
            safeAlter(db, "ALTER TABLE games ADD COLUMN launch_target TEXT DEFAULT 'data.xp3'")
        }
        if (oldVersion < 3) {
            createMetadataCacheTable(db)
        }
        if (oldVersion < 4) {
            upgradePlaySessionsForSync(db)
        }
        if (oldVersion < 5) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN play_status TEXT DEFAULT 'unplayed'")
        }
        if (oldVersion < 7) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN playtime_reset_at INTEGER DEFAULT 0")
        }
        if (oldVersion < 11) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN favorite INTEGER DEFAULT 0")
        }
        if (oldVersion < 12) {
            upgradeMetadataCachePrimaryKey(db)
        }
        if (oldVersion < 13) {
            upgradeRootUriKeysAndIntegrity(db)
        }
        if (oldVersion < 16) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN vndb_binding_mode TEXT NOT NULL DEFAULT 'NONE'")
            safeAlter(db, "ALTER TABLE games ADD COLUMN vndb_match_score REAL NOT NULL DEFAULT 0")
        }
        if (oldVersion < 17) {
            upgradeExplicitEntryIdentity(db)
        }
        if (oldVersion < 18) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN location_verified_at INTEGER NOT NULL DEFAULT 0")
            createGameIdentityIndex(db)
        }
        if (oldVersion < 19) {
            safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN launched_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "UPDATE play_sessions SET launched_at=COALESCE(end_time,start_time,0) " +
                    "WHERE IFNULL(duration,0)>0 AND launched_at=0"
            )
        }
        if (oldVersion < 20) {
            safeAlter(db, "ALTER TABLE games ADD COLUMN manual_order INTEGER NOT NULL DEFAULT 0")
            val gameIdRemap = upgradeCanonicalStorageIdentity(db)
            YukiSettingsStore(appContext).remapImmersiveGalleryIds(gameIdRemap)
            backfillManualOrder(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 15 was produced by a temporary build and did not introduce a
        // schema change.  Allow returning to the checked-in v14 build without
        // deleting the user's game library or crashing during startup.
        if (
            (oldVersion == 15 && newVersion == 14) ||
            (oldVersion == 19 && newVersion == 18) ||
            (oldVersion == 20 && newVersion == 19)
        ) {
            return
        }
        super.onDowngrade(db, oldVersion, newVersion)
    }

    private fun createMetadataCacheTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS metadata_cache (" +
                "game_id INTEGER NOT NULL," +
                "source TEXT NOT NULL," +
                "source_id TEXT," +
                "json TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(game_id, source)" +
                ")")
    }

    private fun upgradeMetadataCachePrimaryKey(db: SQLiteDatabase) {
        try {
            db.beginTransaction()
            db.execSQL("CREATE TABLE IF NOT EXISTS metadata_cache_new (" +
                    "game_id INTEGER NOT NULL," +
                    "source TEXT NOT NULL," +
                    "source_id TEXT," +
                    "json TEXT NOT NULL," +
                    "updated_at INTEGER NOT NULL," +
                    "PRIMARY KEY(game_id, source)" +
                    ")")
            db.execSQL("INSERT OR REPLACE INTO metadata_cache_new(game_id,source,source_id,json,updated_at) " +
                    "SELECT game_id,source,source_id,json,updated_at FROM metadata_cache")
            db.execSQL("DROP TABLE IF EXISTS metadata_cache")
            db.execSQL("ALTER TABLE metadata_cache_new RENAME TO metadata_cache")
            db.setTransactionSuccessful()
        } catch (ignored: Exception) {
            try { createMetadataCacheTable(db) } catch (ignored2: Exception) {}
        } finally {
            try { db.endTransaction() } catch (ignored3: Exception) {}
        }
    }

    private fun upgradePlaySessionsForSync(db: SQLiteDatabase) {
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN session_uuid TEXT")
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN device_id TEXT")
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN created_at INTEGER DEFAULT 0")
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN updated_at INTEGER DEFAULT 0")
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN dirty INTEGER DEFAULT 1")
        safeAlter(db, "ALTER TABLE play_sessions ADD COLUMN deleted INTEGER DEFAULT 0")
        try { db.execSQL("UPDATE play_sessions SET session_uuid=lower(hex(randomblob(16))) WHERE session_uuid IS NULL OR session_uuid='' ") } catch (ignored: Exception) {}
        try { db.execSQL("UPDATE play_sessions SET created_at=start_time WHERE created_at IS NULL OR created_at=0") } catch (ignored: Exception) {}
        try { db.execSQL("UPDATE play_sessions SET updated_at=COALESCE(end_time,start_time) WHERE updated_at IS NULL OR updated_at=0") } catch (ignored: Exception) {}
        try { db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_play_sessions_uuid ON play_sessions(session_uuid)") } catch (ignored: Exception) {}
    }

    /**
     * Gives URI identity a database-level representation.  Older releases compared
     * normalized URI strings in Java, which left a check-then-insert race and could
     * not protect imports from duplicate cards.
     */
    private fun upgradeRootUriKeysAndIntegrity(db: SQLiteDatabase) {
        safeAlter(db, "ALTER TABLE games ADD COLUMN root_uri_key TEXT NOT NULL DEFAULT ''")
        db.beginTransaction()
        try {
            // Foreign keys were not enabled in previous app versions, so repair old
            // orphan rows before the new enforcement becomes observable.
            db.execSQL("DELETE FROM play_sessions WHERE NOT EXISTS (SELECT 1 FROM games WHERE games.id=play_sessions.game_id)")

            val canonicalByKey = HashMap<String, Long>()
            val cursor = db.rawQuery(
                    "SELECT id,root_uri,total_play_time,last_played_at,created_at,updated_at,hidden,favorite " +
                            "FROM games ORDER BY updated_at DESC,id DESC", null)
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val key = GameRepository.normalizeRootUriKey(it.getString(1))
                    db.execSQL("UPDATE games SET root_uri_key=? WHERE id=?", arrayOf<Any>(key, id))
                    if (key.isNotEmpty()) {
                        val canonicalId = canonicalByKey[key]
                        if (canonicalId == null) {
                            canonicalByKey[key] = id
                        } else {
                            mergeDuplicateGame(db, canonicalId, id)
                        }
                    }
                }
            }
            createRootUriKeyIndex(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Moves dependent data before deleting an historical duplicate URI row.  */
    private fun mergeDuplicateGame(db: SQLiteDatabase, canonicalId: Long, duplicateId: Long) {
        db.execSQL("INSERT OR REPLACE INTO metadata_cache(game_id,source,source_id,json,updated_at) " +
                "SELECT ?,d.source,d.source_id,d.json,d.updated_at FROM metadata_cache d " +
                "WHERE d.game_id=? AND NOT EXISTS (SELECT 1 FROM metadata_cache c " +
                "WHERE c.game_id=? AND c.source=d.source AND c.updated_at>=d.updated_at)",
                arrayOf<Any>(canonicalId, duplicateId, canonicalId))
        db.delete("metadata_cache", "game_id=?", arrayOf(duplicateId.toString()))
        db.execSQL(
            "DELETE FROM play_sessions WHERE game_id=? AND IFNULL(session_uuid,'')!='' AND EXISTS (" +
                "SELECT 1 FROM play_sessions c WHERE c.game_id=? AND c.session_uuid=play_sessions.session_uuid)",
            arrayOf<Any>(duplicateId, canonicalId),
        )
        db.execSQL("UPDATE play_sessions SET game_id=? WHERE game_id=?", arrayOf<Any>(canonicalId, duplicateId))
        // A title that differs from the scanner-provided original title represents a
        // user rename.  Prefer it when the oldest survivor still carries its default.
        db.execSQL(
            "UPDATE games SET title=(SELECT title FROM games WHERE id=?), " +
                "original_title=CASE WHEN IFNULL(original_title,'')='' THEN " +
                    "(SELECT original_title FROM games WHERE id=?) ELSE original_title END " +
                "WHERE id=? AND IFNULL((SELECT title FROM games WHERE id=?),'')!='' " +
                "AND IFNULL((SELECT original_title FROM games WHERE id=?),'')!='' " +
                "AND lower(trim((SELECT title FROM games WHERE id=?)))!=" +
                    "lower(trim((SELECT original_title FROM games WHERE id=?))) " +
                "AND (IFNULL(original_title,'')='' OR lower(trim(title))=lower(trim(original_title)))",
            arrayOf<Any>(
                duplicateId, duplicateId, canonicalId, duplicateId, duplicateId,
                duplicateId, duplicateId,
            ),
        )
        db.execSQL(
            "UPDATE games SET " +
                "description=CASE WHEN IFNULL(description,'')='' THEN " +
                    "(SELECT description FROM games WHERE id=?) ELSE description END, " +
                "tags=CASE WHEN IFNULL(tags,'')='' THEN " +
                    "(SELECT tags FROM games WHERE id=?) ELSE tags END WHERE id=?",
            arrayOf<Any>(duplicateId, duplicateId, canonicalId),
        )
        // MANUAL is the durable signal that a user confirmed this cover.  Move all
        // cover fields together so an AUTO survivor cannot retain a mismatched URI.
        db.execSQL(
            "UPDATE games SET cover_uri=(SELECT cover_uri FROM games WHERE id=?), " +
                "cover_persist_uri=(SELECT cover_persist_uri FROM games WHERE id=?), " +
                "cover_source_type=(SELECT cover_source_type FROM games WHERE id=?), " +
                "vndb_match_score=(SELECT vndb_match_score FROM games WHERE id=?) " +
                "WHERE id=? AND (SELECT vndb_binding_mode FROM games WHERE id=?)='MANUAL' " +
                "AND vndb_binding_mode!='MANUAL'",
            arrayOf<Any>(duplicateId, duplicateId, duplicateId, duplicateId, canonicalId, duplicateId),
        )
        // If the survivor has no cover, copy the asset and its provenance atomically.
        db.execSQL(
            "UPDATE games SET cover_uri=(SELECT cover_uri FROM games WHERE id=?), " +
                "cover_persist_uri=(SELECT cover_persist_uri FROM games WHERE id=?), " +
                "cover_source_type=(SELECT cover_source_type FROM games WHERE id=?), " +
                "vndb_match_score=(SELECT vndb_match_score FROM games WHERE id=?) " +
                "WHERE id=? AND IFNULL(cover_uri,'')='' " +
                "AND IFNULL((SELECT cover_uri FROM games WHERE id=?),'')!=''",
            arrayOf<Any>(duplicateId, duplicateId, duplicateId, duplicateId, canonicalId, duplicateId),
        )
        db.execSQL("UPDATE games SET total_play_time=MAX(IFNULL(total_play_time,0),(SELECT IFNULL(total_play_time,0) FROM games WHERE id=?)), " +
                "last_played_at=MAX(IFNULL(last_played_at,0),(SELECT IFNULL(last_played_at,0) FROM games WHERE id=?)), " +
                "created_at=MIN(IFNULL(created_at,0),(SELECT IFNULL(created_at,0) FROM games WHERE id=?)), " +
                "updated_at=MAX(IFNULL(updated_at,0),(SELECT IFNULL(updated_at,0) FROM games WHERE id=?)), " +
                "hidden=MIN(IFNULL(hidden,0),(SELECT IFNULL(hidden,0) FROM games WHERE id=?)), " +
                "favorite=MAX(IFNULL(favorite,0),(SELECT IFNULL(favorite,0) FROM games WHERE id=?)), " +
                "manual_order=CASE WHEN IFNULL(manual_order,0)<=0 THEN " +
                    "(SELECT IFNULL(manual_order,0) FROM games WHERE id=?) " +
                    "WHEN (SELECT IFNULL(manual_order,0) FROM games WHERE id=?)<=0 THEN manual_order " +
                    "ELSE MIN(manual_order,(SELECT manual_order FROM games WHERE id=?)) END, " +
                "root_uri=CASE WHEN (SELECT root_uri FROM games WHERE id=?) LIKE 'content://%' " +
                    "THEN (SELECT root_uri FROM games WHERE id=?) ELSE root_uri END, " +
                "cover_uri=CASE WHEN IFNULL(cover_uri,'')='' THEN " +
                    "(SELECT cover_uri FROM games WHERE id=?) ELSE cover_uri END, " +
                "cover_persist_uri=CASE WHEN IFNULL(cover_persist_uri,'')='' THEN " +
                    "(SELECT cover_persist_uri FROM games WHERE id=?) ELSE cover_persist_uri END, " +
                "vndb_binding_mode=CASE " +
                    "WHEN vndb_binding_mode='MANUAL' THEN vndb_binding_mode " +
                    "WHEN (SELECT vndb_binding_mode FROM games WHERE id=?)='MANUAL' THEN 'MANUAL' " +
                    "WHEN vndb_binding_mode='AUTO' THEN vndb_binding_mode " +
                    "WHEN (SELECT vndb_binding_mode FROM games WHERE id=?)='AUTO' THEN 'AUTO' " +
                    "ELSE vndb_binding_mode END " +
                "WHERE id=?",
                arrayOf<Any>(
                    duplicateId, duplicateId, duplicateId, duplicateId, duplicateId, duplicateId,
                    duplicateId, duplicateId, duplicateId, duplicateId, duplicateId, duplicateId,
                    duplicateId, duplicateId, duplicateId, canonicalId,
                ))
        db.delete("games", "id=?", arrayOf(duplicateId.toString()))
    }

    /** Re-keys file and SAF representations before rebuilding the unique game identity. */
    private fun upgradeCanonicalStorageIdentity(db: SQLiteDatabase): Map<Long, Long> {
        val gameIdRemap = LinkedHashMap<Long, Long>()
        db.beginTransaction()
        try {
            db.execSQL("DROP INDEX IF EXISTS idx_games_identity")
            db.execSQL("DROP INDEX IF EXISTS idx_games_root_uri_key")
            val canonicalByIdentity = LinkedHashMap<String, Long>()
            db.rawQuery(
                "SELECT id,root_uri,IFNULL(entry_key,'') FROM games ORDER BY created_at ASC,id ASC",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val rootKey = StorageRootIdentity.key(cursor.getString(1))
                    val entryKey = GameRepository.normalizeEntryKey(cursor.getString(2))
                    if (rootKey.isEmpty()) continue
                    val identity = "$rootKey#entry=$entryKey"
                    val canonicalId = canonicalByIdentity[identity]
                    if (canonicalId == null) {
                        canonicalByIdentity[identity] = id
                        db.execSQL("UPDATE games SET root_uri_key=? WHERE id=?", arrayOf<Any>(rootKey, id))
                    } else {
                        mergeDuplicateGame(db, canonicalId, id)
                        gameIdRemap[id] = canonicalId
                    }
                }
            }
            createGameIdentityIndex(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return gameIdRemap
    }

    private fun backfillManualOrder(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            var position = 1024L
            db.rawQuery(
                "SELECT id FROM games ORDER BY favorite DESC,last_played_at DESC,created_at DESC," +
                    "title COLLATE NOCASE ASC,id ASC",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    db.execSQL(
                        "UPDATE games SET manual_order=? WHERE id=? AND IFNULL(manual_order,0)<=0",
                        arrayOf<Any>(position, cursor.getLong(0)),
                    )
                    position += 1024L
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createRootUriKeyIndex(db: SQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_games_root_uri_key ON games(root_uri_key) WHERE root_uri_key != ''")
    }

    /**
     * Explicit file imports may share one SAF directory.  Preserve every existing
     * directory card as entry_key='', then move uniqueness to directory + entry.
     */
    private fun upgradeExplicitEntryIdentity(db: SQLiteDatabase) {
        safeAlter(db, "ALTER TABLE games ADD COLUMN entry_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS idx_games_root_uri_key")
        createGameIdentityIndex(db)
    }

    private fun createGameIdentityIndex(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_games_identity " +
                "ON games(root_uri_key,entry_key) WHERE root_uri_key != ''"
        )
    }

    private fun safeAlter(db: SQLiteDatabase, sql: String) {
        try { db.execSQL(sql) } catch (ignored: Exception) {}
    }
}
