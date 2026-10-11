package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.components.KireiSegment
import com.yuki.yukihub.settings.HomePresentationMode

@Composable
internal fun ImmersiveEmptyState(
    onChooseDirectory: () -> Unit,
    palette: ImmersivePalette
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("还没有游戏", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text("选择游戏目录后开始你的故事", color = palette.secondary, style = AppleTypography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        ImmersiveEmptyAction("选择目录", palette, onChooseDirectory)
    }
}

/** Primary pill action of an immersive empty state: accent fill, play ink, a real button node. */
@Composable
private fun ImmersiveEmptyAction(
    label: String,
    palette: ImmersivePalette,
    onClick: () -> Unit,
) {
    Text(
        label,
        color = palette.playInk,
        style = AppleTypography.bodyMediumEmphasis,
        modifier =
            Modifier.padding(top = 20.dp)
                .heightIn(min = 48.dp)
                .clip(CircleShape)
                .background(palette.accent)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 14.dp),
    )
}

/**
 * 最近游玩 with no history yet. The library may well have games, so the state offers the gallery
 * editor (change the source to 全部, or pick games) instead of a dead end.
 */
@Composable
internal fun ImmersiveNoPlayHistoryState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("暂无游玩记录", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "游玩任意游戏后，这里会按最近游玩展示游戏",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction(IMMERSIVE_CHANGE_GALLERY_LABEL, palette, onOpenEditor)
    }
}

/** The empty states' way out: the 编辑沉浸式展示图 editor, where the source or the games change. */
internal const val IMMERSIVE_CHANGE_GALLERY_LABEL = "更换展示内容"

@Composable
internal fun ImmersiveSelectGamesState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("请选择游戏", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        // The action opens the gallery editor (编辑沉浸式展示图), not Settings: say so.
        Text(
            "选择要在沉浸式首页展示的游戏",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction("选择游戏", palette, onOpenEditor)
    }
}

/** 收藏 with no favorites: says how the gallery fills and offers the gallery editor. */
@Composable
internal fun ImmersiveNoFavoritesState(
    palette: ImmersivePalette,
    onOpenEditor: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KireiMascot(Modifier.size(112.dp).clip(KireiShapes.lg), contentDescription = null)
        Text("暂无收藏", color = palette.primary, style = AppleTypography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text(
            "在游戏库收藏游戏后，这里会展示你的收藏",
            color = palette.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(top = 10.dp),
        )
        ImmersiveEmptyAction(IMMERSIVE_CHANGE_GALLERY_LABEL, palette, onOpenEditor)
    }
}

/** 首页样式: two options, so an inline segmented control (spec §8) instead of a sheet. */
@Composable
internal fun HomePresentationModeRow(
    value: HomePresentationMode,
    colors: KireiColors,
    onChange: (HomePresentationMode) -> Unit,
) {
    Box(Modifier.testTag("home-mode-current")) {
        SettingsSegmentedRow(
            SettingsSegmentedRowSpec(
                title = "首页样式",
                segments = HomePresentationMode.entries.map { KireiSegment(it, it.displayName) },
                selected = value,
            ),
            colors,
            onChange,
        )
    }
}
