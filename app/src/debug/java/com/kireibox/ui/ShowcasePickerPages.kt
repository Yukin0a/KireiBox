package com.kireibox.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.EngineType

internal enum class ShowcasePicker(val id: String) {
    /** 选择游戏目录 (built-in browser). `pickers` is an alias of this one. */
    DIRECTORY("picker-dir"),

    /** 选择 XP3 启动文件 for a KIRIKIRI game. */
    XP3("picker-xp3"),

    /** 选择 Android 应用. */
    ANDROID_APP("picker-app"),

    /** 导入桌面游戏快捷方式. */
    SHORTCUTS("picker-shortcuts"),
    ;

    companion object {
        fun fromId(id: String): ShowcasePicker? = if (id == "pickers") DIRECTORY else entries.firstOrNull { it.id == id }
    }
}

/**
 * The four file/directory pickers of PickerPages.kt on fixture data. They are full-screen pages in the
 * app (no dock); back or any "select" button finishes the showcase. The file browser lists a throw-away
 * tree under the app cache, not real storage.
 */
@Composable
internal fun ShowcasePickerPage(
    activity: ComponentActivity,
    colors: KireiColors,
    picker: ShowcasePicker,
) {
    val games = remember { ShowcaseFixtures.libraryGames(activity) }
    ShowcasePageHost(activity, colors, dockTab = null, games = games) { env ->
        val finish: () -> Unit = { activity.finish() }
        when (picker) {
            ShowcasePicker.DIRECTORY -> {
                val roots = remember { ShowcaseFixtures.fileBrowserRoots(activity) }
                BuiltinFileBrowserPage(
                    request =
                        BuiltinFileBrowserRequest(
                            startPath = roots.first.path,
                            appPath = roots.second.path,
                        ),
                    colors = colors,
                    haze = env.haze,
                    onDismiss = finish,
                    onUseSaf = {},
                    onSelected = { finish() },
                )
            }
            ShowcasePicker.XP3 ->
                Xp3PickerPage(
                    picker = ShowcaseFixtures.xp3Picker(games.first { it.engine == EngineType.KIRIKIRI }),
                    colors = colors,
                    haze = env.haze,
                    onDismiss = finish,
                    onSelected = { finish() },
                )
            ShowcasePicker.ANDROID_APP ->
                AndroidAppPickerPage(
                    picker = ShowcaseFixtures.androidAppPicker(),
                    colors = colors,
                    haze = env.haze,
                    onDismiss = finish,
                    onSelected = { finish() },
                )
            ShowcasePicker.SHORTCUTS -> {
                var ui by remember { mutableStateOf(ShowcaseFixtures.shortcutPicker()) }
                ExternalShortcutPickerPage(
                    picker = ui,
                    colors = colors,
                    haze = env.haze,
                    onDismiss = finish,
                    onToggle = { key -> ui = ui.copy(selectedKeys = ExternalShortcutPickerPolicy.toggle(ui.entries, ui.selectedKeys, key)) },
                    onToggleAll = { ui = ui.copy(selectedKeys = ExternalShortcutPickerPolicy.toggleAll(ui.entries, ui.selectedKeys)) },
                    onImport = finish,
                )
            }
        }
    }
}
