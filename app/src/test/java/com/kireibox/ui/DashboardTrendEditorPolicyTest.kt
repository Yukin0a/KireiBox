package com.kireibox.ui

import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.DashboardWidgetSize
import com.yuki.yukihub.settings.DashboardWidgetSpec
import com.yuki.yukihub.settings.DashboardWidgetType
import com.yuki.yukihub.settings.TrendMetric
import com.yuki.yukihub.settings.TrendRange
import com.yuki.yukihub.settings.TrendVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTrendEditorPolicyTest {
    @Test fun playTrendRemainsAvailableAfterItHasBeenAddedButOtherUniqueTypesDoNot() {
        val layout = DashboardLayout(
            widgets = listOf(
                DashboardWidgetSpec("trend", DashboardWidgetType.PLAY_TREND, DashboardWidgetSize(1, 1)),
                DashboardWidgetSpec("overview", DashboardWidgetType.LIBRARY_OVERVIEW, DashboardWidgetSize(1, 1)),
            )
        )

        val available = DashboardTrendEditorPolicy.availableTypes(layout)

        assertTrue(DashboardWidgetType.PLAY_TREND in available)
        assertFalse(DashboardWidgetType.LIBRARY_OVERVIEW in available)
    }

    @Test fun completeCommitsOnlyTheTargetTrendDraftAndCancelLeavesDashboardDraftUnchanged() {
        val layout = DashboardLayout(widgets = listOf(
            DashboardWidgetSpec("first", DashboardWidgetType.PLAY_TREND, DashboardWidgetSize(1, 1)),
            DashboardWidgetSpec("second", DashboardWidgetType.PLAY_TREND, DashboardWidgetSize(1, 1)),
        ))
        val panel = DashboardTrendEditorPolicy.begin(layout, "second")
            .copy(metric = TrendMetric.GAME_OPENS, range = TrendRange.DAYS_30, visualStyle = TrendVisualStyle.AREA)

        val committed = DashboardTrendEditorPolicy.complete(layout, DashboardProfile.COMPACT, "second", panel)

        assertEquals(TrendMetric.PLAY_TIME, committed.widgets.first { it.instanceId == "first" }.trendMetric)
        assertEquals(TrendMetric.GAME_OPENS, committed.widgets.first { it.instanceId == "second" }.trendMetric)
        assertEquals(TrendRange.DAYS_30, committed.widgets.first { it.instanceId == "second" }.trendRange)
        assertEquals(layout, DashboardTrendEditorPolicy.cancel(layout))
    }
}
