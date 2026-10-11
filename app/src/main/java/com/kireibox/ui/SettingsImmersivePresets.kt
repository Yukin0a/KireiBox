package com.kireibox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiBadge
import com.kireibox.designsystem.components.KireiBadgeTone
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode

// 首页 → 视觉效果 (r4 S1): the effect presets as large selectable cards, each with a small drawn
// preview of the immersive home in that preset's style (ImmersivePresetPreview.kt), a one-line
// description and a 推荐 badge on the default. Shared with the 快速设置 guide (three presets there).

/** The preset new users get and the cards recommend (the 标准 preset, plan §9 D4). */
internal val RecommendedImmersivePreset = ImmersiveFxPreset.STANDARD

/**
 * A few plain keywords under each preset card: what it includes. Short phrases split by
 * punctuation, so two lines always break between phrases ([immersivePresetDisplayLine]).
 */
internal fun immersivePresetDescription(preset: ImmersiveFxPreset): String =
    when (preset) {
        ImmersiveFxPreset.MINIMAL -> "仅氛围色"
        ImmersiveFxPreset.STANDARD -> "封面氛围、光晕、立体卡片"
        ImmersiveFxPreset.RICH -> "流光极光、光尘、视差"
        ImmersiveFxPreset.CUSTOM -> "逐项调整"
    }

/** What TalkBack reads for a card: name, 推荐 when it is the default, then the description. */
internal fun immersivePresetCardDescription(
    preset: ImmersiveFxPreset,
    description: String = immersivePresetDescription(preset),
): String {
    val recommended = if (preset == RecommendedImmersivePreset) "，推荐" else ""
    return "效果预设：${preset.displayName}$recommended。$description"
}

private const val WORD_JOINER = '\u2060'
private const val PHRASE_BREAKS = "，、。；： "

/**
 * The displayed description: the characters of each phrase are joined (U+2060, as the landscape
 * play-time label does), so a line only breaks after 「，」/「、」 — never 「装/饰」 or 「粒/子」.
 */
internal fun immersivePresetDisplayLine(text: String): String =
    buildString(text.length * 2) {
        text.forEachIndexed { index, char ->
            if (index > 0 && text[index - 1] !in PHRASE_BREAKS) append(WORD_JOINER)
            append(char)
        }
    }

/** Column count of the card grid: three presets side by side when they fit, else two (or four on wide columns). */
internal object ImmersivePresetGridPolicy {
    /** Narrowest card that still shows the preview, name + badge and a two-line description at 1× text. */
    const val MIN_CARD_WIDTH_DP = 100f
    const val WIDE_FOUR_COLUMN_DP = 560f
    const val GAP_DP = 12f

    fun columns(
        count: Int,
        widthDp: Float,
        fontScale: Float,
    ): Int {
        val minCard = MIN_CARD_WIDTH_DP * fontScale.coerceAtLeast(1f)
        val fitting = ((widthDp + GAP_DP) / (minCard + GAP_DP)).toInt().coerceAtLeast(1)
        return when {
            count <= fitting -> count.coerceAtLeast(1)
            widthDp >= WIDE_FOUR_COLUMN_DP -> minOf(count, fitting)
            // Two only when two minimum-width cards fit (very large text gets one card per row).
            else -> minOf(2, count, fitting)
        }
    }
}

/**
 * The cards to show, the current effects (the 自定义 card previews them) and the selected preset.
 * [customDescription] is the 自定义 card's line (the 快速设置 guide says it keeps today's mix).
 */
internal data class ImmersivePresetCardsSpec(
    val current: ImmersiveVisualSettings,
    val presets: List<ImmersiveFxPreset> = ImmersiveFxPreset.entries,
    val customDescription: String = immersivePresetDescription(ImmersiveFxPreset.CUSTOM),
) {
    fun description(preset: ImmersiveFxPreset): String = if (preset == ImmersiveFxPreset.CUSTOM) customDescription else immersivePresetDescription(preset)
}

/** Colors of one preset preview (ImmersivePresetPreview.kt), resolved once per theme and accent. */
internal data class PresetPreviewPalette(
    val isDark: Boolean,
    val base: Color,
    val solid: Color,
    val tone: Color,
    val companion: Color,
    val accent: Color,
    val glow: Color,
    val coverTop: Color,
    val coverBottom: Color,
    val depthInk: Color,
    val vignette: Color,
    val edge: Color,
    /** The immersive palette the sample is built from (封面氛围's ink and veil, r5). */
    val immersive: ImmersivePalette,
)

private const val PRESET_PREVIEW_ASPECT = 4f / 3f
private const val PRESET_CARD_PRESS_SCALE = .97f
private val PresetPreviewShape = KireiShapes.md
private val PresetRingShape = RoundedCornerShape(KireiShapes.Radius.md + SelectionRingInset)
private val TuneBadgeSize = 22.dp
private val TuneGlyphSize = 14.dp

/** Grid of preset cards (radio group). Selecting writes at once; the parent decides what that means. */
@Composable
internal fun ImmersivePresetCards(
    spec: ImmersivePresetCardsSpec,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    onSelect: (ImmersiveFxPreset) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = ImmersivePresetGridPolicy.columns(spec.presets.size, maxWidth.value, fontScale)
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(KireiSpacing.md)) {
            spec.presets.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ImmersivePresetGridPolicy.GAP_DP.dp)) {
                    row.forEach { preset ->
                        ImmersivePresetCard(preset, spec, colors) { onSelect(preset) }
                    }
                    // Keep a short last row on the grid instead of stretching its cards.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One preset: drawn preview with the selection ring and check, then name (+ 推荐) and description. */
@Composable
private fun RowScope.ImmersivePresetCard(
    preset: ImmersiveFxPreset,
    spec: ImmersivePresetCardsSpec,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val current = spec.current
    val selected = current.preset == preset
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val progress by animateFloatAsState(if (selected) 1f else 0f, selectionSpec(reducedMotion), label = "presetCardSelection")
    val pressScale by animateFloatAsState(if (pressed && !reducedMotion) PRESET_CARD_PRESS_SCALE else 1f, AppleMotion.Spring.Press, label = "presetCardPress")
    val nameColor by animateColorAsState(if (selected) colors.text else colors.secondary, colorSpec(reducedMotion), label = "presetCardName")
    // The 自定义 card previews the current mix; a named preset always previews its canonical look.
    val look = if (preset == ImmersiveFxPreset.CUSTOM) current else ImmersiveVisualSettings.forPreset(preset)
    Column(
        Modifier.weight(1f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = immersivePresetCardDescription(preset, spec.description(preset)) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(PRESET_PREVIEW_ASPECT)) {
            SelectionRing(progress, colors.accent, PresetRingShape, Modifier.matchParentSize())
            FocusRing(focused, colors, PresetRingShape, Modifier.matchParentSize())
            ImmersivePresetPreview(
                look,
                colors,
                Modifier.fillMaxSize()
                    .padding(SelectionRingInset)
                    .clip(PresetPreviewShape)
                    .border(KireiElevation.strokeWidth, colors.strokeStrong, PresetPreviewShape),
            )
            if (preset == ImmersiveFxPreset.CUSTOM) {
                PresetTuneBadge(colors, Modifier.align(Alignment.BottomEnd).padding(SelectionRingInset + KireiSpacing.xs))
            }
            CheckBadge(progress, colors, Modifier.align(Alignment.TopEnd).padding(top = SelectionRingInset + KireiSpacing.xs, end = SelectionRingInset + KireiSpacing.xs))
        }
        PresetCardLabel(preset, spec.description(preset), nameColor, colors)
    }
}

@Composable
private fun PresetCardLabel(
    preset: ImmersiveFxPreset,
    description: String,
    nameColor: Color,
    colors: KireiColors,
) {
    Column(Modifier.fillMaxWidth().padding(start = KireiSpacing.xxs, top = KireiSpacing.xs).clearAndSetSemantics {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            Text(preset.displayName, color = nameColor, style = AppleTypography.bodyMediumEmphasis, maxLines = 1)
            if (preset == RecommendedImmersivePreset) KireiBadge("推荐", colors, tone = KireiBadgeTone.ACCENT)
        }
        Text(
            immersivePresetDisplayLine(description),
            color = colors.secondary,
            style = AppleTypography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = KireiSpacing.xxs / 2),
        )
    }
}

/** Small tune glyph on the 自定义 preview: this card is the mix you build yourself. */
@Composable
private fun PresetTuneBadge(
    colors: KireiColors,
    modifier: Modifier,
) {
    Box(modifier.size(TuneBadgeSize).background(colors.surfaceOverlay, KireiShapes.full), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.text, modifier = Modifier.size(TuneGlyphSize))
    }
}

/** Mini immersive home for [look], painted with the app's own immersive palette and accent. */
@Composable
internal fun ImmersivePresetPreview(
    look: ImmersiveVisualSettings,
    colors: KireiColors,
    modifier: Modifier,
) {
    val palette = rememberPresetPreviewPalette(colors)
    Canvas(modifier) { drawPresetPreview(look, palette) }
}
