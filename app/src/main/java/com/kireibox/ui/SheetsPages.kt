package com.kireibox.ui
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListGroupScope
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiMenu
import com.kireibox.designsystem.components.KireiMenuItem
import com.kireibox.designsystem.components.KireiMenuItemSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.kireibox.designsystem.components.KireiSheet
import com.kireibox.designsystem.components.KireiSheetSpec
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.launch

// Library/game action sheets, dialogs and the modal system-bar effect.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun KireiActionSheet(
    title: String,
    colors: KireiColors,
    onDismiss: () -> Unit,
    expanded: Boolean = false,
    scrollableContent: Boolean = true,
    grouped: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // The shared KireiSheet look (spec §8); this wrapper adds the modal system-bar policy.
    KireiSheet(
        colors = colors,
        onDismiss = onDismiss,
        spec = KireiSheetSpec(title = title, expanded = expanded, scrollable = scrollableContent, grouped = grouped),
    ) {
        ModalSystemBarVisibilityEffect(isDark = colors.isDark)
        content()
    }
}

/**
 * Material bottom sheets own a dialog window, so the Activity controller cannot keep their
 * status bar policy in sync by itself. Apply the same pure device policy to the dialog window
 * and repeat it on the first composed frame, after Material has finished configuring the window.
 */
@Composable
internal fun ModalSystemBarVisibilityEffect(isDark: Boolean) {
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val lightIcons = SystemBarVisibilityPolicy.statusBarIconsLight(isDark)
    val windowClass =
        remember(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.smallestScreenWidthDp,
            configuration.screenLayout,
        ) {
            ResponsiveLayoutPolicy.resolveWindowClass(
                safeWidthDp = configuration.screenWidthDp.toFloat(),
                safeHeightDp = configuration.screenHeightDp.toFloat(),
                smallestScreenWidthDp = configuration.smallestScreenWidthDp,
                screenLayoutSize = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
            )
        }
    val hideStatusBar = SystemBarVisibilityPolicy.shouldHideStatusBar(windowClass)

    fun applyPolicy() {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
            if (hideStatusBar) {
                hide(WindowInsetsCompat.Type.statusBars())
            } else {
                show(WindowInsetsCompat.Type.statusBars())
            }
            show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    DisposableEffect(view, windowClass, hideStatusBar, lightIcons, isDark) {
        applyPolicy()
        onDispose { }
    }
    LaunchedEffect(view, windowClass, hideStatusBar, lightIcons, isDark) {
        withFrameNanos { }
        applyPolicy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemovedGamesSheet(
    games: List<Game>,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onRestore: (Game) -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listHeight = (screenHeight * .52f).coerceIn(280.dp, 430.dp)
    KireiActionSheet(
        title = "已移除的游戏 · ${games.size} 部",
        colors = colors,
        onDismiss = onDismiss,
        scrollableContent = false,
    ) {
        if (games.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(listHeight).background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text("没有已移除的游戏", color = colors.secondary, style = AppleTypography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().height(listHeight),
                contentPadding = PaddingValues(horizontal = KireiSpacing.xl, vertical = KireiSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(KireiSpacing.xxs),
            ) {
                items(games, key = { it.id }) { game ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = KireiSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GameCoverArtwork(game, Modifier.size(SheetCoverWidth, SheetCoverHeight), ContentScale.Crop, KireiShapes.Radius.sm)
                        Column(Modifier.weight(1f).padding(horizontal = KireiSpacing.md)) {
                            Text(
                                game.title.orEmpty(),
                                color = colors.text,
                                style = AppleTypography.bodyLargeEmphasis,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                game.engine?.displayName ?: "游戏",
                                color = colors.secondary,
                                style = AppleTypography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        KireiButton(
                            "恢复",
                            colors,
                            options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL),
                            onClick = { onRestore(game) },
                        )
                    }
                }
            }
        }
    }
}

/** Cover thumbnail of the games listed in the removed/overview sheets (4pt-grid, portrait). */
private val SheetCoverWidth = 44.dp
private val SheetCoverHeight = 60.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryOverviewSheet(
    games: List<Game>,
    sort: LibraryOverviewSort,
    colors: KireiColors,
    onSortChange: (LibraryOverviewSort) -> Unit,
    onDismiss: () -> Unit,
    onGame: (Game) -> Unit
) {
    val orderedGames = remember(games, sort) { LibraryOverviewPolicy.sorted(games, sort) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(sort) {
        listState.scrollToItem(0)
    }
    val selectSort: (LibraryOverviewSort) -> Unit = { target ->
        if (target == sort) {
            scope.launch { listState.scrollToItem(0) }
        } else {
            onSortChange(target)
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val listHeight = (screenHeight * .52f).coerceIn(280.dp, 430.dp)
    KireiActionSheet(
        title = "游玩记录 · ${games.size} 部游戏",
        colors = colors,
        onDismiss = onDismiss,
        scrollableContent = false
    ) {
        KireiSegmented(
            KireiSegmentedSpec("排序", OverviewSortSegments, sort),
            selectSort,
            colors,
            Modifier.padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.md),
        )
        if (orderedGames.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().height(listHeight).background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text("游戏库还没有内容", color = colors.secondary, style = AppleTypography.bodyMedium)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().height(listHeight),
                state = listState,
                contentPadding = PaddingValues(vertical = KireiSpacing.xs),
            ) {
                itemsIndexed(orderedGames, key = { _, game -> game.id }) { index, game ->
                    KireiListRow(
                        spec =
                            KireiListRowSpec(
                                title = game.title.orEmpty(),
                                subtitle = game.engine?.displayName ?: "游戏",
                                trailing = KireiRowTrailing.Value(LibraryOverviewPolicy.compactPlayTime(game.totalPlayTime), chevron = false),
                                tileSize = OverviewRankWidth + SheetCoverWidth,
                                horizontalPadding = KireiSpacing.xl,
                                subtitleMaxLines = 1,
                                verticalPadding = KireiSpacing.xs,
                                leading = { OverviewLeading(index + 1, game, colors) },
                            ),
                        colors = colors,
                        onClick = { onGame(game) },
                    )
                }
            }
        }
    }
}

private val OverviewSortSegments =
    listOf(
        KireiSegment(LibraryOverviewSort.RECENT, "最近游玩"),
        KireiSegment(LibraryOverviewSort.PLAY_TIME, "游玩时长"),
    )

private val OverviewRankWidth = 32.dp

/** Rank number and cover thumbnail: the leading slot of an overview row. */
@Composable
private fun OverviewLeading(
    rank: Int,
    game: Game,
    colors: KireiColors,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString(), color = colors.secondary, style = AppleTypography.numericSmall, maxLines = 1, modifier = Modifier.width(OverviewRankWidth))
        GameCoverArtwork(game, Modifier.size(SheetCoverWidth, SheetCoverHeight), ContentScale.Crop, KireiShapes.Radius.sm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameActionSheet(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var artemisVersionMenuExpanded by remember(game.id) { mutableStateOf(false) }
    val selectedArtemisVersion = com.yuki.yukihub.launcher.ArtemisVersion.fromStored(game.emulatorPackage)
    KireiActionSheet(game.title ?: "游戏操作", colors, onDismiss, expanded = true, grouped = true) {
        Text(
            GameLocationLabel.forGame(context, game),
            color = colors.secondary,
            style = AppleTypography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Centered under the centered sheet title.
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = KireiSpacing.xl, end = KireiSpacing.xl, top = KireiSpacing.xxs, bottom = KireiSpacing.md)
        )
        SheetRowGroup(colors, title = "启动") {
            item {
                SheetActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = "启动游戏",
                    subtitle = GameLocationLabel.launchSubtitle(game),
                    colors = colors,
                    prominent = true
                ) {
                    onDismiss()
                    actions.launchGame(game)
                }
            }
            if (game.engine == EngineType.KIRIKIRI) {
                item {
                    SheetActionRow(
                        icon = Icons.Rounded.Description,
                        title = "KRKR 启动文件",
                        subtitle = GameLocationLabel.xp3LaunchSubtitle(game),
                        colors = colors,
                        trailing = Icons.Rounded.ChevronRight,
                    ) {
                        viewModel.openXp3Picker(game)
                    }
                }
                item {
                    SheetActionRow(
                        icon = Icons.Rounded.Tune,
                        title = "KRKR 游戏设置",
                        subtitle = "启动、渲染、性能和字体",
                        colors = colors,
                        trailing = Icons.Rounded.ChevronRight
                    ) {
                        viewModel.openKrkrEditor(game)
                    }
                }
            }
            if (game.engine == EngineType.WINDOWS) {
                item {
                    SheetActionRow(
                        icon = Icons.Rounded.Description,
                        title = "启动程序",
                        subtitle = GameLocationLabel.exeLaunchSubtitle(game),
                        colors = colors,
                        trailing = Icons.Rounded.ChevronRight,
                    ) {
                        viewModel.openExePicker(game)
                    }
                }
                item {
                    SheetActionRow(
                        icon = Icons.Rounded.Tune,
                        title = "Winlator 游戏设置",
                        subtitle = "容器、图形和语言环境",
                        colors = colors,
                        trailing = Icons.Rounded.ChevronRight
                    ) {
                        viewModel.openWinlatorEditor(game)
                    }
                }
            }
            if (game.engine == EngineType.ARTEMIS) {
                item {
                    Box(Modifier.fillMaxWidth()) {
                        SheetActionRow(
                            icon = Icons.Rounded.Tune,
                            title = "Artemis 版本",
                            subtitle = selectedArtemisVersion.displayName,
                            colors = colors,
                            trailing = if (artemisVersionMenuExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        ) {
                            artemisVersionMenuExpanded = !artemisVersionMenuExpanded
                        }
                        // KireiMenu is the themed DropdownMenu; the selected version gets the trailing check.
                        KireiMenu(
                            expanded = artemisVersionMenuExpanded,
                            onDismissRequest = { artemisVersionMenuExpanded = false },
                            colors = colors,
                            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp).heightIn(max = 240.dp),
                        ) {
                            com.yuki.yukihub.launcher.ArtemisVersion.entries.forEach { version ->
                                KireiMenuItem(
                                    KireiMenuItemSpec(
                                        label = version.displayName,
                                        supporting =
                                            if (version == com.yuki.yukihub.launcher.ArtemisVersion.AUTO) {
                                                "自动探测并保留兼容回退"
                                            } else {
                                                "固定使用 Artemis ${version.displayName}"
                                            },
                                        selected = version == selectedArtemisVersion,
                                    ),
                                    colors,
                                ) {
                                    viewModel.setArtemisVersion(game, version)
                                    artemisVersionMenuExpanded = false
                                }
                            }
                        }
                    }
                }
            }
        }
        SheetRowGroup(colors, title = "封面与信息") {
            item { SheetActionRow(Icons.Rounded.Edit, "游戏名称", "只修改 KireiBox 中的显示名称", colors, onClick = onRename) }
            item {
                SheetActionRow(Icons.Rounded.CollectionsBookmark, "游戏封面", "搜索并选择新的封面", colors) {
                    onDismiss()
                    viewModel.openCoverSearch(game)
                }
            }
            item {
                SheetToggleRow(Icons.Rounded.BlurOn, "模糊封面", "仅影响 KireiBox 内的封面显示", game.nsfw, colors) { viewModel.toggleNsfwCover(game) }
            }
        }
        SheetRowGroup(colors, title = "管理") {
            item {
                SheetActionRow(Icons.Rounded.Home, "桌面快捷方式", "添加由 KireiBox 启动的快捷方式", colors) {
                    onDismiss()
                    actions.pinGameShortcut(game)
                }
            }
            item {
                SheetActionRow(
                    if (game.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (game.favorite) "取消收藏" else "加入收藏",
                    null,
                    colors
                ) { viewModel.toggleFavorite(game) }
            }
        }
        SheetRowGroup(colors) {
            item { SheetActionRow(Icons.Rounded.DeleteOutline, "移出游戏库", "保留游戏文件和游玩记录", colors, destructive = true, onClick = onRemove) }
        }
        SheetCancelButton(colors, onDismiss)
    }
}

/**
 * Titled rounded group of sheet rows (spec §4/§8), inset from the sheet edge like a settings page.
 * Rows inside it use the group's own 16dp text inset instead of the flat sheet rows' 24dp.
 */
@Composable
internal fun SheetRowGroup(
    colors: KireiColors,
    title: String? = null,
    content: KireiListGroupScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSheetRowInset provides KireiSpacing.groupInner) {
        KireiListGroup(
            colors,
            Modifier.padding(start = KireiSpacing.md, end = KireiSpacing.md, bottom = KireiSpacing.groupGap),
            title = title,
            content = content,
        )
    }
}

private val LocalSheetRowInset = compositionLocalOf { KireiSpacing.xl }

@Composable
internal fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    colors: KireiColors,
    prominent: Boolean = false,
    destructive: Boolean = false,
    trailing: androidx.compose.ui.graphics.vector.ImageVector? = null,
    tile: KireiTileColor? = null,
    onClick: () -> Unit
) {
    // Same icon language as the settings lists (spec §8): sm-rounded tile on the accent tint, a
    // category [tile] colour, or the danger tint for destructive rows (KireiListRow owns the tokens).
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = title,
                subtitle = subtitle,
                icon = icon,
                tile = tile,
                trailing = trailing?.let { KireiRowTrailing.Glyph(it) } ?: KireiRowTrailing.None,
                tone =
                    when {
                        destructive -> KireiRowTone.DESTRUCTIVE
                        prominent -> KireiRowTone.PROMINENT
                        else -> KireiRowTone.NORMAL
                    },
                tileSize = SheetRowTileSize,
                horizontalPadding = LocalSheetRowInset.current,
            ),
        colors = colors,
        onClick = onClick,
    )
}

/** A sheet row whose state lives in a switch (the whole row toggles it) instead of in its title. */
@Composable
internal fun SheetToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    colors: KireiColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = title,
                subtitle = subtitle,
                icon = icon,
                trailing = KireiRowTrailing.Toggle(checked, onCheckedChange),
                tileSize = SheetRowTileSize,
                horizontalPadding = LocalSheetRowInset.current,
            ),
        colors = colors,
    )
}

/** Sheet rows use the larger end of the icon-tile range (spec §3: 30–36dp). */
private val SheetRowTileSize = 36.dp

/** One option of a picker sheet: title, optional description and the accent check when selected. */
@Composable
internal fun SheetChoiceRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    KireiListRow(
        spec =
            KireiListRowSpec(
                title = title,
                subtitle = subtitle,
                trailing = if (selected) KireiRowTrailing.Check else KireiRowTrailing.None,
                horizontalPadding = KireiSpacing.xl,
            ),
        colors = colors,
        // One radio button per option: "最近游玩，已选中" once, not again for the check glyph.
        modifier =
            Modifier.semantics {
                this.selected = selected
                role = Role.RadioButton
            },
        onClick = onClick,
    )
}

/** Neutral full-width dismiss at the bottom of an action sheet. */
@Composable
internal fun ColumnScope.SheetCancelButton(
    colors: KireiColors,
    onDismiss: () -> Unit,
) {
    KireiButton(
        "取消",
        colors,
        Modifier.align(Alignment.CenterHorizontally).padding(top = KireiSpacing.xxs),
        KireiButtonOptions(style = KireiButtonStyle.PLAIN, size = KireiButtonSize.MEDIUM),
        onDismiss,
    )
}

@Composable
internal fun RenameGameDialog(
    game: Game,
    value: String,
    onValueChange: (String) -> Unit,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    KireiDialog(
        spec =
            KireiDialogSpec(
                title = "修改游戏名称",
                message = game.originalTitle?.takeIf(String::isNotBlank),
                confirm = KireiDialogAction("保存", onSave, enabled = value.isNotBlank()),
                dismiss = KireiDialogAction("取消", onDismiss, style = KireiButtonStyle.PLAIN),
            ),
        colors = colors,
        onDismissRequest = onDismiss,
    ) {
        KireiTextField(value, onValueChange, colors, options = KireiTextFieldOptions(label = "游戏名称", placeholder = "输入新的游戏名称"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoveGameSheet(
    game: Game,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    KireiActionSheet("移出游戏库", colors, onDismiss) {
        Text(
            "只从游戏库移除；游戏文件和游玩记录会保留。",
            color = colors.secondary,
            style = AppleTypography.bodyMedium,
            modifier = Modifier.padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.md)
        )
        SheetActionRow(Icons.Rounded.DeleteOutline, "移出 ${game.title ?: "游戏"}", "5 秒内可撤销", colors, destructive = true, onClick = onConfirm)
        SheetCancelButton(colors, onDismiss)
    }
}
