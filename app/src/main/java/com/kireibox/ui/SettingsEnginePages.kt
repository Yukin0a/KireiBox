package com.kireibox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSliderSpec
import com.kireibox.ons.OnsGlobalSettings
import com.yuki.yukihub.launcherbridge.LauncherEngineOptions
import com.yuki.yukihub.model.EngineType
import kotlin.math.roundToInt

// 全局引擎设置: the list of the engines, and the global defaults of ONS, Artemis and Tyrano
// (KRKR's page is SettingsKrkrPages.kt). A single game's own options stay in its sheet.

/** The engine pages in the order the list shows them. */
private val EnginePages =
    listOf(
        KireiSettingsPage.KRKR to EngineType.KIRIKIRI,
        KireiSettingsPage.ONS to EngineType.ONS,
        KireiSettingsPage.ARTEMIS to EngineType.ARTEMIS,
        KireiSettingsPage.TYRANO to EngineType.TYRANO,
        KireiSettingsPage.WINLATOR to EngineType.WINDOWS,
    )

private const val ENGINE_PAGE_SUFFIX = " 引擎设置"

@Composable
internal fun EngineHubSettings(
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    KireiListGroup(colors, SettingsGroupSpacing) {
        EnginePages.forEach { (page, engine) ->
            item {
                KireiListRow(
                    KireiListRowSpec(
                        // The page is already 全局引擎设置: each row is just the engine (KRKR, ONS, …).
                        title = settingsPageTitle(page).removeSuffix(ENGINE_PAGE_SUFFIX),
                        icon = libraryEngineIcon(engine),
                        tile = addGameEngineTile(engine),
                        trailing = KireiRowTrailing.Chevron,
                    ),
                    colors,
                    onClick = { viewModel.openSettingsPage(page) },
                )
            }
        }
    }
}

/** 游戏界面屏幕方向 (Tyranor's three options), read by every built-in engine at launch. */
@Composable
internal fun EngineOrientationRow(colors: KireiColors) {
    val context = LocalContext.current
    var orientation by remember { mutableStateOf(LauncherEngineOptions.orientation(context)) }
    SettingsSegmentedRow(
        SettingsSegmentedRowSpec(
            "游戏屏幕方向",
            LauncherEngineOptions.ORIENTATIONS.map { KireiSegment(it.toString(), engineOrientationLabel(it)) },
            orientation.toString(),
        ),
        colors,
    ) {
        orientation = it.toInt()
        LauncherEngineOptions.setOrientation(context, orientation)
    }
}

private fun engineOrientationLabel(value: Int): String =
    when (value) {
        LauncherEngineOptions.ORIENTATION_LEFT_LANDSCAPE -> "左横屏"
        LauncherEngineOptions.ORIENTATION_RIGHT_LANDSCAPE -> "右横屏"
        else -> "传感器"
    }

private const val SAVE_DIR_FOOTER = "关闭后存档写入游戏目录。"

@Composable
internal fun OnsSettingsPage(colors: KireiColors) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(OnsGlobalSettings.load(context)) }
    val update: (OnsGlobalSettings) -> Unit = {
        settings = it
        OnsGlobalSettings.save(context, it)
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "显示") {
        item { EngineOrientationRow(colors) }
        item { SwitchRow("保持屏幕常亮", settings.keepScreenOn, colors) { update(settings.copy(keepScreenOn = it)) } }
        item { SwitchRow("拉伸全屏", settings.stretchFull, colors) { update(settings.copy(stretchFull = it)) } }
        item { SwitchRow("忽略刘海", settings.ignoreCutout, colors) { update(settings.copy(ignoreCutout = it)) } }
        item { SwitchRow("画面锐化", settings.sharpness, colors) { update(settings.copy(sharpness = it)) } }
        item {
            SettingsSliderRow(
                settings.sharpnessValue,
                KireiSliderSpec(
                    label = "锐化强度",
                    valueText = OnsGlobalSettings.formatSharpness(settings.sharpnessValue),
                    valueRange = OnsGlobalSettings.MIN_SHARPNESS..OnsGlobalSettings.MAX_SHARPNESS,
                    // 0.5 stops: TalkBack and arrow keys land on round values.
                    steps = ((OnsGlobalSettings.MAX_SHARPNESS - OnsGlobalSettings.MIN_SHARPNESS) / .5f).roundToInt() - 1,
                    enabled = settings.sharpness,
                ),
                colors,
            ) { update(settings.copy(sharpnessValue = (it * 2).roundToInt() / 2f)) }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "兼容") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("文本编码", OnsGlobalSettings.ENCODINGS.map { KireiSegment(it, onsEncodingLabel(it)) }, settings.encoding),
                colors,
            ) { update(settings.copy(encoding = it)) }
        }
        item { SwitchRow("禁用视频", settings.disableVideo, colors) { update(settings.copy(disableVideo = it)) } }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "存档", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("独立存档目录", settings.scopedSaveDir, colors) { update(settings.copy(scopedSaveDir = it)) } }
    }
}

private fun onsEncodingLabel(value: String): String =
    when (value) {
        "utf8" -> "UTF-8"
        "sjis" -> "Shift-JIS"
        else -> "GBK"
    }

@Composable
internal fun ArtemisSettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "显示") {
        item { EngineOrientationRow(colors) }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "存档", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("独立存档目录", state.settings?.artemisScopedSaveDir != false, colors, viewModel::setArtemisScopedSaveDir) }
    }
}

@Composable
internal fun TyranoSettingsPage(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val context = LocalContext.current
    var keepScreenOn by remember { mutableStateOf(LauncherEngineOptions.tyranoKeepScreenOn(context)) }
    var ignoreCutout by remember { mutableStateOf(LauncherEngineOptions.tyranoIgnoreCutout(context)) }
    KireiListGroup(colors, SettingsGroupSpacing, title = "显示") {
        item { EngineOrientationRow(colors) }
        item {
            SwitchRow("保持屏幕常亮", keepScreenOn, colors) {
                keepScreenOn = it
                LauncherEngineOptions.setTyranoKeepScreenOn(context, it)
            }
        }
        item {
            SwitchRow("忽略刘海", ignoreCutout, colors) {
                ignoreCutout = it
                LauncherEngineOptions.setTyranoIgnoreCutout(context, it)
            }
        }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "存档", footer = SAVE_DIR_FOOTER) {
        item { SwitchRow("独立存档目录", state.settings?.tyranoScopedSaveDir != false, colors, viewModel::setTyranoScopedSaveDir) }
    }
}
