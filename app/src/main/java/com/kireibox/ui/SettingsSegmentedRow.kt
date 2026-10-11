package com.kireibox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowDefaults
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.kireibox.designsystem.components.KireiSegmented
import com.kireibox.designsystem.components.KireiSegmentedSpec
import com.kireibox.designsystem.components.LocalKireiListDivider
import com.kireibox.designsystem.components.kireiListDivider

/**
 * When an inline segmented control stops fitting: four single-line labels need ~73dp each at
 * 1.4× text and a phone segment is ~72dp, so at large font scales (WCAG 1.4.4) a four-option
 * choice becomes a value row with a sheet instead of truncating to 「最近游…」. Five options (the
 * r5 背景样式: 封面氛围 / 封面虚化 / 流光极光 / 氛围色 / 纯色) leave ~60dp per four-character label on a
 * phone, so they always use the sheet.
 */
internal object SettingsSegmentedLayoutPolicy {
    private const val CROWDED_SEGMENTS = 4
    private const val ALWAYS_SHEET_SEGMENTS = 5

    /** Widest the control grows (landscape / tablets): wider segments only spread the labels apart. */
    val MaxControlWidth = 420.dp

    fun useSheet(
        segmentCount: Int,
        fontScale: Float,
    ): Boolean = segmentCount >= ALWAYS_SHEET_SEGMENTS || (segmentCount >= CROWDED_SEGMENTS && fontScale >= HomeContentSettingsPolicy.LARGE_FONT_SCALE)
}

/**
 * Title row with its segmented control underneath, as one list-group row: the group divider is
 * drawn once below the control (never between the title and its own control). The control and
 * the divider start where the row text starts, so a row with an icon tile stays aligned. At large
 * font scales a four-option control falls back to [SettingsChoiceRow] ([SettingsSegmentedLayoutPolicy]).
 */
@Composable
internal fun <T> SettingsSegmentedRow(
    spec: SettingsSegmentedRowSpec<T>,
    colors: KireiColors,
    onSelect: (T) -> Unit,
) {
    if (SettingsSegmentedLayoutPolicy.useSheet(spec.segments.size, LocalDensity.current.fontScale)) {
        // The value row registers itself as the search anchor under the same title.
        SettingsChoiceRow(SettingsChoiceSpec(spec.title, spec.segments.map { SettingsChoiceOption(it.value, it.label) }, spec.selected, spec.subtitle), colors, onSelect)
        return
    }
    val divider = LocalKireiListDivider.current
    val header = spec.header ?: KireiListRowSpec(title = spec.title, subtitle = spec.subtitle)
    val textStart = KireiListRowDefaults.textStart(header)
    // The whole row (title and control) is the search anchor, so a found setting highlights both.
    SettingsAnchor(spec.title) {
        Column(Modifier.fillMaxWidth().kireiListDivider(divider, colors, textStart)) {
            CompositionLocalProvider(LocalKireiListDivider provides false) {
                KireiListRow(header, colors)
            }
            KireiSegmented(
                // The row title names the setting, so each option is announced with it.
                spec = KireiSegmentedSpec(label = spec.title, segments = spec.segments, selected = spec.selected),
                onSelect = onSelect,
                colors = colors,
                modifier = Modifier.padding(start = textStart, end = KireiSpacing.groupInner, bottom = KireiSpacing.sm).widthIn(max = SettingsSegmentedLayoutPolicy.MaxControlWidth),
            )
        }
    }
}

/** Switch row used by settings lists. */
@Composable
internal fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    colors: KireiColors,
    subtitle: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsAnchor(title) {
        KireiListRow(
            KireiListRowSpec(
                title = title,
                subtitle = subtitle,
                trailing = KireiRowTrailing.Toggle(checked, onCheckedChange),
            ),
            colors,
        )
    }
}

/**
 * A titled inline choice (spec §8: ≤ 4 short options use a segmented control instead of a sheet).
 * [header] replaces the plain title row, e.g. with a category tile and compact metrics.
 */
internal data class SettingsSegmentedRowSpec<T>(
    val title: String,
    val segments: List<KireiSegment<T>>,
    val selected: T?,
    val subtitle: String? = null,
    val header: KireiListRowSpec? = null,
)
