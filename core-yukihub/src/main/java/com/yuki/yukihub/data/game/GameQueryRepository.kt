package com.yuki.yukihub.data.game

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.yuki.yukihub.data.GameMapper
import com.yuki.yukihub.data.YukiDatabaseHelper
import com.yuki.yukihub.data.db.GameIdentityKey
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageRootIdentity
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashSet

/**
 * Game row CRUD and lookup queries, split out of `GameRepository`.
 *
 * GameRepository keeps the public API and delegates here, so callers and tests are unchanged.
 */
internal class GameQueryRepository(
    private val helper: YukiDatabaseHelper,
) {
    fun getAll(): List<Game> {
        return getAll("last_played_at DESC, created_at DESC")
    }

    fun getAll(orderBy: String?): List<Game> {
        val list = ArrayList<Game>()
        val db = helper.readableDatabase
        val c =
            db.query(
                "games",
                null,
                "hidden=0",
                null,
                null,
                null,
                if (orderBy == null || orderBy.trim().isEmpty()) "last_played_at DESC, created_at DESC" else orderBy,
            )
        c.use {
            while (it.moveToNext()) list.add(GameMapper.fromCursor(it))
        }
        return list
    }

    fun findById(id: Long): Game? {
        if (id <= 0) return null
        val db = helper.readableDatabase
        val c = db.query("games", null, "id=?", arrayOf(id.toString()), null, null, null)
        return c.use {
            if (it.moveToNext()) GameMapper.fromCursor(it) else null
        }
    }

    fun findAnyByRootKey(rootUriKey: String?): Game? {
        if (rootUriKey.isNullOrBlank()) return null
        val db = helper.readableDatabase
        // Prefer the legacy/scanner identity when a root also owns explicit XP3
        // cards.  Callers using this compatibility lookup should not receive an
        // arbitrary entry card just because SQLite returned it first.
        db.query(
            "games",
            null,
            "root_uri_key=? AND entry_key=''",
            arrayOf(rootUriKey),
            null,
            null,
            "updated_at DESC",
            "1",
        ).use { if (it.moveToFirst()) return GameMapper.fromCursor(it) }
        return db.query(
            "games",
            null,
            "root_uri_key=?",
            arrayOf(rootUriKey),
            null,
            null,
            "updated_at DESC",
            "1",
        ).use { if (it.moveToFirst()) GameMapper.fromCursor(it) else null }
    }

    fun findByIdentity(
        rootUri: String?,
        entryKey: String?,
    ): Game? {
        val rootKey = GameIdentityKey.normalizeRootUriKey(rootUri)
        if (rootKey.isBlank()) return null
        val entry = GameIdentityKey.normalizeEntryKey(entryKey)
        return helper.readableDatabase.query(
            "games",
            null,
            "root_uri_key=? AND entry_key=?",
            arrayOf(rootKey, entry),
            null,
            null,
            "updated_at DESC",
            "1",
        ).use { if (it.moveToFirst()) GameMapper.fromCursor(it) else null }
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
    fun insert(
        game: Game,
        db: SQLiteDatabase,
    ): Long {
        val now = System.currentTimeMillis()
        game.createdAt = if (game.createdAt == 0L) now else game.createdAt
        game.updatedAt = now
        if (game.manualOrder <= 0L) game.manualOrder = GameMapper.nextManualOrder(db, game.favorite)
        val id = db.insert("games", null, GameMapper.toValues(game))
        game.id = id
        return id
    }

    fun existsByRootUri(rootUri: String?): Boolean {
        if (rootUri == null || rootUri.trim().isEmpty()) return false
        val db = helper.readableDatabase
        val key = GameIdentityKey.normalizeRootUriKey(rootUri)
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
        if (game.manualOrder <= 0L) game.manualOrder = GameMapper.nextManualOrder(db, game.favorite)
        val id = db.insertWithOnConflict("games", null, GameMapper.toValues(game), SQLiteDatabase.CONFLICT_IGNORE)
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
        val scannedLeaf = GameIdentityKey.portableRootLeaf(scanned.rootUri)
        val leafMatch = uniqueScannedMatch(games, scanned, scannedLeaf, true)
        if (leafMatch != null) return leafMatch
        return uniqueScannedMatch(games, scanned, GameIdentityKey.normalizeIdentityText(scanned.title), false)
    }

    /** 更新云端恢复卡片的本机扫描路径，保留游玩统计、封面和用户编辑字段。 */
    fun bindScannedLocation(
        existing: Game?,
        scanned: Game?,
    ): Int {
        if (existing == null || existing.id <= 0 || scanned == null) return 0
        val savedKrkrTarget =
            existing.launchTarget?.takeIf {
                (existing.engine == EngineType.KIRIKIRI || scanned.engine == EngineType.KIRIKIRI) &&
                    (it.endsWith(".xp3", ignoreCase = true) || it.endsWith(".exe", ignoreCase = true))
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
        return db.update("games", GameMapper.toValues(game), "id=?", arrayOf(game.id.toString()))
    }

    fun update(
        game: Game,
        db: SQLiteDatabase,
    ): Int {
        game.updatedAt = System.currentTimeMillis()
        return db.update("games", GameMapper.toValues(game), "id=?", arrayOf(game.id.toString()))
    }

    fun gamesUnderRoot(
        rootUri: String,
        includeHidden: Boolean = false,
    ): List<Game> =
        allIncludingHidden.filter { game ->
            (includeHidden || !game.hidden) && StorageRootIdentity.contains(rootUri, game.rootUri)
        }

    fun setGamesHidden(
        ids: Collection<Long>,
        hidden: Boolean,
    ): Int {
        val cleanIds = ids.filter { it > 0L }.distinct()
        if (cleanIds.isEmpty()) return 0
        val db = helper.writableDatabase
        db.beginTransaction()
        return try {
            val values =
                ContentValues().apply {
                    put("hidden", if (hidden) 1 else 0)
                    put("updated_at", System.currentTimeMillis())
                }
            var changed = 0
            cleanIds.forEach { id -> changed += db.update("games", values, "id=?", arrayOf(id.toString())) }
            check(changed == cleanIds.size) { "批量更新游戏库状态失败" }
            db.setTransactionSuccessful()
            changed
        } finally {
            db.endTransaction()
        }
    }

    /** Saves one complete favorite or regular-game group in a single transaction. */
    fun reorderManual(
        favorite: Boolean,
        orderedIds: List<Long>,
    ) {
        val cleanIds = orderedIds.filter { it > 0L }
        require(cleanIds.size == cleanIds.distinct().size) { "手动排序包含重复游戏" }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            cleanIds.forEachIndexed { index, id ->
                val values =
                    ContentValues().apply {
                        put("manual_order", (index + 1L) * 1024L)
                        put("updated_at", System.currentTimeMillis())
                    }
                val changed =
                    db.update(
                        "games",
                        values,
                        "id=? AND favorite=? AND hidden=0",
                        arrayOf(id.toString(), if (favorite) "1" else "0"),
                    )
                check(changed == 1) { "手动排序游戏不存在或跨越收藏分组: $id" }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Saves both visual groups atomically so a failed second group can never leave partial order. */
    fun reorderManualGroups(
        favoriteIds: List<Long>,
        regularIds: List<Long>,
    ) {
        val favorites = favoriteIds.filter { it > 0L }
        val regular = regularIds.filter { it > 0L }
        require(favorites.size == favorites.distinct().size) { "自定义排序的收藏组包含重复游戏" }
        require(regular.size == regular.distinct().size) { "自定义排序的普通组包含重复游戏" }
        require(favorites.none { it in regular.toHashSet() }) { "自定义排序包含跨组重复游戏" }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            fun writeGroup(
                ids: List<Long>,
                favorite: Boolean,
            ) {
                ids.forEachIndexed { index, id ->
                    val values =
                        ContentValues().apply {
                            put("manual_order", (index + 1L) * 1024L)
                            put("updated_at", System.currentTimeMillis())
                        }
                    val changed =
                        db.update(
                            "games",
                            values,
                            "id=? AND favorite=? AND hidden=0",
                            arrayOf(id.toString(), if (favorite) "1" else "0"),
                        )
                    check(changed == 1) { "自定义排序游戏不存在或跨越收藏分组: $id" }
                }
            }
            writeGroup(favorites, favorite = true)
            writeGroup(regular, favorite = false)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendManualOrder(
        gameId: Long,
        favorite: Boolean,
    ): Boolean {
        if (gameId <= 0L) return false
        val values =
            ContentValues().apply {
                put("manual_order", GameMapper.nextManualOrder(helper.readableDatabase, favorite))
                put("updated_at", System.currentTimeMillis())
            }
        return helper.writableDatabase.update("games", values, "id=?", arrayOf(gameId.toString())) == 1
    }

    /**
     * Persists a background cover state only while the game still has no selected/default cover.
     * This prevents a late REVIEW/RETRY callback from replacing a newer manual choice.
     */
    fun updateCoverBindingIfUnconfigured(
        gameId: Long,
        mode: String,
        score: Double,
    ): Boolean {
        if (gameId <= 0L) return false
        val values =
            ContentValues().apply {
                put("vndb_binding_mode", GameMapper.normalizeVndbBindingMode(mode))
                put("vndb_match_score", score.coerceIn(0.0, 100.0))
                put("updated_at", System.currentTimeMillis())
            }
        return helper.writableDatabase.update(
            "games",
            values,
            "id=? AND (cover_uri IS NULL OR TRIM(cover_uri)='') " +
                "AND vndb_binding_mode NOT IN ('MANUAL','AUTO','DISABLED')",
            arrayOf(gameId.toString()),
        ) == 1
    }

    fun loadAllIncludingHidden(): List<Game> = allIncludingHidden

    fun delete(id: Long): Int {
        val db = helper.writableDatabase
        return db.delete("games", "id=?", arrayOf(id.toString()))
    }

    private fun findByRootUri(rootUri: String?): Game? {
        return findByRootUri(helper.readableDatabase, rootUri)
    }

    private fun findByRootUri(
        db: SQLiteDatabase,
        rootUri: String?,
    ): Game? {
        if (rootUri == null || rootUri.trim().isEmpty()) return null
        val key = GameIdentityKey.normalizeRootUriKey(rootUri)
        val c =
            db.query(
                "games",
                null,
                "root_uri_key=? AND entry_key=''",
                arrayOf(key),
                null,
                null,
                "updated_at DESC",
                "1",
            )
        return c.use {
            if (it.moveToFirst()) GameMapper.fromCursor(it) else null
        }
    }

    internal fun findByIdentity(
        db: SQLiteDatabase,
        rootUri: String?,
        entryKey: String?,
    ): Game? {
        val key = GameIdentityKey.normalizeRootUriKey(rootUri)
        if (key.isBlank()) return null
        val entry = GameIdentityKey.normalizeEntryKey(entryKey)
        return db.query(
            "games",
            null,
            "root_uri_key=? AND entry_key=?",
            arrayOf(key, entry),
            null,
            null,
            "updated_at DESC",
            "1",
        ).use { if (it.moveToFirst()) GameMapper.fromCursor(it) else null }
    }

    private val allIncludingHidden: List<Game>
        get() {
            val list = ArrayList<Game>()
            val db = helper.readableDatabase
            val c = db.query("games", null, null, null, null, null, "updated_at DESC")
            c.use {
                while (it.moveToNext()) list.add(GameMapper.fromCursor(it))
            }
            return list
        }

    private fun uniqueScannedMatch(
        games: List<Game>?,
        scanned: Game,
        key: String,
        byLeaf: Boolean,
    ): Game? {
        if (games == null || key.isEmpty()) return null
        var match: Game? = null
        for (candidate in games) {
            if (candidate.entryKey.isNotBlank()) continue
            if (candidate.engine != scanned.engine) continue
            val same: Boolean =
                if (byLeaf) {
                    key == GameIdentityKey.portableRootLeaf(candidate.rootUri)
                } else {
                    key == GameIdentityKey.normalizeIdentityText(candidate.title) ||
                        key == GameIdentityKey.normalizeIdentityText(candidate.originalTitle)
                }
            if (!same) continue
            if (match != null && match.id != candidate.id) return null
            match = candidate
        }
        return match
    }

    internal fun findBySyncIdentity(
        db: SQLiteDatabase,
        o: JSONObject?,
        rootUri: String,
        entryKey: String = GameIdentityKey.normalizeEntryKey(o?.optString("entry_key", "")),
    ): Game? {
        val byRoot = findByIdentity(db, rootUri, entryKey)
        if (byRoot != null) return byRoot
        if (o == null) return null
        return findByTitleForEmptyRoot(db, o.optString("title", "").trim())
            ?.takeIf { GameIdentityKey.normalizeEntryKey(it.entryKey) == GameIdentityKey.normalizeEntryKey(entryKey) }
    }

    private fun findByTitleForEmptyRoot(title: String): Game? {
        return findByTitleForEmptyRoot(helper.readableDatabase, title)
    }

    private fun findByTitleForEmptyRoot(
        db: SQLiteDatabase,
        title: String?,
    ): Game? {
        if (title == null || title.trim().isEmpty()) return null
        val c =
            db.query(
                "games",
                null,
                "IFNULL(root_uri,'')='' AND IFNULL(title,'')=?",
                arrayOf(title.trim()),
                null,
                null,
                "updated_at DESC",
                "1",
            )
        return c.use {
            if (!it.moveToFirst()) null else GameMapper.fromCursor(it)
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
}
