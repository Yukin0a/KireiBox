package com.kireibox.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiTokens
import kotlin.math.roundToInt

/** Switch metrics (spec §8): pill track, white thumb inset by 2dp. */
internal object KireiSwitchDefaults {
    val TrackWidth = 50.dp
    val TrackHeight = 30.dp
    val ThumbSize = 26.dp
    val ThumbInset = 2.dp
    val ThumbStroke = 1.dp
}

/**
 * Spec §8 switch: accent track when on, surface3 when off, always a white thumb (with a 1dp stroke
 * in the light theme so it never melts into the light track). In the light theme the off track
 * also gets a 1dp [KireiTokens.ControlBorderLight] outline: surface3 and the white thumb are both
 * ~1.2:1 against the white group, below WCAG 1.4.11's 3:1 (spec §10 amendment). The thumb slides
 * on Spring.Default and snaps under reduced motion. Pass [onCheckedChange] = null when a parent
 * row owns the toggle.
 */
@Composable
internal fun KireiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reducedMotion = kireiReducedMotion()
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else AppleMotion.Spring.Default,
        label = "kireiSwitchThumb",
    )
    val track by animateColorAsState(if (checked) colors.accent else colors.surface3, kireiFadeSpec(reducedMotion), label = "kireiSwitchTrack")
    val outline by animateColorAsState(
        if (checked || colors.isDark) Color.Transparent else KireiTokens.ControlBorderLight,
        kireiFadeSpec(reducedMotion),
        label = "kireiSwitchOutline",
    )
    val toggle =
        if (onCheckedChange == null) {
            Modifier
        } else {
            Modifier.minimumInteractiveComponentSize()
                .toggleable(
                    value = checked,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
        }
    Box(
        modifier.then(toggle)
            .size(KireiSwitchDefaults.TrackWidth, KireiSwitchDefaults.TrackHeight)
            .graphicsLayer { alpha = if (enabled) 1f else KIREI_DISABLED_ALPHA }
            .clip(KireiShapes.full)
            .background(track)
            .border(KireiSwitchDefaults.ThumbStroke, outline, KireiShapes.full),
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = KireiSwitchDefaults.TrackWidth - KireiSwitchDefaults.ThumbSize - KireiSwitchDefaults.ThumbInset * 2
        Box(
            Modifier
                .offset { IntOffset((KireiSwitchDefaults.ThumbInset + travel * position).roundToPx(), 0) }
                .size(KireiSwitchDefaults.ThumbSize)
                .kireiShadow(KireiElevation.controlShadow, KireiShapes.full, KireiElevation.e2ShadowAlpha(colors.isDark))
                .background(KireiTokens.OnSolid, KireiShapes.full)
                .then(if (colors.isDark) Modifier else Modifier.border(KireiSwitchDefaults.ThumbStroke, colors.stroke, KireiShapes.full)),
        )
    }
}

/** One option of a [KireiSegmented] control. */
internal data class KireiSegment<T>(
    val value: T,
    val label: String,
)

/**
 * What a [KireiSegmented] control chooses: [label] names the setting (e.g. 首页模式) so TalkBack
 * announces every option with it ("首页模式，沉浸式，已选中") instead of a bare option name.
 */
internal data class KireiSegmentedSpec<T>(
    val label: String,
    val segments: List<KireiSegment<T>>,
    val selected: T?,
)

internal object KireiSegmentedDefaults {
    /** 40dp visual height; Compose widens the touch target of each segment to 48dp. */
    val Height = 40.dp
    val Inset = 2.dp

    /** Concentric with the md-rounded track. */
    val IndicatorShape = RoundedCornerShape(KireiShapes.Radius.md - Inset)

    /**
     * Selected block: surface in light; in dark surface3 lifted 18% toward the text color (spec §10
     * amendment: the spec's 8% was ~1.3:1 against the track, so bold text was the only cue).
     */
    fun indicatorColor(colors: KireiColors): Color = if (colors.isDark) lerp(colors.surface3, colors.text, DARK_LIFT) else colors.surface

    /** What TalkBack reads for one segment: the setting name, then the option. */
    fun segmentDescription(
        label: String,
        option: String,
    ): String = if (label.isBlank()) option else "$label，$option"

    private const val DARK_LIFT = .18f
}

/**
 * Spec §8 segmented control for up to four choices: surface3 md track, a raised selection block
 * that slides with Spring.Emphasized (instant under reduced motion). Each segment is a radio
 * button named "<setting>，<option>" with its checked state. When the selected value matches no
 * segment (e.g. a custom combination), no segment is shown or announced as selected.
 */
@Composable
internal fun <T> KireiSegmented(
    spec: KireiSegmentedSpec<T>,
    onSelect: (T) -> Unit,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val segments = spec.segments
    if (segments.isEmpty()) return
    val selectedIndex = segments.indexOfFirst { it.value == spec.selected }
    val reducedMotion = kireiReducedMotion()
    val position by animateFloatAsState(selectedIndex.coerceAtLeast(0).toFloat(), kireiSelectionSpec(reducedMotion), label = "kireiSegmentedIndicator")
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .height(KireiSegmentedDefaults.Height)
            .clip(KireiShapes.md)
            .background(colors.surface3)
            .padding(KireiSegmentedDefaults.Inset),
    ) {
        val segmentWidth = maxWidth / segments.size
        if (selectedIndex >= 0) {
            Box(
                Modifier
                    .offset { IntOffset((segmentWidth.toPx() * position).roundToInt(), 0) }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .kireiShadow(KireiElevation.controlShadow, KireiSegmentedDefaults.IndicatorShape, KireiElevation.e2ShadowAlpha(colors.isDark))
                    .background(KireiSegmentedDefaults.indicatorColor(colors), KireiSegmentedDefaults.IndicatorShape),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            segments.forEachIndexed { index, segment ->
                KireiSegmentCell(
                    description = KireiSegmentedDefaults.segmentDescription(spec.label, segment.label),
                    label = segment.label,
                    selected = index == selectedIndex,
                    colors = colors,
                ) { onSelect(segment.value) }
            }
        }
    }
}

@Composable
private fun RowScope.KireiSegmentCell(
    description: String,
    label: String,
    selected: Boolean,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.weight(1f)
            .fillMaxHeight()
            // Keyboard / D-pad focus rings the cell in accent (WCAG 2.4.7); touch never focuses it.
            .then(if (focused) Modifier.border(KireiElevation.focusRingWidth, colors.accent, KireiSegmentedDefaults.IndicatorShape) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // Inner to selectable: replaces only the label (the Text below), keeping its
            // radio-button role, checked state and click action on this node.
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Primary ink in both states (secondary on surface3 is 4.2:1 in light); the raised block
        // and the weight mark the selection.
        Text(
            label,
            color = colors.text,
            style = if (selected) AppleTypography.bodySmallEmphasis else AppleTypography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
