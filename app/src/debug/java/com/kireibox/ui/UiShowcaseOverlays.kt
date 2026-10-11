package com.kireibox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiMenu
import com.kireibox.designsystem.components.KireiMenuItem
import com.kireibox.designsystem.components.KireiMenuItemSpec
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import dev.chrisbanes.haze.HazeState

// Sheet, dialog, menu and notice demos of the debug component showcase.

private val SortOptions = listOf("最近游玩", "添加时间", "名称", "自定义")

@Composable
internal fun ShowcaseOverlays(
    colors: KireiColors,
    overlay: ShowcaseOverlay,
    onOverlay: (ShowcaseOverlay) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortOptions.first()) }
    KireiListGroup(colors, Modifier.padding(bottom = KireiSpacing.groupGap), title = "弹层", footer = "当前：${overlay.name.lowercase()}") {
        item {
            Column(Modifier.fillMaxWidth().padding(KireiSpacing.groupInner), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                    ShowcaseOverlayButton("底部面板", colors, Modifier.weight(1f)) { onOverlay(ShowcaseOverlay.SHEET) }
                    ShowcaseOverlayButton("对话框", colors, Modifier.weight(1f)) { onOverlay(ShowcaseOverlay.DIALOG) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                    ShowcaseOverlayButton("确认对话框", colors, Modifier.weight(1f)) { onOverlay(ShowcaseOverlay.CONFIRM) }
                    ShowcaseOverlayButton("通知", colors, Modifier.weight(1f)) { onOverlay(ShowcaseOverlay.NOTICE) }
                }
                Box {
                    KireiButton(
                        "排序：$sort",
                        colors,
                        options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                    ) { menuOpen = true }
                    KireiMenu(menuOpen, { menuOpen = false }, colors, Modifier.widthIn(min = 180.dp)) {
                        SortOptions.forEach { option ->
                            KireiMenuItem(KireiMenuItemSpec(option, selected = option == sort), colors) {
                                sort = option
                                menuOpen = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowcaseOverlayButton(
    label: String,
    colors: KireiColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    KireiButton(label, colors, modifier, KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.MEDIUM), onClick)
}

/** Top-bar overflow menu: Artemis-style version list with a supporting line and the selected check. */
@Composable
internal fun ShowcaseTopBarMenu(
    colors: KireiColors,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf("自动") }
    Box {
        IconButton(onClick = { onExpandedChange(true) }) { Icon(Icons.Rounded.MoreHoriz, "更多", tint = colors.accent) }
        KireiMenu(expanded, { onExpandedChange(false) }, colors, Modifier.widthIn(min = 220.dp, max = 280.dp)) {
            listOf("自动" to "自动探测并保留兼容回退", "3.20" to "固定使用 Artemis 3.20", "3.30" to "固定使用 Artemis 3.30").forEach { (label, supporting) ->
                KireiMenuItem(KireiMenuItemSpec(label, supporting, selected = label == selected), colors) {
                    selected = label
                    onExpandedChange(false)
                }
            }
            KireiMenuItem(KireiMenuItemSpec("移出游戏库", icon = Icons.Rounded.DeleteOutline, destructive = true), colors) { onExpandedChange(false) }
        }
    }
}

/** Sheet, dialogs and the notice, layered over the showcase page. */
@Composable
internal fun ShowcaseModalLayers(
    colors: KireiColors,
    haze: HazeState?,
    overlay: ShowcaseOverlay,
    onDismiss: () -> Unit,
) {
    when (overlay) {
        ShowcaseOverlay.SHEET -> ShowcaseSheet(colors, onDismiss)
        ShowcaseOverlay.DIALOG -> ShowcaseRenameDialog(colors, onDismiss)
        ShowcaseOverlay.CONFIRM ->
            KireiDialog(
                KireiDialogSpec(
                    title = "移除扫描目录？",
                    message = "将从游戏库移出该目录下的 3 部游戏。不会删除游戏文件、封面或游玩记录；重新添加目录后可恢复原有卡片和历史。",
                    confirm = KireiDialogAction("移除目录与游戏", onDismiss, style = KireiButtonStyle.DESTRUCTIVE),
                    dismiss = KireiDialogAction("取消", onDismiss, style = KireiButtonStyle.PLAIN),
                ),
                colors,
                onDismiss,
            )
        ShowcaseOverlay.NOTICE -> Notice("封面已保存", NoticeTone.SUCCESS, colors, haze, onDismiss)
        ShowcaseOverlay.MENU, ShowcaseOverlay.NONE -> Unit
    }
}

@Composable
private fun ShowcaseSheet(
    colors: KireiColors,
    onDismiss: () -> Unit,
) {
    KireiActionSheet("千恋＊万花", colors, onDismiss) {
        Text(
            "内部存储 / Galgame / Senren Banka",
            color = colors.secondary,
            style = AppleTypography.labelMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.xs),
        )
        SheetActionRow(Icons.Rounded.PlayArrow, "启动游戏", "KRKR · data.xp3", colors, prominent = true, onClick = onDismiss)
        SheetActionRow(Icons.Rounded.Tune, "KRKR 游戏设置", "渲染、性能、FPS 和字体", colors, trailing = Icons.Rounded.ExpandMore, onClick = onDismiss)
        Spacer(Modifier.height(KireiSpacing.xxs))
        SheetActionRow(Icons.Rounded.Edit, "游戏名称", "只修改 KireiBox 中的显示名称", colors, onClick = onDismiss)
        SheetActionRow(Icons.Rounded.CollectionsBookmark, "游戏封面", "搜索并选择新的封面", colors, onClick = onDismiss)
        Spacer(Modifier.height(KireiSpacing.xxs))
        SheetActionRow(Icons.Rounded.DeleteOutline, "移出游戏库", "保留游戏文件和游玩记录", colors, destructive = true, onClick = onDismiss)
        SheetCancelButton(colors, onDismiss)
    }
}

@Composable
private fun ShowcaseRenameDialog(
    colors: KireiColors,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("千恋＊万花") }
    KireiDialog(
        KireiDialogSpec(
            title = "修改游戏名称",
            message = "Senren＊Banka",
            confirm = KireiDialogAction("保存", onDismiss, enabled = value.isNotBlank()),
            dismiss = KireiDialogAction("取消", onDismiss, style = KireiButtonStyle.PLAIN),
        ),
        colors,
        onDismiss,
    ) {
        KireiTextField(value, { value = it }, colors, options = KireiTextFieldOptions(label = "游戏名称", placeholder = "输入新的游戏名称"))
    }
}
