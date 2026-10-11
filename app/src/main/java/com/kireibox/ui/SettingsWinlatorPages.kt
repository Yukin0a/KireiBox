package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.external.winlator.WinlatorAvailability
import com.kireibox.external.winlator.WinlatorChoices
import com.kireibox.external.winlator.WinlatorGameOverride
import com.kireibox.external.winlator.WinlatorSettings
import com.kireibox.external.winlator.WinlatorSettingsStore
import com.yuki.yukihub.model.Game
import dev.chrisbanes.haze.HazeState

// PC 游戏 (外置 Winlator): the global defaults page under 全局引擎设置, and the per-game page opened
// from a game's sheet. Both only choose what is sent to Winlator-CN; a row left on 「跟随容器」 sends
// nothing, so the container's own configuration decides.

private const val FOLLOW_CONTAINER = "跟随容器"
private const val FOLLOW_GLOBAL = "跟随全局"
private const val CONTAINER_FOOTER = "留空时由 Winlator 选择：最近使用的容器，或唯一的容器。"
private const val SAVE_FOOTER = "开启后每次启动都会弹出确认。"
private const val GAME_FOOTER = "未设置的项沿用 Winlator 引擎设置。"
private const val CONTAINER_ID_DIGITS = 4
internal const val WINLATOR_CN_RELEASES_URL = "https://github.com/hostei33/winlator-cn/releases"
private const val WINLATOR_CN_REPO_LABEL = "github.com/hostei33/winlator-cn"

@Composable
internal fun WinlatorSettingsPage(
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val context = LocalContext.current
    val store = remember { WinlatorSettingsStore(context) }
    var settings by remember { mutableStateOf(store.loadGlobal()) }
    val status = rememberWinlatorStatus()
    val update: (WinlatorSettings) -> Unit = {
        settings = it
        store.saveGlobal(it)
    }
    val installed = status.availability == WinlatorAvailability.READY
    KireiListGroup(colors, SettingsGroupSpacing) {
        item {
            SettingsAnchor("打开 Winlator") {
                if (installed) {
                    KireiListRow(
                        KireiListRowSpec(
                            "打开 Winlator",
                            icon = Icons.Rounded.DesktopWindows,
                            tile = KireiTileColor.BLUE,
                            trailing = KireiRowTrailing.Value(winlatorInstallLabel(status.availability)),
                        ),
                        colors,
                        onClick = {
                            if (!openWinlatorApp(context, status)) viewModel.showNotice("无法打开 Winlator")
                        },
                    )
                } else {
                    WinlatorDownloadRow(status.availability, colors) { viewModel.showNotice("无法打开浏览器") }
                }
            }
        }
        item {
            SettingsAnchor("创建容器教程") {
                KireiListRow(
                    KireiListRowSpec("创建容器教程", icon = Icons.Rounded.MenuBook, tile = KireiTileColor.ORANGE, trailing = KireiRowTrailing.Chevron),
                    colors,
                    onClick = viewModel::openWinlatorGuide,
                )
            }
        }
    }
    WinlatorContainerGroup(settings, update, colors, installed)
    WinlatorGraphicsGroup(settings, update, colors, installed)
    WinlatorLocaleGroup(settings, update, colors, installed)
    WinlatorAdvancedGroup(settings, update, colors, installed)
}

/** Shown in place of 「打开 Winlator」 while it is missing or too old: opens the Winlator-CN releases on GitHub. */
@Composable
internal fun WinlatorDownloadRow(
    availability: WinlatorAvailability,
    colors: KireiColors,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    KireiListRow(
        KireiListRowSpec(
            if (availability == WinlatorAvailability.UNSUPPORTED) "更新 Winlator-CN" else "下载 Winlator-CN",
            subtitle = WINLATOR_CN_REPO_LABEL,
            icon = Icons.Rounded.Download,
            tile = KireiTileColor.BLUE,
            trailing = KireiRowTrailing.Value(winlatorInstallLabel(availability)),
        ),
        colors,
        onClick = { openKireiLink(context, WINLATOR_CN_RELEASES_URL, onFailure) },
    )
}

@Composable
private fun WinlatorContainerGroup(
    settings: WinlatorSettings,
    update: (WinlatorSettings) -> Unit,
    colors: KireiColors,
    enabled: Boolean,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "容器", footer = CONTAINER_FOOTER) {
        item {
            SettingsAnchor("容器名称") {
                WinlatorTextField(settings.containerName, WinlatorTextSpec("容器名称", "自动选择", enabled = enabled), colors) { update(settings.copy(containerName = it)) }
            }
        }
        item {
            SettingsAnchor("容器 ID") {
                WinlatorTextField(settings.containerId.takeIf { it > 0 }?.toString().orEmpty(), WinlatorTextSpec("容器 ID", "自动选择", numeric = true, enabled = enabled), colors) {
                    update(settings.copy(containerId = it.filter(Char::isDigit).take(CONTAINER_ID_DIGITS).toIntOrNull() ?: 0))
                }
            }
        }
    }
}

@Composable
private fun WinlatorGraphicsGroup(
    settings: WinlatorSettings,
    update: (WinlatorSettings) -> Unit,
    colors: KireiColors,
    enabled: Boolean,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "图形") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("图形驱动", containerOptions(WinlatorChoices.graphicsDrivers, ::graphicsDriverLabel), settings.graphicsDriver, enabled = enabled),
                colors,
            ) { update(settings.copy(graphicsDriver = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("图形加速", containerOptions(WinlatorChoices.dxwrappers, ::dxwrapperLabel), settings.dxwrapper, enabled = enabled),
                colors,
            ) { update(settings.copy(dxwrapper = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("分辨率", containerOptions(WinlatorChoices.screenSizes), settings.screenSize, enabled = enabled),
                colors,
            ) { update(settings.copy(screenSize = it)) }
        }
    }
}

@Composable
private fun WinlatorLocaleGroup(
    settings: WinlatorSettings,
    update: (WinlatorSettings) -> Unit,
    colors: KireiColors,
    enabled: Boolean,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "语言与时区") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("语言环境", containerOptions(WinlatorChoices.locales), settings.lcAll, enabled = enabled),
                colors,
            ) { update(settings.copy(lcAll = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("时区", containerOptions(WinlatorChoices.timezones), settings.tz, enabled = enabled),
                colors,
            ) { update(settings.copy(tz = it)) }
        }
    }
}

@Composable
private fun WinlatorAdvancedGroup(
    settings: WinlatorSettings,
    update: (WinlatorSettings) -> Unit,
    colors: KireiColors,
    enabled: Boolean,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "高级", footer = if (settings.save) SAVE_FOOTER else null) {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("Box64 预设", containerOptions(WinlatorChoices.box64Presets, ::box64PresetLabel), settings.box64Preset, enabled = enabled),
                colors,
            ) { update(settings.copy(box64Preset = it)) }
        }
        item {
            SettingsAnchor("环境变量") {
                WinlatorTextField(settings.envVars, WinlatorTextSpec("环境变量", ENV_VARS_HINT, enabled = enabled), colors) { update(settings.copy(envVars = it)) }
            }
        }
        item {
            SettingsAnchor("写入容器配置") {
                KireiListRow(KireiListRowSpec("写入容器配置", trailing = KireiRowTrailing.Toggle(settings.save) { update(settings.copy(save = it)) }, enabled = enabled), colors)
            }
        }
    }
}

private const val ENV_VARS_HINT = "名称=值，多个用空格分隔"

/**
 * One game's own Winlator choices (from its sheet's 「Winlator 游戏设置」): the settings large-title
 * template, the editor groups and a pinned 保存 button. Back closes without saving.
 */
@Composable
internal fun WinlatorGameSettingsPage(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
) {
    var override by remember(game.id, state.winlatorOverride) { mutableStateOf(state.winlatorOverride) }
    val context = LocalContext.current
    val global = remember { WinlatorSettingsStore(context).loadGlobal() }
    BackHandler(onBack = viewModel::closeWinlatorEditor)
    val scroll = rememberScrollState()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = KireiSpacing.lg
    val frame =
        SettingsFrame(
            left = safe.calculateLeftPadding(direction) + gutter,
            right = safe.calculateRightPadding(direction) + gutter,
            top = safe.calculateTopPadding(),
            contentTop = KireiTopBarDefaults.CompactHeight,
            bottom = KireiSpacing.md,
            maxContentWidth = KrkrPageMaxWidth,
        )
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            SettingsLargeTitleLayout(
                SettingsLayoutSpec(SettingsHeaderSpec("Winlator 游戏设置", onBack = viewModel::closeWinlatorEditor), frame, haze),
                colors,
                scroll,
            ) {
                KireiListGroup(colors, SettingsGroupSpacing, footer = GAME_FOOTER) {
                    item {
                        KireiListRow(
                            KireiListRowSpec(
                                title = game.title.orEmpty(),
                                subtitle = GameLocationLabel.forGame(context, game),
                                leading = { SettingsCoverThumbnail(game, colors) },
                                tileSize = SettingsCoverThumbWidth,
                            ),
                            colors,
                        )
                    }
                }
                WinlatorGameEditor(override, global, { override = it }, colors)
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(KireiSpacing.lg).background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        }
        KrkrSaveBar(colors, frame, safe.calculateBottomPadding()) { viewModel.saveWinlatorOverride(game, override) }
    }
}

@Composable
internal fun WinlatorGameEditor(
    override: WinlatorGameOverride,
    global: WinlatorSettings,
    onChange: (WinlatorGameOverride) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "容器") {
        item {
            SettingsAnchor("容器名称") {
                WinlatorTextField(override.containerName.orEmpty(), WinlatorTextSpec("容器名称", global.containerName.ifBlank { FOLLOW_GLOBAL }), colors) {
                    onChange(override.copy(containerName = it.ifEmpty { null }))
                }
            }
        }
        item {
            SettingsAnchor("容器 ID") {
                val hint = global.containerId.takeIf { it > 0 }?.toString() ?: FOLLOW_GLOBAL
                WinlatorTextField(override.containerId?.toString().orEmpty(), WinlatorTextSpec("容器 ID", hint, numeric = true), colors) {
                    onChange(override.copy(containerId = it.filter(Char::isDigit).take(CONTAINER_ID_DIGITS).toIntOrNull()))
                }
            }
        }
    }
    WinlatorGameGraphicsGroup(override, onChange, colors)
    WinlatorGameLocaleGroup(override, onChange, colors)
    WinlatorGameAdvancedGroup(override, global, onChange, colors)
}

@Composable
private fun WinlatorGameGraphicsGroup(
    override: WinlatorGameOverride,
    onChange: (WinlatorGameOverride) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "图形") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("图形驱动", gameOptions(WinlatorChoices.graphicsDrivers, ::graphicsDriverLabel), override.graphicsDriver),
                colors,
            ) { onChange(override.copy(graphicsDriver = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("图形加速", gameOptions(WinlatorChoices.dxwrappers, ::dxwrapperLabel), override.dxwrapper),
                colors,
            ) { onChange(override.copy(dxwrapper = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("分辨率", gameOptions(WinlatorChoices.screenSizes), override.screenSize),
                colors,
            ) { onChange(override.copy(screenSize = it)) }
        }
    }
}

@Composable
private fun WinlatorGameLocaleGroup(
    override: WinlatorGameOverride,
    onChange: (WinlatorGameOverride) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "语言与时区") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("语言环境", gameOptions(WinlatorChoices.locales), override.lcAll),
                colors,
            ) { onChange(override.copy(lcAll = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("时区", gameOptions(WinlatorChoices.timezones), override.tz),
                colors,
            ) { onChange(override.copy(tz = it)) }
        }
    }
}

@Composable
private fun WinlatorGameAdvancedGroup(
    override: WinlatorGameOverride,
    global: WinlatorSettings,
    onChange: (WinlatorGameOverride) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "高级", footer = if (override.save ?: global.save) SAVE_FOOTER else null) {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("Box64 预设", gameOptions(WinlatorChoices.box64Presets, ::box64PresetLabel), override.box64Preset),
                colors,
            ) { onChange(override.copy(box64Preset = it)) }
        }
        item {
            SettingsAnchor("环境变量") {
                WinlatorTextField(override.envVars.orEmpty(), WinlatorTextSpec("环境变量", global.envVars.ifBlank { ENV_VARS_HINT }), colors) {
                    onChange(override.copy(envVars = it.ifEmpty { null }))
                }
            }
        }
        item {
            WinlatorTextField(override.execArgs.orEmpty(), WinlatorTextSpec("启动参数", "追加给游戏的命令行参数"), colors) {
                onChange(override.copy(execArgs = it.ifEmpty { null }))
            }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec(
                    "写入容器配置",
                    listOf(SettingsChoiceOption<Boolean?>(null, FOLLOW_GLOBAL), SettingsChoiceOption<Boolean?>(true, "开"), SettingsChoiceOption<Boolean?>(false, "关")),
                    override.save,
                ),
                colors,
            ) { onChange(override.copy(save = it)) }
        }
    }
}

private data class WinlatorTextSpec(
    val label: String,
    val placeholder: String,
    val numeric: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
private fun WinlatorTextField(
    value: String,
    spec: WinlatorTextSpec,
    colors: KireiColors,
    onValueChange: (String) -> Unit,
) {
    KireiTextField(
        value = value,
        onValueChange = onValueChange,
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(horizontal = KireiSpacing.groupInner, vertical = KireiSpacing.sm),
        options =
            KireiTextFieldOptions(
                label = spec.label,
                placeholder = spec.placeholder,
                enabled = spec.enabled,
                keyboardOptions = if (spec.numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            ),
    )
}

/** 「跟随容器」 (sends nothing) first, then the values Winlator accepts. */
private fun containerOptions(
    values: List<String>,
    label: (String) -> String = { it },
): List<SettingsChoiceOption<String>> = listOf(SettingsChoiceOption("", FOLLOW_CONTAINER)) + values.map { SettingsChoiceOption(it, label(it)) }

/** A game adds 「跟随全局」 (null) in front: 「跟随容器」 there is a deliberate "send nothing" that beats the global value. */
private fun gameOptions(
    values: List<String>,
    label: (String) -> String = { it },
): List<SettingsChoiceOption<String?>> {
    val own = containerOptions(values, label).map { SettingsChoiceOption<String?>(it.value, it.label) }
    return listOf(SettingsChoiceOption<String?>(null, FOLLOW_GLOBAL)) + own
}

private val GraphicsDriverNames = mapOf("turnip" to "Turnip", "vortek" to "Vortek", "zink" to "Zink", "virgl" to "VirGL", "gladio" to "Gladio")

internal fun graphicsDriverLabel(value: String): String = value.split(',').joinToString(" + ") { GraphicsDriverNames[it] ?: it }

internal fun dxwrapperLabel(value: String): String =
    when (value) {
        "dxvk" -> "DXVK"
        "wined3d" -> "WineD3D"
        else -> value
    }

internal fun box64PresetLabel(value: String): String =
    when (value) {
        "STABILITY" -> "稳定"
        "CONSERVATIVE" -> "兼容"
        "INTERMEDIATE" -> "均衡"
        "PERFORMANCE" -> "性能"
        else -> value
    }
