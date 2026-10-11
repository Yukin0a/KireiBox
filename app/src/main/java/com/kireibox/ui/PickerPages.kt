package com.kireibox.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiChip
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTone
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSearchField
import com.kireibox.designsystem.components.KireiSearchFieldOptions
import com.kireibox.designsystem.components.KireiStatusChip
import com.kireibox.designsystem.components.KireiStatusTone
import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import dev.chrisbanes.haze.HazeState
import java.io.File

// Pickers for XP3 launch targets, Android apps, desktop shortcuts and the built-in file browser.
// All four share PickerPage (PickerScaffold.kt): large title, group-card rows, primary bottom action.

private val AppIconSize = 40.dp
private val SelectionMarkSize = 24.dp
private val SelectionMarkStroke = 1.5.dp

@Composable
internal fun Xp3PickerPage(
    picker: Xp3PickerUi,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val currentTarget = picker.game.launchTarget.orEmpty().replace('\\', '/')
    PickerPage(PickerPageSpec(picker.kind.pageTitle, onDismiss), colors, haze) {
        picker.game.title?.takeIf(String::isNotBlank)?.let { groupCaption(it, colors, "xp3-game") }
        when {
            picker.loading -> groupSkeleton(colors)
            picker.error != null -> pickerErrorRow(picker.error, colors)
            picker.candidates.isEmpty() -> groupStateRow("xp3-empty", colors, KireiListRowSpec(picker.kind.emptyText))
            else ->
                groupItems(picker.candidates, colors, { it }) { target ->
                    val checked = target.equals(currentTarget, ignoreCase = true)
                    KireiListRow(
                        KireiListRowSpec(
                            title = target.substringAfterLast('/'),
                            // Candidates are paths relative to the game folder: the folder is the only extra information.
                            subtitle = target.substringBeforeLast('/', "").takeIf(String::isNotEmpty),
                            icon = Icons.Rounded.Description,
                            tile = KireiTileColor.INDIGO,
                            trailing = if (checked) KireiRowTrailing.Custom { KireiStatusChip("当前", KireiStatusTone.ACCENT, colors) } else KireiRowTrailing.None,
                            subtitleMaxLines = 1,
                        ),
                        colors,
                        Modifier.semantics { this.selected = checked },
                    ) { onSelected(target) }
                }
        }
    }
}

@Composable
internal fun AndroidAppPickerPage(
    picker: AndroidAppPickerUi,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onSelected: (AndroidAppEntry) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    DisposableEffect(Unit) { onDispose { AndroidAppIcons.clear() } }
    var query by remember { mutableStateOf("") }
    val normalized = query.trim()
    val visible =
        remember(picker.entries, normalized) {
            if (normalized.isEmpty()) {
                picker.entries
            } else {
                picker.entries.filter {
                    it.label.contains(normalized, ignoreCase = true) ||
                        it.packageName.contains(normalized, ignoreCase = true)
                }
            }
        }
    PickerPage(PickerPageSpec("选择 Android 应用", onDismiss), colors, haze) {
        pickerSearch(query, { query = it }, "搜索应用名称或包名", colors)
        when {
            picker.loading -> groupSkeleton(colors)
            picker.error != null -> pickerErrorRow(picker.error, colors)
            visible.isEmpty() -> groupStateRow("app-empty", colors, KireiListRowSpec(if (query.isBlank()) "没有查询到可启动的应用" else "没有匹配的应用"))
            else ->
                groupItems(visible, colors, AndroidAppEntry::componentKey) { entry ->
                    KireiListRow(
                        KireiListRowSpec(
                            title = entry.label,
                            subtitle = entry.packageName,
                            trailing = KireiRowTrailing.Chevron,
                            tileSize = AppIconSize,
                            leading = { AndroidAppIcon(entry, colors, AppIconSize) },
                            subtitleMaxLines = 1,
                        ),
                        colors,
                    ) { onSelected(entry) }
                }
        }
    }
}

@Composable
internal fun ExternalShortcutPickerPage(
    picker: ExternalShortcutPickerUi,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onImport: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var query by remember { mutableStateOf("") }
    val normalized = query.trim()
    val visible =
        remember(picker.entries, normalized) {
            ExternalShortcutPickerPolicy.visible(picker.entries, normalized)
        }
    val selectable = picker.entries.count { !it.imported }
    val allSelected = selectable > 0 && picker.selectedKeys.size == selectable
    val showSelectAll = !picker.loading && picker.error == null && selectable > 0
    val spec =
        PickerPageSpec(
            title = "导入桌面游戏快捷方式",
            onBack = onDismiss,
            topActions = {
                if (showSelectAll) {
                    KireiButton(
                        if (allSelected) "取消全选" else "全选未导入",
                        colors,
                        options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                        onClick = onToggleAll,
                    )
                }
            },
            bottomAction = PickerAction("导入所选（${picker.selectedKeys.size}）", onImport, enabled = picker.selectedKeys.isNotEmpty() && !picker.loading),
        )
    PickerPage(spec, colors, haze) {
        pickerSearch(query, { query = it }, "搜索标题或应用", colors)
        when {
            picker.loading -> groupSkeleton(colors)
            picker.error != null -> pickerErrorRow(picker.error, colors)
            visible.isEmpty() -> groupStateRow("shortcut-empty", colors, KireiListRowSpec(if (query.isBlank()) "没有找到可导入的桌面快捷方式" else "没有匹配的快捷方式"))
            else -> groupItems(visible, colors, ExternalShortcutPickerEntry::stableKey) { entry -> ShortcutRow(entry, entry.stableKey in picker.selectedKeys, colors, onToggle) }
        }
    }
}

@Composable
private fun ShortcutRow(
    entry: ExternalShortcutPickerEntry,
    selected: Boolean,
    colors: KireiColors,
    onToggle: (String) -> Unit,
) {
    val shortcut = entry.shortcut
    KireiListRow(
        KireiListRowSpec(
            title = shortcutRowTitle(shortcut),
            subtitle = shortcutRowDetail(shortcut),
            icon = Icons.Rounded.Gamepad,
            tile = if (entry.imported) KireiTileColor.GRAY else KireiTileColor.INDIGO,
            trailing =
                KireiRowTrailing.Custom {
                    if (entry.imported) KireiStatusChip("已导入", KireiStatusTone.POSITIVE, colors) else PickerSelectionMark(selected, colors)
                },
        ),
        colors,
        // An imported row is display-only: no click, one TalkBack stop with its status chip.
        Modifier.semantics { if (!entry.imported) stateDescription = if (selected) "已选择" else "未选择" },
        onClick = if (entry.imported) null else ({ onToggle(entry.stableKey) }),
    )
}

/** The shortcut's own title; the id is the last resort and then shows as the title, never beside a real one. */
internal fun shortcutRowTitle(shortcut: ExternalShortcutDescriptor): String = shortcut.title.ifBlank { shortcut.appName }.ifBlank { shortcut.shortcutId }

/** Source app and how the shortcut is kept, plus whether importing it still needs Shizuku. */
internal fun shortcutRowDetail(shortcut: ExternalShortcutDescriptor): String =
    listOfNotNull(
        shortcut.appName.takeIf { it.isNotBlank() && it != shortcutRowTitle(shortcut) },
        if (shortcut.pinned) "桌面固定" else "动态",
        "导入后免 Shizuku".takeIf { shortcut.directLaunchTarget != null },
    ).joinToString(" · ")

/** Round selection mark of a multi-select row; the row itself carries the state for TalkBack. */
@Composable
internal fun PickerSelectionMark(
    selected: Boolean,
    colors: KireiColors,
) {
    Box(
        Modifier.size(SelectionMarkSize)
            .background(if (selected) colors.accent else Color.Transparent, KireiShapes.full)
            .border(SelectionMarkStroke, if (selected) colors.accent else colors.secondary, KireiShapes.full),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(SelectionMarkSize - KireiSpacing.xs))
    }
}

private fun LazyListScope.pickerSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    colors: KireiColors,
) {
    item(key = "picker-search") {
        KireiSearchField(query, onQueryChange, colors, Modifier.padding(bottom = KireiSpacing.md), KireiSearchFieldOptions(placeholder = placeholder))
    }
}

internal fun LazyListScope.pickerErrorRow(
    message: String,
    colors: KireiColors,
) {
    groupStateRow("picker-error", colors, KireiListRowSpec(title = message, icon = Icons.Rounded.ErrorOutline, tone = KireiRowTone.DESTRUCTIVE))
}

@Composable
internal fun BuiltinFileBrowserPage(
    request: BuiltinFileBrowserRequest,
    colors: KireiColors,
    haze: HazeState?,
    onDismiss: () -> Unit,
    onUseSaf: () -> Unit,
    onSelected: (Uri) -> Unit
) {
    val internalRoot = remember(request) { File(request.startPath).safeCanonical() }
    val appRoot = remember(request) { File(request.appPath).safeCanonical() }
    var navigationRoot by remember(request) { mutableStateOf(internalRoot) }
    var current by remember(request) { mutableStateOf(internalRoot) }
    val entries =
        remember(current) {
            runCatching { current.listFiles()?.toList().orEmpty() }.getOrDefault(emptyList())
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    val goBack: () -> Unit = {
        val parent = current.parentFile?.safeCanonical()
        if (parent != null && current.safeCanonical() != navigationRoot.safeCanonical() &&
            parent.path.startsWith(navigationRoot.safeCanonical().path)
        ) {
            current = parent
        } else {
            onDismiss()
        }
    }
    BackHandler(onBack = goBack)
    val breadcrumbs =
        remember(current, navigationRoot) {
            buildList {
                var cursor: File? = current.safeCanonical()
                val rootPath = navigationRoot.safeCanonical().path
                while (cursor != null && cursor.path.startsWith(rootPath)) {
                    add(cursor)
                    if (cursor.path == rootPath) break
                    cursor = cursor.parentFile
                }
            }.asReversed()
        }
    val spec =
        PickerPageSpec(
            title = "选择游戏目录",
            onBack = goBack,
            topActions = {
                KireiButton("系统 SAF", colors, options = KireiButtonOptions(style = KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL), onClick = onUseSaf)
            },
            bottomAction = PickerAction("选择此文件夹", { onSelected(Uri.fromFile(current)) }),
            scrollResetKey = current,
        )
    PickerPage(spec, colors, haze) {
        browserLocations(navigationRoot == internalRoot, navigationRoot == appRoot, colors, onUseSaf) { internal ->
            navigationRoot = if (internal) internalRoot else appRoot
            current = navigationRoot
        }
        if (!BuiltinFileBrowserPolicy.shouldHideCrumbRow(breadcrumbs.size)) {
            item(key = "browser-crumbs") {
                BrowserCrumbs(breadcrumbs, current, if (navigationRoot == internalRoot) "内部存储" else "应用目录", colors) { current = it }
            }
        }
        if (entries.isEmpty()) {
            groupStateRow("browser-empty", colors, KireiListRowSpec("这个文件夹里没有可选择的内容"))
        } else {
            groupItems(entries, colors, { it.path }) { file ->
                BrowserRow(file, colors) { current = file.safeCanonical() }
            }
        }
    }
}

/** Storage roots as selectable chips; the SD card / restricted storage entry is an action, so it is a button. */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.browserLocations(
    internalSelected: Boolean,
    appSelected: Boolean,
    colors: KireiColors,
    onUseSaf: () -> Unit,
    onRoot: (isInternal: Boolean) -> Unit,
) {
    item(key = "browser-locations") {
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = KireiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
        ) {
            KireiChip("内部存储", internalSelected, colors) { onRoot(true) }
            KireiChip("应用目录", appSelected, colors) { onRoot(false) }
            KireiButton("SD 卡 / 受限存储", colors, options = KireiButtonOptions(style = KireiButtonStyle.SECONDARY, size = KireiButtonSize.SMALL), onClick = onUseSaf)
        }
    }
}

@Composable
private fun BrowserCrumbs(
    crumbs: List<File>,
    current: File,
    rootLabel: String,
    colors: KireiColors,
    onOpen: (File) -> Unit,
) {
    LazyRow(Modifier.fillMaxWidth().padding(bottom = KireiSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(crumbs, key = { _, file -> file.path }) { index, file ->
            if (index > 0) Icon(Icons.Rounded.ChevronRight, null, tint = colors.tertiary, modifier = Modifier.size(KireiSpacing.lg))
            KireiButton(
                if (index == 0) rootLabel else file.name,
                colors,
                options = KireiButtonOptions(style = if (file == current) KireiButtonStyle.PLAIN else KireiButtonStyle.TEXT, size = KireiButtonSize.SMALL),
                onClick = { onOpen(file) },
            )
        }
    }
}

@Composable
private fun BrowserRow(
    file: File,
    colors: KireiColors,
    onOpen: () -> Unit,
) {
    // File system calls once per row, not on every recomposition.
    val detail = remember(file.path) { BuiltinFileBrowserPolicy.detailLabel(file) }
    KireiListRow(
        KireiListRowSpec(
            title = file.name,
            subtitle = detail,
            icon = if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
            tile = if (file.isDirectory) KireiTileColor.BLUE else KireiTileColor.GRAY,
            trailing = if (file.isDirectory) KireiRowTrailing.Chevron else KireiRowTrailing.None,
            subtitleMaxLines = 1,
        ),
        colors,
        // Choosing a folder only needs folders to open; files are merely listed.
        onClick = if (file.isDirectory) onOpen else null,
    )
}

internal fun File.safeCanonical(): File = BuiltinFileBrowserPolicy.safeCanonicalPath(this)
