package com.kireibox.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.components.KireiListRow
import com.kireibox.designsystem.components.KireiListRowSpec
import com.kireibox.designsystem.components.KireiRowTrailing
import com.kireibox.designsystem.components.KireiSegment
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.OrientationMode

// Settings row primitives shared by every settings surface. Every settings list is a
// KireiListGroup of KireiListRow-based rows (spec §8); there is no second group style. Each titled
// row registers itself as a search anchor under its title (SettingsAnchor, r4 · S2).

@Composable internal fun SwitchRow(
    label: String,
    checked: Boolean,
    colors: KireiColors,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsAnchor(label) {
        KireiListRow(KireiListRowSpec(title = label, trailing = KireiRowTrailing.Toggle(checked, onCheckedChange)), colors)
    }
}

@Composable
internal fun MotionModeRow(
    requested: MotionMode,
    resolved: MotionMode,
    colors: KireiColors,
    onChange: (MotionMode) -> Unit
) {
    // Inline segmented choice; the group divider sits below the control, not under the title.
    SettingsSegmentedRow(
        SettingsSegmentedRowSpec(
            title = "动画效果",
            subtitle = if (requested == MotionMode.AUTO) "当前：${motionModeLabel(resolved)}" else null,
            segments = MotionMode.entries.map { mode -> KireiSegment(mode, motionModeLabel(mode)) },
            selected = requested,
        ),
        colors,
        onChange,
    )
}

internal fun motionModeLabel(mode: MotionMode): String =
    when (mode) {
        MotionMode.AUTO -> "自动"
        MotionMode.POLISHED -> "完整"
        MotionMode.REDUCED -> "精简"
    }

/**
 * 外观 → 显示 → 屏幕方向 (r4: moved off the settings root): a plain titled segmented row like
 * 动画效果 above it, so every row of the 显示 group starts its text at the same edge.
 */
@Composable
internal fun OrientationModeRow(
    value: OrientationMode,
    colors: KireiColors,
    onChange: (OrientationMode) -> Unit,
) {
    SettingsSegmentedRow(
        SettingsSegmentedRowSpec(
            title = "屏幕方向",
            segments = OrientationMode.entries.map { option -> KireiSegment(option, option.displayName) },
            selected = value,
        ),
        colors,
        onChange,
    )
}

/** Root category-row metrics: the spec list metrics in portrait, compact rows in landscape. */
internal data class SettingsCategoryRowMetrics(
    val verticalPaddingDp: Float,
    val minimumHeightDp: Float,
    val iconSizeDp: Float,
)

/** Landscape rows are compact so the root's first groups still clear the dock (AdaptiveShellWiringTest). */
internal fun resolveSettingsCategoryRowMetrics(orientation: HomeOrientation): SettingsCategoryRowMetrics =
    if (orientation == HomeOrientation.LANDSCAPE) {
        SettingsCategoryRowMetrics(verticalPaddingDp = 4f, minimumHeightDp = 40f, iconSizeDp = 28f)
    } else {
        SettingsCategoryRowMetrics(verticalPaddingDp = 12f, minimumHeightDp = 64f, iconSizeDp = 34f)
    }

/** Applies the root category metrics to a list row (padding, minimum height and tile size). */
internal fun KireiListRowSpec.withCategoryMetrics(metrics: SettingsCategoryRowMetrics): KireiListRowSpec =
    copy(verticalPadding = metrics.verticalPaddingDp.dp, minHeight = metrics.minimumHeightDp.dp, tileSize = metrics.iconSizeDp.dp)

internal fun choiceLabel(value: String): String =
    when (value) {
        "vndb" -> "VNDB"
        "bangumi" -> "Bangumi"
        "hikarinagi" -> "Hikarinagi"
        "recent" -> "最近游玩"
        "newest" -> "添加时间"
        "name" -> "名称"
        "manual" -> "自定义"
        "auto" -> "自动"
        else -> value
    }
