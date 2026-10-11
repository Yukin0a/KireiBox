package com.kireibox.ui
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.kireibox.external.winlator.WinlatorGameOverride
import com.kireibox.krkr.KrkrGameSettings
import com.kireibox.update.UpdateUiState
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.metadata.match.CoverMatchDecision
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardLayout
import com.yuki.yukihub.settings.DashboardLayouts
import com.yuki.yukihub.settings.DashboardProfile
import com.yuki.yukihub.settings.ImmersiveGallerySettings
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy

enum class HomeLoadState { INITIAL_LOADING, LOADED, RELOAD_FAILED }

/**
 * Deduplicates automatic-cover scheduling. The stable key is derived from the game
 * identity set (ids), the active metadata source, and the relevant credential/token
 * state, so theme/layout-only reloads never enqueue again while game/source/credential
 * changes do. In-memory only; no database or settings keys are involved.
 */
internal class AutoCoverSchedulingGate {
    private var lastKey: String? = null

    @Synchronized fun reset() {
        lastKey = null
    }

    @Synchronized
    fun shouldSchedule(
        games: List<Game>,
        source: CoverSourceMode,
        credentialToken: String
    ): Boolean {
        val key =
            buildString {
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
    APPEARANCE,
    LIBRARY_HUB,
    LIBRARY,
    COVERS,

    /** 来源账号: one card per cover-source account; a row of 封面与元数据 and a shortcut on the root (back returns to where it was opened). */
    COVER_ACCOUNTS,

    /** 同步游玩时长: NextMoe playtime sync — switch, status, and the games waiting for the user. */
    PLAYTIME_SYNC,

    /** 全局引擎设置: the list of the four engines' pages below. */
    ENGINES,
    KRKR,
    ONS,
    ARTEMIS,
    TYRANO,

    /** PC 游戏（外置 Winlator）的全局默认：容器、图形、语言环境。 */
    WINLATOR,
    INTERFACE,
    PERMISSIONS,
    DIAGNOSTICS,
    ABOUT,
}

data class CoverCandidateUi(
    val metadata: VnMetadata,
    val source: CoverSourceMode,
    val score: Double,
    /** Every catalog that contributed to this work (includes [source]); empty for a single-source card. */
    val sources: List<CoverSourceMode> = emptyList(),
    /**
     * Which catalog each cover URL came from. Hybrid cards merge covers across catalogs, so the
     * chosen variant must be downloaded with the headers of *its* catalog, not the card's primary
     * one (VNDB images, for example, need the `vndb_img` cookie that Bangumi images do not).
     */
    val coverSources: Map<String, CoverSourceMode> = emptyMap(),
    /** Best (lowest) result rank this work held in its contributing sources; 1 = first, 0 = unknown. */
    val bestRank: Int = 0,
    /** True when the merged work was linked by an id/anchor rather than only by title. */
    val linkedById: Boolean = false,
    /** Confidence reasons produced by [CoverMatchConfidence.evaluate] for this card. */
    val matchReasons: List<CoverMatchDecision.Reason> = emptyList(),
    /** True when this card's best lexical hit came from an alias rather than a primary title. */
    val aliasHit: Boolean = false,
) {
    /**
     * Stable per-work identity. Deliberately excludes the cover URL: choosing a variant mutates
     * [VnMetadata.coverUrl] in place and the selection key must not move with it.
     */
    val key: String get() = "${source.key}:${metadata.id.ifBlank { metadata.coverUrl }}"

    /** Sources to badge on the card; a plain single-source card badges just its own source. */
    val sourceBadges: List<CoverSourceMode> get() = sources.ifEmpty { listOf(source) }

    /** True when at least two catalogs contributed this same work. */
    val crossSource: Boolean get() = sourceBadges.size >= 2

    /** A single source returning exactly one result is a hint only, never an automatic bind. */
    val isUniqueResult: Boolean get() = sourceBadges.size == 1 && bestRank == 1

    /** Human-readable confidence badges shown on the picker card, in priority order. */
    val matchBadges: List<String> get() =
        buildList {
            val lexical = CoverMatchDecision.Reason.LEXICAL in matchReasons
            if (crossSource && bestRank == 1) add("跨源一致")
            if (aliasHit || linkedById || CoverMatchDecision.Reason.ID_LINK in matchReasons) add("别名命中")
            // A sole result is a hint only; suppress it when a stronger lexical hit already explains it.
            if (isUniqueResult && !lexical) add("唯一结果")
        }
}

data class CoverSearchUi(
    val game: Game? = null,
    val source: CoverSourceMode = CoverSourceMode.BANGUMI,
    val keyword: String = "",
    val candidates: List<CoverCandidateUi> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val selectedKey: String? = null,
    val attemptedKeywords: List<String> = emptyList(),
    val error: String? = null,
    val origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU,
    val originalBindingMode: String = "NONE",
    val hadCoverOnEntry: Boolean = false,
    /** When true the candidates come from every usable source, not just [source]. */
    val searchAllSources: Boolean = false,
    /** Per-source match counts for the current hybrid search (0 = answered with no match). */
    val sourceHits: Map<CoverSourceMode, Int> = emptyMap(),
    /** Sources that never answered (timeout / unreachable), shown as skipped. */
    val sourceFailed: Set<CoverSourceMode> = emptySet(),
)

data class UndoRemoval(val game: Game, val index: Int)

data class UndoScanRootRemoval(
    val root: String,
    val previousRoots: List<String>,
    val previousEnabled: List<Boolean>,
    val gameIds: List<Long>,
)

/** Which launch file a game card's picker chooses: a KRKR archive (or exe with one inside) or a Winlator game's exe. */
enum class LaunchFileKind(
    val engine: EngineType,
    val extension: String,
    val pageTitle: String,
    val emptyText: String,
) {
    XP3(EngineType.KIRIKIRI, ".xp3", "选择启动文件", "这个游戏目录中没有找到 XP3 或内含 XP3 的 exe，请检查目录授权或重新添加游戏。"),
    EXE(EngineType.WINDOWS, ".exe", "选择启动程序", "目录内未找到可启动的 .exe，请检查目录授权或重新添加游戏。"),
}

data class Xp3PickerUi(
    val game: Game,
    val kind: LaunchFileKind = LaunchFileKind.XP3,
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

data class ExternalShortcutPickerEntry(
    val shortcut: ExternalShortcutDescriptor,
    val imported: Boolean,
) {
    val stableKey: String get() = shortcut.stableKey
}

data class ExternalShortcutPickerUi(
    val entries: List<ExternalShortcutPickerEntry> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val loading: Boolean = true,
    val error: String? = null,
)

internal object ExternalShortcutPickerPolicy {
    fun visible(
        entries: List<ExternalShortcutPickerEntry>,
        query: String
    ): List<ExternalShortcutPickerEntry> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            entries
        } else {
            entries.filter {
                it.shortcut.title.contains(normalized, ignoreCase = true) ||
                    it.shortcut.appName.contains(normalized, ignoreCase = true) ||
                    it.shortcut.packageName.contains(normalized, ignoreCase = true) ||
                    it.shortcut.shortcutId.contains(normalized, ignoreCase = true)
            }
        }
    }

    fun toggle(
        entries: List<ExternalShortcutPickerEntry>,
        selectedKeys: Set<String>,
        stableKey: String,
    ): Set<String> {
        val entry = entries.firstOrNull { it.stableKey == stableKey } ?: return selectedKeys
        if (entry.imported) return selectedKeys
        return selectedKeys.toMutableSet().apply {
            if (!add(stableKey)) remove(stableKey)
        }
    }

    fun toggleAll(
        entries: List<ExternalShortcutPickerEntry>,
        selectedKeys: Set<String>
    ): Set<String> {
        val available =
            entries.filterNot(ExternalShortcutPickerEntry::imported)
                .mapTo(linkedSetOf()) { it.stableKey }
        return if (available.isNotEmpty() && selectedKeys.containsAll(available)) emptySet() else available
    }
}

data class KireiUiState(
    val coverAccounts: Map<CoverSourceMode, com.yuki.yukihub.auth.CoverAccountStatus> = emptyMap(),
    val playtimeSync: com.yuki.yukihub.sync.PlaytimeSyncStatus = com.yuki.yukihub.sync.PlaytimeSyncStatus(),
    /** Games whose NextMoe work the user must confirm or pick, and games NextMoe refused (loaded by the sync page). */
    val playtimeAttention: List<com.yuki.yukihub.sync.PlaytimeGame> = emptyList(),
    val accountBusy: Boolean = false,
    val includeAdult: Boolean = false,
    val tab: KireiTab = KireiTab.HOME,
    val settingsPage: KireiSettingsPage = KireiSettingsPage.ROOT,
    val games: List<Game> = emptyList(),
    val settings: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot? = null,
    val selectedGame: Game? = null,
    val removedGames: List<Game> = emptyList(),
    val krkrEditorGame: Game? = null,
    val krkrSettings: KrkrGameSettings = KrkrGameSettings(),
    /** The game whose own Winlator choices are being edited (its sheet's 「Winlator 游戏设置」). */
    val winlatorEditorGame: Game? = null,
    val winlatorOverride: WinlatorGameOverride = WinlatorGameOverride(),
    /** First-use Winlator guide: the one-time notice after the first added exe game, then the tutorial sheet. */
    val winlatorGuide: WinlatorGuideStage = WinlatorGuideStage.NONE,
    val coverSearch: CoverSearchUi? = null,
    val xp3Picker: Xp3PickerUi? = null,
    val addGame: AddGameUi? = null,
    val androidAppPicker: AndroidAppPickerUi? = null,
    val externalShortcutPicker: ExternalShortcutPickerUi? = null,
    val shortcutServiceStatus: ShortcutServiceStatus = ShortcutServiceStatus.NOT_RUNNING,
    val undoRemoval: UndoRemoval? = null,
    val undoScanRootRemoval: UndoScanRootRemoval? = null,
    val busy: Boolean = false,
    val homeLoadState: HomeLoadState = HomeLoadState.INITIAL_LOADING,
    val homeLoadError: String? = null,
    val notice: String? = null,
    /** Tone set where [notice] was raised (see withNotice); ignored once another notice replaces it. */
    val noticeTone: NoticeToneTag? = null,
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
    val pendingCoverReviewCount: Int = 0,
    /** Review + failed cover tasks; drives the 设置 red dot and the 封面与元数据 row badge. */
    val coverAttentionCount: Int = 0,
    val updateState: UpdateUiState = UpdateUiState.Idle,
)

internal data class ReloadPayload(
    val games: List<Game>,
    val snapshot: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot,
    val trend: List<PlayTrendPoint>,
    val searchIndex: Map<Long, String>,
    val removedGames: List<Game> = emptyList(),
)
