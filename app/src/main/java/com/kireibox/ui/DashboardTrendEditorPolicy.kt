package com.kireibox.ui

import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import com.yuki.yukihub.settings.TrendVisualStyle

internal data class TrendConfigDraft(
    val metric: TrendMetric,
    val range: TrendRange,
    val visualStyle: TrendVisualStyle,
)

internal object DashboardTrendEditorPolicy {
    fun availableTypes(layout: DashboardLayout): List<DashboardWidgetType> {
        val present = layout.widgets.mapTo(HashSet()) { it.type }
        return DashboardWidgetType.entries.filter { type -> !type.unique || type !in present }
    }

    fun begin(
        layout: DashboardLayout,
        instanceId: String
    ): TrendConfigDraft {
        val spec = layout.widgets.firstOrNull { it.instanceId == instanceId && it.type == DashboardWidgetType.PLAY_TREND }
        return TrendConfigDraft(
            metric = spec?.trendMetric ?: TrendMetric.PLAY_TIME,
            range = spec?.trendRange ?: TrendRange.DAYS_7,
            visualStyle = spec?.trendVisualStyle ?: TrendVisualStyle.LINE,
        )
    }

    fun complete(
        layout: DashboardLayout,
        profile: DashboardProfile,
        instanceId: String,
        draft: TrendConfigDraft,
    ): DashboardLayout =
        DashboardLayouts.setTrendConfig(
            layout,
            profile,
            instanceId,
            draft.metric,
            draft.range,
            draft.visualStyle,
        )

    fun cancel(layout: DashboardLayout): DashboardLayout = layout
}
