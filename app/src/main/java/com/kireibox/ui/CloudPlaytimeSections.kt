package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.sync.CloudPlaytimeWork
import com.yuki.yukihub.sync.PlaytimeGame

private fun syncResultLabel(result: String?): String =
    when (result) {
        "PULLED" -> "已导入"
        "UPLOADED" -> "已上传"
        "IN_SYNC" -> "已同步"
        "WAITING_UPLOAD" -> "待上传"
        "SKIPPED_REMOTE_HIGHER" -> "保留手动调整值"
        "MERGED" -> "作品已合并，等待重试"
        else -> if (result?.startsWith("FAILED:") == true) "写入失败（${result.substringAfter(':')}）" else "等待同步"
    }

private fun minutesLabel(minutes: Long) = "${minutes / 60}小时${minutes % 60}分钟"

internal fun cloudPlaytimeSubtitle(
    work: CloudPlaytimeWork,
    game: PlaytimeGame?
): String {
    val cloud = "云端 ${minutesLabel(work.minutes.toLong())}"
    return when {
        game == null -> "$cloud · 选择本地游戏"
        work.match == "BOUND" && work.minutes == game.localMinutes && game.row?.rejected == null -> "$cloud · 已同步"
        else -> "$cloud · 本机 ${minutesLabel(game.localMinutes.toLong())} · ${syncResultLabel(game.row?.lastResult)}"
    }
}

@Composable
internal fun CloudPlaytimeSections(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel
) {
    val cloud = state.cloudPlaytimes
    var selecting by remember { mutableStateOf<String?>(null) }
    val candidates =
        state.playtimeAttention.filter {
                game ->
            game.row?.candidateId != null && game.row?.lastResult == "TITLE_SUGGESTED" && cloud.count {
                it.match == "SUGGESTED" && it.gameId == game.gameId
            } == 1 && state.playtimeLibrary.count { com.yuki.yukihub.sync.NextmoePlaytimeSync.normalizedTitle(it.title) == com.yuki.yukihub.sync.NextmoePlaytimeSync.normalizedTitle(game.title) } == 1
        }
    KireiListGroup(colors, SettingsGroupSpacing, title = "云端时长") {
        item {
            KireiListRow(
                KireiListRowSpec(
                    title = "云端 ${minutesLabel(cloud.sumOf { it.minutes.toLong() })}",
                    subtitle = "本机 ${minutesLabel(
                        state.playtimeLibrary.sumOf { it.localMinutes.toLong() }
                    )} · 已同步 ${state.playtimeSync.summary.synced} · 需要处理 ${playtimeAttentionCount(state.playtimeSync)}"
                ),
                colors
            )
        }
        if (candidates.isNotEmpty()) {
            item {
                KireiListRow(KireiListRowSpec(title = "确认全部同名建议（${candidates.size}）"), colors, onClick = { candidates.forEach { viewModel.confirmPlaytimeWork(it.gameId, it.row!!.candidateId!!) } })
            }
        }
    }
    if (cloud.isEmpty()) {
        KireiListGroup(colors, SettingsGroupSpacing) {
            item { KireiListRow(KireiListRowSpec(title = "暂无云端快照", subtitle = "登录 NextMoe 后点立即同步；无需先添加游戏。"), colors) }
        }
    }
    listOf(
        "需要处理" to
            cloud.filter {
                it.match == "SUGGESTED"
            },
        "云端有、本机没有" to cloud.filter { it.match == "NONE" },
        "已绑定" to cloud.filter { it.match == "BOUND" },
        "已忽略" to cloud.filter { it.match == "IGNORED" }
    ).forEach {
            (title, works) ->
        if (works.isNotEmpty()) {
            KireiListGroup(colors, SettingsGroupSpacing, title = "$title（${works.size}）") {
                works.forEach { work ->
                    item(key = work.workId) {
                        val game = state.playtimeLibrary.firstOrNull { it.gameId == work.gameId }
                        KireiListRow(
                            KireiListRowSpec(
                                title = work.metadata?.chineseTitle?.ifBlank { work.metadata?.originalTitle.orEmpty() }?.ifBlank { "作品 ${work.workId}" } ?: game?.title ?: "作品 ${work.workId}",
                                subtitle = cloudPlaytimeSubtitle(work, game),
                                leading = { CloudWorkArtwork(work, Modifier.size(40.dp, 56.dp), state.settings?.nsfwBlurEnabled != false, colors) }
                            ),
                            colors,
                            onClick = { selecting = work.workId }
                        )
                    }
                }
            }
        }
    }
    if (state.nextmoeConnected()) {
        KireiListGroup(colors, SettingsGroupSpacing, title = "隐私") {
            item {
                if (state.playRecordsPrivate != null) {
                    SettingsToggleRow("游戏记录设为私密", state.playRecordsPrivate, colors, subtitle = "NextMoe 默认公开；此设置影响其他站点读取你的游戏记录。") { viewModel.setPlaytimePrivacy(it) }
                } else {
                    KireiListRow(KireiListRowSpec(title = "读取隐私设置", subtitle = "点击重试"), colors, onClick = { viewModel.loadPlaytimePrivacy() })
                }
            }
        }
    }
    selecting?.let { id ->
        KireiDialog(
            KireiDialogSpec(
                "选择本地游戏",
                dismissible = false,
                confirm =
                    KireiDialogAction("本机没有，忽略", {
                        viewModel.ignoreCloudPlaytime(id)
                        selecting = null
                    }),
                dismiss = KireiDialogAction("取消", { selecting = null }, KireiButtonStyle.PLAIN)
            ),
            colors,
            { selecting = null },
        ) {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(state.games.size) { index ->
                    val game = state.games[index]
                    KireiButton(game.title.orEmpty(), colors, options = KireiButtonOptions(style = KireiButtonStyle.PLAIN)) {
                        viewModel.confirmPlaytimeWork(game.id, id)
                        selecting = null
                    }
                }
            }
        }
    }
}

@Composable
internal fun GamePlaytimeDialog(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    dismiss: () -> Unit
) {
    var hours by remember(game.id) { mutableStateOf("") }
    var minutes by remember(game.id) { mutableStateOf("") }
    var add by remember { mutableStateOf(true) }
    val detail = state.playtimeLibrary.firstOrNull { it.gameId == game.id }
    val total =
        hours.toLongOrNull()?.takeIf { it in 0..1000 }?.let { h ->
            minutes.ifBlank { "0" }.toLongOrNull()?.takeIf { it in 0..59 }?.let { h * 60 + it }
        }?.takeIf { it <= 60000 }
    KireiDialog(
        KireiDialogSpec(
            "游玩时长",
            dismissible = false,
            confirm =
                KireiDialogAction("保存并同步", {
                    total?.let {
                        viewModel.adjustPlaytime(game.id, it.toInt(), add)
                        dismiss()
                    }
                }, enabled = total != null && !state.playtimeSync.running),
            dismiss = KireiDialogAction("取消", dismiss, KireiButtonStyle.PLAIN)
        ),
        colors,
        dismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "本机 ${minutesLabel(
                    detail?.localMinutes?.toLong() ?: game.totalPlayTime / 60000
                )}\n云端 ${minutesLabel(detail?.row?.remoteMinutes?.toLong() ?: 0)}\n其中云端导入 ${minutesLabel((detail?.cloudImportMs ?: 0) / 60000)}",
                color = colors.text,
                style = MaterialTheme.typography.bodyMedium
            )
            if (detail?.row?.lastResult == "SKIPPED_REMOTE_HIGHER") Text("NextMoe 上其他应用记录的值更大，这里只能修改 KireiBox 自己的记录。", color = colors.secondary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KireiButton("补录", colors, options = KireiButtonOptions(style = if (add) KireiButtonStyle.PRIMARY else KireiButtonStyle.SECONDARY)) { add = true }
                KireiButton("设为总时长", colors, options = KireiButtonOptions(style = if (!add) KireiButtonStyle.PRIMARY else KireiButtonStyle.SECONDARY)) { add = false }
            }
            if (!add) Text("设为总时长会替换此游戏的历史会话记录。", color = colors.secondary, style = MaterialTheme.typography.bodySmall)
            val numberKeyboard = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            KireiTextField(hours, { hours = it }, colors, options = KireiTextFieldOptions(label = "小时（0–1000）", keyboardOptions = numberKeyboard))
            KireiTextField(minutes, { minutes = it }, colors, options = KireiTextFieldOptions(label = "分钟（0–59）", keyboardOptions = numberKeyboard))
            if ((detail?.cloudImportMs ?: 0) > 0) {
                KireiButton("撤销云端导入并解绑", colors, options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY)) {
                    viewModel.undoCloudPlaytime(game.id)
                    dismiss()
                }
            }
        }
    }
}

/** A cloud cover is presentation only; image ratings never classify the game's content. */
@Composable
internal fun CloudWorkArtwork(
    work: com.yuki.yukihub.sync.CloudPlaytimeWork,
    modifier: Modifier,
    blurEnabled: Boolean,
    colors: KireiColors
) {
    val url = work.metadata?.coverUrl.orEmpty()
    val image = rememberCover(url, sourceKey = "nextmoe")
    val blur =
        blurEnabled && work.metadata?.let { metadata ->
            com.yuki.yukihub.metadata.policy.NsfwCoverPolicy.shouldAutoMark(
                com.yuki.yukihub.metadata.model.CoverSourceMode.NEXTMOE, metadata
            )
        } == true
    Box(modifier.clip(KireiShapes.sm).background(colors.surface), contentAlignment = Alignment.Center) {
        if (image != null) {
            androidx.compose.foundation.Image(
                image,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blur) Modifier.blur(14.dp) else Modifier)
            )
        } else {
            Icon(Icons.Rounded.Cloud, contentDescription = "云端封面", tint = colors.secondary, modifier = Modifier.size(24.dp))
        }
    }
}
