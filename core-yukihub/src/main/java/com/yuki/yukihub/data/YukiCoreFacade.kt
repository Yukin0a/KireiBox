package com.yuki.yukihub.data

import android.content.Context
import android.net.Uri
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.scanner.GameScanner
import com.yuki.yukihub.scanner.ScanReport
import com.yuki.yukihub.scanner.ScanRequest
import com.yuki.yukihub.settings.YukiSettingsStore

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

    fun importScan(report: ScanReport): List<Game> {
        val imported = ArrayList<Game>()
        for (result in report.results) {
            val rootUri = result.uri?.takeIf(String::isNotBlank) ?: continue
            val game = games.findByIdentity(rootUri, "") ?: Game()
            game.title = result.title?.takeIf(String::isNotBlank) ?: "未命名游戏"
            game.originalTitle = game.originalTitle ?: game.title
            game.engine = result.engine
            game.rootUri = rootUri
            game.entryKey = ""
            game.launchTarget = result.launchTarget?.takeIf(String::isNotBlank) ?: "[游戏目录]"
            game.emulatorPackage = defaultPackage(result.engine?.name)
            game.hidden = false
            val id = if (game.id > 0) {
                games.update(game)
                game.id
            } else {
                games.insertIfNotExists(game)
            }
            games.findById(id)?.let(imported::add)
        }
        return imported
    }

    private fun defaultPackage(engine: String?): String = when (engine) {
        "KIRIKIRI" -> "internal.krkr"
        "ONS" -> "internal.ons"
        "TYRANO" -> "internal.tyrano"
        "ARTEMIS" -> "internal.artemis"
        else -> ""
    }
}
