package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTileColor

// "添加游戏" action sheet opened from the library toolbar (both header layouts). A row closes the
// sheet first and then opens its flow page.

@Composable
internal fun LibraryAddGameSheet(
    colors: KireiColors,
    onBatch: () -> Unit,
    onSingle: () -> Unit,
    onDismiss: () -> Unit,
) {
    KireiActionSheet("添加游戏", colors, onDismiss, grouped = true) {
        SheetRowGroup(colors) {
            item {
                SheetActionRow(
                    Icons.Rounded.CreateNewFolder,
                    "批量添加",
                    "选择多个文件夹，逐个确认引擎和启动文件",
                    colors,
                    tile = KireiTileColor.BLUE,
                ) {
                    onDismiss()
                    onBatch()
                }
            }
            item {
                SheetActionRow(
                    Icons.Rounded.SportsEsports,
                    "添加单个游戏",
                    "识别游戏目录，或选择引擎、应用、快捷方式",
                    colors,
                    tile = KireiTileColor.ORANGE,
                ) {
                    onDismiss()
                    onSingle()
                }
            }
        }
    }
}
