package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apps.theme.AppleTypography
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy

/** Shared draft value for dashboard and future home-content settings surfaces. */
internal data class HomeContentSettings(
    val mode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
    val manualGameIds: List<Long> = emptyList(),
    val source: SpotlightSource = SpotlightSource.RECENT,
    val strategy: SpotlightStrategy = SpotlightStrategy.FIRST,
    val count: Int = MAX_COUNT,
) {
    fun normalized(): HomeContentSettings = copy(
        manualGameIds = HomeContentSettingsPolicy.normalizeManualIds(manualGameIds),
        source = source.takeUnless { it == SpotlightSource.FIXED } ?: SpotlightSource.RECENT,
        count = count.coerceIn(MIN_COUNT, MAX_COUNT),
    )

    companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 5
    }
}

internal enum class HomeContentSettingsLayout {
    SINGLE_COLUMN,
    TWO_COLUMNS,
}

/** Pure sizing and ordered-selection rules used by the shared settings panel. */
internal object HomeContentSettingsPolicy {
    const val TWO_COLUMN_MIN_CONTENT_WIDTH_DP = 600f
    const val CHOICE_CARD_MIN_WIDTH_DP = 280f
    const val CHOICE_CARD_GAP_DP = 10f
    const val LARGE_FONT_SCALE = 1.3f

    fun layoutFor(widthDp: Float, fontScale: Float = 1f): HomeContentSettingsLayout {
        val availableCardWidth = (widthDp - CHOICE_CARD_GAP_DP) / 2f
        val requiredCardWidth = CHOICE_CARD_MIN_WIDTH_DP * fontScale.coerceAtLeast(1f)
        return if (
            widthDp >= TWO_COLUMN_MIN_CONTENT_WIDTH_DP &&
                fontScale < LARGE_FONT_SCALE &&
                availableCardWidth >= requiredCardWidth
        ) {
            HomeContentSettingsLayout.TWO_COLUMNS
        } else {
            HomeContentSettingsLayout.SINGLE_COLUMN
        }
    }

    fun normalizeManualIds(ids: List<Long>): List<Long> =
        ids.filter { it > 0L }.distinct().take(HomeContentSettings.MAX_COUNT)

    /** Selected games are presented in saved order, followed by the remaining library. */
    fun orderedManualGames(games: List<Game>, ids: List<Long>): List<Game> {
        val byId = games.associateBy(Game::id)
        val selected = normalizeManualIds(ids).mapNotNull(byId::get)
        val selectedIds = selected.mapTo(HashSet()) { it.id }
        return selected + games.filterNot { it.id in selectedIds }
    }

    fun toggleManualId(ids: List<Long>, gameId: Long): List<Long> {
        val normalized = normalizeManualIds(ids)
        return if (gameId in normalized) {
            normalized.filterNot { it == gameId }
        } else {
            (normalized + gameId).take(HomeContentSettings.MAX_COUNT)
        }
    }

    fun moveManualId(ids: List<Long>, from: Int, to: Int): List<Long> {
        val normalized = normalizeManualIds(ids)
        if (from !in normalized.indices || to !in normalized.indices) return normalized
        val moved = normalized.toMutableList().apply {
            add(to, removeAt(from))
        }
        return moved
    }
}

internal fun DashboardWidgetSpec.toHomeContentSettings(): HomeContentSettings = HomeContentSettings(
    mode = spotlightSelectionMode,
    manualGameIds = spotlightGameIds,
    source = spotlightSource,
    strategy = spotlightStrategy,
    count = spotlightMaxPages,
).normalized()

private data class HomeContentChoice<T>(
    val value: T,
    val label: String,
    val description: String,
)

/**
 * Reusable settings body. This body owns the only scroll container below the fixed sheet title.
 */
@Composable
internal fun HomeContentSettingsPanel(
    settings: HomeContentSettings,
    games: List<Game>,
    colors: KireiColors,
    onSettingsChange: (HomeContentSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val current = settings.normalized()

    fun update(next: HomeContentSettings) {
        onSettingsChange(next.normalized())
    }

    BoxWithConstraints(
        modifier.fillMaxWidth().fillMaxHeight().testTag("home-content-settings"),
    ) {
        val layout = HomeContentSettingsPolicy.layoutFor(
            widthDp = maxWidth.value,
            fontScale = density.fontScale,
        )
        val navigationBarBottom = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = navigationBarBottom + 24.dp,
            ),
        ) {
            item(key = "selection-settings-section") {
                Column {
                    HomeContentSettingsSectionTitle("选择方式", colors)
                    HomeContentChoiceGrid(
                        layout = layout,
                        options = listOf(
                            HomeContentChoice(
                                SpotlightSelectionMode.MANUAL,
                                "手动选择",
                                "按保存的顺序展示 1-5 个游戏",
                            ),
                            HomeContentChoice(
                                SpotlightSelectionMode.AUTOMATIC,
                                "自动推荐",
                                "按来源与随机策略生成",
                            ),
                        ),
                        selected = current.mode,
                        colors = colors,
                        onSelected = { update(current.copy(mode = it)) },
                    )
                }
            }

            if (current.mode == SpotlightSelectionMode.MANUAL) {
                item(key = "manual-settings-section") {
                    Column {
                        HomeContentSettingsSectionTitle("展示顺序", colors)
                        Text(
                            "点击游戏加入或移除；选中后可调整顺序。",
                            color = colors.secondary,
                            style = AppleTypography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp),
                        )
                        if (games.isEmpty()) {
                            Text(
                                "当前没有可选择的游戏。",
                                color = colors.secondary,
                                style = AppleTypography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            )
                        } else {
                            HomeContentSettingsPolicy.orderedManualGames(games, current.manualGameIds).forEach { game ->
                                val index = current.manualGameIds.indexOf(game.id)
                                HomeContentManualGameRow(
                                    game = game,
                                    selectedIndex = index,
                                    selectedCount = current.manualGameIds.size,
                                    colors = colors,
                                    onToggle = {
                                        update(current.copy(
                                            mode = SpotlightSelectionMode.MANUAL,
                                            manualGameIds = HomeContentSettingsPolicy.toggleManualId(current.manualGameIds, game.id),
                                        ))
                                    },
                                    onMove = { to ->
                                        update(current.copy(
                                            mode = SpotlightSelectionMode.MANUAL,
                                            manualGameIds = HomeContentSettingsPolicy.moveManualId(current.manualGameIds, index, to),
                                        ))
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "source-settings-section") {
                    Column {
                        HomeContentSettingsSectionTitle("内容来源", colors)
                        HomeContentChoiceGrid(
                            layout = layout,
                            options = listOf(
                                HomeContentChoice(
                                    SpotlightSource.RECENT,
                                    "最近打开",
                                    "优先显示最近实际启动过的游戏",
                                ),
                                HomeContentChoice(
                                    SpotlightSource.FAVORITES,
                                    "收藏",
                                    "只从收藏游戏中选择",
                                ),
                                HomeContentChoice(
                                    SpotlightSource.ALL,
                                    "全部游戏",
                                    "从当前游戏库中选择",
                                ),
                            ),
                            selected = current.source,
                            colors = colors,
                            onSelected = { update(current.copy(source = it)) },
                        )
                    }
                }
                item(key = "strategy-settings-section") {
                    Column {
                        HomeContentSettingsSectionTitle("展示方式", colors)
                        HomeContentChoiceGrid(
                            layout = layout,
                            options = listOf(
                                HomeContentChoice(
                                    SpotlightStrategy.FIRST,
                                    "按顺序",
                                    "最近打开按时间排序，其余来源使用稳定顺序",
                                ),
                                HomeContentChoice(
                                    SpotlightStrategy.RANDOM_EACH_LAUNCH,
                                    "每次启动随机",
                                    "本次打开 KireiBox 期间保持不变",
                                ),
                                HomeContentChoice(
                                    SpotlightStrategy.DAILY_RANDOM,
                                    "每日随机",
                                    "每天自动切换一次并保持稳定",
                                ),
                            ),
                            selected = current.strategy,
                            colors = colors,
                            onSelected = { update(current.copy(strategy = it)) },
                        )
                    }
                }
            }

            item(key = "count-settings-row") {
                HomeContentCountRow(
                    count = current.count,
                    colors = colors,
                    onCountChange = { update(current.copy(count = it)) },
                )
            }
        }
    }
}

@Composable
private fun HomeContentSettingsSectionTitle(label: String, colors: KireiColors) {
    Text(
        label,
        color = colors.secondary,
        style = AppleTypography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
private fun <T> HomeContentChoiceGrid(
    layout: HomeContentSettingsLayout,
    options: List<HomeContentChoice<T>>,
    selected: T,
    colors: KireiColors,
    onSelected: (T) -> Unit,
) {
    if (layout == HomeContentSettingsLayout.TWO_COLUMNS) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.chunked(2).forEach { rowOptions ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowOptions.forEach { option ->
                        HomeContentChoiceCard(
                            option = option,
                            selected = option.value == selected,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelected(option.value) },
                        )
                    }
                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                HomeContentChoiceCard(
                    option = option,
                    selected = option.value == selected,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelected(option.value) },
                )
            }
        }
    }
}

@Composable
private fun <T> HomeContentChoiceCard(
    option: HomeContentChoice<T>,
    selected: Boolean,
    colors: KireiColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    GlassSurface(
        colors = colors,
        haze = null,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    option.label,
                    color = colors.text,
                    style = AppleTypography.bodyLargeEmphasis,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                HomeContentSelectionIndicator(selected, colors)
            }
            Text(
                option.description,
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun HomeContentManualGameRow(
    game: Game,
    selectedIndex: Int,
    selectedCount: Int,
    colors: KireiColors,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit,
) {
    val selected = selectedIndex >= 0
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .heightIn(min = 66.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameCoverArtwork(game, Modifier.size(38.dp, 50.dp), ContentScale.Crop, 9.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                game.title.orEmpty().ifBlank { "未命名游戏" },
                color = colors.text,
                style = AppleTypography.bodyLargeEmphasis,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selected) "第 ${selectedIndex + 1} 位" else game.engine?.displayName.orEmpty(),
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            IconButton(
                onClick = { onMove(selectedIndex - 1) },
                enabled = selectedIndex > 0,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "上移",
                    tint = if (selectedIndex > 0) colors.secondary else colors.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(
                onClick = { onMove(selectedIndex + 1) },
                enabled = selectedIndex < selectedCount - 1,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "下移",
                    tint = if (selectedIndex < selectedCount - 1) colors.secondary else colors.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        HomeContentSelectionIndicator(selected, colors)
    }
}

@Composable
private fun HomeContentSelectionIndicator(selected: Boolean, colors: KireiColors) {
    Box(
        Modifier.size(22.dp).background(
            if (selected) colors.accent else colors.outline,
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已选择",
                tint = colors.onAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun HomeContentCountRow(
    count: Int,
    colors: KireiColors,
    onCountChange: (Int) -> Unit,
) {
    val current = count.coerceIn(HomeContentSettings.MIN_COUNT, HomeContentSettings.MAX_COUNT)
    Row(
        Modifier.fillMaxWidth()
            .testTag("home-content-settings-count")
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "展示数量",
                color = colors.text,
                style = AppleTypography.bodyLargeEmphasis,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "最多轮播 $current 个游戏",
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onCountChange((current - 1).coerceAtLeast(HomeContentSettings.MIN_COUNT)) },
            enabled = current > HomeContentSettings.MIN_COUNT,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.Remove,
                contentDescription = "减少展示数量",
                tint = if (current > HomeContentSettings.MIN_COUNT) colors.secondary else colors.outline,
            )
        }
        Text(
            "$current",
            color = colors.text,
            style = AppleTypography.headlineSmall,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 24.dp).padding(horizontal = 8.dp),
        )
        IconButton(
            onClick = { onCountChange((current + 1).coerceAtMost(HomeContentSettings.MAX_COUNT)) },
            enabled = current < HomeContentSettings.MAX_COUNT,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = "增加展示数量",
                tint = if (current < HomeContentSettings.MAX_COUNT) colors.secondary else colors.outline,
            )
        }
    }
}
