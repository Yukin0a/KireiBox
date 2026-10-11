package com.kireibox.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.settings.DashboardProfile

/**
 * `components-home`: the components-mode home (ComponentsHomePage with the stored default dashboard),
 * `dashboard-editor`: DashboardEditorPage for the compact profile. 保存 in the editor would write the
 * dashboard layout of the emulator's real settings, so screenshots never press it (取消 only finishes).
 */
@Composable
internal fun ShowcaseHomePage(
    activity: ComponentActivity,
    colors: KireiColors,
    editor: Boolean,
) {
    val games = remember { ShowcaseFixtures.libraryGames(activity) }
    ShowcasePageHost(
        activity = activity,
        colors = colors,
        dockTab = if (editor) null else KireiTab.HOME,
        games = games,
        transform = { s -> s.copy(playTrend = ShowcaseFixtures.playTrend(), tab = KireiTab.HOME) },
    ) { env ->
        if (editor) {
            DashboardEditorPage(
                profile = DashboardProfile.COMPACT,
                state = env.state,
                colors = colors,
                haze = env.haze,
                viewModel = env.viewModel,
                onClose = { activity.finish() },
            )
        } else {
            ComponentsHomePage(
                state = env.state,
                colors = colors,
                haze = env.haze,
                viewModel = env.viewModel,
                actions = env.actions,
                layoutMode = env.layoutMode,
                safeDrawingPadding = env.safeDrawingPadding,
                bottomContentPadding = env.dockContentPadding,
                navigationBottom = env.navigationBottom,
                statusBarBottomDp = env.statusBarBottomDp,
                displayCutout = env.displayCutout,
                adaptiveMetrics = env.adaptiveMetrics,
                windowClass = env.windowClass,
                currentSpotlightPage = 0,
                onSpotlightPageChanged = {},
                onOpenOverview = {},
                onEditDashboard = {},
            )
        }
    }
}
