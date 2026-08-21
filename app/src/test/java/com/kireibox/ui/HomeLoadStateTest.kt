package com.kireibox.ui

import android.app.Application
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.YukiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeLoadStateTest {
    @Before
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun snapshot(): YukiSettingsStore.Snapshot = YukiSettingsStore.Snapshot(
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
        homePresentationMode = HomePresentationMode.IMMERSIVE,
    )

    private fun payload(games: List<Game> = emptyList()): ReloadPayload =
        ReloadPayload(
            games = games,
            snapshot = snapshot(),
            trend = emptyList(),
            searchIndex = emptyMap(),
        )

    private fun viewModel(loader: suspend () -> ReloadPayload): KireiViewModel =
        KireiViewModel(
            Application(),
            testHomePresentationWriter = {},
            testReloadAction = {},
            testPayloadLoader = loader,
        )

    @Test
    fun `initial ui state starts in initial loading with no error`() {
        val state = KireiUiState()
        assertEquals(HomeLoadState.INITIAL_LOADING, state.homeLoadState)
        assertNull(state.homeLoadError)
    }

    @Test
    fun `successful reload marks loaded and empty success`() = runTest {
        val vm = viewModel { payload(emptyList()) }
        vm.reloadInternal()
        assertEquals(HomeLoadState.LOADED, vm.state.value.homeLoadState)
        assertNull(vm.state.value.homeLoadError)
        assertTrue(vm.state.value.games.isEmpty())
    }

    @Test
    fun `failed reload keeps games and transports repository error`() = runTest {
        var fail = false
        val vm = viewModel {
            if (fail) throw IllegalStateException("simulated repository failure") else payload(listOf(Game()))
        }
        vm.reloadInternal()
        assertEquals(HomeLoadState.LOADED, vm.state.value.homeLoadState)
        assertEquals(1, vm.state.value.games.size)

        fail = true
        vm.reloadInternal()
        assertEquals(HomeLoadState.RELOAD_FAILED, vm.state.value.homeLoadState)
        assertEquals("simulated repository failure", vm.state.value.homeLoadError)
        assertEquals(1, vm.state.value.games.size)
    }
}
