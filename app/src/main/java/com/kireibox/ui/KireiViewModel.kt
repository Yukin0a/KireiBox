package com.kireibox.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverBindingCompletion
import com.kireibox.cover.CoverTaskQueue
import com.kireibox.cover.CoverTaskSchedulingPolicy
import com.kireibox.cover.CoverTaskStatus
import com.kireibox.cover.KireiCoverStore
import com.kireibox.cover.KireiCoverTaskBackend
import com.kireibox.krkr.KrkrGameSettings
import com.kireibox.krkr.KrkrGameSettingsStore
import com.kireibox.update.GitHubUpdateRepository
import com.kireibox.update.UpdateCheckResponse
import com.kireibox.update.UpdateCheckResult
import com.kireibox.update.UpdateDownloadProgress
import com.kireibox.update.UpdateDownloader
import com.kireibox.update.UpdateInstaller
import com.kireibox.update.UpdateUiState
import com.yuki.yukihub.data.GameSearchIndex
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.GameLibraryOrdering
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.data.YukiCoreFacade
import com.yuki.yukihub.launch.GameLocationPreflight
import com.yuki.yukihub.launch.KrkrLaunchTargetScanner
import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.TitleMatchScorer
import com.yuki.yukihub.metadata.VnMetadata
import com.yuki.yukihub.metadata.YukiMetadataService
import com.yuki.yukihub.metadata.YukiMetadataController
import com.yuki.yukihub.migration.SingleCoreMigration
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.scanner.EngineDetector
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.LandscapeHomeStyle
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.HomePresentationMode
import com.yuki.yukihub.settings.OrientationMode
import com.yuki.yukihub.settings.ImmersiveGalleryAutoSource
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.ImmersiveGallerySettingsPolicy
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale

enum class HomeLoadState { INITIAL_LOADING, LOADED, RELOAD_FAILED }

/**
 * Deduplicates automatic-cover scheduling. The stable key is derived from the game
 * identity set (ids), the active metadata source, and the relevant credential/token
 * state, so theme/layout-only reloads never enqueue again while game/source/credential
 * changes do. In-memory only; no database or settings keys are involved.
 */
internal class AutoCoverSchedulingGate {
    private var lastKey: String? = null

    @Synchronized
    fun shouldSchedule(games: List<Game>, source: CoverSourceMode, credentialToken: String): Boolean {
        val key = buildString {
            games.asSequence().map(Game::id).distinct().sorted().forEach { append(it).append(',') }
            append('|').append(source.key)
            append('|').append(if (source.isBangumiFamily) credentialToken else "")
        }
        if (key == lastKey) return false
        lastKey = key
        return true
    }
}

/** Small scoped gate used by rapid immersive-setting writes; only the newest token may apply. */
internal class LatestWinsGate {
    private var generation = 0L

    @Synchronized
    fun issue(): Long {
        generation += 1L
        return generation
    }

    @Synchronized
    fun current(): Long = generation

    @Synchronized
    fun isCurrent(token: Long): Boolean = token == generation
}

enum class KireiTab { HOME, GAMES, SETTINGS }
enum class KireiSettingsPage {
    ROOT,
    LIBRARY_HUB,
    LIBRARY,
    COVERS,
    KRKR,
    INTERFACE,
    PERMISSIONS,
    DIAGNOSTICS,
    ABOUT,
}

data class CoverCandidateUi(val metadata: VnMetadata, val source: CoverSourceMode, val score: Double) {
    val key: String get() = "${source.key}:${metadata.id}:${metadata.coverUrl}"
}
data class CoverSearchUi(
    val game: Game? = null,
    val source: CoverSourceMode = CoverSourceMode.VNDB,
    val keyword: String = "",
    val candidates: List<CoverCandidateUi> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val selectedKey: String? = null,
    val attemptedKeywords: List<String> = emptyList(),
    val error: String? = null,
    val origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU,
)

data class UndoRemoval(val game: Game, val index: Int)

data class Xp3PickerUi(
    val game: Game,
    val candidates: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class AndroidAppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
) {
    val componentKey: String get() = "$packageName/$activityName"
}

data class AndroidAppPickerUi(
    val entries: List<AndroidAppEntry> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class KireiUiState(
    val tab: KireiTab = KireiTab.HOME,
    val settingsPage: KireiSettingsPage = KireiSettingsPage.ROOT,
    val games: List<Game> = emptyList(),
    val settings: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot? = null,
    val selectedGame: Game? = null,
    val krkrEditorGame: Game? = null,
    val krkrSettings: KrkrGameSettings = KrkrGameSettings(),
    val coverSearch: CoverSearchUi? = null,
    val xp3Picker: Xp3PickerUi? = null,
    val androidAppPicker: AndroidAppPickerUi? = null,
    val undoRemoval: UndoRemoval? = null,
    val busy: Boolean = false,
    val homeLoadState: HomeLoadState = HomeLoadState.INITIAL_LOADING,
    val homeLoadError: String? = null,
    val notice: String? = null,
    val migrationMessage: String? = null,
    val dashboardCompact: DashboardLayout = DashboardLayouts.default(DashboardProfile.COMPACT),
    val dashboardWide: DashboardLayout = DashboardLayouts.default(DashboardProfile.WIDE),
    val playTrend: List<PlayTrendPoint> = emptyList(),
    val gameSearchIndex: Map<Long, String> = emptyMap(),
    val diagnosticLoggingEnabled: Boolean = false,
    val immersiveGalleryMode: SpotlightSelectionMode = SpotlightSelectionMode.AUTOMATIC,
    val immersiveGalleryIds: List<Long> = emptyList(),
    val immersiveGallerySource: SpotlightSource = SpotlightSource.RECENT,
    val immersiveGalleryStrategy: SpotlightStrategy = SpotlightStrategy.FIRST,
    val immersiveGalleryCount: Int = ImmersiveGallerySettings.DEFAULT_COUNT,
    val coverQueue: CoverQueueUiState = CoverQueueUiState(emptyList(), com.kireibox.cover.CoverQueueStatus.RUNNING),
    val taskCenterOpen: Boolean = false,
    val coverTaskGameIds: Set<Long> = emptySet(),
    val pendingCoverCount: Int = 0,
    val updateState: UpdateUiState = UpdateUiState.Idle,
)

internal data class ReloadPayload(
    val games: List<Game>,
    val snapshot: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot,
    val trend: List<PlayTrendPoint>,
    val searchIndex: Map<Long, String>,
)

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
    internal val testUpdateValidate: ((File, com.kireibox.update.UpdateManifest) -> com.kireibox.update.UpdatePackageValidation)? = null,
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

    private val isTestMode: Boolean
        get() = testReloadAction != null && (
            testHomePresentationWriter != null ||
                testPayloadLoader != null ||
                testImmersiveGalleryModeWriter != null ||
                testImmersiveGalleryIdsWriter != null ||
                testImmersiveGalleryAutoWriter != null ||
                testImmersiveGallerySourceWriter != null ||
                testImmersiveGalleryStrategyWriter != null ||
                testImmersiveGalleryCountWriter != null ||
                testImmersiveGallerySettingsWriter != null
                || testUpdateCheck != null
            )
    // Lazy collaborators: deferred so test mode can avoid real Android init
    private val core: YukiCoreFacade by lazy { YukiCoreFacade(application) }
    private val repository by lazy { core.games }
    private val metadata by lazy { YukiMetadataService(application) }
    private val metadataController by lazy { YukiMetadataController(application) }
    private val coverStore by lazy { KireiCoverStore(application) }
    private val krkrStore by lazy { KrkrGameSettingsStore(application) }
    private val coverSearchSemaphore = Semaphore(3)
    private val coverTaskBackend by lazy { KireiCoverTaskBackend(metadata, coverStore, repository) }
    private val coverQueue by lazy { CoverTaskQueue(scope = viewModelScope, backend = coverTaskBackend) }
    private val updateHttpClient by lazy { OkHttpClient() }
    private val updateRepository by lazy { GitHubUpdateRepository(updateHttpClient) }
    private val updateDownloader by lazy {
        UpdateDownloader(updateHttpClient, File(application.cacheDir, "updates"))
    }
    private val updateInstaller by lazy { UpdateInstaller(application) }
    private var bindingRefreshJob: Job? = null
    private val immersiveReloadGate = LatestWinsGate()
    private val autoCoverGate = AutoCoverSchedulingGate()
    private var immersiveReloadJob: Job? = null
    private var updateDownloadJob: Job? = null
    private val _state = MutableStateFlow(KireiUiState())
    val state: StateFlow<KireiUiState> = _state.asStateFlow()
    private val _homeSpotlightPage = MutableStateFlow(0)
    val homeSpotlightPage: StateFlow<Int> = _homeSpotlightPage.asStateFlow()

    fun setHomeSpotlightPage(page: Int) {
        _homeSpotlightPage.value = page.coerceAtLeast(0)
    }

    init {
        if (!isTestMode) {
        _state.value = _state.value.copy(diagnosticLoggingEnabled = DevLogger.isEnabled())
        viewModelScope.launch {
            coverQueue.state.collect(::applyCoverQueueState)
        }
        viewModelScope.launch {
            coverQueue.bindingCompletions.collect(::scheduleBindingRefresh)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val paused = core.settings.isCoverQueuePaused()
            core.settings.migrateHomePresentationModeIfNeeded()
            core.settings.migrateImmersiveGallerySettingsIfNeeded()
            withContext(Dispatchers.Main) {
                if (paused) coverQueue.pause()
            }
            val migration = SingleCoreMigration.run(application)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(migrationMessage = migration.message)
                reloadInternal()
                checkForUpdates(manual = false)
            }
        }
        }
    }

    fun selectTab(tab: KireiTab) { _state.value = _state.value.copy(tab = tab, settingsPage = KireiSettingsPage.ROOT) }
    fun openSettingsPage(page: KireiSettingsPage) { _state.value = _state.value.copy(tab = KireiTab.SETTINGS, settingsPage = page) }
    fun navigateUpSettings() {
        val parent = KireiBackPolicy.settingsParent(_state.value.settingsPage) ?: KireiSettingsPage.ROOT
        _state.value = _state.value.copy(tab = KireiTab.SETTINGS, settingsPage = parent)
    }
    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
    fun dismissHomeLoadError() {
        _state.value = _state.value.copy(homeLoadError = null)
    }
    fun showNotice(message: String) { _state.value = _state.value.copy(notice = message) }

    fun checkForUpdates(manual: Boolean = false) {
        _state.value = _state.value.copy(updateState = UpdateUiState.Checking(manual))
        viewModelScope.launch {
            val settings = _state.value.settings
            val ignoredVersion = settings?.ignoredUpdateVersionCode ?: 0
            // Startup checks are unconditional: after a process restart an ETag alone
            // cannot recreate the update dialog that may have been interrupted. Manual
            // checks can safely reuse it because this repository retains the last result.
            val etag = settings?.updateEtag?.takeIf(String::isNotBlank)?.takeIf { manual }
            val response = if (testUpdateCheck != null) {
                testUpdateCheck.invoke(com.yuki.yukihub.BuildConfig.VERSION_CODE, ignoredVersion, etag)
            } else {
                updateRepository.check(com.yuki.yukihub.BuildConfig.VERSION_CODE, ignoredVersion, etag)
            }
            writeUpdatePreferences(etag = response.etag, checkedAt = System.currentTimeMillis())
            _state.value = when (val result = response.result) {
                is UpdateCheckResult.Available -> _state.value.copy(
                    updateState = UpdateUiState.Available(result.release),
                )
                UpdateCheckResult.UpToDate,
                UpdateCheckResult.NotModified,
                -> _state.value.copy(
                    updateState = UpdateUiState.Idle,
                    notice = if (manual) "已是最新版" else _state.value.notice,
                )
                UpdateCheckResult.NoPublishedRelease -> _state.value.copy(
                    updateState = UpdateUiState.Idle,
                    notice = if (manual) "GitHub 暂无公开版本" else _state.value.notice,
                )
                is UpdateCheckResult.Failed -> _state.value.copy(
                    updateState = if (manual) UpdateUiState.Error(result.message) else UpdateUiState.Idle,
                )
            }
        }
    }

    fun ignoreAvailableUpdate() {
        val release = (_state.value.updateState as? UpdateUiState.Available)?.release ?: return
        writeUpdatePreferences(ignoredVersion = release.manifest.versionCode)
        _state.value = _state.value.copy(
            updateState = UpdateUiState.Idle,
            settings = _state.value.settings?.copy(
                ignoredUpdateVersionCode = release.manifest.versionCode,
            ),
        )
    }

    fun downloadAvailableUpdate() {
        val release = when (val current = _state.value.updateState) {
            is UpdateUiState.Available -> current.release
            is UpdateUiState.Error -> current.release
            else -> null
        } ?: return
        updateDownloadJob?.cancel()
        _state.value = _state.value.copy(
            updateState = UpdateUiState.Downloading(release, 0L, release.apkSize, 0L),
        )
        updateDownloadJob = viewModelScope.launch {
            try {
                val file = if (testUpdateDownload != null) {
                    testUpdateDownload.invoke(release, ::applyUpdateDownloadProgress)
                } else {
                    updateDownloader.download(release, ::applyUpdateDownloadProgress)
                }
                val validation = if (testUpdateValidate != null) {
                    testUpdateValidate.invoke(file, release.manifest)
                } else {
                    updateInstaller.validate(file, release.manifest)
                }
                _state.value = when (validation) {
                    com.kireibox.update.UpdatePackageValidation.Valid -> _state.value.copy(
                        updateState = UpdateUiState.ReadyToInstall(release, file.absolutePath),
                    )
                    is com.kireibox.update.UpdatePackageValidation.Invalid -> {
                        file.delete()
                        _state.value.copy(updateState = UpdateUiState.Error(validation.message, release))
                    }
                }
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(updateState = UpdateUiState.Available(release))
                throw cancelled
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    updateState = UpdateUiState.Error(
                        error.message?.takeIf(String::isNotBlank) ?: "更新下载失败",
                        release,
                    ),
                )
            }
        }
    }

    fun cancelUpdateDownload() {
        val release = (_state.value.updateState as? UpdateUiState.Downloading)?.release ?: return
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        _state.value = _state.value.copy(updateState = UpdateUiState.Available(release))
    }

    fun discardReadyUpdate() {
        val ready = _state.value.updateState as? UpdateUiState.ReadyToInstall ?: return
        val file = File(ready.apkPath)
        val updateRoot = File(getApplication<Application>().cacheDir, "updates").canonicalFile
        if (runCatching { file.canonicalFile.parentFile == updateRoot }.getOrDefault(false)) file.delete()
        _state.value = _state.value.copy(updateState = UpdateUiState.Idle)
    }

    private fun applyUpdateDownloadProgress(progress: UpdateDownloadProgress) {
        val current = _state.value.updateState as? UpdateUiState.Downloading ?: return
        _state.value = _state.value.copy(
            updateState = current.copy(
                bytesRead = progress.bytesRead,
                totalBytes = progress.totalBytes,
                bytesPerSecond = progress.bytesPerSecond,
            ),
        )
    }

    fun dismissUpdateUi() {
        _state.value = _state.value.copy(updateState = UpdateUiState.Idle)
    }

    private fun writeUpdatePreferences(
        ignoredVersion: Int? = null,
        etag: String? = null,
        checkedAt: Long? = null,
    ) {
        if (testUpdatePreferenceWriter != null) {
            testUpdatePreferenceWriter.invoke(ignoredVersion, etag, checkedAt)
            return
        }
        ignoredVersion?.let(core.settings::setIgnoredUpdateVersionCode)
        etag?.let(core.settings::setUpdateEtag)
        checkedAt?.let(core.settings::setLastUpdateCheckAt)
    }
    fun selectedGame(game: Game) {
        _state.value = _state.value.copy(selectedGame = game, krkrSettings = if (game.engine == EngineType.KIRIKIRI) krkrStore.load(game) else KrkrGameSettings())
    }
    fun dismissSelectedGame() { _state.value = _state.value.copy(selectedGame = null) }

    fun openXp3Picker(game: Game) {
        _state.value = _state.value.copy(
            selectedGame = null,
            xp3Picker = Xp3PickerUi(game = game),
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                KrkrLaunchTargetScanner.list(getApplication(), game.rootUri, maxDepth = 4)
            }
            withContext(Dispatchers.Main) {
                val current = _state.value.xp3Picker
                if (current?.game?.id != game.id) return@withContext
                _state.value = _state.value.copy(
                    xp3Picker = current.copy(
                        candidates = result.getOrDefault(emptyList()),
                        loading = false,
                        error = result.exceptionOrNull()?.message,
                    ),
                )
            }
        }
    }

    fun dismissXp3Picker() {
        _state.value = _state.value.copy(xp3Picker = null)
    }

    fun selectXp3LaunchTarget(relativePath: String) {
        val picker = _state.value.xp3Picker ?: return
        val target = relativePath.replace('\\', '/').trimStart('/')
        if (target !in picker.candidates) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.findById(picker.game.id)
            val result = runCatching {
                check(current != null) { "游戏记录不存在" }
                check(current.engine == EngineType.KIRIKIRI) { "只有 KRKR 游戏可以选择 XP3" }
                val updated = current.copy(
                    launchTarget = target,
                    updatedAt = System.currentTimeMillis(),
                )
                check(repository.update(updated) == 1) { "保存启动文件失败" }
            }
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(
                        xp3Picker = null,
                        notice = "已选择启动文件：$target",
                    )
                } else {
                    _state.value.copy(
                        xp3Picker = _state.value.xp3Picker?.copy(
                            loading = false,
                            error = result.exceptionOrNull()?.message ?: "保存启动文件失败",
                        ),
                    )
                }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun openAndroidAppPicker() {
        _state.value = _state.value.copy(androidAppPicker = AndroidAppPickerUi())
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val result = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                    .asSequence()
                    .filter { info ->
                        val activity = info.activityInfo
                        activity != null &&
                            activity.enabled &&
                            activity.applicationInfo?.enabled == true &&
                            activity.packageName != app.packageName
                    }
                    .map { info ->
                        val activity = requireNotNull(info.activityInfo)
                        AndroidAppEntry(
                            packageName = activity.packageName,
                            activityName = activity.name,
                            label = info.loadLabel(app.packageManager).toString()
                                .trim().ifEmpty { activity.packageName },
                        )
                    }
                    .distinctBy(AndroidAppEntry::componentKey)
                    .sortedWith(
                        compareBy<AndroidAppEntry> { it.label.lowercase(Locale.ROOT) }
                            .thenBy { it.packageName },
                    )
                    .toList()
            }
            withContext(Dispatchers.Main) {
                if (_state.value.androidAppPicker == null) return@withContext
                _state.value = _state.value.copy(
                    androidAppPicker = AndroidAppPickerUi(
                        entries = result.getOrDefault(emptyList()),
                        loading = false,
                        error = result.exceptionOrNull()?.message,
                    ),
                )
            }
        }
    }

    fun dismissAndroidAppPicker() {
        _state.value = _state.value.copy(androidAppPicker = null)
    }

    fun selectAndroidApp(entry: AndroidAppEntry) {
        _state.value = _state.value.copy(androidAppPicker = null)
        addAndroidApp(entry.packageName, entry.activityName, entry.label)
    }

    fun openKrkrEditor(game: Game) {
        _state.value = _state.value.copy(
            selectedGame = null,
            krkrEditorGame = game,
            krkrSettings = krkrStore.load(game)
        )
    }
    fun closeKrkrEditor() { _state.value = KireiBackPolicy.closeKrkrEditor(_state.value) }

    fun renameGame(game: Game, title: String) {
        val normalized = title.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            game.title = normalized
            game.updatedAt = System.currentTimeMillis()
            repository.update(game)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(selectedGame = null, notice = "游戏名称已更新")
            }
            reloadInternal()
        }
    }

    fun toggleFavorite(game: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = game.copy(favorite = !game.favorite, updatedAt = System.currentTimeMillis())
            val result = runCatching { check(repository.update(updated) == 1) { "收藏状态保存失败" } }
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(
                        selectedGame = null,
                        notice = if (updated.favorite) "已收藏并置顶" else "已取消收藏"
                    )
                } else {
                    _state.value.copy(notice = result.exceptionOrNull()?.message ?: "收藏状态保存失败")
                }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun removeFromLibrary(game: Game) {
        val originalIndex = _state.value.games.indexOfFirst { it.id == game.id }.coerceAtLeast(0)
        viewModelScope.launch(Dispatchers.IO) {
            val hiddenGame = game.copy(hidden = true, updatedAt = System.currentTimeMillis())
            val result = runCatching {
                check(repository.update(hiddenGame) == 1) { "无法移出游戏库" }
            }
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(
                        games = _state.value.games.filterNot { it.id == game.id },
                        selectedGame = null,
                        undoRemoval = UndoRemoval(game.copy(hidden = false), originalIndex),
                        notice = null
                    )
                } else {
                    _state.value.copy(notice = result.exceptionOrNull()?.message ?: "无法移出游戏库")
                }
            }
        }
    }

    fun undoLastRemoval() {
        val undo = _state.value.undoRemoval ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val restoredGame = undo.game.copy(hidden = false, updatedAt = System.currentTimeMillis())
            val result = runCatching { check(repository.update(restoredGame) == 1) { "恢复失败" } }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val restored = _state.value.games.toMutableList().apply {
                        if (none { it.id == restoredGame.id }) add(undo.index.coerceIn(0, size), restoredGame)
                    }
                    _state.value = _state.value.copy(
                        games = GameLibraryOrdering.sorted(restored, _state.value.settings?.sortMode.orEmpty()),
                        undoRemoval = null,
                        notice = "已恢复 ${restoredGame.title ?: "游戏"}"
                    )
                } else {
                    _state.value = _state.value.copy(notice = result.exceptionOrNull()?.message ?: "恢复失败")
                }
            }
        }
    }

    fun dismissUndoRemoval() { _state.value = _state.value.copy(undoRemoval = null) }

    fun reload() { viewModelScope.launch(Dispatchers.IO) { reloadInternal() } }

    private fun reloadImmersiveSettingsLatest() {
        val token = immersiveReloadGate.issue()
        immersiveReloadJob?.cancel()
        if (isTestMode && testReloadAction != null) {
            testReloadAction!!()
            return
        }
        immersiveReloadJob = viewModelScope.launch(Dispatchers.IO) {
            reloadInternal(token)
        }
    }

    internal suspend fun reloadInternal(immersiveGeneration: Long? = null) {
        val observedImmersiveGeneration = immersiveGeneration ?: immersiveReloadGate.current()
        if (_state.value.homeLoadState == HomeLoadState.RELOAD_FAILED) {
            _state.value = _state.value.copy(homeLoadState = HomeLoadState.INITIAL_LOADING, homeLoadError = null)
        }
        val result = runCatching {
            withContext(Dispatchers.IO) {
                testPayloadLoader?.invoke() ?: loadPayloadFromCore()
            }
        }
        result.onSuccess { payload ->
            if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@onSuccess
            val normalizedManualIds = ImmersiveGallerySettingsPolicy.normalizeIds(
                payload.snapshot.immersiveGalleryIds,
                payload.games.mapTo(linkedSetOf()) { it.id },
            )
            val snapshot = if (normalizedManualIds == payload.snapshot.immersiveGalleryIds) {
                payload.snapshot
            } else {
                payload.snapshot.copy(immersiveGalleryIds = normalizedManualIds)
            }
            if (!isTestMode && normalizedManualIds != payload.snapshot.immersiveGalleryIds) {
                core.settings.setImmersiveGalleryIds(normalizedManualIds)
            }
            withContext(Dispatchers.Main) {
                if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@withContext
                _state.value = _state.value.copy(
                    games = payload.games,
                    settings = snapshot,
                    dashboardCompact = snapshot.dashboardCompact,
                    dashboardWide = snapshot.dashboardWide,
                    playTrend = payload.trend,
                    gameSearchIndex = payload.searchIndex,
                    immersiveGalleryMode = snapshot.immersiveGalleryMode,
                    immersiveGalleryIds = snapshot.immersiveGalleryIds,
                    immersiveGallerySource = snapshot.immersiveGallerySource,
                    immersiveGalleryStrategy = snapshot.immersiveGalleryStrategy,
                    immersiveGalleryCount = snapshot.immersiveGalleryCount,
                    busy = false,
                    homeLoadState = HomeLoadState.LOADED,
                    homeLoadError = null,
                )
            }
            if (!isTestMode && immersiveReloadGate.isCurrent(observedImmersiveGeneration)) {
                enqueueAutomaticCoversIfChanged(
                    payload.games,
                    CoverSourceMode.fromStored(snapshot.metadataSource),
                    snapshot.bangumiToken,
                )
            }
        }.onFailure { error ->
            if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@onFailure
            withContext(Dispatchers.Main) {
                if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@withContext
                _state.value = _state.value.copy(
                    busy = false,
                    homeLoadState = HomeLoadState.RELOAD_FAILED,
                    homeLoadError = error.message ?: "加载失败",
                )
            }
        }
    }

    private suspend fun loadPayloadFromCore(): ReloadPayload {
        val games = core.listGames().filter {
            !it.hidden && it.engine in setOf(
                EngineType.KIRIKIRI,
                EngineType.ONS,
                EngineType.TYRANO,
                EngineType.ARTEMIS,
                EngineType.ANDROID_APP,
            )
        }
        val searchIndex = games.associate { game ->
            val aliases = if (game.coverUri.isNullOrBlank()) {
                emptyList()
            } else {
                CoverSourceMode.entries.flatMap { source ->
                    metadataController.metadata(game.id, source)?.let { item ->
                        buildList {
                            add(item.chineseTitle)
                            add(item.originalTitle)
                            add(item.romanTitle)
                            addAll(item.titleAliases)
                        }
                    }.orEmpty()
                }
            }
            game.id to GameSearchIndex.build(game, aliases)
        }
        return ReloadPayload(
            games = games,
            snapshot = core.settings.snapshot(),
            trend = repository.dailyPlayTrend(30),
            searchIndex = searchIndex,
        )
    }

    private fun enqueueAutomaticCoversIfChanged(
        games: List<Game>,
        source: CoverSourceMode,
        credentialToken: String,
    ) {
        if (!autoCoverGate.shouldSchedule(games, source, credentialToken)) return
        enqueueAutomaticCovers(games, source)
    }

    private fun enqueueAutomaticCovers(games: List<Game>, source: CoverSourceMode) {
        CoverTaskSchedulingPolicy.requests(games, source).forEach(coverQueue::enqueue)
    }

    private fun applyCoverQueueState(snapshot: CoverQueueUiState) {
        _state.value = _state.value.copy(
            coverQueue = snapshot,
            coverTaskGameIds = snapshot.tasks
                .filter { it.status in ACTIVE_COVER_QUEUE_STATUSES }
                .mapTo(linkedSetOf()) { it.gameId },
            pendingCoverCount = snapshot.pendingCount,
        )
    }

    private fun scheduleBindingRefresh(completion: CoverBindingCompletion) {
        if (!completion.success) return
        bindingRefreshJob?.cancel()
        bindingRefreshJob = viewModelScope.launch {
            delay(BINDING_REFRESH_DEBOUNCE_MS)
            reloadInternal()
        }
    }
    fun pauseCoverQueue() {
        core.settings.setCoverQueuePaused(true)
        coverQueue.pause()
    }

    fun resumeCoverQueue() {
        core.settings.setCoverQueuePaused(false)
        coverQueue.resume()
    }

    fun cancelCoverTask(gameId: Long) = coverQueue.cancel(gameId)
    fun retryCoverTask(gameId: Long) = coverQueue.retry(gameId)
    fun dismissCoverTaskSessionRows() = coverQueue.dismissSessionRows()
    fun openCoverTaskCenter() { _state.value = _state.value.copy(taskCenterOpen = true) }
    fun closeCoverTaskCenter() { _state.value = _state.value.copy(taskCenterOpen = false) }

    fun openCoverReview(gameId: Long) {
        val task = _state.value.coverQueue.tasks.firstOrNull { it.gameId == gameId } ?: return
        if (task.status != CoverTaskStatus.NEEDS_REVIEW) return
        val game = _state.value.games.firstOrNull { it.id == gameId } ?: return
        val candidates = coverQueue.candidates(gameId).map { it.toUi(task.source) }
        _state.value = _state.value.copy(taskCenterOpen = false, coverSearch = CoverSearchUi(
            game = game, source = task.source, keyword = game.title.orEmpty(), candidates = candidates,
            selectedKey = candidates.firstOrNull()?.key, origin = CoverPickerOrigin.TASK_CENTER,
        ))
    }

    fun addScanRoot(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(busy = true)
            core.settings.addScanRoot(uri.toString())
            val report = core.scan(uri)
            val imported = core.importScan(report)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(busy = false, notice = "扫描完成：发现 ${imported.size} 个游戏")
            }
            reloadInternal()
        }
    }

    fun importLaunchFile(treeUri: Uri, fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = runCatching { androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), treeUri) }.getOrNull()
            val file = root?.findFile(fileName)
            if (file == null) {
                withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "所选文件不在授权目录中") }
                return@launch
            }
            val lower = fileName.lowercase()
            val engine = when {
                lower.endsWith(".xp3") -> EngineType.KIRIKIRI
                lower.endsWith(".html") || lower == "index.html" -> EngineType.TYRANO
                lower.endsWith(".nsa") || lower.endsWith(".ns2") || lower.endsWith(".txt") -> EngineType.ONS
                lower.endsWith(".pfs") || lower.endsWith(".iet") -> EngineType.ARTEMIS
                else -> EngineType.UNKNOWN
            }
            if (engine == EngineType.UNKNOWN) {
                withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "不支持的启动文件") }
                return@launch
            }
            val existing = repository.findByIdentity(treeUri.toString(), fileName)
            val game = existing ?: Game()
            game.title = if (fileName.equals("data.xp3", true) || fileName.startsWith("patch", true)) {
                root?.name ?: fileName
            } else fileName.substringBeforeLast('.')
            game.originalTitle = game.originalTitle ?: game.title
            game.engine = engine
            game.rootUri = treeUri.toString()
            game.entryKey = GameRepository.normalizeEntryKey(fileName)
            game.launchTarget = fileName
            game.emulatorPackage = when (engine) {
                EngineType.KIRIKIRI -> "internal.krkr"
                EngineType.ONS -> "internal.ons"
                EngineType.TYRANO -> "internal.tyrano"
                EngineType.ARTEMIS -> "internal.artemis"
                else -> ""
            }
            if (game.id > 0) repository.update(game) else repository.insert(game)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "已添加 ${game.title}") }
            reloadInternal()
        }
    }

    fun importPhysicalLaunchFile(fileUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = fileUri.path?.let { java.io.File(it) }
            if (file?.isFile != true) {
                withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "所选启动文件无法读取") }
                return@launch
            }
            val fileName = file.name
            val lower = fileName.lowercase()
            val engine = when {
                lower.endsWith(".xp3") || lower == "startup.tjs" -> EngineType.KIRIKIRI
                lower.endsWith(".html") -> EngineType.TYRANO
                lower.endsWith(".nsa") || lower.endsWith(".sar") || lower.endsWith(".ns2") ||
                    lower.endsWith(".nt2") || lower.endsWith(".nt3") ||
                    lower == "0.txt" || lower == "00.txt" -> EngineType.ONS
                lower.endsWith(".pfs") || lower.endsWith(".iet") -> EngineType.ARTEMIS
                else -> EngineType.UNKNOWN
            }
            if (engine == EngineType.UNKNOWN) {
                withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "不支持的启动文件") }
                return@launch
            }
            val rootUri = Uri.fromFile(file.parentFile).toString()
            val existing = repository.findByIdentity(rootUri, fileName)
            val game = existing ?: Game()
            game.title = if (
                fileName.equals("data.xp3", true) ||
                fileName.startsWith("patch", true) ||
                fileName.equals("startup.tjs", true)
            ) file.parentFile?.name ?: fileName else fileName.substringBeforeLast('.')
            game.originalTitle = game.originalTitle ?: game.title
            game.engine = engine
            game.rootUri = rootUri
            game.entryKey = GameRepository.normalizeEntryKey(fileName)
            game.launchTarget = fileName
            game.emulatorPackage = when (engine) {
                EngineType.KIRIKIRI -> "internal.krkr"
                EngineType.ONS -> "internal.ons"
                EngineType.TYRANO -> "internal.tyrano"
                EngineType.ARTEMIS -> "internal.artemis"
                else -> ""
            }
            if (game.id > 0) repository.update(game) else repository.insert(game)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "已添加 ${game.title}") }
            reloadInternal()
        }
    }

    fun addAndroidApp(packageName: String, activity: String?, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = Game(title = label, originalTitle = label, engine = EngineType.ANDROID_APP)
            game.rootUri = "android-app://$packageName"
            game.entryKey = activity.orEmpty()
            game.launchTarget = activity.orEmpty()
            game.emulatorPackage = packageName
            repository.insertIfNotExists(game)
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(notice = "已添加 Android 应用：$label") }
            reloadInternal()
        }
    }

    fun setMetadataSource(source: CoverSourceMode) {
        core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_METADATA_SOURCE, source.key)
        reload()
    }
    fun setBangumiToken(token: String) { core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_BANGUMI_TOKEN, token); reload() }
    fun setSortMode(mode: String) { core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_SORT_MODE, mode); reload() }
    fun setUseBuiltinFileChooser(value: Boolean) {
        core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_USE_BUILTIN_FILE_CHOOSER, value)
        reload()
    }
    fun setKrEngineVersion(value: String) { core.settings.putString(com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_ENGINE_VERSION, value); reload() }
    fun setKrCompat(value: Boolean) { core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_COMPAT_MODE, value); reload() }
    fun setKrSkipStartupInfoDialog(value: Boolean) {
        core.settings.putString(
            com.yuki.yukihub.settings.YukiSettingsStore.KEY_KR_STARTUP_DIALOG_POLICY,
            if (value) {
                com.yuki.yukihub.settings.YukiSettingsStore.DIALOG_POLICY_SKIP_SINGLE_BUTTON_STARTUP_INFO
            } else {
                com.yuki.yukihub.settings.YukiSettingsStore.DIALOG_POLICY_SHOW_ALL
            }
        )
        reload()
    }
    fun setDarkMode(value: Boolean) { core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_DARK_MODE, value); reload() }
    fun setMotionMode(value: MotionMode) { core.settings.setMotionMode(value); reload() }
    fun setHomePresentationMode(mode: HomePresentationMode) {
        if (testHomePresentationWriter != null && testReloadAction != null) {
            testHomePresentationWriter!!(mode)
            testReloadAction!!()
        } else {
            core.settings.setHomePresentationMode(mode)
            reload()
        }
    }
    fun setOrientationMode(mode: OrientationMode) {
        core.settings.setOrientationMode(mode)
        reload()
    }
    fun setImmersiveGalleryMode(mode: SpotlightSelectionMode) {
        if (testImmersiveGalleryModeWriter != null && testReloadAction != null) {
            testImmersiveGalleryModeWriter!!(mode)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryMode(mode)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGalleryIds(ids: List<Long>) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeIds(ids)
        if (testImmersiveGalleryIdsWriter != null && testReloadAction != null) {
            testImmersiveGalleryIdsWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryIds(ids)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGallerySelectionMode(mode: SpotlightSelectionMode) = setImmersiveGalleryMode(mode)

    fun setImmersiveGalleryManualIds(ids: List<Long>) = setImmersiveGalleryIds(ids)

    fun setImmersiveGalleryAutoSource(source: ImmersiveGalleryAutoSource) {
        if (testImmersiveGalleryAutoWriter != null && testReloadAction != null) {
            testImmersiveGalleryAutoWriter!!(source)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryAutoSource(source)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGallerySource(source: SpotlightSource) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeSource(source)
        if (testImmersiveGallerySourceWriter != null && testReloadAction != null) {
            testImmersiveGallerySourceWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGallerySource(normalized)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGalleryStrategy(strategy: SpotlightStrategy) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeStrategy(strategy)
        if (testImmersiveGalleryStrategyWriter != null && testReloadAction != null) {
            testImmersiveGalleryStrategyWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryStrategy(normalized)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGalleryCount(count: Int) {
        val normalized = ImmersiveGallerySettingsPolicy.normalizeCount(count)
        if (testImmersiveGalleryCountWriter != null && testReloadAction != null) {
            testImmersiveGalleryCountWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGalleryCount(normalized)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setImmersiveGalleryMaxCount(count: Int) = setImmersiveGalleryCount(count)

    fun setImmersiveGallerySettings(settings: ImmersiveGallerySettings) {
        val normalized = settings.normalized()
        if (testImmersiveGallerySettingsWriter != null && testReloadAction != null) {
            testImmersiveGallerySettingsWriter!!(normalized)
            testReloadAction!!()
        } else {
            core.settings.setImmersiveGallerySettings(normalized)
            reloadImmersiveSettingsLatest()
        }
    }
    fun setReducedMotion(value: Boolean) { setMotionMode(if (value) MotionMode.REDUCED else MotionMode.AUTO) }
    fun setReducedTransparency(value: Boolean) { core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_REDUCED_TRANSPARENCY, value); reload() }
    fun setContrast(value: Boolean) { core.settings.putBoolean(com.yuki.yukihub.settings.YukiSettingsStore.KEY_INCREASED_CONTRAST, value); reload() }
    fun setDiagnosticLogging(value: Boolean) {
        DevLogger.setEnabled(getApplication(), value)
        _state.value = _state.value.copy(
            diagnosticLoggingEnabled = DevLogger.isEnabled(),
            notice = if (value) "诊断日志已开启" else "诊断日志已关闭",
        )
    }
    fun saveDashboardLayout(profile: DashboardProfile, layout: DashboardLayout) {
        core.settings.saveDashboardLayout(profile, layout)
        val normalized = DashboardLayouts.normalize(layout, profile)
        _state.value = if (profile == DashboardProfile.COMPACT) {
            _state.value.copy(dashboardCompact = normalized, notice = "手机首页布局已保存")
        } else {
            _state.value.copy(dashboardWide = normalized, notice = "宽屏首页布局已保存")
        }
        reload()
    }

    fun saveKrkrSettings(game: Game, settings: KrkrGameSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = krkrStore.save(game, settings)
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(krkrSettings = settings, krkrEditorGame = null, notice = "KRKR 设置已保存")
                } else {
                    _state.value.copy(krkrSettings = settings, notice = result.exceptionOrNull()?.message ?: "保存失败")
                }
            }
        }
    }

    fun searchCover(game: Game, keyword: String, source: CoverSourceMode, origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU) {
        val query = keyword.trim().ifEmpty { game.title.orEmpty() }
        _state.value = _state.value.copy(coverSearch = CoverSearchUi(game, source, query, loading = true, origin = origin))
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                coverSearchSemaphore.withPermit {
                    metadata.searchRankedStaged(source, query, limit = 8, maxQueries = 3)
                }
            }
            val searchResult = result.getOrNull()
            val ranked = searchResult?.candidates.orEmpty()
            val candidates = ranked.map { it.toUi(source) }
            if (result.isSuccess) coverTaskBackend.markBinding(game.id, "REVIEW", ranked.firstOrNull()?.score ?: 0.0)
            else coverTaskBackend.markBinding(game.id, "RETRY", 0.0)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    coverSearch = CoverSearchUi(
                        game = game,
                        source = source,
                        keyword = query,
                        candidates = candidates,
                        selectedKey = candidates.firstOrNull()?.key,
                        loading = false,
                        attemptedKeywords = searchResult?.attemptedQueries.orEmpty(),
                        error = result.exceptionOrNull()?.message,
                        origin = origin,
                    )
                )
            }
        }
    }

    fun dismissCoverSearch() {
        val origin = _state.value.coverSearch?.origin ?: return
        _state.value = _state.value.copy(coverSearch = null, taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(origin))
    }
    fun updateCoverKeyword(value: String) {
        _state.value.coverSearch?.let { _state.value = _state.value.copy(coverSearch = it.copy(keyword = value)) }
    }
    fun selectCoverCandidate(key: String) {
        _state.value.coverSearch?.let { _state.value = _state.value.copy(coverSearch = it.copy(selectedKey = key, error = null)) }
    }
    fun retryCoverSearch(source: CoverSourceMode = _state.value.coverSearch?.source ?: CoverSourceMode.VNDB) {
        val search = _state.value.coverSearch ?: return
        val game = search.game ?: return
        searchCover(game, search.keyword, source, CoverPickerNavigationPolicy.retryOrigin(search.origin))
    }
    fun confirmSelectedCover() {
        val search = _state.value.coverSearch ?: return
        val game = search.game ?: return
        val candidate = search.candidates.firstOrNull { it.key == search.selectedKey } ?: return
        _state.value = _state.value.copy(coverSearch = search.copy(saving = true, error = null))
        viewModelScope.launch(Dispatchers.IO) {
            val result = coverStore.confirm(game.id, candidate.source, candidate.metadata, "MANUAL", candidate.score)
            if (result.isSuccess && search.origin == CoverPickerOrigin.TASK_CENTER) coverQueue.resolveReview(game.id)
            if (result.isSuccess) _state.value = _state.value.copy(
                taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
            )
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(coverSearch = null, notice = "封面已保存")
                } else {
                    _state.value.copy(
                        coverSearch = _state.value.coverSearch?.copy(
                            saving = false,
                            error = result.exceptionOrNull()?.message ?: "封面下载失败"
                        )
                    )
                }
            }
            reloadInternal()
        }
    }

    fun useDefaultCover() {
        val search = _state.value.coverSearch ?: return
        val game = search.game ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = coverStore.useDefault(game.id)
            if (result.isSuccess && search.origin == CoverPickerOrigin.TASK_CENTER) coverQueue.cancel(game.id)
            if (result.isSuccess) _state.value = _state.value.copy(
                taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
            )
            withContext(Dispatchers.Main) {
                _state.value = if (result.isSuccess) {
                    _state.value.copy(coverSearch = null, notice = "已使用 ${game.engine?.displayName ?: "引擎"} 默认封面")
                } else {
                    _state.value.copy(coverSearch = search.copy(error = result.exceptionOrNull()?.message ?: "默认封面设置失败"))
                }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    private fun RankedCoverCandidate.toUi(source: CoverSourceMode) =
        CoverCandidateUi(metadata, source, score)

    private companion object {
        val ACTIVE_COVER_QUEUE_STATUSES = setOf(
            CoverTaskStatus.QUEUED,
            CoverTaskStatus.SEARCHING,
            CoverTaskStatus.DOWNLOADING,
        )
        const val BINDING_REFRESH_DEBOUNCE_MS = 200L
    }
}
