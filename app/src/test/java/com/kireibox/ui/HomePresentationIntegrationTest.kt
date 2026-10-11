package com.kireibox.ui

import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.YukiSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePresentationIntegrationTest {
    private fun snapshot(mode: HomePresentationMode): YukiSettingsStore.Snapshot =
        YukiSettingsStore.Snapshot(
            scanRoots = emptyList(),
            scanRootEnabled = emptyList(),
            useBuiltinFileChooser = true,
            startupScanDepth = 2,
            autoScanOnStartup = false,
            sortMode = "",
            engineLabelPosition = "",
            metadataSource = "",
            bangumiToken = "",
            krEngineVersion = "",
            krCompatMode = false,
            krSkipStartupInfoDialog = false,
            artemisScopedSaveDir = true,
            tyranoScopedSaveDir = true,
            darkMode = true,
            motionMode = MotionMode.AUTO,
            reducedMotion = false,
            reducedTransparency = false,
            increasedContrast = false,
            dashboardCompact = DashboardLayouts.default(DashboardProfile.COMPACT),
            dashboardWide = DashboardLayouts.default(DashboardProfile.WIDE),
            homePresentationMode = mode,
        )

    @Test
    fun `snapshot to ui state to routing for all four combinations`() {
        val immersivePortrait = KireiUiState(settings = snapshot(HomePresentationMode.IMMERSIVE))
        assertEquals(
            HomeRenderKind.IMMERSIVE_PORTRAIT,
            HomePresentationPolicy.renderKind(immersivePortrait.settings!!.homePresentationMode, 852f, 1846f, HomeSizeClass.COMPACT),
        )
        val immersiveLandscape = KireiUiState(settings = snapshot(HomePresentationMode.IMMERSIVE))
        assertEquals(
            HomeRenderKind.IMMERSIVE_LANDSCAPE,
            HomePresentationPolicy.renderKind(immersiveLandscape.settings!!.homePresentationMode, 1672f, 941f, HomeSizeClass.WIDE),
        )
        val componentsPortrait = KireiUiState(settings = snapshot(HomePresentationMode.COMPONENTS))
        assertEquals(
            HomeRenderKind.COMPONENTS_PORTRAIT,
            HomePresentationPolicy.renderKind(componentsPortrait.settings!!.homePresentationMode, 852f, 1846f, HomeSizeClass.COMPACT),
        )
        val componentsLandscape = KireiUiState(settings = snapshot(HomePresentationMode.COMPONENTS))
        assertEquals(
            HomeRenderKind.COMPONENTS_LANDSCAPE,
            HomePresentationPolicy.renderKind(componentsLandscape.settings!!.homePresentationMode, 1672f, 941f, HomeSizeClass.WIDE),
        )
    }

    @Test
    fun `global dock is always drawn by the shell outside the page hazeSource`() {
        val immersive = KireiUiState(tab = KireiTab.HOME, settings = snapshot(HomePresentationMode.IMMERSIVE))
        assertTrue(HomePresentationPolicy.shouldShowGlobalDock(immersive.tab, immersive.settings?.homePresentationMode))
        val components = KireiUiState(tab = KireiTab.HOME, settings = snapshot(HomePresentationMode.COMPONENTS))
        assertTrue(HomePresentationPolicy.shouldShowGlobalDock(components.tab, components.settings?.homePresentationMode))
        val games = KireiUiState(tab = KireiTab.GAMES, settings = snapshot(HomePresentationMode.IMMERSIVE))
        assertTrue(HomePresentationPolicy.shouldShowGlobalDock(games.tab, games.settings?.homePresentationMode))
    }
}
