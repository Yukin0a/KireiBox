package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSliderSpec
import com.kireibox.designsystem.components.KireiTextField
import com.kireibox.designsystem.components.KireiTextFieldOptions
import com.kireibox.designsystem.components.KireiTopBarDefaults
import com.kireibox.designsystem.components.LocalKireiListDivider
import com.kireibox.krkr.KrkrFontScalePatch
import com.kireibox.krkr.KrkrGameSettings
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.MotionMode
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt

// KRKR 引擎 (global defaults) and the per-game KRKR settings page opened from a game's sheet.

/** 引擎版本 options: four short labels, so an inline segmented control (spec §8). */
internal val KrkrEngineVersions = listOf("auto", "1.3.9", "1.3.4", "1.2.6")

@Composable
internal fun KrkrSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
    actions: KireiActions,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "启动") {
        item {
            SettingsAnchor("启动 KRKR 原生引擎") {
                KireiListRow(
                    KireiListRowSpec("启动 KRKR 原生引擎", icon = Icons.Rounded.PlayArrow, tile = KireiTileColor.ORANGE, trailing = KireiRowTrailing.Chevron),
                    colors,
                    onClick = actions.launchKrkrNative,
                )
            }
        }
    }
    KireiListGroup(
        colors,
        SettingsGroupSpacing,
        title = "全局默认",
        footer = "单个游戏的设置：在游戏库长按卡片，选择「KRKR 游戏设置」。",
    ) {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec(
                    title = "引擎版本",
                    segments = KrkrEngineVersions.map { KireiSegment(it, choiceLabel(it)) },
                    selected = state.settings?.krEngineVersion ?: "auto",
                ),
                colors,
                viewModel::setKrEngineVersion,
            )
        }
        item { SwitchRow("兼容模式", state.settings?.krCompatMode == true, colors, viewModel::setKrCompat) }
        item { SwitchRow("跳过开头信息弹窗", state.settings?.krSkipStartupInfoDialog == true, colors, viewModel::setKrSkipStartupInfoDialog) }
        item { EngineOrientationRow(colors) }
    }
}

/**
 * Per-game KRKR settings (from the game sheet's 「KRKR 游戏设置」): the settings large-title
 * template over the window's safe area, the editor groups and a pinned 保存 button. Back (or the
 * back button) closes without saving and returns to the game's sheet.
 */
@Composable
internal fun KrkrGameSettingsPage(
    game: Game,
    state: KireiUiState,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel
) {
    var settings by remember(game.id, state.krkrSettings) { mutableStateOf(state.krkrSettings) }
    BackHandler(onBack = viewModel::closeKrkrEditor)
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
                SettingsLayoutSpec(SettingsHeaderSpec("KRKR 游戏设置", onBack = viewModel::closeKrkrEditor), frame, haze),
                colors,
                scroll,
            ) {
                // Which game these settings belong to (cover, title, folder); its footer scopes the whole page.
                KireiListGroup(colors, SettingsGroupSpacing, footer = "下次启动时生效。") {
                    item {
                        KireiListRow(
                            KireiListRowSpec(
                                title = game.title.orEmpty(),
                                subtitle = GameLocationLabel.forGame(LocalContext.current, game),
                                leading = { SettingsCoverThumbnail(game, colors) },
                                tileSize = SettingsCoverThumbWidth,
                            ),
                            colors,
                        )
                    }
                }
                KrkrGameEditor(settings, { settings = it }, colors)
            }
            // Rows fade out into the pinned save bar instead of being cut at its hairline.
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(KireiSpacing.lg).background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        }
        KrkrSaveBar(colors, frame, safe.calculateBottomPadding()) { viewModel.saveKrkrSettings(game, settings) }
    }
}

internal val KrkrPageMaxWidth = 720.dp

/** The pinned primary 保存 button, separated from the scrolling groups by a hairline. */
@Composable
internal fun KrkrSaveBar(
    colors: KireiColors,
    frame: SettingsFrame,
    bottomInset: Dp,
    onSave: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth()
            .background(colors.background)
            .drawBehind {
                val stroke = KireiElevation.strokeWidth.toPx()
                drawLine(colors.stroke, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
            }
            .padding(start = frame.left, end = frame.right, top = KireiSpacing.sm, bottom = bottomInset + KireiSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        KireiButton(
            "保存此游戏设置",
            colors,
            Modifier.widthIn(max = frame.maxContentWidth).fillMaxWidth(),
            KireiButtonOptions(icon = Icons.Rounded.Check),
            onSave,
        )
    }
}

/** KRKR renderer / memory / thread values as the original Kirikiroid2 preference stores them. */
internal val KrkrRenderers = listOf("software", "opengl")
internal val KrkrMemoryLevels = listOf("unlimited", "low", "medium", "high")
internal val KrkrRenderThreads = listOf("auto", "1", "2", "3", "4", "6", "8")

internal fun krkrOptionLabel(value: String): String =
    when (value) {
        "software" -> "软件渲染"
        "opengl" -> "OpenGL"
        "unlimited" -> "不限"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        "auto" -> "自动"
        else -> "$value 线程"
    }

@Composable
internal fun KrkrGameEditor(
    settings: KrkrGameSettings,
    onChange: (KrkrGameSettings) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "启动") {
        item { SwitchRow("跳过开头信息弹窗", settings.skipStartupDialog, colors) { onChange(settings.copy(skipStartupDialog = it)) } }
    }
    KireiListGroup(colors, SettingsGroupSpacing, title = "渲染") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("渲染器", KrkrRenderers.map { KireiSegment(it, krkrOptionLabel(it)) }, settings.renderer),
                colors,
            ) { onChange(settings.copy(renderer = it)) }
        }
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec("渲染线程", KrkrRenderThreads.map { SettingsChoiceOption(it, krkrOptionLabel(it)) }, settings.renderThread),
                colors,
            ) { onChange(settings.copy(renderThread = it)) }
        }
        item { SwitchRow("纹理压缩", settings.textureCompression, colors) { onChange(settings.copy(textureCompression = it)) } }
    }
    KrkrPerformanceGroup(settings, onChange, colors)
    KrkrInterfaceGroup(settings, onChange, colors)
    KrkrFontGroup(settings, onChange, colors)
}

@Composable
private fun KrkrPerformanceGroup(
    settings: KrkrGameSettings,
    onChange: (KrkrGameSettings) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "性能") {
        item {
            SettingsSegmentedRow(
                SettingsSegmentedRowSpec("内存", KrkrMemoryLevels.map { KireiSegment(it, krkrOptionLabel(it)) }, settings.memoryUsage),
                colors,
            ) { onChange(settings.copy(memoryUsage = it)) }
        }
        item {
            SettingsSliderRow(
                settings.fpsLimit.toFloat(),
                // 5-FPS stops: TalkBack and arrow keys land on round values instead of 1/20 of the range.
                KireiSliderSpec(label = "FPS 限制", valueText = settings.fpsLimit.toString(), valueRange = 15f..240f, steps = 44),
                colors,
            ) { onChange(settings.copy(fpsLimit = it.roundToInt().coerceIn(15, 240))) }
        }
        item { SwitchRow("显示 FPS", settings.showFps, colors) { onChange(settings.copy(showFps = it)) } }
        item { SwitchRow("保持屏幕常亮", settings.keepScreenAwake, colors) { onChange(settings.copy(keepScreenAwake = it)) } }
    }
}

@Composable
private fun KrkrInterfaceGroup(
    settings: KrkrGameSettings,
    onChange: (KrkrGameSettings) -> Unit,
    colors: KireiColors,
) {
    KireiListGroup(colors, SettingsGroupSpacing, title = "界面与调试") {
        item {
            // Kirikiroid2's menu_handler_opa is the floating menu button's opacity (default 15%).
            SettingsSliderRow(
                settings.menuOpacity,
                KireiSliderSpec(label = "菜单按钮不透明度", valueText = percentLabel(settings.menuOpacity), valueRange = .05f..1f, steps = 18),
                colors,
            ) { onChange(settings.copy(menuOpacity = percentStop(it))) }
        }
        item {
            SettingsSliderRow(
                settings.cursorScale,
                KireiSliderSpec(label = "虚拟光标大小", valueText = percentLabel(settings.cursorScale), valueRange = .25f..2f, steps = 34),
                colors,
            ) { onChange(settings.copy(cursorScale = percentStop(it))) }
        }
        item { SwitchRow("输出引擎日志", settings.outputLog, colors) { onChange(settings.copy(outputLog = it)) } }
    }
}

@Composable
private fun KrkrFontGroup(
    settings: KrkrGameSettings,
    onChange: (KrkrGameSettings) -> Unit,
    colors: KireiColors,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    KireiListGroup(colors, SettingsGroupSpacing, title = "字体") {
        item {
            SettingsChoiceRow(
                SettingsChoiceSpec(
                    "调整字体大小",
                    (listOf(KrkrFontScalePatch.OFF) + KrkrFontScalePatch.Presets).map { SettingsChoiceOption(it, KrkrFontScalePatch.label(it)) },
                    KrkrFontScalePatch.snap(settings.fontScale),
                ),
                colors,
            ) { onChange(settings.copy(fontScale = it)) }
        }
        item {
            // One group row: the switch draws its divider only while the file-name field is shown.
            Column {
                CompositionLocalProvider(LocalKireiListDivider provides settings.forceDefaultFont) {
                    SwitchRow("强制使用默认字体", settings.forceDefaultFont, colors) { onChange(settings.copy(forceDefaultFont = it)) }
                }
                // Reduced motion (spec §7): the field appears without moving the rows below.
                AnimatedVisibility(
                    visible = settings.forceDefaultFont,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(AppleMotion.Duration.MEDIUM)) + expandVertically(),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(AppleMotion.Duration.SHORT)) + shrinkVertically(),
                ) {
                    KireiTextField(
                        value = settings.defaultFont,
                        onValueChange = { onChange(settings.copy(defaultFont = it)) },
                        colors = colors,
                        modifier = Modifier.padding(start = KireiSpacing.groupInner, end = KireiSpacing.groupInner, top = KireiSpacing.sm, bottom = KireiSpacing.md),
                        options = KireiTextFieldOptions(label = "默认字体文件名", placeholder = "例如 SourceHanSansSC.otf"),
                    )
                }
            }
        }
    }
}

private fun percentLabel(value: Float): String = "${(value * 100).roundToInt()}%"

/** A 5% slider stop stored as a clean two-decimal fraction (.15f, not .15000001f). */
private fun percentStop(value: Float): Float = (value * 100).roundToInt() / 100f
