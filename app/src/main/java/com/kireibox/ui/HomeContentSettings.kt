package com.kireibox.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.ImmersiveGallerySettingsPolicy
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
    fun normalized(): HomeContentSettings =
        copy(
            manualGameIds = HomeContentSettingsPolicy.normalizeManualIds(manualGameIds),
            source = source.takeUnless { it == SpotlightSource.FIXED } ?: SpotlightSource.RECENT,
            count = count.coerceIn(MIN_COUNT, MAX_COUNT),
        )

    companion object {
        const val MIN_COUNT = 1
        const val MAX_COUNT = 5
    }
}

/** Pure ordered-selection rules used by the shared settings panel. */
internal object HomeContentSettingsPolicy {
    const val LARGE_FONT_SCALE = 1.3f

    fun normalizeManualIds(ids: List<Long>): List<Long> = ImmersiveGallerySettingsPolicy.normalizeManualIds(ids)

    /** Selected games are presented in saved order, followed by the remaining library. */
    fun orderedManualGames(
        games: List<Game>,
        ids: List<Long>
    ): List<Game> {
        val byId = games.associateBy(Game::id)
        val selected = normalizeManualIds(ids).mapNotNull(byId::get)
        val selectedIds = selected.mapTo(HashSet()) { it.id }
        return selected + games.filterNot { it.id in selectedIds }
    }

    fun toggleManualId(
        ids: List<Long>,
        gameId: Long
    ): List<Long> = ImmersiveGallerySettingsPolicy.toggleManualId(ids, gameId)

    fun moveManualId(
        ids: List<Long>,
        from: Int,
        to: Int
    ): List<Long> = ImmersiveGallerySettingsPolicy.moveManualId(ids, from, to)
}

internal fun DashboardWidgetSpec.toHomeContentSettings(): HomeContentSettings =
    HomeContentSettings(
        mode = spotlightSelectionMode,
        manualGameIds = spotlightGameIds,
        source = spotlightSource,
        strategy = spotlightStrategy,
        count = spotlightMaxPages,
    ).normalized()

/**
 * Reusable settings body: one captioned group per setting, like the settings pages. This body owns
 * the only scroll container below the fixed sheet title.
 */
@Composable
internal fun HomeContentSettingsPanel(
    settings: HomeContentSettings,
    games: List<Game>,
    colors: KireiColors,
    onSettingsChange: (HomeContentSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = settings.normalized()

    fun update(next: HomeContentSettings) {
        onSettingsChange(next.normalized())
    }

    Box(
        modifier.fillMaxWidth().fillMaxHeight().testTag("home-content-settings"),
    ) {
        val navigationBarBottom =
            WindowInsets.navigationBars
                .asPaddingValues()
                .calculateBottomPadding()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = 16.dp,
                    bottom = navigationBarBottom + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(KireiSpacing.groupGap),
        ) {
            item(key = "selection-settings-section") {
                HomeContentSegmentedGroup(
                    title = "选择方式",
                    segments =
                        listOf(
                            KireiSegment(SpotlightSelectionMode.MANUAL, "手动选择"),
                            KireiSegment(SpotlightSelectionMode.AUTOMATIC, "自动推荐"),
                        ),
                    selected = current.mode,
                    colors = colors,
                    onSelect = { update(current.copy(mode = it)) },
                )
            }

            if (current.mode == SpotlightSelectionMode.MANUAL) {
                item(key = "manual-settings-section") {
                    KireiListGroup(
                        colors,
                        Modifier.padding(horizontal = KireiSpacing.md),
                        title = "展示顺序",
                        footer = "点击游戏加入或移除；选中后可调整顺序。",
                    ) {
                        if (games.isEmpty()) {
                            item { KireiListRow(KireiListRowSpec(title = "当前没有可选择的游戏。"), colors) }
                        } else {
                            HomeContentSettingsPolicy.orderedManualGames(games, current.manualGameIds).forEach { game ->
                                val index = current.manualGameIds.indexOf(game.id)
                                item(key = game.id) {
                                    HomeContentManualGameRow(
                                        game = game,
                                        selectedIndex = index,
                                        selectedCount = current.manualGameIds.size,
                                        colors = colors,
                                        onToggle = {
                                            update(
                                                current.copy(
                                                    mode = SpotlightSelectionMode.MANUAL,
                                                    manualGameIds = HomeContentSettingsPolicy.toggleManualId(current.manualGameIds, game.id),
                                                )
                                            )
                                        },
                                        onMove = { to ->
                                            update(
                                                current.copy(
                                                    mode = SpotlightSelectionMode.MANUAL,
                                                    manualGameIds = HomeContentSettingsPolicy.moveManualId(current.manualGameIds, index, to),
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "source-settings-section") {
                    HomeContentSegmentedGroup(
                        title = "内容来源",
                        segments =
                            listOf(
                                KireiSegment(SpotlightSource.RECENT, "最近打开"),
                                KireiSegment(SpotlightSource.FAVORITES, "收藏"),
                                KireiSegment(SpotlightSource.ALL, "全部游戏"),
                            ),
                        selected = current.source,
                        colors = colors,
                        onSelect = { update(current.copy(source = it)) },
                    )
                }
                item(key = "strategy-settings-section") {
                    // Each option needs its sentence, so these stay radio rows instead of a segmented control.
                    KireiListGroup(colors, Modifier.padding(horizontal = KireiSpacing.md), title = "展示方式") {
                        listOf(
                            HomeContentChoice(SpotlightStrategy.FIRST, "按顺序", "最近打开按时间排序，其余来源使用稳定顺序"),
                            HomeContentChoice(SpotlightStrategy.RANDOM_EACH_LAUNCH, "每次启动随机", "本次打开 KireiBox 期间保持不变"),
                            HomeContentChoice(SpotlightStrategy.DAILY_RANDOM, "每日随机", "每天自动切换一次并保持稳定"),
                        ).forEach { option ->
                            item(key = option.value) {
                                HomeContentChoiceRow(option, option.value == current.strategy, colors) {
                                    update(current.copy(strategy = option.value))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "count-settings-row") {
                Box(Modifier.testTag("home-content-settings-count")) {
                    HomeContentSegmentedGroup(
                        title = "展示数量",
                        segments = (HomeContentSettings.MIN_COUNT..HomeContentSettings.MAX_COUNT).map { KireiSegment(it, it.toString()) },
                        selected = current.count,
                        colors = colors,
                        onSelect = { update(current.copy(count = it)) },
                    )
                }
            }
        }
    }
}

private data class HomeContentChoice<T>(
    val value: T,
    val label: String,
    val description: String,
)

/** A captioned group holding one segmented control (≤ 5 short options), like the settings pages. */
@Composable
private fun <T> HomeContentSegmentedGroup(
    title: String,
    segments: List<KireiSegment<T>>,
    selected: T,
    colors: KireiColors,
    onSelect: (T) -> Unit,
) {
    KireiListGroup(colors, Modifier.padding(horizontal = KireiSpacing.md), title = title) {
        item {
            KireiSegmented(
                spec = KireiSegmentedSpec(label = title, segments = segments, selected = selected),
                onSelect = onSelect,
                colors = colors,
                modifier = Modifier.padding(KireiSpacing.sm).widthIn(max = SettingsSegmentedLayoutPolicy.MaxControlWidth),
            )
        }
    }
}

/** One radio option with its description; the row carries the selected state, the check is visual. */
@Composable
private fun <T> HomeContentChoiceRow(
    option: HomeContentChoice<T>,
    selected: Boolean,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = option.label,
                subtitle = option.description,
                trailing = if (selected) KireiRowTrailing.Check else KireiRowTrailing.None,
                subtitleMaxLines = 3,
            ),
        colors = colors,
        modifier =
            Modifier.semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        onClick = onClick,
    )
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
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = game.title.orEmpty().ifBlank { "未命名游戏" },
                subtitle = if (selected) "第 ${selectedIndex + 1} 位" else game.engine?.displayName.orEmpty(),
                leading = { SettingsCoverThumbnail(game, colors) },
                tileSize = SettingsCoverThumbWidth,
                trailing =
                    KireiRowTrailing.Custom {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected) {
                                HomeContentMoveButton(Icons.Rounded.KeyboardArrowUp, "上移", selectedIndex > 0, colors) { onMove(selectedIndex - 1) }
                                HomeContentMoveButton(Icons.Rounded.KeyboardArrowDown, "下移", selectedIndex < selectedCount - 1, colors) { onMove(selectedIndex + 1) }
                            }
                            HomeContentSelectionIndicator(selected, colors)
                        }
                    },
                subtitleMaxLines = 1,
            ),
        colors = colors,
        modifier =
            Modifier.semantics {
                this.selected = selected
                role = Role.Checkbox
            },
        onClick = onToggle,
    )
}

/** Move arrow with a 48dp touch target. */
@Composable
private fun HomeContentMoveButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) colors.secondary else colors.tertiary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HomeContentSelectionIndicator(
    selected: Boolean,
    colors: KireiColors
) {
    Box(
        Modifier.size(24.dp).then(
            if (selected) {
                Modifier.background(colors.accent, KireiShapes.full)
            } else {
                Modifier.border(KireiElevation.focusStrokeWidth, colors.tertiary, KireiShapes.full)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            // Visual only: the row carries the selected state.
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
