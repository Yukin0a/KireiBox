package com.kireibox.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kireibox.cover.KireiCoverStore
import com.kireibox.krkr.KrkrGameSettings
import com.kireibox.krkr.KrkrGameSettingsStore
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
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class KireiTab { HOME, GAMES, SETTINGS }
enum class KireiSettingsPage { ROOT, KRKR, COVERS, LIBRARY, PERMISSIONS }

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
    val error: String? = null
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
    val notice: String? = null,
    val migrationMessage: String? = null,
    val dashboardCompact: DashboardLayout = DashboardLayouts.default(DashboardProfile.COMPACT),
    val dashboardWide: DashboardLayout = DashboardLayouts.default(DashboardProfile.WIDE),
    val playTrend: List<PlayTrendPoint> = emptyList(),
    val gameSearchIndex: Map<Long, String> = emptyMap(),
    val diagnosticLoggingEnabled: Boolean = false,
    val coverTaskGameIds: Set<Long> = emptySet(),
    val pendingCoverCount: Int = 0
)

private data class ReloadPayload(
    val games: List<Game>,
    val snapshot: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot,
    val trend: List<PlayTrendPoint>,
    val searchIndex: Map<Long, String>,
)

class KireiViewModel(application: Application) : AndroidViewModel(application) {
    private val core = YukiCoreFacade(application)
    private val repository = core.games
    private val metadata = YukiMetadataService(application)
    private val metadataController = YukiMetadataController(application)
    private val coverStore = KireiCoverStore(application)
    private val krkrStore = KrkrGameSettingsStore(application)
    private val coverSearchSemaphore = Semaphore(3)
    private val coverDownloadSemaphore = Semaphore(2)
    private val scheduledCoverGames = ConcurrentHashMap.newKeySet<Long>()
    private val _state = MutableStateFlow(KireiUiState())
    val state: StateFlow<KireiUiState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(diagnosticLoggingEnabled = DevLogger.isEnabled())
        viewModelScope.launch(Dispatchers.IO) {
            val migration = SingleCoreMigration.run(application)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(migrationMessage = migration.message)
                reloadInternal()
            }
        }
    }

    fun selectTab(tab: KireiTab) { _state.value = _state.value.copy(tab = tab, settingsPage = KireiSettingsPage.ROOT) }
    fun openSettingsPage(page: KireiSettingsPage) { _state.value = _state.value.copy(tab = KireiTab.SETTINGS, settingsPage = page) }
    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
    fun showNotice(message: String) { _state.value = _state.value.copy(notice = message) }
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
    fun closeKrkrEditor() { _state.value = _state.value.copy(krkrEditorGame = null) }

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

    private suspend fun reloadInternal() {
        val payload = withContext(Dispatchers.IO) {
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
            ReloadPayload(
                games = games,
                snapshot = core.settings.snapshot(),
                trend = repository.dailyPlayTrend(30),
                searchIndex = searchIndex,
            )
        }
        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(
                games = payload.games,
                settings = payload.snapshot,
                dashboardCompact = payload.snapshot.dashboardCompact,
                dashboardWide = payload.snapshot.dashboardWide,
                playTrend = payload.trend,
                gameSearchIndex = payload.searchIndex,
                pendingCoverCount = payload.games.count {
                    it.vndbBindingMode == "REVIEW" || it.vndbBindingMode == "RETRY"
                },
                busy = false
            )
        }
        scheduleAutomaticCovers(
            payload.games,
            CoverSourceMode.fromStored(payload.snapshot.metadataSource),
        )
    }

    private fun scheduleAutomaticCovers(games: List<Game>, source: CoverSourceMode) {
        games.asSequence()
            .filter { it.id > 0 && it.coverUri.isNullOrBlank() }
            .filter { it.engine in setOf(EngineType.KIRIKIRI, EngineType.ONS, EngineType.TYRANO, EngineType.ARTEMIS) }
            .filter { it.vndbBindingMode.isBlank() || it.vndbBindingMode == "NONE" }
            .forEach { game ->
                if (!scheduledCoverGames.add(game.id)) return@forEach
                viewModelScope.launch(Dispatchers.IO) {
                    delay(250)
                    discoverCover(game.id, source)
                }
            }
    }

    private suspend fun discoverCover(gameId: Long, source: CoverSourceMode) {
        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(coverTaskGameIds = _state.value.coverTaskGameIds + gameId)
        }
        val game = repository.findById(gameId)
        if (game == null || !game.coverUri.isNullOrBlank() || game.vndbBindingMode !in setOf("", "NONE")) {
            finishCoverTask(gameId)
            return
        }
        val result = runCatching {
            coverSearchSemaphore.withPermit {
                metadata.searchRankedStaged(source, game.title.orEmpty(), limit = 8, maxQueries = 3)
            }
        }
        val search = result.getOrNull()
        if (search?.automatic == true && search.candidates.isNotEmpty()) {
            val chosen = search.candidates.first()
            val stored = coverDownloadSemaphore.withPermit {
                coverStore.confirm(gameId, source, chosen.metadata, "AUTO", chosen.score)
            }
            if (stored.isSuccess) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        notice = "已自动匹配 ${source.label} 封面 · ${chosen.score.toInt()}%"
                    )
                }
            } else {
                markCoverState(gameId, "RETRY", chosen.score)
            }
        } else if (result.isSuccess) {
            markCoverState(gameId, "REVIEW", search?.candidates?.firstOrNull()?.score ?: 0.0)
        } else {
            markCoverState(gameId, "RETRY", 0.0)
        }
        finishCoverTask(gameId)
        reloadInternal()
    }

    private fun markCoverState(gameId: Long, mode: String, score: Double) {
        repository.findById(gameId)?.let { current ->
            current.vndbBindingMode = mode
            current.vndbMatchScore = score.coerceIn(0.0, 100.0)
            current.updatedAt = System.currentTimeMillis()
            repository.update(current)
        }
    }

    private suspend fun finishCoverTask(gameId: Long) {
        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(coverTaskGameIds = _state.value.coverTaskGameIds - gameId)
        }
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

    fun searchCover(game: Game, keyword: String, source: CoverSourceMode) {
        val query = keyword.trim().ifEmpty { game.title.orEmpty() }
        _state.value = _state.value.copy(coverSearch = CoverSearchUi(game, source, query, loading = true))
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                coverSearchSemaphore.withPermit {
                    metadata.searchRankedStaged(source, query, limit = 8, maxQueries = 3)
                }
            }
            val searchResult = result.getOrNull()
            val ranked = searchResult?.candidates.orEmpty()
            val candidates = ranked.map { it.toUi(source) }
            val automatic = searchResult?.automatic == true
            if (automatic && candidates.isNotEmpty()) {
                val chosen = candidates.first()
                val stored = coverDownloadSemaphore.withPermit {
                    coverStore.confirm(game.id, source, chosen.metadata, "AUTO", chosen.score)
                }
                withContext(Dispatchers.Main) {
                    _state.value = if (stored.isSuccess) {
                        _state.value.copy(
                            coverSearch = null,
                            notice = "已自动匹配 ${source.key} 封面 · ${chosen.score.toInt()}%"
                        )
                    } else {
                        _state.value.copy(
                            coverSearch = CoverSearchUi(
                                game, source, query, candidates,
                                selectedKey = chosen.key,
                                attemptedKeywords = searchResult?.attemptedQueries.orEmpty(),
                                error = stored.exceptionOrNull()?.message ?: "自动保存失败"
                            )
                        )
                    }
                }
                reloadInternal()
                return@launch
            }
            if (result.isSuccess) markCoverState(game.id, "REVIEW", ranked.firstOrNull()?.score ?: 0.0)
            else markCoverState(game.id, "RETRY", 0.0)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    coverSearch = CoverSearchUi(
                        game = game,
                        source = source,
                        keyword = query,
                        candidates = candidates,
                        loading = false,
                        attemptedKeywords = searchResult?.attemptedQueries.orEmpty(),
                        error = result.exceptionOrNull()?.message
                    )
                )
            }
        }
    }

    fun dismissCoverSearch() { _state.value = _state.value.copy(coverSearch = null) }
    fun updateCoverKeyword(value: String) {
        _state.value.coverSearch?.let { _state.value = _state.value.copy(coverSearch = it.copy(keyword = value)) }
    }
    fun selectCoverCandidate(key: String) {
        _state.value.coverSearch?.let { _state.value = _state.value.copy(coverSearch = it.copy(selectedKey = key, error = null)) }
    }
    fun retryCoverSearch(source: CoverSourceMode = _state.value.coverSearch?.source ?: CoverSourceMode.VNDB) {
        val search = _state.value.coverSearch ?: return
        val game = search.game ?: return
        searchCover(game, search.keyword, source)
    }
    fun confirmSelectedCover() {
        val search = _state.value.coverSearch ?: return
        val game = search.game ?: return
        val candidate = search.candidates.firstOrNull { it.key == search.selectedKey } ?: return
        _state.value = _state.value.copy(coverSearch = search.copy(saving = true, error = null))
        viewModelScope.launch(Dispatchers.IO) {
            val result = coverDownloadSemaphore.withPermit {
                coverStore.confirm(game.id, candidate.source, candidate.metadata, "MANUAL", candidate.score)
            }
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
}
