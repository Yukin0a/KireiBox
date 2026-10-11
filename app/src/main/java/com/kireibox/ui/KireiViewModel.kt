package com.kireibox.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kireibox.cover.CoverTaskQueue
import com.kireibox.cover.KireiCoverStore
import com.kireibox.cover.KireiCoverTaskBackend
import com.kireibox.cover.NsfwCoverBackfill
import com.kireibox.external.shortcut.ShizukuShortcutGateway
import com.kireibox.external.winlator.WinlatorGameOverride
import com.kireibox.krkr.KrkrGameSettings
import com.kireibox.krkr.KrkrGameSettingsStore
import com.kireibox.update.GitHubUpdateRepository
import com.kireibox.update.UpdateCheckResponse
import com.kireibox.update.UpdateDownloadProgress
import com.kireibox.update.UpdateDownloader
import com.kireibox.update.UpdateInstaller
import com.kireibox.update.UpdateRelease
import com.yuki.yukihub.data.YukiCoreFacade
import com.yuki.yukihub.metadata.YukiMetadataController
import com.yuki.yukihub.metadata.YukiMetadataService
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.migration.SingleCoreMigration
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.settings.ThemeMode
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

class KireiViewModel
    internal constructor(
        application: Application,
        // Test seam: injected collaborators for setHomePresentationMode.
        // When null (default), production code paths are used.
        internal val testHomePresentationWriter: ((HomePresentationMode) -> Unit)? = null,
        internal val testReloadAction: (() -> Unit)? = null,
        internal val testPayloadLoader: (suspend () -> ReloadPayload)? = null,
        internal val testImmersiveGalleryModeWriter: ((SpotlightSelectionMode) -> Unit)? = null,
        internal val testImmersiveGalleryIdsWriter: ((List<Long>) -> Unit)? = null,
        internal val testImmersiveGalleryAutoWriter: ((ImmersiveGalleryAutoSource) -> Unit)? = null,
        internal val testImmersiveGallerySourceWriter: ((SpotlightSource) -> Unit)? = null,
        internal val testImmersiveGalleryStrategyWriter: ((SpotlightStrategy) -> Unit)? = null,
        internal val testImmersiveGalleryCountWriter: ((Int) -> Unit)? = null,
        internal val testImmersiveGallerySettingsWriter: ((ImmersiveGallerySettings) -> Unit)? = null,
        internal val testUpdateCheck: (suspend (Int, Int, String?) -> UpdateCheckResponse)? = null,
        internal val testUpdatePreferenceWriter: ((Int?, String?, Long?) -> Unit)? = null,
        internal val testUpdateDownload: (suspend (com.kireibox.update.UpdateRelease, (UpdateDownloadProgress) -> Unit) -> File)? = null,
        internal val testUpdateValidate: ((File, UpdateRelease) -> com.kireibox.update.UpdatePackageValidation)? = null,
    ) : AndroidViewModel(application) {
        constructor(application: Application) : this(
            application = application,
            testHomePresentationWriter = null,
            testReloadAction = null,
            testPayloadLoader = null,
            testImmersiveGalleryModeWriter = null,
            testImmersiveGalleryIdsWriter = null,
            testImmersiveGalleryAutoWriter = null,
            testImmersiveGallerySourceWriter = null,
            testImmersiveGalleryStrategyWriter = null,
            testImmersiveGalleryCountWriter = null,
            testImmersiveGallerySettingsWriter = null,
            testUpdateCheck = null,
            testUpdatePreferenceWriter = null,
            testUpdateDownload = null,
            testUpdateValidate = null,
        )

        /** Optional visual-settings seam kept out of the constructor to preserve its baseline API. */
        internal var testImmersiveVisualWriter: ((ImmersiveVisualSettings) -> Unit)? = null

        internal val isTestMode: Boolean
            get() =
                testReloadAction != null && (
                    testHomePresentationWriter != null ||
                        testPayloadLoader != null ||
                        testImmersiveGalleryModeWriter != null ||
                        testImmersiveGalleryIdsWriter != null ||
                        testImmersiveGalleryAutoWriter != null ||
                        testImmersiveGallerySourceWriter != null ||
                        testImmersiveGalleryStrategyWriter != null ||
                        testImmersiveGalleryCountWriter != null ||
                        testImmersiveGallerySettingsWriter != null ||
                        testImmersiveVisualWriter != null ||
                        testUpdateCheck != null
                )

        // Lazy collaborators: deferred so test mode can avoid real Android init
        internal val core: YukiCoreFacade by lazy { YukiCoreFacade(application) }
        internal val repository by lazy { core.games }
        internal val metadata by lazy { YukiMetadataService(application) }
        internal val metadataController by lazy { YukiMetadataController(application) }
        internal val coverStore by lazy { KireiCoverStore(application) }
        private val nsfwCoverBackfill by lazy { NsfwCoverBackfill(application) }
        internal val krkrStore by lazy { KrkrGameSettingsStore(application) }
        internal val shortcutGateway by lazy { ShizukuShortcutGateway(application) }
        internal val accounts by lazy { com.yuki.yukihub.auth.CoverAccounts.get(application) }
        internal val playtimeSync by lazy { com.yuki.yukihub.sync.NextmoePlaytimeSync.get(application) }
        internal var coverSearchJob: Job? = null
        internal var coverSaveJob: Job? = null
        internal val coverSearchSemaphore = Semaphore(3)
        private val coverTaskBackend by lazy { KireiCoverTaskBackend(metadata, coverStore, repository, core.settings) }
        internal val coverNoMatchStore by lazy { com.kireibox.cover.CoverNoMatchStore(core.settings.rawPreferences()) }
        internal val coverQueue by lazy {
            CoverTaskQueue(
                scope = viewModelScope,
                backend = coverTaskBackend,
                onAutomaticNoMatch = { request ->
                    coverNoMatchStore.add(com.kireibox.cover.CoverNoMatchStore.key(request.gameId, request.title))
                },
            )
        }

        /**
         * The check must fail fast on a flaky network, while the APK download is large and long.
         * They therefore use separate clients: a bounded one for the API, an unbounded-call one for
         * the transfer.
         */
        private val updateHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
        private val updateDownloadClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }
        internal val updateRepository by lazy { GitHubUpdateRepository(updateHttpClient) }
        internal val updateDownloader by lazy {
            UpdateDownloader(updateDownloadClient, File(application.cacheDir, "updates"))
        }
        internal val updateInstaller by lazy { UpdateInstaller(application) }
        internal val autoCoverGate = AutoCoverSchedulingGate()
        internal val mutableState = MutableStateFlow(KireiUiState())
        val state: StateFlow<KireiUiState> = mutableState.asStateFlow()
        private val _homeSpotlightPage = MutableStateFlow(0)
        val homeSpotlightPage: StateFlow<Int> = _homeSpotlightPage.asStateFlow()

        // Extracted controllers (same package). Each holds a reference back to this facade so the
        // moved bodies can keep using the shared dependencies and state flow unchanged.
        private val updateController by lazy { KireiUpdateController(this) }
        private val reloadController by lazy { KireiLibraryReloadController(this) }
        private val coverTaskController by lazy { KireiCoverTaskController(this) }
        private val scanRootController by lazy { KireiScanRootController(this) }
        private val libraryMutationController by lazy { KireiLibraryMutationController(this) }
        private val libraryEntryController by lazy { KireiLibraryEntryController(this) }
        private val addGameController by lazy { KireiAddGameController(this) }
        internal val winlatorController by lazy { KireiWinlatorController(this) }
        private val coverAccountController by lazy { KireiCoverAccountController(this) }
        private val coverPickerController by lazy { KireiCoverPickerController(this) }
        private val settingsController by lazy { KireiSettingsController(this) }
        private val playtimeSyncController by lazy { KireiPlaytimeSyncController(this) }

        fun setHomeSpotlightPage(page: Int) {
            _homeSpotlightPage.value = page.coerceAtLeast(0)
        }

        /**
         * True when the active data network is available and unmetered (Wi-Fi/Ethernet, or a VPN
         * over them). Kept here because it needs [ConnectivityManager]; the pure decision lives in
         * [com.yuki.yukihub.settings.CoverAutoMatchNetworkPolicy].
         */
        internal fun isOnUnmeteredNetwork(): Boolean {
            val manager =
                getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !manager.isActiveNetworkMetered
        }

        init {
            if (!isTestMode) {
                mutableState.value =
                    mutableState.value.copy(
                        diagnosticLoggingEnabled = DevLogger.isEnabled(),
                        shortcutServiceStatus = shortcutGateway.status(),
                    )
                viewModelScope.launch {
                    accounts.status.collect { statuses ->
                        mutableState.value = mutableState.value.copy(coverAccounts = statuses, includeAdult = accounts.includesAdult())
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { accounts.migrateBangumi() }
                }
                playtimeSyncController.start()
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { updateController.cleanupStaleUpdateFiles() }
                }
                viewModelScope.launch {
                    coverQueue.state.collect(coverTaskController::applyCoverQueueState)
                }
                viewModelScope.launch {
                    coverQueue.bindingCompletions.collect(coverTaskController::scheduleBindingRefresh)
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val paused = core.settings.isCoverQueuePaused()
                    core.settings.migrateScanRootsIfNeeded()
                    core.settings.migrateHomePresentationModeIfNeeded()
                    core.settings.migrateImmersiveGallerySettingsIfNeeded()
                    withContext(Dispatchers.Main) {
                        if (paused) coverQueue.pause()
                    }
                    val migration = SingleCoreMigration.run(application)
                    nsfwCoverBackfill.run()
                    withContext(Dispatchers.Main) {
                        mutableState.value = mutableState.value.copy(migrationMessage = migration.message)
                        reloadInternal()
                        checkForUpdates(manual = false)
                    }
                }
            }
        }

        /** The page opened as a shortcut (search result, root row, cover picker); back from it returns to the root (r4 · S2). */
        private var settingsShortcutOrigin: KireiSettingsPage? = null

        fun selectTab(tab: KireiTab) {
            settingsShortcutOrigin = null
            mutableState.value = mutableState.value.copy(tab = tab, settingsPage = KireiSettingsPage.ROOT)
        }

        fun openSettingsPage(page: KireiSettingsPage) {
            settingsShortcutOrigin = null
            mutableState.value = mutableState.value.copy(tab = KireiTab.SETTINGS, settingsPage = page)
        }

        /** A second-level page reached without its parent (search result, root row, cover picker): back goes straight to the root. */
        fun openSettingsShortcut(page: KireiSettingsPage) {
            openSettingsPage(page)
            settingsShortcutOrigin = page
        }

        fun navigateUpSettings() {
            val parent = KireiBackPolicy.settingsBackTarget(mutableState.value.settingsPage, settingsShortcutOrigin)
            settingsShortcutOrigin = null
            mutableState.value = mutableState.value.copy(tab = KireiTab.SETTINGS, settingsPage = parent)
        }

        fun clearNotice() {
            mutableState.value = mutableState.value.copy(notice = null, noticeTone = null)
        }

        fun dismissHomeLoadError() {
            mutableState.value = mutableState.value.copy(homeLoadError = null)
        }

        fun showNotice(message: String) {
            mutableState.value = mutableState.value.copy(notice = message)
        }

        fun selectedGame(game: Game) {
            mutableState.value = mutableState.value.copy(selectedGame = game, krkrSettings = if (game.engine == EngineType.KIRIKIRI) krkrStore.load(game) else KrkrGameSettings())
        }

        fun dismissSelectedGame() {
            mutableState.value = mutableState.value.copy(selectedGame = null)
        }

        // --- Updates -------------------------------------------------------------------------------

        fun checkForUpdates(manual: Boolean = false) = updateController.checkForUpdates(manual)

        fun ignoreAvailableUpdate() = updateController.ignoreAvailableUpdate()

        fun downloadAvailableUpdate() = updateController.downloadAvailableUpdate()

        fun cancelUpdateDownload() = updateController.cancelUpdateDownload()

        fun discardReadyUpdate() = updateController.discardReadyUpdate()

        fun dismissUpdateUi() = updateController.dismissUpdateUi()

        // --- Library reload ------------------------------------------------------------------------

        fun reload() = reloadController.reload()

        internal suspend fun reloadInternal(immersiveGeneration: Long? = null) = reloadController.reloadInternal(immersiveGeneration)

        internal fun reloadImmersiveSettingsLatest() = reloadController.reloadImmersiveSettingsLatest()

        // --- Library entries -----------------------------------------------------------------------

        fun openXp3Picker(game: Game) = libraryEntryController.openXp3Picker(game)

        fun openExePicker(game: Game) = libraryEntryController.openExePicker(game)

        fun dismissXp3Picker() = libraryEntryController.dismissXp3Picker()

        fun selectXp3LaunchTarget(relativePath: String) = libraryEntryController.selectXp3LaunchTarget(relativePath)

        fun openAndroidAppPicker() = libraryEntryController.openAndroidAppPicker()

        fun dismissAndroidAppPicker() = libraryEntryController.dismissAndroidAppPicker()

        fun selectAndroidApp(entry: AndroidAppEntry) = libraryEntryController.selectAndroidApp(entry)

        fun openExternalShortcutPicker() = libraryEntryController.openExternalShortcutPicker()

        fun onShizukuPermissionResult(granted: Boolean) = libraryEntryController.onShizukuPermissionResult(granted)

        fun refreshShortcutServiceStatus() = libraryEntryController.refreshShortcutServiceStatus()

        fun dismissExternalShortcutPicker() = libraryEntryController.dismissExternalShortcutPicker()

        fun toggleExternalShortcutSelection(stableKey: String) = libraryEntryController.toggleExternalShortcutSelection(stableKey)

        fun toggleAllExternalShortcuts() = libraryEntryController.toggleAllExternalShortcuts()

        fun importSelectedExternalShortcuts() = libraryEntryController.importSelectedExternalShortcuts()

        // --- Add game ------------------------------------------------------------------------------

        fun openAddSingle() = addGameController.openSingle()

        fun openAddBatch() = addGameController.openBatch()

        fun addGameBack() = addGameController.backToStart()

        fun addBatchFolder(uri: Uri) = addGameController.addBatchFolder(uri)

        fun removeBatchFolder(uri: String) = addGameController.removeBatchFolder(uri)

        fun startBatchDetect() = addGameController.startBatchDetect()

        fun addSingleFolder(
            uri: Uri,
            engine: EngineType?
        ) = addGameController.addSingleFolder(uri, engine)

        fun selectAddGameEngine(
            key: String,
            engine: EngineType
        ) = addGameController.review { AddGameReview.selectEngine(it, key, engine) }

        fun selectAddGameLaunchTarget(
            key: String,
            target: String
        ) = addGameController.review { AddGameReview.selectLaunchTarget(it, key, target) }

        fun setAddGameTitle(
            key: String,
            title: String
        ) = addGameController.review { AddGameReview.setTitle(it, key, title) }

        fun toggleAddGame(key: String) = addGameController.review { AddGameReview.toggle(it, key) }

        fun toggleAllAddGames() = addGameController.review(AddGameReview::toggleAll)

        fun editAddGame(key: String?) = addGameController.review { it.copy(editing = key) }

        fun confirmAddGame() = addGameController.confirm()

        fun addAndroidApp(
            packageName: String,
            activity: String?,
            label: String
        ) = libraryEntryController.addAndroidApp(packageName, activity, label)

        fun restoreRemovedGame(game: Game) = libraryEntryController.restoreRemovedGame(game)

        fun openKrkrEditor(game: Game) = libraryEntryController.openKrkrEditor(game)

        fun closeKrkrEditor() = libraryEntryController.closeKrkrEditor()

        fun saveKrkrSettings(
            game: Game,
            settings: KrkrGameSettings
        ) = libraryEntryController.saveKrkrSettings(game, settings)

        fun openWinlatorEditor(game: Game) = winlatorController.openEditor(game)

        fun closeWinlatorEditor() = winlatorController.closeEditor()

        fun saveWinlatorOverride(
            game: Game,
            override: WinlatorGameOverride
        ) = winlatorController.saveOverride(game, override)

        fun openWinlatorGuide() = winlatorController.openGuide()

        fun dismissWinlatorGuide() = winlatorController.dismissGuide()

        fun completeWinlatorGuide() = winlatorController.completeGuide()

        // --- Library mutations ---------------------------------------------------------------------

        fun renameGame(
            game: Game,
            title: String
        ) = libraryMutationController.renameGame(game, title)

        fun toggleFavorite(game: Game) = libraryMutationController.toggleFavorite(game)

        fun removeFromLibrary(game: Game) = libraryMutationController.removeFromLibrary(game)

        fun undoLastRemoval() = libraryMutationController.undoLastRemoval()

        fun dismissUndoRemoval() = libraryMutationController.dismissUndoRemoval()

        fun moveManualGame(
            gameId: Long,
            delta: Int
        ) = libraryMutationController.moveManualGame(gameId, delta)

        fun commitManualOrder(favorite: Boolean) = libraryMutationController.commitManualOrder(favorite)

        fun saveManualOrder(
            favoriteIds: List<Long>,
            regularIds: List<Long>
        ) = libraryMutationController.saveManualOrder(favoriteIds, regularIds)

        fun setArtemisVersion(
            game: Game,
            version: com.yuki.yukihub.launcher.ArtemisVersion
        ) = libraryMutationController.setArtemisVersion(game, version)

        // --- Cover task queue ----------------------------------------------------------------------

        fun suspendForEngineHandoff() = coverTaskController.suspendForEngineHandoff()

        fun resumeAfterEngineHandoff() = coverTaskController.resumeAfterEngineHandoff()

        fun pauseCoverQueue() = coverTaskController.pauseCoverQueue()

        fun resumeCoverQueue() = coverTaskController.resumeCoverQueue()

        fun cancelCoverTask(gameId: Long) = coverTaskController.cancelCoverTask(gameId)

        fun retryCoverTask(gameId: Long) = coverTaskController.retryCoverTask(gameId)

        fun dismissCoverTaskSessionRows() = coverTaskController.dismissCoverTaskSessionRows()

        fun openCoverTaskCenter() = coverTaskController.openCoverTaskCenter()

        fun closeCoverTaskCenter() = coverTaskController.closeCoverTaskCenter()

        fun openCoverReview(gameId: Long) = coverTaskController.openCoverReview(gameId)

        // --- Scan roots ----------------------------------------------------------------------------

        fun addScanRoot(uri: Uri) = scanRootController.addScanRoot(uri)

        fun replaceScanRoot(
            previousRoot: String?,
            uri: Uri
        ) = scanRootController.replaceScanRoot(previousRoot, uri)

        fun removeScanRoot(root: String) = scanRootController.removeScanRoot(root)

        fun undoScanRootRemoval() = scanRootController.undoScanRootRemoval()

        fun dismissUndoScanRootRemoval() = scanRootController.dismissUndoScanRootRemoval()

        // --- Cover accounts ------------------------------------------------------------------------

        fun setMetadataSource(source: CoverSourceMode) = coverAccountController.setMetadataSource(source)

        fun setNsfwBlurEnabled(value: Boolean) = coverAccountController.setNsfwBlurEnabled(value)

        fun setCoverHybridAutoMatch(value: Boolean) = coverAccountController.setCoverHybridAutoMatch(value)

        fun setCoverStrictL2(value: Boolean) = coverAccountController.setCoverStrictL2(value)

        fun setCoverAutoMatchWifiOnly(value: Boolean) = coverAccountController.setCoverAutoMatchWifiOnly(value)

        fun clearCoverNoMatchStore() = coverAccountController.clearCoverNoMatchStore()

        fun setBangumiToken(token: String) = coverAccountController.setBangumiToken(token)

        fun cancelAccountConnection() = coverAccountController.cancelAccountConnection()

        fun refreshCoverProfile(source: CoverSourceMode) = coverAccountController.refreshCoverProfile(source)

        fun disconnectCoverAccount(source: CoverSourceMode) = coverAccountController.disconnectCoverAccount(source)

        fun completeCoverLogin(
            source: CoverSourceMode,
            success: Boolean,
            message: String,
            selectDefault: Boolean
        ) = coverAccountController.completeCoverLogin(source, success, message, selectDefault)

        fun setIncludeAdult(value: Boolean) = coverAccountController.setIncludeAdult(value)

        fun setBangumiApiBase(base: String) = coverAccountController.setBangumiApiBase(base)

        fun toggleNsfwCover(game: Game) {
            if (game.id <= 0L) return
            viewModelScope.launch(Dispatchers.IO) {
                val updated = game.copy(nsfw = !game.nsfw, updatedAt = System.currentTimeMillis())
                val result =
                    runCatching {
                        check(repository.update(updated) == 1) { "保存 NSFW 封面设置失败" }
                        // Any explicit user choice is authoritative over later metadata refreshes.
                        core.settings.markNsfwDetected(game.id)
                    }
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        if (result.isSuccess) {
                            mutableState.value.copy(
                                games = mutableState.value.games.map { if (it.id == updated.id) updated else it },
                                selectedGame =
                                    mutableState.value.selectedGame?.let {
                                        if (it.id == updated.id) updated else it
                                    },
                                notice = if (updated.nsfw) "NSFW 封面模糊已开启" else "NSFW 封面模糊已关闭",
                            )
                        } else {
                            mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "保存 NSFW 封面设置失败", NoticeTone.ERROR)
                        }
                }
            }
        }

        // --- Settings ------------------------------------------------------------------------------

        fun setSortMode(mode: String) = settingsController.setSortMode(mode)

        fun setUseBuiltinFileChooser(value: Boolean) = settingsController.setUseBuiltinFileChooser(value)

        fun setAutoScanOnStartup(value: Boolean) = settingsController.setAutoScanOnStartup(value)

        fun setKeepRemovedGamesHiddenOnRefresh(value: Boolean) = settingsController.setKeepRemovedGamesHiddenOnRefresh(value)

        fun onAppForeground() {
            settingsController.onAppForeground()
            playtimeSyncController.onAppForeground()
        }

        // --- NextMoe playtime sync ---

        fun syncPlaytimeNow() = playtimeSyncController.syncNow()

        fun setPlaytimeSyncEnabled(value: Boolean) = playtimeSyncController.setEnabled(value)

        fun loadPlaytimeAttention() = playtimeSyncController.loadAttention()

        fun confirmPlaytimeWork(
            gameId: Long,
            workId: String,
        ) = playtimeSyncController.confirm(gameId, workId)

        fun pickPlaytimeWork(gameId: Long) = playtimeSyncController.pickWork(gameId)

        internal fun onCoverAccountChanged(source: CoverSourceMode) = playtimeSyncController.onAccountChanged(source)

        fun setKrEngineVersion(value: String) = settingsController.setKrEngineVersion(value)

        fun setKrCompat(value: Boolean) = settingsController.setKrCompat(value)

        fun setKrSkipStartupInfoDialog(value: Boolean) = settingsController.setKrSkipStartupInfoDialog(value)

        fun setArtemisScopedSaveDir(value: Boolean) = settingsController.setArtemisScopedSaveDir(value)

        fun setTyranoScopedSaveDir(value: Boolean) = settingsController.setTyranoScopedSaveDir(value)

        /** Compatibility shim: maps to an explicit [ThemeMode.DARK] / [ThemeMode.LIGHT]. */
        fun setDarkMode(value: Boolean) = settingsController.setDarkMode(value)

        fun setThemeMode(mode: ThemeMode) = settingsController.setThemeMode(mode)

        fun setAccentChoice(choice: AccentChoice) = settingsController.setAccentChoice(choice)

        fun setQuickSetupDone(done: Boolean) = settingsController.setQuickSetupDone(done)

        fun setMotionMode(value: MotionMode) = settingsController.setMotionMode(value)

        fun setHomePresentationMode(mode: HomePresentationMode) = settingsController.setHomePresentationMode(mode)

        fun setImmersivePlayTimeVisible(visible: Boolean) = settingsController.setImmersivePlayTimeVisible(visible)

        fun setImmersiveVisual(next: ImmersiveVisualSettings) = settingsController.setImmersiveVisual(next)

        fun setOrientationMode(mode: OrientationMode) = settingsController.setOrientationMode(mode)

        fun setImmersiveGalleryMode(mode: SpotlightSelectionMode) = settingsController.setImmersiveGalleryMode(mode)

        fun setImmersiveGalleryIds(ids: List<Long>) = settingsController.setImmersiveGalleryIds(ids)

        fun setImmersiveGallerySelectionMode(mode: SpotlightSelectionMode) = settingsController.setImmersiveGallerySelectionMode(mode)

        fun setImmersiveGalleryManualIds(ids: List<Long>) = settingsController.setImmersiveGalleryManualIds(ids)

        fun setImmersiveGalleryAutoSource(source: ImmersiveGalleryAutoSource) = settingsController.setImmersiveGalleryAutoSource(source)

        fun setImmersiveGallerySource(source: SpotlightSource) = settingsController.setImmersiveGallerySource(source)

        fun setImmersiveGalleryStrategy(strategy: SpotlightStrategy) = settingsController.setImmersiveGalleryStrategy(strategy)

        fun setImmersiveGalleryCount(count: Int) = settingsController.setImmersiveGalleryCount(count)

        fun setImmersiveGalleryMaxCount(count: Int) = settingsController.setImmersiveGalleryMaxCount(count)

        fun setImmersiveGallerySettings(settings: ImmersiveGallerySettings) = settingsController.setImmersiveGallerySettings(settings)

        fun setReducedMotion(value: Boolean) = settingsController.setReducedMotion(value)

        fun setReducedTransparency(value: Boolean) = settingsController.setReducedTransparency(value)

        fun setContrast(value: Boolean) = settingsController.setContrast(value)

        fun setDiagnosticLogging(value: Boolean) = settingsController.setDiagnosticLogging(value)

        fun saveDashboardLayout(
            profile: DashboardProfile,
            layout: DashboardLayout
        ) = settingsController.saveDashboardLayout(profile, layout)

        // --- Cover picker (the search itself lives in the picker controller) ---

        fun openCoverSearch(
            game: Game,
            origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU
        ) = coverPickerController.openCoverSearch(game, origin)

        fun dismissCoverSearch() {
            coverSearchJob?.cancel()
            coverSaveJob?.cancel()
            val origin = mutableState.value.coverSearch?.origin ?: return
            mutableState.value = mutableState.value.copy(coverSearch = null, taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(origin))
        }

        /** The picker's login hint: leaves the picker (not while saving) for 来源账号, whose back returns to the settings root. */
        fun openCoverAccountsFromPicker() {
            if (mutableState.value.coverSearch?.saving == true) return
            coverSearchJob?.cancel()
            mutableState.value = mutableState.value.copy(coverSearch = null, taskCenterOpen = false)
            openSettingsShortcut(KireiSettingsPage.COVER_ACCOUNTS)
        }

        fun searchCoverAllSources(
            game: Game,
            keyword: String,
            origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU,
        ) = coverPickerController.searchCoverAllSources(game, keyword, origin)

        fun updateCoverKeyword(value: String) = coverPickerController.updateCoverKeyword(value)

        fun selectCoverCandidate(key: String) = coverPickerController.selectCoverCandidate(key)

        fun selectCoverVariant(
            candidateKey: String,
            url: String
        ) = coverPickerController.selectCoverVariant(candidateKey, url)

        fun retryCoverSearch() = coverPickerController.retryCoverSearch()

        fun confirmSelectedCover() = coverPickerController.confirmSelectedCover()

        fun useDefaultCover() = coverPickerController.useDefaultCover()

        fun importLocalCover(uri: Uri) = coverPickerController.importLocalCover(uri)

        internal fun resolvePersistedCoverImmediately(
            gameId: Long,
            mode: String,
            persistedCoverUri: String?,
            keepCompletedRow: Boolean,
            nsfw: Boolean? = null,
        ): KireiUiState {
            coverQueue.resolvePersistedCover(gameId, keepCompletedRow)
            val queue = coverQueue.state.value
            val games =
                mutableState.value.games.map { game ->
                    if (game.id == gameId) {
                        game.copy(
                            coverUri = persistedCoverUri,
                            coverPersistUri = persistedCoverUri,
                            coverSourceType = if (persistedCoverUri == null) 0 else 1,
                            vndbBindingMode = mode,
                            nsfw = nsfw ?: game.nsfw,
                        )
                    } else {
                        game
                    }
                }
            return mutableState.value.copy(
                games = games,
                coverQueue = queue,
                coverTaskGameIds =
                    queue.tasks
                        .filter { it.status in ACTIVE_COVER_QUEUE_STATUSES }
                        .mapTo(linkedSetOf()) { it.gameId },
                pendingCoverCount = queue.pendingCount,
                pendingCoverReviewCount = pendingCoverReviewCount(games, queue),
                coverAttentionCount = coverAttentionCount(games, queue),
            )
        }
    }
