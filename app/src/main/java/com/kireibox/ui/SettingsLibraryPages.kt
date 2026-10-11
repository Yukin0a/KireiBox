package com.kireibox.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiDialog
import com.kireibox.designsystem.components.KireiDialogAction
import com.kireibox.designsystem.components.KireiDialogSpec
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.components.KireiStatusTone
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.YukiSettingsStore
import com.yuki.yukihub.storage.StorageRootIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 游戏库顺序 options: four short labels, so an inline segmented control (spec §8) instead of a sheet. */
internal val LibrarySortOptions =
    listOf(YukiSettingsStore.SORT_RECENT, YukiSettingsStore.SORT_NEWEST, YukiSettingsStore.SORT_NAME, YukiSettingsStore.SORT_MANUAL)

@Composable
internal fun LibraryHubSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val sortMode = state.settings?.sortMode ?: YukiSettingsStore.SORT_RECENT
    KireiListGroup(colors, SettingsGroupSpacing, title = "目录") {
        item {
            KireiListRow(
                KireiListRowSpec(
                    title = "扫描目录",
                    icon = Icons.Rounded.Folder,
                    tile = KireiTileColor.BLUE,
                    trailing = KireiRowTrailing.Value("${state.settings?.scanRoots?.size ?: 0} 个"),
                ),
                colors,
                onClick = { viewModel.openSettingsPage(KireiSettingsPage.LIBRARY) },
            )
        }
    }
    KireiListGroup(
        colors,
        SettingsGroupSpacing,
        title = "游戏卡片",
        footer = "长按游戏卡片后依次编号。".takeIf { sortMode == YukiSettingsStore.SORT_MANUAL },
    ) {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec(
                    title = "游戏库顺序",
                    segments = LibrarySortOptions.map { KireiSegment(it, choiceLabel(it)) },
                    selected = sortMode,
                ),
                colors,
                viewModel::setSortMode,
            )
        }
    }
}

/**
 * 首页 (KireiSettingsPage.INTERFACE, r4 S1): the home style, then — immersive only — 展示内容 (which
 * games, 显示游玩时长) and 视觉效果 (preset cards, 高级选项). 动画效果 / 减少透明度 / 增强对比度 /
 * 屏幕方向 live on 外观 only; effects they override say so with a link back to 外观.
 */
@Composable
internal fun InterfaceSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel
) {
    val homeMode = state.settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE
    // The page is titled 首页, so its first group carries no caption of its own.
    KireiListGroup(colors, SettingsGroupSpacing, footer = homeStyleFooter(homeMode)) {
        item {
            HomePresentationModeRow(
                value = homeMode,
                colors = colors,
                onChange = viewModel::setHomePresentationMode,
            )
        }
    }
    // The content and effects only exist on the immersive home; components mode never shows them.
    // Reduced motion (spec §7) swaps the groups in place instead of expanding them under the rows.
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    AnimatedVisibility(
        visible = homeMode == HomePresentationMode.IMMERSIVE,
        enter = if (reducedMotion) EnterTransition.None else fadeIn() + expandVertically(),
        exit = if (reducedMotion) ExitTransition.None else fadeOut() + shrinkVertically(),
    ) {
        Column {
            HomeContentSettingsGroup(state, colors, viewModel)
            ImmersiveVisualSettingsPanel(state, colors, viewModel)
        }
    }
}

@Composable
internal fun LibrarySettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions
) {
    var actionRoot by remember { mutableStateOf<String?>(null) }
    var removalRoot by remember { mutableStateOf<String?>(null) }
    var showRemovedGames by remember { mutableStateOf(false) }
    ScanRootGroup(state, colors, onAdd = actions.chooseDirectory) { actionRoot = it }
    KireiListGroup(colors, SettingsGroupSpacing, title = "自动更新") {
        item { SwitchRow("目录自动更新", state.settings?.autoScanOnStartup ?: true, colors, viewModel::setAutoScanOnStartup) }
        item {
            SwitchRow("已移除游戏更新目录不会再添加", state.settings?.keepRemovedGamesHiddenOnRefresh ?: true, colors, viewModel::setKeepRemovedGamesHiddenOnRefresh)
        }
        item {
            SettingsAnchor("已移除的游戏") {
                KireiListRow(
                    KireiListRowSpec(title = "已移除的游戏", trailing = KireiRowTrailing.Value("${state.removedGames.size} 部")),
                    colors,
                    onClick = { showRemovedGames = true },
                )
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "文件选择") {
        item { SwitchRow("使用内置文件浏览器", state.settings?.useBuiltinFileChooser != false, colors, viewModel::setUseBuiltinFileChooser) }
    }
    if (showRemovedGames) {
        RemovedGamesSheet(
            games = state.removedGames,
            colors = colors,
            onDismiss = { showRemovedGames = false },
            onRestore = actions.restoreRemovedGame,
        )
    }
    actionRoot?.let { root ->
        ScanRootActionSheet(
            root,
            scanRootGameCount(state, root),
            colors,
            ScanRootSheetActions(onRepair = { actions.repairPermission(root) }, onRemove = { removalRoot = root }, onDismiss = { actionRoot = null }),
        )
    }
    removalRoot?.let { root -> ScanRootRemovalDialog(root, state, colors, viewModel) { removalRoot = null } }
}

/** 扫描目录: one tiled row per root (opens its action sheet), then the accent 添加扫描目录 row. */
@Composable
private fun ScanRootGroup(
    state: KireiUiState,
    colors: KireiColors,
    onAdd: () -> Unit,
    onRoot: (String) -> Unit,
) {
    val context = LocalContext.current
    val roots = state.settings?.scanRoots.orEmpty()
    // The page is already titled 扫描目录, so the group names what it lists.
    KireiListGroup(colors, SettingsGroupSpacing, title = "已添加的目录") {
        roots.forEach { root ->
            item(key = root) {
                KireiListRow(
                    KireiListRowSpec(
                        title = GameLocationLabel.format(context, root),
                        subtitle = "已导入 ${scanRootGameCount(state, root)} 部游戏",
                        icon = Icons.Rounded.Folder,
                        tile = KireiTileColor.BLUE,
                        trailing = KireiRowTrailing.Glyph(Icons.Rounded.MoreHoriz),
                    ),
                    colors,
                    onClick = { onRoot(root) },
                )
            }
        }
        item(key = "add-scan-root") {
            SettingsAnchor("添加扫描目录") {
                KireiListRow(
                    KireiListRowSpec(title = "添加扫描目录", icon = Icons.Rounded.Add, tile = KireiTileColor.GREEN, tone = KireiRowTone.ACTION),
                    colors,
                    onClick = onAdd,
                )
            }
        }
    }
}

private fun scanRootGameCount(
    state: KireiUiState,
    root: String,
): Int = state.games.count { game -> StorageRootIdentity.contains(root, game.rootUri) }

/** What the scan-root sheet can do; every action closes the sheet first. */
private class ScanRootSheetActions(
    val onRepair: () -> Unit,
    val onRemove: () -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
private fun ScanRootActionSheet(
    root: String,
    gameCount: Int,
    colors: KireiColors,
    actions: ScanRootSheetActions,
) {
    val context = LocalContext.current
    KireiActionSheet(title = GameLocationLabel.format(context, root), colors = colors, onDismiss = actions.onDismiss) {
        SheetActionRow(Icons.Rounded.Refresh, "重新授权", "需重新选择同一文件夹", colors) {
            actions.onDismiss()
            actions.onRepair()
        }
        SheetActionRow(Icons.Rounded.DeleteOutline, "移除扫描目录", "同时移出该目录下的 $gameCount 部游戏", colors, destructive = true) {
            actions.onDismiss()
            actions.onRemove()
        }
        SheetCancelButton(colors, actions.onDismiss)
    }
}

@Composable
private fun ScanRootRemovalDialog(
    root: String,
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    onClose: () -> Unit,
) {
    KireiDialog(
        spec =
            KireiDialogSpec(
                title = "移除扫描目录？",
                message = "将从游戏库移出该目录下的 ${scanRootGameCount(state, root)} 部游戏，不会删除游戏文件。",
                confirm =
                    KireiDialogAction("移除目录与游戏", {
                        onClose()
                        viewModel.removeScanRoot(root)
                    }, style = KireiButtonStyle.DESTRUCTIVE),
                dismiss = KireiDialogAction("取消", onClose, style = KireiButtonStyle.PLAIN),
                // An explicit choice is required: back and outside taps do nothing (as before).
                dismissible = false,
            ),
        colors = colors,
        onDismissRequest = {},
    )
}

/**
 * 存储与权限: KRKR's native file access with its status and, while it is missing, the page's one
 * primary action (开启所有文件访问 / 授予存储权限) right under it in the same card; then every scan
 * root with its status chip (点按 = 重新授权), or an empty-state row, and the 检查访问权限 report.
 */
@Composable
internal fun PermissionSettings(
    state: KireiUiState,
    colors: KireiColors,
    actions: KireiActions
) {
    val context = LocalContext.current
    val roots = state.settings?.scanRoots.orEmpty()
    val access = rememberStorageAccess(roots)
    KireiListGroup(colors, SettingsGroupSpacing, title = "KRKR 原生引擎") {
        item {
            // One search anchor for the status row and its button (the title depends on the API level).
            SettingsAnchor(SettingsSearchIndex.NATIVE_ACCESS_ANCHOR) {
                Column {
                    KireiListRow(StorageAccessPolicy.nativeAccessRow(access.nativeGranted, colors), colors)
                    if (!access.nativeGranted) {
                        KireiButton(
                            StorageAccessPolicy.nativeAccessAction(),
                            colors,
                            Modifier.fillMaxWidth().padding(start = KireiSpacing.groupInner, end = KireiSpacing.groupInner, bottom = KireiSpacing.md),
                            KireiButtonOptions(style = KireiButtonStyle.PRIMARY),
                            actions.requestNativeAccess,
                        )
                    }
                }
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "扫描目录") {
        if (roots.isEmpty()) {
            item(key = "no-roots") {
                KireiListRow(KireiListRowSpec("还没有扫描目录", "在「游戏库 › 扫描目录」添加", icon = Icons.Rounded.Folder, tile = KireiTileColor.BLUE), colors)
            }
        }
        roots.forEach { root ->
            item(key = root) {
                val granted = root in access.grantedRoots
                KireiListRow(
                    KireiListRowSpec(
                        title = GameLocationLabel.format(context, root),
                        icon = Icons.Rounded.Folder,
                        tile = KireiTileColor.BLUE,
                        trailing = KireiRowTrailing.Custom { AccessChip(granted, colors) },
                    ),
                    colors,
                    onClick = { actions.repairPermission(root) },
                )
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing) {
        item { SettingsAnchor("检查访问权限") { KireiListRow(KireiListRowSpec(title = "检查访问权限", tone = KireiRowTone.ACTION), colors, onClick = actions.checkPermissions) } }
    }
}

/** Status chip: 已授权, or [missing] (a scan root's grant is repaired by choosing the folder again). */
@Composable
private fun AccessChip(
    granted: Boolean,
    colors: KireiColors,
    missing: String = "需要重新授权",
) {
    KireiStatusChip(if (granted) "已授权" else missing, if (granted) KireiStatusTone.POSITIVE else KireiStatusTone.WARNING, colors)
}

/** Storage access as the permission page shows it; re-read every time the launcher resumes. */
internal data class StorageAccess(
    val nativeGranted: Boolean,
    val grantedRoots: Set<String>,
)

/**
 * The grants are read at once; whether each granted folder can still be read (a deleted or moved
 * folder, a removed SD card keep their grant) is checked off the main thread and replaces them.
 */
@Composable
private fun rememberStorageAccess(roots: List<String>): StorageAccess {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumes by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumes++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val granted = remember(roots, resumes) { StorageAccessPolicy.read(context, roots, verifyReadable = false) }
    val verified by produceState<StorageAccess?>(null, roots, resumes) {
        value = withContext(Dispatchers.IO) { StorageAccessPolicy.read(context, roots, verifyReadable = true) }
    }
    return verified ?: granted
}

/**
 * The same checks the launcher's 权限检查 report uses (LauncherStoragePermission.kt): a root counts
 * as granted only with a persisted read grant and, when [read] verifies, a tree that can be read.
 */
internal object StorageAccessPolicy {
    fun read(
        context: Context,
        roots: List<String>,
        verifyReadable: Boolean,
    ): StorageAccess {
        val persisted =
            runCatching { context.contentResolver.persistedUriPermissions }.getOrDefault(emptyList())
                .filter { it.isReadPermission }
                .mapTo(HashSet()) { it.uri.toString() }
        val granted = roots.filterTo(HashSet()) { root -> root in persisted && (!verifyReadable || readable(context, root)) }
        return StorageAccess(nativeGranted = nativeAccessGranted(context), grantedRoots = granted)
    }

    private fun readable(
        context: Context,
        root: String,
    ): Boolean = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(root))?.canRead() == true }.getOrDefault(false)

    /**
     * The native-access status row. A folder glyph (not a shield, which reads as "protected") on
     * the category's green tile; the chip alone carries the status.
     */
    fun nativeAccessRow(
        granted: Boolean,
        colors: KireiColors,
    ): KireiListRowSpec =
        KireiListRowSpec(
            title = nativeAccessTitle(),
            subtitle = "供 KRKR 引擎读取游戏",
            icon = Icons.Rounded.FolderOpen,
            tile = KireiTileColor.GREEN,
            trailing = KireiRowTrailing.Custom { AccessChip(granted, colors, missing = "需要授权") },
        )

    /** The primary action's label, written out per API level (never 「授权存储权限」). */
    fun nativeAccessAction(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "开启所有文件访问" else "授予存储权限"

    private fun nativeAccessGranted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun nativeAccessTitle(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "所有文件访问" else "存储权限"
}
