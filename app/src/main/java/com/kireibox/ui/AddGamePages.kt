package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.components.KireiStatusTone
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import dev.chrisbanes.haze.HazeState

// 添加游戏: the single-game page, the batch page and the review page both of them end in.

@Composable
internal fun AddGameHost(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    val ui = state.addGame ?: return
    BackHandler(onBack = viewModel::addGameBack)
    when (ui.stage) {
        AddGameStage.SINGLE -> AddSingleGamePage(state, colors, haze, viewModel::addGameBack, actions)
        AddGameStage.BATCH -> AddBatchPage(ui, colors, haze, viewModel, actions)
        AddGameStage.DETECTING ->
            PickerPage(PickerPageSpec("正在识别", viewModel::addGameBack), colors, haze) { groupSkeleton(colors) }
        AddGameStage.REVIEW -> AddGameReviewPage(ui, colors, haze, viewModel)
    }
}

@Composable
private fun AddSingleGamePage(
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    onBack: () -> Unit,
    actions: KireiActions,
) {
    PickerPage(PickerPageSpec("添加单个游戏", onBack), colors, haze) {
        state.addGame?.error?.let {
            pickerErrorRow(it, colors)
            pickerGap("single-error-gap")
        }
        groupItems(listOf("auto"), colors, { it }) {
            KireiListRow(
                KireiListRowSpec("选择游戏目录", "自动识别引擎", Icons.Rounded.Folder, KireiTileColor.BLUE, KireiRowTrailing.Chevron),
                colors,
            ) { actions.pickAddGame(AddGamePick(AddGamePick.Kind.SINGLE_FOLDER)) }
        }
        pickerGap("single-engines-gap")
        groupCaption("选择引擎", colors, "single-engines")
        groupItems(SINGLE_ENGINE_ROWS, colors, { it.title }) { row ->
            KireiListRow(
                KireiListRowSpec(row.title, row.subtitle ?: row.shortcutSubtitle(state.shortcutServiceStatus), row.icon, row.tile, KireiRowTrailing.Chevron),
                colors,
            ) { row.open(actions) }
        }
    }
}

private data class SingleEngineRow(
    val title: String,
    val icon: ImageVector,
    val tile: KireiTileColor,
    val subtitle: String? = null,
    val shortcut: Boolean = false,
    val open: (KireiActions) -> Unit,
) {
    fun shortcutSubtitle(status: ShortcutServiceStatus): String? = if (shortcut) shortcutServiceSubtitle(status) else null
}

private fun folderEngineRow(engine: EngineType) =
    SingleEngineRow(addGameEngineLabel(engine), libraryEngineIcon(engine), addGameEngineTile(engine)) {
        it.pickAddGame(AddGamePick(AddGamePick.Kind.SINGLE_FOLDER, engine))
    }

private val SINGLE_ENGINE_ROWS =
    AddGamePolicy.FOLDER_ENGINES.map(::folderEngineRow) +
        listOf(
            SingleEngineRow("Android 应用", Icons.Rounded.Apps, KireiTileColor.GREEN, open = { it.chooseAndroidApp() }),
            SingleEngineRow("盖世游戏（快捷方式）", Icons.AutoMirrored.Rounded.Shortcut, KireiTileColor.INDIGO, shortcut = true, open = { it.chooseExternalShortcuts() }),
            SingleEngineRow(
                "Winlator 外置启动",
                Icons.Rounded.DesktopWindows,
                KireiTileColor.ORANGE,
                "选择游戏目录，自动选择启动程序",
                open = { it.pickAddGame(AddGamePick(AddGamePick.Kind.SINGLE_FOLDER, EngineType.WINDOWS)) },
            ),
        )

@Composable
private fun AddBatchPage(
    ui: AddGameUi,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    val context = LocalContext.current
    val spec =
        PickerPageSpec(
            title = "批量添加",
            onBack = viewModel::addGameBack,
            bottomAction =
                PickerAction(
                    if (ui.folders.isEmpty()) "开始识别" else "开始识别（${ui.folders.size}）",
                    viewModel::startBatchDetect,
                    enabled = ui.folders.isNotEmpty(),
                ),
        )
    PickerPage(spec, colors, haze) {
        ui.error?.let {
            pickerErrorRow(it, colors)
            pickerGap("batch-error-gap")
        }
        groupItems(listOf("add"), colors, { it }) {
            KireiListRow(
                KireiListRowSpec("添加文件夹", "游戏目录，或包含多个游戏的目录", Icons.Rounded.Add, KireiTileColor.BLUE, KireiRowTrailing.Chevron),
                colors,
            ) { actions.pickAddGame(AddGamePick(AddGamePick.Kind.BATCH_FOLDER)) }
        }
        if (ui.folders.isNotEmpty()) {
            pickerGap("batch-folders-gap")
            groupCaption("已选择", colors, "batch-folders")
            groupItems(ui.folders, colors, AddGameFolder::uri) { folder ->
                KireiListRow(
                    KireiListRowSpec(
                        title = folder.name,
                        subtitle = GameLocationLabel.format(context, folder.uri),
                        icon = Icons.Rounded.Folder,
                        tile = KireiTileColor.GRAY,
                        trailing =
                            KireiRowTrailing.Custom {
                                KireiButton(
                                    "移除",
                                    colors,
                                    options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                                    onClick = { viewModel.removeBatchFolder(folder.uri) },
                                )
                            },
                        subtitleMaxLines = 1,
                    ),
                    colors,
                )
            }
        }
    }
}

@Composable
private fun AddGameReviewPage(
    ui: AddGameUi,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
) {
    if (ui.listReview) AddGameListReview(ui, colors, haze, viewModel) else AddGameSingleReview(ui, colors, haze, viewModel)
}

/** One folder: its cover, an editable name (the folder name), the engine and the launch file. */
@Composable
private fun AddGameSingleReview(
    ui: AddGameUi,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
) {
    val candidate = ui.single ?: return
    val context = LocalContext.current
    val spec =
        PickerPageSpec(
            title = "确认游戏",
            onBack = viewModel::addGameBack,
            bottomAction = PickerAction(if (candidate.inLibrary) "更新游戏" else "添加", viewModel::confirmAddGame, enabled = ui.canConfirm),
            scrollResetKey = candidate.key,
        )
    PickerPage(spec, colors, haze) {
        ui.error?.let {
            pickerErrorRow(it, colors)
            pickerGap("review-error-gap")
        }
        groupItems(listOf(candidate), colors, AddGameCandidate::key) {
            KireiListRow(
                KireiListRowSpec(
                    title = GameLocationLabel.format(context, candidate.rootUri),
                    leading = { SettingsCoverThumbnail(candidate.previewGame(), colors) },
                    tileSize = SettingsCoverThumbWidth,
                    trailing = if (candidate.inLibrary) KireiRowTrailing.Custom { KireiStatusChip("已在游戏库中", KireiStatusTone.NEUTRAL, colors) } else KireiRowTrailing.None,
                    subtitleMaxLines = 1,
                ),
                colors,
            )
        }
        pickerGap("review-title-gap")
        groupCaption("名称", colors, "review-title")
        groupItems(listOf("title"), colors, { it }) {
            KireiTextField(
                value = candidate.title,
                onValueChange = { viewModel.setAddGameTitle(candidate.key, it) },
                colors = colors,
                modifier = Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.groupInner, vertical = KireiSpacing.sm),
                options = KireiTextFieldOptions(label = "游戏名称", placeholder = "默认使用文件夹名"),
            )
        }
        pickerGap("review-engine-gap")
        engineSection(candidate, colors) { viewModel.selectAddGameEngine(candidate.key, it) }
        if (candidate.engine != EngineType.UNKNOWN) {
            pickerGap("review-launch-gap")
            launchSection(candidate, colors) { viewModel.selectAddGameLaunchTarget(candidate.key, it) }
        }
    }
}

/** Several folders: one compact row each (folder name, engine, launch file) that is ticked to import. */
@Composable
private fun AddGameListReview(
    ui: AddGameUi,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
) {
    val allSelected = ui.queue.filter(AddGameCandidate::ready).let { ready -> ready.isNotEmpty() && ready.all { it.key in ui.selected } }
    val spec =
        PickerPageSpec(
            title = "确认游戏（${ui.queue.size}）",
            onBack = viewModel::addGameBack,
            topActions = {
                KireiButton(
                    if (allSelected) "取消全选" else "全选",
                    colors,
                    options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL, enabled = !ui.saving),
                    onClick = viewModel::toggleAllAddGames,
                )
            },
            bottomAction = PickerAction("添加所选（${ui.importable.size}）", viewModel::confirmAddGame, enabled = ui.canConfirm),
        )
    PickerPage(spec, colors, haze) {
        ui.error?.let {
            pickerErrorRow(it, colors)
            pickerGap("review-error-gap")
        }
        groupItems(ui.queue, colors, AddGameCandidate::key) { candidate ->
            val checked = candidate.key in ui.selected
            KireiListRow(
                KireiListRowSpec(
                    title = candidate.title,
                    subtitle = addGameSummary(candidate),
                    icon = libraryEngineIcon(candidate.engine),
                    tile = addGameEngineTile(candidate.engine),
                    trailing =
                        KireiRowTrailing.Custom {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KireiButton(
                                    "修改",
                                    colors,
                                    options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                                    onClick = { viewModel.editAddGame(candidate.key) },
                                )
                                if (candidate.ready) PickerSelectionMark(checked, colors)
                            }
                        },
                    subtitleMaxLines = 1,
                ),
                colors,
                Modifier.semantics { if (candidate.ready) stateDescription = if (checked) "已选择" else "未选择" },
            ) { viewModel.toggleAddGame(candidate.key) }
        }
    }
    ui.editingCandidate?.let { AddGameEditSheet(it, colors, viewModel) }
}

/** Engine and launch file of one listed folder, edited without leaving the list. */
@Composable
private fun AddGameEditSheet(
    candidate: AddGameCandidate,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    KireiActionSheet(candidate.title, colors, { viewModel.editAddGame(null) }, expanded = true, grouped = true) {
        SheetRowGroup(colors, title = "名称") {
            item {
                KireiTextField(
                    value = candidate.title,
                    onValueChange = { viewModel.setAddGameTitle(candidate.key, it) },
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.groupInner, vertical = KireiSpacing.sm),
                    options = KireiTextFieldOptions(label = "游戏名称"),
                )
            }
        }
        SheetRowGroup(colors, title = if (candidate.engine == EngineType.UNKNOWN) "未识别到引擎，请选择" else "引擎") {
            AddGamePolicy.REVIEW_ENGINES.forEach { engine ->
                item {
                    SheetChoiceRow(addGameEngineLabel(engine), "识别结果".takeIf { engine == candidate.detectedEngine }, engine == candidate.engine, colors) {
                        viewModel.selectAddGameEngine(candidate.key, engine)
                    }
                }
            }
        }
        if (candidate.launchOptions.size > 1) {
            SheetRowGroup(colors, title = "启动文件") {
                candidate.launchOptions.forEach { target ->
                    item {
                        val checked = target.equals(candidate.launchTarget, ignoreCase = true)
                        SheetChoiceRow(target.substringAfterLast('/'), target.substringBeforeLast('/', "").takeIf(String::isNotEmpty), checked, colors) {
                            viewModel.selectAddGameLaunchTarget(candidate.key, target)
                        }
                    }
                }
            }
        }
        KireiButton("完成", colors, Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.md)) { viewModel.editAddGame(null) }
    }
}

private fun LazyListScope.engineSection(
    candidate: AddGameCandidate,
    colors: KireiColors,
    onEngine: (EngineType) -> Unit,
) {
    groupCaption(if (candidate.engine == EngineType.UNKNOWN) "未识别到引擎，请选择" else "引擎", colors, "review-engines")
    groupItems(AddGamePolicy.REVIEW_ENGINES, colors, { it.name }) { engine ->
        val checked = engine == candidate.engine
        KireiListRow(
            KireiListRowSpec(
                title = addGameEngineLabel(engine),
                subtitle = "识别结果".takeIf { engine == candidate.detectedEngine },
                icon = libraryEngineIcon(engine),
                tile = addGameEngineTile(engine),
                trailing = if (checked) KireiRowTrailing.Check else KireiRowTrailing.None,
            ),
            colors,
            Modifier.semantics { selected = checked },
        ) { onEngine(engine) }
    }
}

private fun LazyListScope.launchSection(
    candidate: AddGameCandidate,
    colors: KireiColors,
    onTarget: (String) -> Unit,
) {
    groupCaption("启动文件", colors, "review-launch")
    if (candidate.launchOptions.size > 1) {
        groupItems(candidate.launchOptions, colors, { it }) { target ->
            val checked = target.equals(candidate.launchTarget, ignoreCase = true)
            KireiListRow(
                KireiListRowSpec(
                    title = target.substringAfterLast('/'),
                    subtitle = target.substringBeforeLast('/', "").takeIf(String::isNotEmpty),
                    icon = Icons.Rounded.Description,
                    tile = KireiTileColor.INDIGO,
                    trailing = if (checked) KireiRowTrailing.Check else KireiRowTrailing.None,
                    subtitleMaxLines = 1,
                ),
                colors,
                Modifier.semantics { selected = checked },
            ) { onTarget(target) }
        }
    } else {
        groupItems(listOf(candidate.launchTarget), colors, { it }) { target ->
            KireiListRow(
                KireiListRowSpec(
                    title = launchFileName(target),
                    subtitle = target.substringBeforeLast('/', "").takeIf { target != AddGamePolicy.DIRECTORY_TARGET && it.isNotEmpty() },
                    icon = Icons.Rounded.Description,
                    tile = KireiTileColor.GRAY,
                    subtitleMaxLines = 1,
                ),
                colors,
            )
        }
    }
}

private fun launchFileName(target: String): String = if (target == AddGamePolicy.DIRECTORY_TARGET) "游戏目录" else target.substringAfterLast('/')

/** 「KRKR · data.xp3」: what the list shows under a folder name. */
internal fun addGameSummary(candidate: AddGameCandidate): String =
    if (candidate.engine == EngineType.UNKNOWN) {
        "未识别到引擎，点击修改"
    } else {
        "${addGameEngineShortLabel(candidate.engine)} · ${launchFileName(candidate.launchTarget)}"
    }

/** The card the review page previews: the folder's own cover when it has one, else the engine's default. */
private fun AddGameCandidate.previewGame(): Game = Game(title = title, engine = engine, coverUri = coverUri.takeIf(String::isNotBlank))

internal fun addGameEngineLabel(engine: EngineType): String =
    when (engine) {
        EngineType.KIRIKIRI -> "KRKR（Kirikiri）"
        EngineType.ONS -> "ONScripter"
        EngineType.TYRANO -> "Tyrano"
        EngineType.ARTEMIS -> "Artemis"
        EngineType.ANDROID_APP -> "Android 应用"
        EngineType.WINDOWS -> "Winlator 外置启动"
        EngineType.UNKNOWN -> "未识别"
    }

internal fun addGameEngineShortLabel(engine: EngineType): String =
    when (engine) {
        EngineType.KIRIKIRI -> "KRKR"
        EngineType.ONS -> "ONS"
        EngineType.TYRANO -> "Tyrano"
        EngineType.ARTEMIS -> "Artemis"
        EngineType.ANDROID_APP -> "Android"
        EngineType.WINDOWS -> "PC"
        EngineType.UNKNOWN -> "未识别"
    }

internal fun addGameEngineTile(engine: EngineType): KireiTileColor =
    when (engine) {
        EngineType.KIRIKIRI -> KireiTileColor.PINK
        EngineType.ONS -> KireiTileColor.ORANGE
        EngineType.TYRANO -> KireiTileColor.PURPLE
        EngineType.ARTEMIS -> KireiTileColor.INDIGO
        EngineType.WINDOWS -> KireiTileColor.BLUE
        else -> KireiTileColor.GRAY
    }

internal fun shortcutServiceSubtitle(status: ShortcutServiceStatus): String =
    when (status) {
        ShortcutServiceStatus.READY -> "扫描桌面游戏图标 · 支持的盖世游戏只需导入一次"
        ShortcutServiceStatus.NOT_INSTALLED -> "未安装 Shizuku"
        ShortcutServiceStatus.NOT_RUNNING -> "Shizuku 未运行 · 请先完成无线调试配对"
        ShortcutServiceStatus.PERMISSION_REQUIRED -> "等待授权 KireiBox 使用 Shizuku"
        ShortcutServiceStatus.PERMISSION_DENIED -> "权限已拒绝 · 请在 Shizuku 中重新允许"
        ShortcutServiceStatus.BINDING -> "正在绑定 Shizuku 快捷方式服务"
        ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> "不支持 Root/Sui · 请使用 shell 模式"
        ShortcutServiceStatus.SYSTEM_PERMISSION_MISSING -> "当前系统未向 shell 开放快捷方式权限"
        ShortcutServiceStatus.UNSUPPORTED_VERSION -> "需要 Shizuku 13.1.5 或更新版本"
        ShortcutServiceStatus.CONNECTION_LOST -> "连接已失效 · 请重新启动 Shizuku"
    }
