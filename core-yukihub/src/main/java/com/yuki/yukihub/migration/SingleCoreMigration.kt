package com.yuki.yukihub.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.YukiDatabaseHelper
import com.yuki.yukihub.settings.YukiSettingsStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One-time, fail-closed migration from the alpha side database. */
object SingleCoreMigration {
    data class Result(val success: Boolean, val migratedRoots: Int, val migratedOptions: Int, val backup: File?, val message: String)

    @Synchronized
    fun run(context: Context): Result {
        val app = context.applicationContext
        val settings = YukiSettingsStore(app)
        val preferences = settings.rawPreferences()
        if (!preferences.getBoolean(KEY_PRODUCTION_SAMPLE_CLEANUP, false)) {
            GameRepository(app).deleteSampleGames()
            preferences.edit().putBoolean(KEY_PRODUCTION_SAMPLE_CLEANUP, true).apply()
        }
        if (preferences.getBoolean(YukiSettingsStore.KEY_MIGRATION_V18, false)) {
            return Result(true, 0, 0, null, "already migrated")
        }
        val mainFile = app.getDatabasePath(YukiDatabaseHelper.DB_NAME)
        val demoFile = app.getDatabasePath(LEGACY_DB)
        val backup = createBackup(app, mainFile, demoFile)
        return try {
            // Opening the helper upgrades the authoritative database to v18 first.
            val repository = GameRepository(app)
            repository.getAll(null)
            var roots = 0
            var options = 0
            if (demoFile.isFile) {
                SQLiteDatabase.openDatabase(demoFile.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
                    val rootUris = ArrayList<String>()
                    val enabled = ArrayList<Boolean>()
                    if (hasTable(legacy, "scan_roots")) {
                        legacy.rawQuery("SELECT tree_uri,enabled FROM scan_roots ORDER BY id", null).use { c ->
                            while (c.moveToNext()) {
                                val uri = c.getString(0)?.trim().orEmpty()
                                if (uri.isNotEmpty() && uri !in rootUris) {
                                    rootUris += uri
                                    enabled += c.getInt(1) != 0
                                    roots++
                                }
                            }
                        }
                    }
                    if (rootUris.isNotEmpty()) settings.replaceScanRoots(rootUris, enabled)

                    if (hasTable(legacy, "game_local_options")) {
                        legacy.rawQuery(
                            "SELECT game_root_uri_key,custom_title,kr_saf_compatibility FROM game_local_options",
                            null
                        ).use { c ->
                            while (c.moveToNext()) {
                                val identity = c.getString(0)?.trim().orEmpty()
                                if (identity.isEmpty()) continue
                                val title = c.getString(1)?.trim().orEmpty()
                                repository.findAnyByRootKey(identity)?.let { game ->
                                    if (title.isNotEmpty()) {
                                        game.title = title
                                        repository.update(game)
                                    }
                                }
                                settings.putBoolean("kr_saf_compatibility_$identity", c.getInt(2) != 0)
                                options++
                            }
                        }
                    }

                    if (hasTable(legacy, "cover_assets")) {
                        legacy.rawQuery("SELECT game_id,local_uri FROM cover_assets WHERE local_uri!=''", null).use { c ->
                            while (c.moveToNext()) {
                                val game = repository.findById(c.getLong(0)) ?: continue
                                if (game.coverUri.isNullOrBlank()) {
                                    game.coverUri = c.getString(1)
                                    game.coverPersistUri = c.getString(1)
                                    game.coverSourceType = 1
                                    repository.update(game)
                                }
                            }
                        }
                    }
                }
            }
            settings.putBoolean(YukiSettingsStore.KEY_MIGRATION_V18, true)
            Result(true, roots, options, backup, "migration complete")
        } catch (t: Throwable) {
            Result(false, 0, 0, backup, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun createBackup(context: Context, main: File, demo: File): File? = runCatching {
        runCatching {
            YukiDatabaseHelper(context).writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = File(context.filesDir, "migration-backups/alpha1-$stamp").apply { mkdirs() }
        listOf(main, File(main.path + "-wal"), File(main.path + "-shm"), demo, File(demo.path + "-wal"), File(demo.path + "-shm"))
            .filter(File::isFile).forEach { it.copyTo(File(dir, it.name), overwrite = false) }
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs/${YukiSettingsStore.PREFS_NAME}.xml")
        if (prefs.isFile) prefs.copyTo(File(dir, prefs.name), overwrite = false)
        dir
    }.getOrNull()

    private fun hasTable(db: SQLiteDatabase, name: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
            .use { it.moveToFirst() }

    private const val LEGACY_DB = "kireibox_demo.db"
    private const val KEY_PRODUCTION_SAMPLE_CLEANUP = "production_sample_cleanup_v1"
}
