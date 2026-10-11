package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.yuki.yukihub.BuildConfig

/**
 * Root-page summary: the app mark, version, 同步数据 (NextMoe playtime sync; 快速设置 is a one-time
 * banner and a search result), the 游戏时长 / 游戏数 / 上次同步 tiles and the pending actions.
 * [compact] (landscape) drops the stat tiles so the first settings groups still clear the dock.
 */
@Composable
internal fun SettingsOverviewCard(
    state: KireiUiState,
    colors: KireiColors,
    compact: Boolean,
    actions: SettingsOverviewActions,
) {
    val visibleGames = state.games.count { !it.hidden }
    val totalPlayTime = state.games.filterNot { it.hidden }.sumOf { it.totalPlayTime }
    val pending = state.pendingCoverReviewCount
    val sync = state.playtimeSync
    val syncAttention = playtimeAttentionCount(sync)

    Column(
        Modifier.fillMaxWidth()
            .background(colors.surface, KireiShapes.lg)
            .border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.lg)
            .padding(KireiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(KireiSpacing.sm),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Narrow cards and large text put 同步数据 under the name, so it never clips the version.
            val stacked = overviewStacksAction(maxWidth.value, LocalDensity.current.fontScale)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The brand mark (the launcher tile artwork) instead of a generic settings glyph.
                    KireiMascot(Modifier.size(OverviewMarkSize).clip(KireiShapes.sm), contentDescription = null)
                    Column(Modifier.padding(start = KireiSpacing.sm).weight(1f)) {
                        Text("KireiBox", color = colors.text, style = AppleTypography.headlineSmall)
                        Text(BuildConfig.VERSION_NAME, color = colors.secondary, style = AppleTypography.numericSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!stacked) PlaytimeSyncButton(sync, state.nextmoeConnected(), colors, Modifier.padding(start = KireiSpacing.xs), actions.onSync)
                }
                if (stacked) PlaytimeSyncButton(sync, state.nextmoeConnected(), colors, Modifier.padding(start = OverviewMarkSize + KireiSpacing.sm, top = KireiSpacing.xs), actions.onSync)
            }
        }
        if (!compact) {
            // Equal-height tiles: the tallest tile sets the row, the others stretch to it.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
            ) {
                // 游戏时长 opens the 游玩记录 ranking; 上次同步 opens the sync page.
                OverviewStat("游戏时长", LibraryOverviewPolicy.summaryPlayTime(totalPlayTime), colors, Modifier.weight(1f).fillMaxHeight(), actions.onOpenPlayTime)
                OverviewStat("游戏数", visibleGames.toString(), colors, Modifier.weight(1f).fillMaxHeight())
                OverviewStat("上次同步", lastSyncLabel(sync.lastSyncAt, System.currentTimeMillis()), colors, Modifier.weight(1f).fillMaxHeight(), actions.onOpenSync)
            }
        }
        if (pending > 0) {
            KireiButton(
                label = "查看待确认封面（$pending）",
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
                options =
                    KireiButtonOptions(
                        style = KireiButtonStyle.SECONDARY,
                        size = KireiButtonSize.MEDIUM,
                        icon = Icons.Rounded.CollectionsBookmark,
                    ),
                onClick = actions.onOpenPendingCovers,
            )
        }
        if (syncAttention > 0) {
            KireiButton(
                label = "确认游玩时长同步（$syncAttention）",
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
                options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.MEDIUM, icon = Icons.Rounded.Sync),
                onClick = actions.onOpenSync,
            )
        }
    }
}

private val OverviewMarkSize = 44.dp

/**
 * Below this text-column width (or from 1.6× text) the 同步数据 button moves under the name and
 * version. The threshold is deliberately low so mainstream phones (≥ 320 dp, card inner width
 * ≥ ~250 dp) always keep the button on the title row: only genuinely tiny cards and very large
 * accessibility text stack it, instead of every narrower device model looking different.
 */
private const val OVERVIEW_INLINE_MIN_WIDTH_DP = 240f
private const val OVERVIEW_STACK_FONT_SCALE = 1.6f

internal fun overviewStacksAction(
    widthDp: Float,
    fontScale: Float,
): Boolean = fontScale >= OVERVIEW_STACK_FONT_SCALE || widthDp < OVERVIEW_INLINE_MIN_WIDTH_DP

/**
 * One stat tile. A unit ("0 分钟") is split off into smaller secondary text on the number's
 * baseline, so CJK glyphs never grow the numeric line box and every tile's number shares one
 * baseline. The label is 12sp (10sp was too small for CJK).
 */
@Composable
private fun OverviewStat(
    label: String,
    value: String,
    colors: KireiColors,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val number = value.substringBefore(' ')
    val unit = value.substringAfter(' ', missingDelimiterValue = "")
    Column(
        modifier
            .then(if (onClick != null) Modifier.clip(KireiShapes.md).clickable(role = Role.Button, onClick = onClick) else Modifier)
            // One TalkBack stop per tile, label first: "游戏数 3", not "3" then "游戏数".
            .clearAndSetSemantics { contentDescription = "$label $value" }
            .background(colors.surfaceRaised, KireiShapes.md)
            .padding(horizontal = KireiSpacing.sm, vertical = KireiSpacing.xs),
    ) {
        // Every value line is exactly the numeric line height, so the labels of 「12 小时」「5」「未同步」
        // share one baseline: CJK font metrics and the baseline-aligned unit would otherwise grow it.
        val numericLine = with(LocalDensity.current) { AppleTypography.numericMedium.lineHeight.toDp() }
        val wordValue = number.none(Char::isDigit)
        Row(Modifier.height(numericLine), verticalAlignment = Alignment.CenterVertically) {
            Text(
                number,
                color = colors.text,
                style = AppleTypography.numericMedium,
                maxLines = 1,
                modifier = if (wordValue) Modifier.wrapContentHeight(unbounded = true) else Modifier.alignByBaseline(),
            )
            if (unit.isNotEmpty()) {
                Text(unit, color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = KireiSpacing.xxs).alignByBaseline())
            }
        }
        Text(label, color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1)
    }
}
