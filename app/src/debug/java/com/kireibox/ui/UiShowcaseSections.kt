package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiBadge
import com.kireibox.designsystem.components.KireiBadgeTone
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiChip
import com.kireibox.designsystem.components.KireiCountBadge
import com.kireibox.designsystem.components.KireiDotBadge
import com.kireibox.designsystem.components.KireiEmptyState
import com.kireibox.designsystem.components.KireiEmptyStateSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiProgressBar
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.kireibox.designsystem.components.KireiSkeleton
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions

// Sections of the debug component showcase (UiShowcaseActivity), in realistic KireiBox copy.

private val sectionSpacing = Modifier.padding(bottom = KireiSpacing.groupGap)

@Composable
internal fun ShowcaseSettingsGroups(colors: KireiColors) {
    var autoUpdate by remember { mutableStateOf(true) }
    var orientation by remember { mutableStateOf("auto") }
    KireiListGroup(colors, sectionSpacing, title = "通用", footer = "开启「自动检查更新」后，KireiBox 每次启动都会在后台检查 GitHub 上的新版本。") {
        item { KireiListRow(KireiListRowSpec("外观", icon = Icons.Rounded.Palette, tile = KireiTileColor.PINK, trailing = KireiRowTrailing.Value("跟随系统")), colors) {} }
        item {
            KireiListRow(
                KireiListRowSpec(
                    "自动检查更新",
                    subtitle = "启动时检查 GitHub 新版本",
                    icon = Icons.Rounded.SystemUpdate,
                    tile = KireiTileColor.BLUE,
                    trailing = KireiRowTrailing.Toggle(autoUpdate) { autoUpdate = it },
                ),
                colors,
            )
        }
        item { KireiListRow(KireiListRowSpec("待确认封面", icon = Icons.Rounded.CollectionsBookmark, tile = KireiTileColor.ORANGE, trailing = KireiRowTrailing.Badge(3)), colors) {} }
        item {
            KireiListRow(
                KireiListRowSpec("存储与权限", subtitle = "已授权 2 个扫描目录", icon = Icons.Rounded.Storage, tile = KireiTileColor.GREEN, trailing = KireiRowTrailing.Chevron),
                colors,
            ) {}
        }
    }
    KireiListGroup(colors, sectionSpacing, title = "屏幕方向", footer = "固定方向后，旋转设备不会改变布局。") {
        item {
            KireiSegmented(
                KireiSegmentedSpec(
                    "屏幕方向",
                    listOf(KireiSegment("auto", "自动"), KireiSegment("portrait", "竖屏"), KireiSegment("landscape", "横屏")),
                    orientation,
                ),
                { orientation = it },
                colors,
                Modifier.padding(KireiSpacing.groupInner),
            )
        }
    }
    KireiListGroup(colors, sectionSpacing, title = "封面") {
        // No icon of its own: it reserves the tile slot so its text lines up with the row below (spec §8).
        item { KireiListRow(KireiListRowSpec("自动匹配来源", trailing = KireiRowTrailing.Value("Bangumi"), reserveIconSpace = true), colors) {} }
        item {
            KireiListRow(
                KireiListRowSpec("清除封面无结果记录", subtitle = "下次扫描时重新搜索封面", icon = Icons.Rounded.DeleteSweep, tone = KireiRowTone.DESTRUCTIVE),
                colors,
            ) {}
        }
    }
}

/** A captioned e1 card holding one free-form demo. */
@Composable
private fun ShowcaseCard(
    title: String,
    colors: KireiColors,
    content: @Composable ColumnScope.() -> Unit,
) {
    KireiListGroup(colors, sectionSpacing, title = title) {
        item {
            Column(Modifier.fillMaxWidth().padding(KireiSpacing.groupInner), verticalArrangement = Arrangement.spacedBy(KireiSpacing.sm), content = content)
        }
    }
}

@Composable
internal fun ShowcaseButtons(colors: KireiColors) {
    ShowcaseCard("按钮", colors) {
        Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            KireiButton("开始游戏", colors, Modifier.weight(1f), KireiButtonOptions(icon = Icons.Rounded.PlayArrow)) {}
            KireiButton("稍后", colors, Modifier.weight(1f), KireiButtonOptions(style = KireiButtonStyle.SECONDARY)) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            KireiButton("了解更多", colors, options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.MEDIUM)) {}
            KireiButton("取消", colors, options = KireiButtonOptions(style = KireiButtonStyle.PLAIN, size = KireiButtonSize.MEDIUM)) {}
            KireiButton("移出", colors, options = KireiButtonOptions(style = KireiButtonStyle.DESTRUCTIVE, size = KireiButtonSize.MEDIUM)) {}
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            KireiButton("小号", colors, options = KireiButtonOptions(size = KireiButtonSize.SMALL)) {}
            KireiButton("中号", colors, options = KireiButtonOptions(size = KireiButtonSize.MEDIUM)) {}
            KireiButton("已停用", colors, options = KireiButtonOptions(size = KireiButtonSize.MEDIUM, enabled = false)) {}
            KireiButton("圆角", colors, options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.MEDIUM, pill = false)) {}
        }
    }
}

@Composable
internal fun ShowcaseChipsAndBadges(colors: KireiColors) {
    var filter by remember { mutableStateOf("全部") }
    ShowcaseCard("芯片与徽章", colors) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            listOf("全部", "KRKR", "Artemis", "Tyrano", "安卓应用").forEach { label ->
                KireiChip(label, filter == label, colors) { filter = label }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            KireiCountBadge(3, colors)
            KireiCountBadge(128, colors)
            KireiBadge("NEW", colors, tone = KireiBadgeTone.ACCENT)
            KireiBadge("KRKR", colors, tone = KireiBadgeTone.NEUTRAL)
            KireiDotBadge(colors)
            KireiDotBadge(colors, tone = KireiBadgeTone.ACCENT)
        }
    }
}

@Composable
internal fun ShowcaseInputs(colors: KireiColors) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("千恋＊万花") }
    ShowcaseCard("输入框", colors) {
        KireiTextField(query, { query = it }, colors, options = KireiTextFieldOptions(placeholder = "搜索游戏", leadingIcon = Icons.Rounded.Search))
        KireiTextField(name, { name = it }, colors, options = KireiTextFieldOptions(label = "游戏名称", placeholder = "输入新的游戏名称"))
    }
}

@Composable
internal fun ShowcaseLoading(colors: KireiColors) {
    val progress by remember { mutableFloatStateOf(.62f) }
    ShowcaseCard("进度与占位", colors) {
        Text("正在下载更新", color = colors.text, style = AppleTypography.bodyMediumEmphasis)
        KireiProgressBar(progress, colors)
        Text("38.4 MB / 61.9 MB · 2.1 MB/秒", color = colors.secondary, style = AppleTypography.numericSmall)
        Row(Modifier.padding(top = KireiSpacing.xs), horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm)) {
            KireiSkeleton(colors, Modifier.size(width = 64.dp, height = 88.dp), KireiShapes.md)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
                KireiSkeleton(colors, Modifier.fillMaxWidth(.8f).height(16.dp), KireiShapes.xs)
                KireiSkeleton(colors, Modifier.fillMaxWidth(.5f).height(12.dp), KireiShapes.xs)
                KireiSkeleton(colors, Modifier.width(72.dp).height(28.dp), KireiShapes.full)
            }
        }
    }
}

@Composable
internal fun ShowcaseEmptyState(
    colors: KireiColors,
    onAction: () -> Unit,
) {
    KireiListGroup(colors, sectionSpacing, title = "空状态") {
        item {
            KireiEmptyState(
                KireiEmptyStateSpec(
                    title = "游戏库还是空的",
                    message = "添加一个游戏目录，KireiBox 会自动识别其中的 KRKR、Artemis 与 Tyrano 游戏。",
                    actionLabel = "添加目录",
                ),
                colors,
                onAction = onAction,
            ) {
                Box(Modifier.size(72.dp).background(colors.accentContainer, KireiShapes.full), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SportsEsports, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}
