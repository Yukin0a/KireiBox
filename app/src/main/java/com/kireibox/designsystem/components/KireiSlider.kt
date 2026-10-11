package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import kotlin.math.roundToInt

/**
 * What a [KireiSlider] edits. [label] names the setting and [valueText] is the formatted current
 * value ("60", "15%"): both are shown above the track and announced together, so TalkBack reads
 * "FPS 限制，60" and adjusts the value with the standard slider gestures. [steps] is the number of
 * discrete stops between the ends (0 = continuous).
 */
internal data class KireiSliderSpec(
    val label: String,
    val valueText: String,
    val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val steps: Int = 0,
    val enabled: Boolean = true,
)

internal object KireiSliderDefaults {
    val TrackHeight = 6.dp
    val ThumbSize = 26.dp

    /** Touch band around the track: the 48dp minimum target every other control keeps. */
    val TouchHeight = 48.dp

    /** Accent ring around the thumb while the slider has keyboard / D-pad focus (WCAG 2.4.7). */
    val FocusRing = 2.dp

    /** Keyboard / TalkBack adjustments of a continuous slider move by 1/20 of the range. */
    private const val CONTINUOUS_INCREMENTS = 20

    /** 0..1 position of [value] inside [range] (clamped). */
    fun fraction(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
    ): Float {
        val span = range.endInclusive - range.start
        return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
    }

    /** Value at [fraction] of [range], snapped to the nearest of the [steps] stops when there are any. */
    fun valueAt(
        fraction: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int = 0,
    ): Float {
        val clamped = fraction.coerceIn(0f, 1f)
        val snapped = if (steps > 0) (clamped * (steps + 1)).roundToInt() / (steps + 1f) else clamped
        return range.start + (range.endInclusive - range.start) * snapped
    }

    /** One keyboard / accessibility step: a stop when the slider is discrete, else 1/20 of the range. */
    fun increment(
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
    ): Float = (range.endInclusive - range.start) / (if (steps > 0) steps + 1 else CONTINUOUS_INCREMENTS)

    /** [value] moved by [delta] increments, clamped and snapped like a drag would be. */
    fun stepped(
        value: Float,
        delta: Int,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
    ): Float = valueAt(fraction(value + increment(range, steps) * delta, range), range, steps)
}

/**
 * Spec-styled slider: the setting name (bodyLarge) with its value in the numeric style on the
 * right, then a full-width pill track (its ends line up with the title and the value) — accent up
 * to the value, surface3 after it — and a white thumb with the e2 shadow. In the light theme the
 * thumb gets the switch's ControlBorderLight outline (surface3 and white are ~1.2:1 on the white
 * group, WCAG 1.4.11). Taps and horizontal drags set the value (vertical drags still scroll the
 * page); left/right keys and TalkBack move it by [KireiSliderDefaults.increment], and keyboard
 * focus rings the thumb in accent. The whole control is one accessibility node with range info.
 */
@Composable
internal fun KireiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    spec: KireiSliderSpec,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val latestChange by rememberUpdatedState(onValueChange)
    val range = spec.valueRange
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer { alpha = if (spec.enabled) 1f else KIREI_DISABLED_ALPHA }
            .clearAndSetSemantics {
                contentDescription = spec.label
                stateDescription = spec.valueText
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(range.start, range.endInclusive), range, spec.steps)
                if (spec.enabled) {
                    setProgress { target ->
                        latestChange(KireiSliderDefaults.valueAt(KireiSliderDefaults.fraction(target, range), range, spec.steps))
                        true
                    }
                } else {
                    disabled()
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spec.label,
                color = colors.text,
                style = AppleTypography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(spec.valueText, color = colors.accent, style = AppleTypography.numericMedium, maxLines = 1, modifier = Modifier.padding(start = KireiSpacing.sm))
        }
        KireiSliderTrack(value, spec, colors) { latestChange(it) }
    }
}

@Composable
private fun KireiSliderTrack(
    value: Float,
    spec: KireiSliderSpec,
    colors: KireiColors,
    onValueChange: (Float) -> Unit,
) {
    val range = spec.valueRange
    val fraction = KireiSliderDefaults.fraction(value, range)
    val latestValue by rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier.fillMaxWidth()
            .height(KireiSliderDefaults.TouchHeight)
            .sliderInput(spec, onValueChange)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled = spec.enabled)
            .onKeyEvent { event ->
                val delta = sliderKeyDelta(event.key)
                if (event.type != KeyEventType.KeyDown || delta == 0 || !spec.enabled) return@onKeyEvent false
                onValueChange(KireiSliderDefaults.stepped(latestValue, delta, range, spec.steps))
                true
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = maxWidth - KireiSliderDefaults.ThumbSize
        Box(
            Modifier.fillMaxWidth()
                .height(KireiSliderDefaults.TrackHeight)
                .drawBehind {
                    // The track spans the full width; the thumb center travels from one radius in.
                    val radius = CornerRadius(size.height / 2f)
                    val thumbRadius = KireiSliderDefaults.ThumbSize.toPx() / 2f
                    val filled = thumbRadius + (size.width - thumbRadius * 2f).coerceAtLeast(0f) * fraction
                    drawRoundRect(colors.surface3, Offset.Zero, size, radius)
                    drawRoundRect(colors.accent, Offset.Zero, Size(filled, size.height), radius)
                },
        )
        Box(
            Modifier
                .offset { IntOffset((travel * fraction).roundToPx(), 0) }
                .size(KireiSliderDefaults.ThumbSize)
                .kireiE2Shadow(KireiShapes.full, colors.isDark)
                .background(KireiTokens.OnSolid, KireiShapes.full)
                .then(if (colors.isDark) Modifier else Modifier.border(KireiSwitchDefaults.ThumbStroke, KireiTokens.ControlBorderLight, KireiShapes.full))
                .then(if (focused) Modifier.border(KireiSliderDefaults.FocusRing, colors.accent, KireiShapes.full) else Modifier),
        )
    }
}

/** Taps jump to the touched value; horizontal drags follow the finger (vertical drags keep scrolling). */
private fun Modifier.sliderInput(
    spec: KireiSliderSpec,
    onValueChange: (Float) -> Unit,
): Modifier =
    if (!spec.enabled) {
        this
    } else {
        pointerInput(spec.valueRange, spec.steps) {
            detectTapGestures { offset -> onValueChange(sliderValueAt(offset.x, size.width, KireiSliderDefaults.ThumbSize.toPx(), spec)) }
        }.pointerInput(spec.valueRange, spec.steps) {
            val thumb = KireiSliderDefaults.ThumbSize.toPx()
            detectHorizontalDragGestures(onDragStart = { offset -> onValueChange(sliderValueAt(offset.x, size.width, thumb, spec)) }) { change, _ ->
                change.consume()
                onValueChange(sliderValueAt(change.position.x, size.width, thumb, spec))
            }
        }
    }

/** Value under a pointer at [x] on a track [widthPx] wide whose ends are inset by half the thumb. */
private fun sliderValueAt(
    x: Float,
    widthPx: Int,
    thumbPx: Float,
    spec: KireiSliderSpec,
): Float {
    val travel = (widthPx - thumbPx).coerceAtLeast(1f)
    return KireiSliderDefaults.valueAt((x - thumbPx / 2f) / travel, spec.valueRange, spec.steps)
}

/** Left/right arrows adjust the value; up/down stay free for moving focus between rows. */
private fun sliderKeyDelta(key: Key): Int =
    when (key) {
        Key.DirectionRight -> 1
        Key.DirectionLeft -> -1
        else -> 0
    }
