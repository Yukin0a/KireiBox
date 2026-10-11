package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiButton
import com.kireibox.designsystem.components.KireiButtonOptions
import com.kireibox.designsystem.components.KireiButtonSize
import com.kireibox.designsystem.components.KireiButtonStyle
import com.kireibox.designsystem.components.KireiIconTile
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.kireibox.designsystem.components.KireiSheet
import com.kireibox.designsystem.components.KireiSheetSpec
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.ThemeMode

// 快速设置 (r4 S1): a three-step guide in a KireiSheet — 主题 → 强调色 → 首页风格 / 效果预设 —
// with a step indicator, 上一步 / 下一步 / 完成 and 跳过. Every choice is written at once through the
// same view-model setters the 外观 and 首页 pages use, so the whole app (this sheet included)
// recolors live while choosing. Finishing, skipping or dismissing marks it done
// (AppearanceSettings.KEY_QUICK_SETUP_DONE), which retires the one-time suggestion on the root.

internal const val QUICK_SETUP_TITLE = "快速设置"

/** The guide's steps in order, with the heading each step shows. */
internal enum class QuickSetupStep(
    val title: String,
) {
    THEME("选择主题"),
    ACCENT("选择强调色"),
    HOME("选择首页风格"),
}

/** Pure navigation rules of the guide (unit-tested). */
internal object QuickSetupFlow {
    val steps: List<QuickSetupStep> = QuickSetupStep.entries

    fun next(step: QuickSetupStep): QuickSetupStep? = steps.getOrNull(step.ordinal + 1)

    fun previous(step: QuickSetupStep): QuickSetupStep? = steps.getOrNull(step.ordinal - 1)

    fun progressLabel(step: QuickSetupStep): String = "第 ${step.ordinal + 1} 步，共 ${steps.size} 步"

    fun primaryLabel(step: QuickSetupStep): String = if (next(step) == null) "完成" else "下一步"

    /** 跳过 only while there is something left to skip: on the last step it would just repeat 完成. */
    fun canSkip(step: QuickSetupStep): Boolean = next(step) != null
}

/** The 自定义 card's line in the guide: picking it keeps today's mix instead of overwriting it. */
internal const val QUICK_SETUP_KEEP_CUSTOM = "保持现在的组合"

/**
 * One-time suggestion on the settings root for users who never finished the guide: accent-tinted
 * card with 开始 and a close button. Closing it counts as done; the overview card keeps the entry.
 */
@Composable
internal fun QuickSetupSuggestion(
    colors: KireiColors,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        SettingsGroupSpacing.fillMaxWidth()
            .clip(KireiShapes.lg)
            .background(colors.accentContainer)
            .padding(start = KireiSpacing.md, top = KireiSpacing.md, bottom = KireiSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        KireiIconTile(Icons.Rounded.AutoAwesome, colors.accent, colors.onAccent, QuickSetupTileSize)
        Column(Modifier.weight(1f).padding(start = KireiSpacing.sm)) {
            Text("用一分钟完成快速设置", color = colors.text, style = AppleTypography.bodyLarge, modifier = Modifier.semantics { heading() })
            KireiButton(
                label = "开始",
                colors = colors,
                modifier = Modifier.padding(top = KireiSpacing.xs),
                options = KireiButtonOptions(size = KireiButtonSize.SMALL),
                onClick = onStart,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "不再提示快速设置", tint = colors.secondary)
        }
    }
}

private val QuickSetupTileSize = 34.dp

/** The indicator row keeps 跳过's height on the last step too, so the title does not move. */
private val SkipRowMinHeight = 48.dp
private val StepPillHeight = 6.dp
private val StepPillWidth = 14.dp
private val StepPillActiveWidth = 28.dp
private val AccentPreviewHeight = 150.dp
private const val ACCENT_PREVIEW_MIN_SCREEN_DP = 560

/** About the tallest step (强调色: preview + swatches) at 1× text; steps never shrink below it. */
private val QuickSetupStepMinHeight = 268.dp

/** The guide sheet. [onClose] runs on 完成, 跳过 and when the sheet is dismissed. */
@Composable
internal fun QuickSetupSheet(
    context: SettingsPageContext,
    onClose: () -> Unit,
) {
    val colors = context.colors
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = QuickSetupFlow.steps[stepIndex.coerceIn(0, QuickSetupFlow.steps.lastIndex)]
    // One scroll position per step, hoisted so the button row can show a hairline while the step
    // content continues under it (short landscape windows, large text).
    val scrolls = QuickSetupFlow.steps.map { rememberScrollState() }
    KireiSheet(colors, onDismiss = onClose, spec = KireiSheetSpec(scrollable = false, skipPartiallyExpanded = true)) {
        // System back steps back through the guide; only on the first step does it close the sheet.
        BackHandler(enabled = stepIndex > 0) { stepIndex -= 1 }
        QuickSetupHeader(step, colors, onSkip = onClose)
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                if (reducedMotion) {
                    fadeIn(tween(AppleMotion.Duration.SHORT)) togetherWith fadeOut(tween(AppleMotion.Duration.SHORT))
                } else {
                    (fadeIn(tween(AppleMotion.Duration.MEDIUM)) + slideInHorizontally(tween(AppleMotion.Duration.MEDIUM)) { it * direction / 8 }) togetherWith
                        fadeOut(tween(AppleMotion.Duration.SHORT))
                }
            },
            modifier = Modifier.weight(1f, fill = false),
            label = "quickSetupStep",
        ) { shown ->
            // A shared minimum keeps the sheet's top edge from jumping between steps.
            Column(Modifier.fillMaxWidth().heightIn(min = QuickSetupStepMinHeight).verticalScroll(scrolls[shown.ordinal]).padding(horizontal = KireiSpacing.md)) {
                QuickSetupStepContent(shown, context)
            }
        }
        QuickSetupNavigation(QuickSetupNavigationSpec(step, moreAbove = scrolls[step.ordinal].canScrollForward), colors, onBack = { stepIndex -= 1 }) {
            if (QuickSetupFlow.next(step) == null) onClose() else stepIndex += 1
        }
    }
}

/** Step pills + 「第 n 步，共 3 步」, 跳过, then the step's heading. */
@Composable
private fun QuickSetupHeader(
    step: QuickSetupStep,
    colors: KireiColors,
    onSkip: () -> Unit,
) {
    // The pane title makes TalkBack announce 「快速设置」 when the sheet opens.
    Column(Modifier.fillMaxWidth().padding(start = KireiSpacing.lg, end = KireiSpacing.sm).semantics { paneTitle = QUICK_SETUP_TITLE }) {
        Row(Modifier.fillMaxWidth().heightIn(min = SkipRowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            QuickSetupStepIndicator(step, colors, Modifier.weight(1f))
            if (QuickSetupFlow.canSkip(step)) {
                KireiButton(
                    label = "跳过",
                    colors = colors,
                    options = KireiButtonOptions(style = KireiButtonStyle.PLAIN, size = KireiButtonSize.SMALL),
                    onClick = onSkip,
                )
            }
        }
        // One persistent node whose text changes with the step: TalkBack announces each new step title.
        Text(
            step.title,
            color = colors.text,
            style = AppleTypography.headlineMedium,
            modifier =
                Modifier.padding(top = KireiSpacing.xxs, bottom = KireiSpacing.sm).semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
    }
}

/** Three pills (done and current in accent, the current one longer) with the spoken progress. */
@Composable
private fun QuickSetupStepIndicator(
    step: QuickSetupStep,
    colors: KireiColors,
    modifier: Modifier,
) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = QuickSetupFlow.progressLabel(step) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs),
    ) {
        // Steps still ahead use a boundary-strength neutral (≥ 3:1 on the sheet), not the near-invisible surface3.
        val ahead = if (colors.isDark) colors.tertiary else KireiTokens.ControlBorderLight
        QuickSetupFlow.steps.forEach { pill ->
            val reached = pill.ordinal <= step.ordinal
            Box(
                Modifier.size(if (pill == step) StepPillActiveWidth else StepPillWidth, StepPillHeight)
                    .background(if (reached) colors.accent else ahead, KireiShapes.full),
            )
        }
        Text(
            "$QUICK_SETUP_TITLE · ${step.ordinal + 1} / ${QuickSetupFlow.steps.size}",
            color = colors.secondary,
            style = AppleTypography.numericSmall,
            modifier = Modifier.padding(start = KireiSpacing.xs),
        )
    }
}

@Composable
private fun ColumnScope.QuickSetupStepContent(
    step: QuickSetupStep,
    context: SettingsPageContext,
) {
    val settings = context.state.settings
    val colors = context.colors
    val viewModel = context.viewModel
    val accent = settings?.accentChoice ?: AccentChoice.DEFAULT
    when (step) {
        QuickSetupStep.THEME -> ThemeModePicker(settings?.themeMode ?: ThemeMode.SYSTEM, accent, colors, viewModel::setThemeMode)
        QuickSetupStep.ACCENT -> {
            // A live mini screen in the current theme: the accent dot and dock follow each swatch.
            // Short (landscape phone) windows skip it so the swatches stay above the buttons; the
            // sheet itself still recolors live.
            if (LocalConfiguration.current.screenHeightDp >= ACCENT_PREVIEW_MIN_SCREEN_DP) {
                ThemeMockup(
                    mode = if (colors.isDark) ThemeMode.DARK else ThemeMode.LIGHT,
                    accent = accent,
                    reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED,
                    modifier =
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(vertical = KireiSpacing.xs)
                            .size(AccentPreviewHeight / THEME_MOCK_ASPECT, AccentPreviewHeight)
                            .clip(KireiShapes.lg)
                            .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.lg)
                            .clearAndSetSemantics {},
                )
            }
            AccentSwatchPicker(accent, colors, viewModel::setAccentChoice)
        }
        QuickSetupStep.HOME -> QuickSetupHomeStep(context)
    }
}

/** 首页样式, then (immersive) the three named effect presets; components says where it is edited. */
@Composable
private fun QuickSetupHomeStep(context: SettingsPageContext) {
    val settings = context.state.settings
    val colors = context.colors
    val mode = settings?.homePresentationMode ?: HomePresentationMode.IMMERSIVE
    KireiSegmented(
        KireiSegmentedSpec("首页样式", HomePresentationMode.entries.map { KireiSegment(it, it.displayName) }, mode),
        context.viewModel::setHomePresentationMode,
        colors,
        Modifier.fillMaxWidth().padding(top = KireiSpacing.xxs, bottom = KireiSpacing.md),
    )
    if (mode == HomePresentationMode.IMMERSIVE) {
        Text("视觉效果", color = colors.secondary, style = AppleTypography.labelLarge, modifier = Modifier.padding(start = KireiSpacing.xxs, bottom = KireiSpacing.xs))
        // A custom mix shows as a selected 自定义 card that keeps it, so no tap silently overwrites it.
        val visual = settings?.immersiveVisual ?: ImmersiveVisualSettings()
        val presets = if (visual.preset == ImmersiveFxPreset.CUSTOM) ImmersivePresetSegments + ImmersiveFxPreset.CUSTOM else ImmersivePresetSegments
        ImmersivePresetCards(ImmersivePresetCardsSpec(visual, presets, customDescription = QUICK_SETUP_KEEP_CUSTOM), colors) { preset ->
            context.viewModel.setImmersiveVisual(immersivePresetSelection(preset, visual))
        }
    } else {
        homeStyleFooter(mode)?.let { Text(it, color = colors.secondary, style = AppleTypography.bodySmall, modifier = Modifier.padding(start = KireiSpacing.xxs)) }
    }
}

/** Where the guide is and whether its step content continues under the buttons. */
private data class QuickSetupNavigationSpec(
    val step: QuickSetupStep,
    val moreAbove: Boolean,
)

/**
 * 上一步 (from the second step on) and 下一步 / 完成, pinned under the scrolling step content; a
 * hairline separates them from content that is still scrolled under the row.
 */
@Composable
private fun QuickSetupNavigation(
    spec: QuickSetupNavigationSpec,
    colors: KireiColors,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val step = spec.step
    Row(
        Modifier.fillMaxWidth()
            .drawBehind { if (spec.moreAbove) drawLine(colors.stroke, Offset.Zero, Offset(size.width, 0f), KireiElevation.strokeWidth.toPx()) }
            .padding(start = KireiSpacing.md, end = KireiSpacing.md, top = KireiSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm),
    ) {
        if (QuickSetupFlow.previous(step) != null) {
            KireiButton("上一步", colors, Modifier.weight(1f), KireiButtonOptions(style = KireiButtonStyle.SECONDARY), onBack)
        }
        KireiButton(QuickSetupFlow.primaryLabel(step), colors, Modifier.weight(1f), KireiButtonOptions(style = KireiButtonStyle.PRIMARY), onNext)
    }
}
