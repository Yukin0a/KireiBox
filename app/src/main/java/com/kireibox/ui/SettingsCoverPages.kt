package com.kireibox.ui

import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.MotionMode
import kotlin.math.roundToInt

// 封面与元数据 (plan §5 P3): 「来源与匹配」, one 自动匹配 group (switches + 清除无结果记录), 成人内容
// and 「封面管理」 (待确认 / 已设置: five rows, then 显示更多 adds pages of twenty). No footers or
// subtitles that only repeat a title (r5): a line stays only for a value or a consequence.

/**
 * Plain-language name of the stored coverStrictL2 setting: only lexical evidence (the game name
 * matching the title, or nearly matching with a clear lead) may bind automatically; several sources
 * agreeing on a work no longer does (CoverMatchConfidence).
 */
internal const val COVER_STRICT_MATCH_TITLE = "仅标题一致时自动设置"

@Composable
internal fun CoverSettings(
    context: SettingsPageContext,
    scroll: ScrollState,
) {
    val state = context.state
    val colors = context.colors
    val viewModel = context.viewModel
    var editBangumiBase by remember { mutableStateOf(false) }
    CoverSourceGroup(state, colors, viewModel) { editBangumiBase = true }
    CoverMatchingGroups(state, colors, viewModel)
    CoverManagementGroups(context, scroll)
    if (editBangumiBase) BangumiBaseDialog(state, colors, viewModel) { editBangumiBase = false }
}

/** 来源与匹配: default source (sheet with descriptions), the accounts sub-page and the Bangumi address. */
@Composable
private fun CoverSourceGroup(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    onEditBangumiBase: () -> Unit,
) {
    val source = CoverSourceMode.selected(state.settings?.metadataSource)
    KireiListGroup(colors, SettingsGroupSpacing, title = "来源与匹配") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec(
                    title = "默认来源",
                    options = CoverSourceMode.active.map { SettingsChoiceOption(it, it.label, KireiPublicCopy.coverSourceDescription(it)) },
                    selected = source,
                ),
                colors,
                viewModel::setMetadataSource,
            )
        }
        item {
            KireiListRow(
                KireiListRowSpec(title = "封面来源账号", trailing = KireiRowTrailing.Value(coverAccountSummary(state))),
                colors,
                onClick = { viewModel.openSettingsPage(KireiSettingsPage.COVER_ACCOUNTS) },
            )
        }
        item {
            SettingsAnchor("Bangumi 镜像地址") {
                KireiListRow(KireiListRowSpec(title = "Bangumi 镜像地址", trailing = KireiRowTrailing.Value(bangumiBaseLabel(state))), colors, onClick = onEditBangumiBase)
            }
        }
    }
}

/** 自动匹配: the three switches and the 清除无结果记录 command in one group, then 成人内容. */
@Composable
private fun CoverMatchingGroups(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val settings = state.settings
    KireiListGroup(colors, SettingsGroupSpacing, title = "自动匹配") {
        item {
            SettingsToggleRow("同时查找多个来源", settings?.coverHybridAutoMatch != false, colors, subtitle = "关闭后只查默认来源") {
                viewModel.setCoverHybridAutoMatch(it)
            }
        }
        item {
            SettingsToggleRow("仅 Wi-Fi 自动匹配", settings?.coverAutoMatchWifiOnly == true, colors) {
                viewModel.setCoverAutoMatchWifiOnly(it)
            }
        }
        item {
            SettingsToggleRow(COVER_STRICT_MATCH_TITLE, settings?.coverStrictL2 == true, colors, onCheckedChange = viewModel::setCoverStrictL2)
        }
        item {
            SettingsAnchor("清除无结果记录") {
                KireiListRow(KireiListRowSpec(title = "清除无结果记录", tone = KireiRowTone.ACTION), colors, onClick = viewModel::clearCoverNoMatchStore)
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "成人内容") {
        item { SwitchRow("模糊成人内容封面", settings?.nsfwBlurEnabled != false, colors, viewModel::setNsfwBlurEnabled) }
        item { SwitchRow("搜索结果包含成人作品", state.includeAdult, colors, viewModel::setIncludeAdult) }
    }
}

/** 「已登录 n 个」 style summary of the three account-backed sources. */
internal fun coverAccountSummary(state: KireiUiState): String {
    val statuses = CoverAccountSources.mapNotNull { state.coverAccounts[it] }
    val connected = statuses.count { it.connected && !it.needsReauth }
    return when {
        statuses.any { it.needsReauth } -> "需要重新授权"
        connected > 0 -> "$connected 个已登录"
        else -> "未登录"
    }
}

private fun bangumiBaseLabel(state: KireiUiState): String {
    val base = state.settings?.bangumiApiBase?.takeIf(String::isNotBlank) ?: return "自动"
    return runCatching { Uri.parse(base).host }.getOrNull()?.takeIf(String::isNotBlank) ?: base
}

@Composable
private fun BangumiBaseDialog(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    onClose: () -> Unit,
) {
    var bangumiBaseInput by remember { mutableStateOf(state.settings?.bangumiApiBase.orEmpty()) }
    KireiDialog(
        spec =
            KireiDialogSpec(
                title = "Bangumi 镜像地址",
                message = "留空则自动选择：先试官方 api.bgm.tv，失败再用社区镜像。",
                confirm =
                    KireiDialogAction("保存", {
                        viewModel.setBangumiApiBase(bangumiBaseInput)
                        onClose()
                    }),
                dismiss = KireiDialogAction("取消", onClose, style = KireiButtonStyle.PLAIN),
            ),
        colors = colors,
        onDismissRequest = onClose,
    ) {
        KireiTextField(
            bangumiBaseInput,
            { bangumiBaseInput = it },
            colors,
            options = KireiTextFieldOptions(label = "镜像地址", placeholder = "例如 https://api.bangumi.vip", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)),
        )
    }
}

/**
 * Bangumi signs in with a personal token (令牌 everywhere: card, dialog, field): verified, then
 * stored encrypted; without one, search stays anonymous.
 */
@Composable
internal fun BangumiTokenDialog(
    colors: KireiColors,
    viewModel: KireiViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    KireiDialog(
        spec =
            KireiDialogSpec(
                title = "设置 Bangumi 令牌",
                message = "在 Bangumi 官方页面生成个人令牌，验证后加密保存。",
                confirm =
                    KireiDialogAction("验证并保存", {
                        viewModel.setBangumiToken(token.trim())
                        onClose()
                    }, enabled = token.isNotBlank()),
                dismiss = KireiDialogAction("取消", onClose, style = KireiButtonStyle.PLAIN),
            ),
        colors = colors,
        onDismissRequest = onClose,
    ) {
        KireiButton(
            "打开官方令牌页面",
            colors,
            Modifier.padding(bottom = KireiSpacing.xs),
            KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
        ) { openKireiLink(context, BANGUMI_TOKEN_URL) { viewModel.showNotice("无法打开浏览器") } }
        KireiTextField(token, { token = it }, colors, options = KireiTextFieldOptions(label = "个人令牌", placeholder = "粘贴从官方页面生成的令牌", secret = true))
    }
}

private const val BANGUMI_TOKEN_URL = "https://next.bgm.tv/demo/access-token"

private const val COVER_PREVIEW_LIMIT = 5

/**
 * Rows 显示更多 adds per tap. The page is a plain scrolling column and every row decodes its cover
 * thumbnail, so a large library is revealed in bounded pages instead of composing (and decoding)
 * every game at once.
 */
private const val COVER_PAGE_SIZE = 20

/** How many rows of a 封面管理 list are composed: the preview, then whole pages, never past [total]. */
internal fun coverRowsShown(
    requested: Int,
    total: Int,
): Int = requested.coerceIn(minOf(COVER_PREVIEW_LIMIT, total), total)

/** Cover thumbnails lead the 封面管理 rows (3:4, sm radius); the 显示更多 row reserves the same slot. */
internal val SettingsCoverThumbWidth = 36.dp
private val SettingsCoverThumbHeight = 48.dp

/** A game's cover as a list-row leading thumbnail (engine default artwork when it has none). */
@Composable
internal fun SettingsCoverThumbnail(
    game: Game,
    colors: KireiColors,
) {
    GameCoverArtwork(
        game = game,
        modifier = Modifier.size(SettingsCoverThumbWidth, SettingsCoverThumbHeight).border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.sm),
        contentScale = ContentScale.Crop,
        cornerRadius = KireiShapes.Radius.sm,
        role = CoverImageRole.THUMBNAIL,
        showFallbackMetadata = false,
    )
}

/**
 * 封面管理: 待确认封面 (review-first) and 已设置封面, each showing [COVER_PREVIEW_LIMIT] rows and
 * growing by [COVER_PAGE_SIZE] per 显示更多. When the overview card asked for it, the 待确认 group
 * is scrolled up under the top bar once laid out.
 */
@Composable
private fun CoverManagementGroups(
    context: SettingsPageContext,
    scroll: ScrollState,
) {
    val state = context.state
    // Saveable: the expanded lists survive rotation (the settings page owns a SaveableStateProvider).
    var pendingRequested by rememberSaveable { mutableIntStateOf(COVER_PREVIEW_LIMIT) }
    var configuredRequested by rememberSaveable { mutableIntStateOf(COVER_PREVIEW_LIMIT) }
    val coverGroups = CoverSettingsPolicy.groups(state.games)
    // The overview card's "待确认封面" button lands here: covers awaiting review lead the capped list.
    val pendingFirst =
        remember(coverGroups.pending, state.coverQueue, state.coverTaskGameIds) {
            CoverSettingsPolicy.reviewFirst(coverGroups.pending, reviewTaskGameIds(state.coverQueue), state.coverTaskGameIds)
        }
    var pendingTop by remember { mutableFloatStateOf(-1f) }
    RevealPendingCovers(context, scroll, pendingTop, hasPending = pendingFirst.isNotEmpty())
    CoverGameGroup(
        CoverGameGroupSpec(
            title = "待确认封面",
            games = pendingFirst,
            shown = coverRowsShown(pendingRequested, pendingFirst.size),
            empty = KireiListRowSpec("没有待确认的封面", icon = Icons.Rounded.TaskAlt, tile = KireiTileColor.GREEN),
            onShowMore = { pendingRequested = coverRowsShown(pendingRequested, pendingFirst.size) + COVER_PAGE_SIZE },
            onCollapse = { pendingRequested = COVER_PREVIEW_LIMIT },
        ),
        context,
        SettingsGroupSpacing.onGloballyPositioned { pendingTop = it.positionInParent().y },
    )
    CoverGameGroup(
        CoverGameGroupSpec(
            title = "已设置封面",
            games = coverGroups.configured,
            shown = coverRowsShown(configuredRequested, coverGroups.configured.size),
            empty = KireiListRowSpec("还没有已设置的封面", icon = Icons.Rounded.Image, tile = KireiTileColor.GRAY),
            onShowMore = { configuredRequested = coverRowsShown(configuredRequested, coverGroups.configured.size) + COVER_PAGE_SIZE },
            onCollapse = { configuredRequested = COVER_PREVIEW_LIMIT },
        ),
        context,
        SettingsGroupSpacing,
    )
}

/** Scrolls the 待确认封面 group (at [groupTop] in the page column) up under the top bar, once. */
@Composable
private fun RevealPendingCovers(
    context: SettingsPageContext,
    scroll: ScrollState,
    groupTop: Float,
    hasPending: Boolean,
) {
    val reveal = context.reveal
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    LaunchedEffect(reveal.pendingCovers, groupTop >= 0f) {
        if (!reveal.pendingCovers || groupTop < 0f) return@LaunchedEffect
        withFrameNanos { }
        if (hasPending) {
            val target = groupTop.roundToInt().coerceAtMost(scroll.maxValue)
            if (reducedMotion) scroll.scrollTo(target) else scroll.animateScrollTo(target)
        }
        reveal.consumePendingCovers()
    }
}

/** One 封面管理 list: the first [shown] of [games] and the 显示更多 / 收起列表 row, or the [empty] row while it has no games. */
private data class CoverGameGroupSpec(
    val title: String,
    val games: List<Game>,
    val shown: Int,
    val empty: KireiListRowSpec,
    val onShowMore: () -> Unit,
    val onCollapse: () -> Unit,
) {
    val total: Int get() = games.size
}

@Composable
private fun CoverGameGroup(
    spec: CoverGameGroupSpec,
    context: SettingsPageContext,
    modifier: Modifier,
) {
    val colors = context.colors
    // A search result for this list highlights the group's card (its rows are covers, not settings).
    SettingsGroupAnchor(spec.title, modifier) { cardHighlight ->
        KireiListGroup(colors, title = spec.title) {
            cardModifier = cardHighlight
            if (spec.total == 0) item(key = "cover-list-empty") { KireiListRow(spec.empty, colors) }
            spec.games.take(spec.shown).forEach { game ->
                item(key = game.id) { CoverGameRow(game, context.state, colors) { context.viewModel.openCoverSearch(game) } }
            }
            if (spec.total > COVER_PREVIEW_LIMIT) {
                val more = spec.shown < spec.total
                item(key = "cover-list-toggle") {
                    KireiListRow(
                        KireiListRowSpec(
                            title = if (more) "显示更多（还有 ${spec.total - spec.shown} 个）" else "收起列表",
                            tone = KireiRowTone.ACTION,
                            reserveIconSpace = true,
                            tileSize = SettingsCoverThumbWidth,
                            trailing = KireiRowTrailing.Glyph(if (more) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess),
                        ),
                        colors,
                        onClick = if (more) spec.onShowMore else spec.onCollapse,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverGameRow(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    KireiListRow(
        KireiListRowSpec(
            title = game.title.orEmpty(),
            subtitle = coverBindingStatus(game, state),
            leading = { SettingsCoverThumbnail(game, colors) },
            tileSize = SettingsCoverThumbWidth,
            trailing = KireiRowTrailing.Chevron,
            subtitleMaxLines = 1,
        ),
        colors,
        onClick = onClick,
    )
}

private fun coverBindingStatus(
    game: Game,
    state: KireiUiState,
): String =
    when {
        game.id in state.coverTaskGameIds -> "正在后台匹配…"
        game.vndbBindingMode == "REVIEW" -> "待确认 · 最高匹配 ${game.vndbMatchScore.toInt()}%"
        game.vndbBindingMode == "RETRY" -> "匹配失败 · 点按重试"
        game.vndbBindingMode == "AUTO" -> "已自动匹配 ${game.vndbMatchScore.toInt()}%"
        game.vndbBindingMode == "MANUAL" -> "已手动选择"
        game.vndbBindingMode == "DISABLED" -> "使用引擎默认封面"
        else -> "尚未匹配"
    }
