package com.kireibox.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.sync.PlaytimeGame
import com.yuki.yukihub.sync.PlaytimeSyncProblem
import com.yuki.yukihub.sync.PlaytimeSyncStatus

// 同步游玩时长 (doc §5.6): one row on the NextMoe account card and the overview's 上次同步 tile lead
// here; the page holds the switch, 立即同步, and the games that wait for the user.

internal const val PLAYTIME_SYNC_TITLE = "同步游玩时长"

/** 「3 分钟前」 for the overview tile and the status line; 「未同步」 before the first sync. */
internal fun lastSyncLabel(
    lastSyncAt: Long,
    now: Long,
): String {
    val minutes = ((now - lastSyncAt).coerceAtLeast(0L) / 60_000L)
    return when {
        lastSyncAt <= 0L -> "未同步"
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        minutes < 24 * 60 -> "${minutes / 60} 小时前"
        else -> "${minutes / (24 * 60)} 天前"
    }
}

/**
 * The single status line: what needs the user first, then what is happening, then the result.
 * Null while sync is off (the switch already says so).
 */
internal fun playtimeSyncLine(
    status: PlaytimeSyncStatus,
    connected: Boolean,
    now: Long,
): String? {
    val summary = status.summary
    return when {
        !status.enabled -> null
        !connected -> "登录 NextMoe 后开始同步"
        status.problem == PlaytimeSyncProblem.NEEDS_LOGIN -> "需要重新登录 NextMoe"
        summary.needsConfirm > 0 -> "${summary.needsConfirm} 款待确认 · 已同步 ${summary.synced} 款"
        summary.rejected > 0 -> "${summary.rejected} 款未能同步 · 已同步 ${summary.synced} 款"
        status.problem == PlaytimeSyncProblem.QUOTA -> "今日额度已用完，明天继续"
        status.problem == PlaytimeSyncProblem.RATE_LIMITED -> "请求过于频繁，稍后继续"
        status.problem == PlaytimeSyncProblem.NETWORK -> "暂时无法连接，稍后重试"
        else -> playtimeSyncProgressLine(status, now)
    }
}

/** Nothing needs the user: what is happening, or how the last sync ended. */
private fun playtimeSyncProgressLine(
    status: PlaytimeSyncStatus,
    now: Long,
): String {
    val summary = status.summary
    return when {
        status.running -> "正在同步…"
        summary.waiting > 0 -> "${summary.waiting} 款等待上传"
        status.lastSyncAt <= 0 -> "尚未同步"
        // Nothing played for a minute yet: there is nothing to count, only that NextMoe is current.
        summary.synced == 0 -> "已是最新 · ${lastSyncLabel(status.lastSyncAt, now)}"
        else -> "已同步 ${summary.synced} 款 · ${lastSyncLabel(status.lastSyncAt, now)}"
    }
}

/** What the 同步数据 button's color says. */
internal enum class SyncButtonTone { OFF, IDLE, RUNNING, SYNCED, ATTENTION, FAILED }

internal fun syncButtonTone(
    status: PlaytimeSyncStatus,
    connected: Boolean,
): SyncButtonTone =
    when {
        !connected || !status.enabled || status.problem == PlaytimeSyncProblem.NOT_CONNECTED -> SyncButtonTone.OFF
        status.running -> SyncButtonTone.RUNNING
        status.problem != null -> SyncButtonTone.FAILED
        playtimeAttentionCount(status) > 0 -> SyncButtonTone.ATTENTION
        status.lastSyncAt > 0 && status.summary.waiting == 0 -> SyncButtonTone.SYNCED
        else -> SyncButtonTone.IDLE
    }

/** Games the user has to act on: a work to confirm or pick, or a refusal to resolve. */
internal fun playtimeAttentionCount(status: PlaytimeSyncStatus): Int = status.summary.needsConfirm + status.summary.rejected

internal fun KireiUiState.nextmoeConnected(): Boolean = coverAccounts[CoverSourceMode.NEXTMOE]?.let { it.connected && !it.needsReauth } == true

/** Why NextMoe refused a write, in words. */
internal fun playtimeRejectionReason(code: String): String =
    when (code) {
        "NOT_FOUND", "HTTP_404" -> "NextMoe 上找不到这部作品"
        "VALIDATION_FAILED", "HTTP_422" -> "NextMoe 拒绝了这个时长"
        else -> "NextMoe 拒绝写入（$code）"
    }

/** The row under the NextMoe account card: status as its subtitle, a badge while games wait. */
@Composable
internal fun NextmoePlaytimeSyncRow(
    state: KireiUiState,
    colors: KireiColors,
    onOpen: () -> Unit,
) {
    val status = state.playtimeSync
    val attention = playtimeAttentionCount(status)
    KireiListRow(
        KireiListRowSpec(
            title = PLAYTIME_SYNC_TITLE,
            subtitle = playtimeSyncLine(status, state.nextmoeConnected(), System.currentTimeMillis()),
            icon = Icons.Rounded.Sync,
            tile = KireiTileColor.BLUE,
            trailing = if (attention > 0) KireiRowTrailing.Badge(attention) else KireiRowTrailing.Value(if (status.enabled) "已开启" else "已关闭"),
        ),
        colors,
        onClick = onOpen,
    )
}

@Composable
internal fun PlaytimeSyncSettings(context: SettingsPageContext) {
    val state = context.state
    val colors = context.colors
    val viewModel = context.viewModel
    val status = state.playtimeSync
    val connected = state.nextmoeConnected()
    LaunchedEffect(Unit) { viewModel.loadPlaytimeAttention() }
    // Coming back from the cover picker (选择作品): sync at once so the pick takes effect.
    var pickerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.coverSearch == null) {
        if (state.coverSearch != null) {
            pickerWasOpen = true
        } else if (pickerWasOpen) {
            pickerWasOpen = false
            viewModel.syncPlaytimeNow()
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing) {
        item {
            SettingsToggleRow(PLAYTIME_SYNC_TITLE, status.enabled, colors, subtitle = playtimeSyncLine(status, connected, System.currentTimeMillis())) {
                viewModel.setPlaytimeSyncEnabled(it)
            }
        }
        item {
            val title =
                when {
                    !connected -> "登录 NextMoe"
                    status.running -> "正在同步…"
                    else -> "立即同步"
                }
            KireiListRow(
                KireiListRowSpec(title = title, tone = KireiRowTone.ACTION, enabled = !status.running && (status.enabled || !connected)),
                colors,
                onClick = { if (connected) viewModel.syncPlaytimeNow() else viewModel.openSettingsPage(KireiSettingsPage.COVER_ACCOUNTS) },
            )
        }
    }
    val games = state.playtimeAttention.filter { it.row?.rejected == null }
    val refused = state.playtimeAttention.filter { it.row?.rejected != null }
    if (games.isNotEmpty()) {
        KireiListGroup(colors, SettingsGroupSpacing, title = "待确认（${games.size}）") {
            games.forEach { game -> item(key = game.gameId) { PlaytimeConfirmRow(game, state, colors, viewModel) } }
        }
    }
    if (refused.isNotEmpty()) {
        KireiListGroup(colors, SettingsGroupSpacing, title = "未能同步（${refused.size}）") {
            refused.forEach { game ->
                item(key = game.gameId) {
                    val row = PlaytimeRow(game, state, playtimeRejectionReason(game.row?.rejected.orEmpty()), KireiRowTrailing.Chevron)
                    PlaytimeGameRow(row, colors) { viewModel.pickPlaytimeWork(game.gameId) }
                }
            }
        }
    }
}

/** 「是这部」 binds the suggested work; tapping the row picks another one in the cover picker. */
@Composable
private fun PlaytimeConfirmRow(
    game: PlaytimeGame,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val candidate = game.row?.candidateId
    val trailing =
        if (candidate == null) {
            KireiRowTrailing.Chevron
        } else {
            KireiRowTrailing.Custom {
                KireiButton(
                    "是这部",
                    colors,
                    Modifier.semantics { contentDescription = "确认 ${game.title} 是 ${game.row?.candidateTitle.orEmpty()}" },
                    options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL),
                ) { viewModel.confirmPlaytimeWork(game.gameId, candidate) }
            }
        }
    val subtitle = if (candidate == null) "没有找到对应作品 · 点按选择" else "NextMoe：${game.row?.candidateTitle?.ifBlank { null } ?: "作品 $candidate"}"
    PlaytimeGameRow(PlaytimeRow(game, state, subtitle, trailing), colors) { viewModel.pickPlaytimeWork(game.gameId) }
}

/** One listed game: its library entry (for the cover thumbnail and current title), what to say and what to show trailing. */
private class PlaytimeRow(
    game: PlaytimeGame,
    state: KireiUiState,
    val subtitle: String,
    val trailing: KireiRowTrailing,
) {
    val libraryGame = state.games.firstOrNull { it.id == game.gameId }
    val title = libraryGame?.title ?: game.title
}

@Composable
private fun PlaytimeGameRow(
    row: PlaytimeRow,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val libraryGame = row.libraryGame
    KireiListRow(
        KireiListRowSpec(
            title = row.title,
            subtitle = row.subtitle,
            leading = libraryGame?.let { { SettingsCoverThumbnail(it, colors) } },
            reserveIconSpace = libraryGame == null,
            tileSize = SettingsCoverThumbWidth,
            trailing = row.trailing,
            subtitleMaxLines = 2,
        ),
        colors,
        onClick = onClick,
    )
}
