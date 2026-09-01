package com.yuki.yukihub.data

import android.content.Context
import android.net.Uri
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.scanner.GameScanner
import com.yuki.yukihub.scanner.ScanReport
import com.yuki.yukihub.scanner.ScanRequest
import com.yuki.yukihub.settings.YukiSettingsStore

data class ScanImportSummary(
    val addedGames: List<Game> = emptyList(),
    val updatedCount: Int = 0,
    val keptHiddenCount: Int = 0,
    val skippedCount: Int = 0,
) {
    val addedCount: Int get() = addedGames.size
    /** Total number of games newly added by this import. Mirrors [List.size] for alpha callers. */
    val size: Int get() = addedGames.size

    operator fun plus(other: ScanImportSummary): ScanImportSummary = ScanImportSummary(
        addedGames = addedGames + other.addedGames,
        updatedCount = updatedCount + other.updatedCount,
        keptHiddenCount = keptHiddenCount + other.keptHiddenCount,
        skippedCount = skippedCount + other.skippedCount,
    )
}

internal object ScanImportVisibilityPolicy {
    fun hiddenAfterRefresh(wasHidden: Boolean, keepRemovedHidden: Boolean): Boolean =
        wasHidden && keepRemovedHidden
}

/** Single entry point used by Compose. UI code never opens SQLite directly. */
class YukiCoreFacade(context: Context) {
    private val app = context.applicationContext
    val games = GameRepository(app)
    val settings = YukiSettingsStore(app)
    val metadata = MetadataRepository(app)

    fun listGames(): List<Game> = games.getAll(
        GameLibraryOrdering.orderByClause(settings.snapshot().sortMode)
    )

    fun scan(root: Uri, depth: Int = settings.snapshot().startupScanDepth): ScanReport =
        GameScanner.scan(app, root, ScanRequest.defaults(depth.coerceIn(1, 4)))

    fun importScan(
        report: ScanReport,
        keepRemovedHidden: Boolean = false,
    ): ScanImportSummary {
        val added = ArrayList<Game>()
        var updated = 0
        var keptHidden = 0
        var skipped = 0
        for (result in report.results) {
            val rootUri = result.uri?.takeIf(String::isNotBlank)
            if (rootUri == null) {
                skipped += 1
                continue
            }
            val game = games.findByIdentity(rootUri, "") ?: Game()
            val wasHidden = game.hidden
            game.title = result.title?.takeIf(String::isNotBlank) ?: "未命名游戏"
            game.originalTitle = game.originalTitle ?: game.title
            game.engine = result.engine
            game.rootUri = rootUri
            game.entryKey = ""
            game.launchTarget = result.launchTarget?.takeIf(String::isNotBlank) ?: "[游戏目录]"
            game.emulatorPackage = defaultPackage(result.engine?.name)
            game.hidden = ScanImportVisibilityPolicy.hiddenAfterRefresh(wasHidden, keepRemovedHidden)
            val id = if (game.id > 0) {
                if (games.update(game) != 1) {
                    skipped += 1
                    continue
                }
                updated += 1
                if (game.hidden) keptHidden += 1
                game.id
            } else {
                val insertedId = games.insertIfNotExists(game)
                ScanImportClassification.addedIdFor(existingId = game.id, insertedId = insertedId)
                    ?.let { newId -> games.findById(newId)?.let(added::add) }
                insertedId
            }
        }
        return ScanImportSummary(
            addedGames = added,
            updatedCount = updated,
            keptHiddenCount = keptHidden,
            skippedCount = skipped,
        )
    }

    private fun defaultPackage(engine: String?): String = when (engine) {
        "KIRIKIRI" -> "internal.krkr"
        "ONS" -> "internal.ons"
        "TYRANO" -> "internal.tyrano"
        "ARTEMIS" -> "internal.artemis"
        else -> ""
    }
}
