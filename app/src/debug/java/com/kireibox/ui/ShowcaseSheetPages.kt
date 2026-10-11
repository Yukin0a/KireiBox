package com.kireibox.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.EngineType

internal enum class ShowcaseSheet(val id: String) {
    /** GameActionSheet for a fixture game (`--ei game <index>`, default the first KIRIKIRI game). */
    ACTIONS("sheet-actions"),
    RENAME("sheet-rename"),
    REMOVE("sheet-remove"),
    OVERVIEW("sheet-overview"),
    ADD("sheet-add"),
    ;

    companion object {
        fun fromId(id: String): ShowcaseSheet? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One of the library's sheets / dialogs open over the real library page, as in the app. Dismissing the
 * overlay (scrim, cancel) just closes it; every action button is inert (fixture view model state only).
 */
@Composable
internal fun ShowcaseSheetPage(
    activity: ComponentActivity,
    colors: KireiColors,
    sheet: ShowcaseSheet,
    gameIndex: Int?,
) {
    val games = remember { ShowcaseFixtures.libraryGames(activity) }
    val game = remember(gameIndex) { gameIndex?.let { games.getOrNull(it) } ?: games.first { it.engine == EngineType.KIRIKIRI } }
    ShowcasePageHost(activity, colors, dockTab = KireiTab.GAMES, games = games) { env ->
        ShowcaseLibraryContent(env)
        var open by remember { mutableStateOf(true) }
        var renameText by remember { mutableStateOf(game.title.orEmpty()) }
        var sort by remember { mutableStateOf(LibraryOverviewSort.RECENT) }
        val close = { open = false }
        if (open) {
            when (sheet) {
                ShowcaseSheet.ACTIONS ->
                    GameActionSheet(game, env.state, colors, env.viewModel, env.actions, onDismiss = close, onRename = close, onRemove = close)
                ShowcaseSheet.RENAME -> RenameGameDialog(game, renameText, { renameText = it }, colors, onDismiss = close, onSave = close)
                ShowcaseSheet.REMOVE -> RemoveGameSheet(game, colors, onDismiss = close, onConfirm = close)
                ShowcaseSheet.OVERVIEW -> LibraryOverviewSheet(games, sort, colors, { sort = it }, onDismiss = close, onGame = { close() })
                ShowcaseSheet.ADD -> LibraryAddGameSheet(colors, {}, {}, close)
            }
        }
    }
}
