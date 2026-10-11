package com.kireibox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSlider
import com.kireibox.designsystem.components.KireiSliderSpec
import com.kireibox.designsystem.components.LocalKireiListDivider
import com.kireibox.designsystem.components.kireiListDivider

/** One option of a [SettingsChoiceRow] sheet: its value, label and optional one-line description. */
internal data class SettingsChoiceOption<T>(
    val value: T,
    val label: String,
    val description: String? = null,
)

/** A setting with more (or longer) options than an inline segmented control can hold. */
internal data class SettingsChoiceSpec<T>(
    val title: String,
    val options: List<SettingsChoiceOption<T>>,
    /** Null (or a value no option has) selects nothing, e.g. a custom effects mix. */
    val selected: T?,
    val subtitle: String? = null,
    val enabled: Boolean = true,
)

/**
 * Value row (title, current value in secondary, chevron) that opens a [KireiActionSheet] of
 * [SheetChoiceRow] radio options (spec §8: more than four options, or options that need a
 * description, use a sheet).
 */
@Composable
internal fun <T> SettingsChoiceRow(
    spec: SettingsChoiceSpec<T>,
    colors: KireiColors,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = spec.options.firstOrNull { it.value == spec.selected }?.label.orEmpty()
    SettingsAnchor(spec.title) {
        KireiListRow(
            KireiListRowSpec(title = spec.title, subtitle = spec.subtitle, trailing = KireiRowTrailing.Value(current), enabled = spec.enabled),
            colors,
            onClick = { expanded = true },
        )
    }
    if (expanded) {
        KireiActionSheet(spec.title, colors, { expanded = false }) {
            spec.options.forEach { option ->
                SheetChoiceRow(option.label, option.description, option.value == spec.selected, colors) {
                    expanded = false
                    onSelect(option.value)
                }
            }
        }
    }
}

/** A [KireiSlider] as one list-group row: group padding, divider under the track from the text start. */
@Composable
internal fun SettingsSliderRow(
    value: Float,
    spec: KireiSliderSpec,
    colors: KireiColors,
    onValueChange: (Float) -> Unit,
) {
    SettingsAnchor(spec.label) {
        KireiSlider(
            value = value,
            onValueChange = onValueChange,
            spec = spec,
            colors = colors,
            modifier =
                Modifier.fillMaxWidth()
                    .kireiListDivider(LocalKireiListDivider.current, colors, KireiSpacing.groupInner)
                    // xs on top: the 48dp touch band around the track already adds the breathing room.
                    .padding(start = KireiSpacing.groupInner, end = KireiSpacing.groupInner, top = KireiSpacing.xs, bottom = KireiSpacing.xxs),
        )
    }
}
