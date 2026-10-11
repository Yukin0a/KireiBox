package com.kireibox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.kireibox.designsystem.components.KireiListGroup
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.ThemeMode

// Settings → 外观: theme-mode cards, accent swatches and display options (spec §8), laid out in
// KireiListGroup/KireiListRow like every other settings list. r4: everything about how the whole
// app looks and behaves lives here, including 屏幕方向 (moved off the root); the theme and accent
// pickers are shared with the 快速设置 guide (SettingsQuickSetup.kt).

/** Theme cards from left to right: 浅色 / 深色 / 跟随系统. */
internal val AppearanceThemeModeOrder = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** Selection ring (spec §8): 2dp ring drawn 3dp outside the selected card or swatch. */
private val SelectionRingWidth = 2.dp
private val SelectionRingGap = 3.dp
internal val SelectionRingInset = SelectionRingWidth + SelectionRingGap

/** Concentric with the lg-rounded mock it surrounds. */
private val ThemeCardRingShape = RoundedCornerShape(KireiShapes.Radius.lg + SelectionRingInset)
private val ThemeMockMaxHeight = 156.dp
internal const val THEME_MOCK_ASPECT = 1.36f
private const val THEME_CARD_PRESS_SCALE = .97f
private val CheckBadgeSize = 20.dp
private val CheckBadgeGlyphSize = 14.dp
private val CheckBadgeOutline = 1.5.dp

private val SwatchSize = 36.dp
private val SwatchRingSize = SwatchSize + SelectionRingInset * 2
private val SwatchTouchTarget = 48.dp
private val SwatchGlyphSize = 20.dp

/** Keeps the swatch row phone-sized on wide/landscape settings columns. */
private val SwatchRowMaxWidth = SwatchTouchTarget * AccentChoice.entries.size + KireiSpacing.xl * (AccentChoice.entries.size - 1)

/** Optical inset that lines the visible swatch edge up with list-row text (16dp). */
private val SwatchEdgeInset = (SwatchTouchTarget - SwatchSize) / 2

@Composable
internal fun AppearanceSettings(
    state: KireiUiState,
    colors: KireiColors,
    viewModel: KireiViewModel,
) {
    val settings = state.settings
    val accent = settings?.accentChoice ?: AccentChoice.DEFAULT
    val groupSpacing = Modifier.padding(bottom = KireiSpacing.groupGap)
    KireiListGroup(colors, groupSpacing, title = "主题") {
        item {
            SettingsAnchor("主题") {
                ThemeModePicker(
                    selected = settings?.themeMode ?: ThemeMode.SYSTEM,
                    accent = accent,
                    colors = colors,
                    onSelect = viewModel::setThemeMode,
                )
            }
        }
    }
    KireiListGroup(colors, groupSpacing, title = "强调色") {
        item { SettingsAnchor("强调色") { AccentSwatchPicker(selected = accent, colors = colors, onSelect = viewModel::setAccentChoice) } }
    }
    KireiListGroup(colors, groupSpacing, title = "显示") {
        item {
            MotionModeRow(
                requested = settings?.motionMode ?: MotionMode.AUTO,
                resolved = LocalResolvedMotionMode.current,
                colors = colors,
                onChange = viewModel::setMotionMode,
            )
        }
        item { OrientationModeRow(value = settings?.orientationMode ?: OrientationMode.AUTO, colors = colors, onChange = viewModel::setOrientationMode) }
        item {
            SettingsAnchor("减少透明度") {
                KireiListRow(
                    KireiListRowSpec(
                        title = "减少透明度",
                        trailing = KireiRowTrailing.Toggle(settings?.reducedTransparency == true, viewModel::setReducedTransparency),
                    ),
                    colors,
                )
            }
        }
        item {
            SettingsAnchor("增强对比度") {
                KireiListRow(
                    KireiListRowSpec(
                        title = "增强对比度",
                        trailing = KireiRowTrailing.Toggle(settings?.increasedContrast == true, viewModel::setContrast),
                    ),
                    colors,
                )
            }
        }
    }
}

internal fun selectionSpec(reducedMotion: Boolean): AnimationSpec<Float> = if (reducedMotion) snap() else AppleMotion.Spring.Emphasized

internal fun colorSpec(reducedMotion: Boolean): AnimationSpec<Color> = tween(if (reducedMotion) 0 else AppleMotion.Duration.MEDIUM)

@Composable
internal fun ThemeModePicker(
    selected: ThemeMode,
    accent: AccentChoice,
    colors: KireiColors,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .selectableGroup()
            .padding(start = KireiSpacing.sm, end = KireiSpacing.sm, top = KireiSpacing.sm, bottom = KireiSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
    ) {
        AppearanceThemeModeOrder.forEach { mode ->
            ThemeModeCard(mode, mode == selected, accent, colors) { onSelect(mode) }
        }
    }
}

/** One theme choice: a live mini-UI mock with an accent ring and check badge when selected. */
@Composable
private fun RowScope.ThemeModeCard(
    mode: ThemeMode,
    selected: Boolean,
    accent: AccentChoice,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val progress by animateFloatAsState(if (selected) 1f else 0f, selectionSpec(reducedMotion), label = "themeCardSelection")
    val pressScale by animateFloatAsState(
        if (pressed && !reducedMotion) THEME_CARD_PRESS_SCALE else 1f,
        AppleMotion.Spring.Press,
        label = "themeCardPress",
    )
    val labelColor by animateColorAsState(if (selected) colors.text else colors.secondary, colorSpec(reducedMotion), label = "themeCardLabel")
    Column(
        Modifier.weight(1f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "主题：${mode.displayName}" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Phone-shaped mock: on wide/landscape columns the height caps and the mock narrows to
            // keep its aspect, centered in the equal-width card.
            val availableWidth = maxWidth - SelectionRingInset * 2
            val mockHeight = (availableWidth * THEME_MOCK_ASPECT).coerceAtMost(ThemeMockMaxHeight)
            val mockWidth = (mockHeight / THEME_MOCK_ASPECT).coerceAtMost(availableWidth)
            Box(Modifier.size(mockWidth + SelectionRingInset * 2, mockHeight + SelectionRingInset * 2)) {
                SelectionRing(progress, colors.accent, ThemeCardRingShape, Modifier.matchParentSize())
                FocusRing(focused, colors, ThemeCardRingShape, Modifier.matchParentSize())
                ThemeMockup(
                    mode = mode,
                    accent = accent,
                    reducedMotion = reducedMotion,
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(SelectionRingInset)
                            .clip(KireiShapes.lg)
                            .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.lg),
                )
                CheckBadge(
                    progress,
                    colors,
                    Modifier.align(Alignment.TopEnd).padding(top = SelectionRingInset + KireiSpacing.xs, end = SelectionRingInset + KireiSpacing.xs),
                )
            }
        }
        Text(
            mode.displayName,
            color = labelColor,
            style = if (selected) AppleTypography.bodySmallEmphasis else AppleTypography.bodySmall,
            maxLines = 1,
            modifier = Modifier.padding(top = KireiSpacing.xxs).clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun SelectionRing(
    progress: Float,
    color: Color,
    shape: Shape,
    modifier: Modifier,
) {
    Box(
        modifier
            .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
            .border(SelectionRingWidth, color, shape),
    )
}

/**
 * Keyboard / D-pad focus (WCAG 2.4.7) on a selectable card or swatch: a 2dp ring in the text color
 * where the selection ring sits — distinct from the accent selection ring, which it covers while
 * focused (the check badge still marks the selection). Gamepads reach the 快速设置 guide too.
 */
@Composable
internal fun FocusRing(
    focused: Boolean,
    colors: KireiColors,
    shape: Shape,
    modifier: Modifier,
) {
    if (focused) Box(modifier.border(SelectionRingWidth, colors.text, shape))
}

/** Accent circle with an onAccent check that pops in with the selection spring. */
@Composable
internal fun CheckBadge(
    progress: Float,
    colors: KireiColors,
    modifier: Modifier,
) {
    Box(
        modifier
            .size(CheckBadgeSize)
            .graphicsLayer {
                alpha = progress.coerceIn(0f, 1f)
                val scale = .6f + .4f * progress
                scaleX = scale
                scaleY = scale
            }
            .background(colors.accent, CircleShape)
            .border(CheckBadgeOutline, colors.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(CheckBadgeGlyphSize))
    }
}

/** Neutral + accent colors a theme mock is painted with (always the plain, non-high-contrast theme). */
private data class ThemeMockPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondary: Color,
    val stroke: Color,
    val accent: Color,
)

@Composable
private fun rememberThemeMockPalette(
    isDark: Boolean,
    accent: AccentChoice,
    reducedMotion: Boolean,
): ThemeMockPalette {
    val neutrals = KireiTokens.neutrals(isDark)
    val accentColor by animateColorAsState(KireiTokens.accent(accent, isDark).accent, colorSpec(reducedMotion), label = "themeMockAccent")
    return ThemeMockPalette(
        background = neutrals.background,
        surface = neutrals.surface,
        text = neutrals.text,
        secondary = neutrals.secondary,
        stroke = neutrals.strokeStrong,
        accent = accentColor,
    )
}

/** Mini KireiBox screen: status bar, large title, a list card with an accent dot and the dock. */
@Composable
internal fun ThemeMockup(
    mode: ThemeMode,
    accent: AccentChoice,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val light = rememberThemeMockPalette(isDark = false, accent = accent, reducedMotion = reducedMotion)
    val dark = rememberThemeMockPalette(isDark = true, accent = accent, reducedMotion = reducedMotion)
    Canvas(modifier) {
        when (mode) {
            ThemeMode.LIGHT -> drawThemeMock(light)
            ThemeMode.DARK -> drawThemeMock(dark)
            ThemeMode.SYSTEM -> {
                // 跟随系统: left half light, right half dark (spec §8).
                clipRect(right = size.width / 2f) { drawThemeMock(light) }
                clipRect(left = size.width / 2f) { drawThemeMock(dark) }
            }
        }
    }
}

private fun DrawScope.drawThemeMock(palette: ThemeMockPalette) {
    val w = size.width
    val h = size.height
    val left = w * .12f
    val hairline = 1.dp.toPx()
    drawRect(palette.background)
    // Status-bar clock (the top-end corner stays clear for the selection badge).
    val statusHeight = maxOf(h * .024f, 1.5.dp.toPx())
    drawPill(palette.text, Offset(left, h * .065f), Size(w * .18f, statusHeight), alpha = .55f)
    // Large title + subtitle.
    drawPill(palette.text, Offset(left, h * .15f), Size(w * .56f, h * .055f))
    drawPill(palette.secondary, Offset(left, h * .245f), Size(w * .38f, h * .035f), alpha = .8f)
    // List card with an accent dot and two text lines.
    val cardTop = h * .34f
    val cardSize = Size(w * .76f, h * .26f)
    val cardRadius = CornerRadius(w * .09f)
    drawRoundRect(palette.surface, Offset(left, cardTop), cardSize, cardRadius)
    drawRoundRect(palette.stroke, Offset(left, cardTop), cardSize, cardRadius, style = Stroke(hairline))
    val dotRadius = w * .065f
    drawCircle(palette.accent, dotRadius, Offset(left + w * .07f + dotRadius, cardTop + cardSize.height / 2f))
    val lineLeft = left + w * .07f + dotRadius * 2f + w * .07f
    drawPill(palette.text, Offset(lineLeft, cardTop + cardSize.height * .3f), Size(w * .36f, h * .035f), alpha = .85f)
    drawPill(palette.secondary, Offset(lineLeft, cardTop + cardSize.height * .58f), Size(w * .24f, h * .03f), alpha = .7f)
    // Dock with the selected tab in the accent color.
    val dockSize = Size(w * .58f, h * .1f)
    val dockOffset = Offset((w - dockSize.width) / 2f, h * .8f)
    val dockRadius = CornerRadius(dockSize.height / 2f)
    drawRoundRect(palette.surface, dockOffset, dockSize, dockRadius)
    drawRoundRect(palette.stroke, dockOffset, dockSize, dockRadius, style = Stroke(hairline))
    val tabInset = dockSize.height * .22f
    drawPill(palette.accent, dockOffset + Offset(tabInset, tabInset), Size(dockSize.width * .36f, dockSize.height - tabInset * 2f))
    val tabDot = dockSize.height * .14f
    drawCircle(palette.secondary, tabDot, dockOffset + Offset(dockSize.width * .62f, dockSize.height / 2f), alpha = .6f)
    drawCircle(palette.secondary, tabDot, dockOffset + Offset(dockSize.width * .82f, dockSize.height / 2f), alpha = .6f)
}

private fun DrawScope.drawPill(
    color: Color,
    topLeft: Offset,
    size: Size,
    alpha: Float = 1f,
) = drawRoundRect(color, topLeft, size, CornerRadius(size.height / 2f), alpha = alpha)

@Composable
internal fun AccentSwatchPicker(
    selected: AccentChoice,
    colors: KireiColors,
    onSelect: (AccentChoice) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(start = KireiSpacing.md - SwatchEdgeInset, end = KireiSpacing.md - SwatchEdgeInset, top = KireiSpacing.sm, bottom = KireiSpacing.sm),
    ) {
        Row(
            Modifier.widthIn(max = SwatchRowMaxWidth).fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccentChoice.entries.forEach { choice ->
                AccentSwatch(choice, choice == selected, colors) { onSelect(choice) }
            }
        }
        Text(
            accentChoiceLabel(selected),
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            modifier = Modifier.padding(start = SwatchEdgeInset, top = KireiSpacing.xxs).clearAndSetSemantics {},
        )
    }
}

/** Swatch caption and spoken name; the default accent (purple, listed first) says so: 「紫色（默认）」. */
internal fun accentChoiceLabel(choice: AccentChoice): String = if (choice == AccentChoice.DEFAULT) "${choice.displayName}（默认）" else choice.displayName

/** 36dp accent circle in its own color for the current theme; selected = same-color ring + check. */
@Composable
private fun AccentSwatch(
    choice: AccentChoice,
    selected: Boolean,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val tone = KireiTokens.accent(choice, colors.isDark)
    val swatch by animateColorAsState(tone.accent, colorSpec(reducedMotion), label = "accentSwatch")
    val glyph by animateColorAsState(tone.onAccent, colorSpec(reducedMotion), label = "accentSwatchGlyph")
    val progress by animateFloatAsState(if (selected) 1f else 0f, selectionSpec(reducedMotion), label = "accentSwatchSelection")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.size(SwatchTouchTarget)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "强调色：${accentChoiceLabel(choice)}" },
        contentAlignment = Alignment.Center,
    ) {
        SelectionRing(
            progress,
            swatch,
            CircleShape,
            Modifier.size(SwatchRingSize).graphicsLayer {
                val scale = .86f + .14f * progress
                scaleX = scale
                scaleY = scale
            },
        )
        FocusRing(focused, colors, CircleShape, Modifier.size(SwatchRingSize))
        Box(Modifier.size(SwatchSize).background(swatch, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = glyph,
                modifier =
                    Modifier.size(SwatchGlyphSize).graphicsLayer {
                        alpha = progress.coerceIn(0f, 1f)
                        val scale = .5f + .5f * progress
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
    }
}
