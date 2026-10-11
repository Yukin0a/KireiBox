package com.kireibox.ui

import androidx.lifecycle.viewModelScope
import com.kireibox.cover.CoverNoMatchStore
import com.kireibox.cover.CoverTaskSchedulingPolicy
import com.yuki.yukihub.data.GameSearchIndex
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.ImmersiveGallerySettingsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class KireiLibraryReloadController(host: KireiViewModel) : KireiViewModelController(host) {
    private val immersiveReloadGate = LatestWinsGate()
    private var immersiveReloadJob: Job? = null

    public override fun reload() {
        viewModelScope.launch(Dispatchers.IO) { reloadInternal() }
    }

    public override fun reloadImmersiveSettingsLatest() {
        val token = immersiveReloadGate.issue()
        immersiveReloadJob?.cancel()
        val reloadAction = testReloadAction
        if (isTestMode && reloadAction != null) {
            reloadAction()
            return
        }
        immersiveReloadJob =
            viewModelScope.launch(Dispatchers.IO) {
                reloadInternal(token)
            }
    }

    public override suspend fun reloadInternal(immersiveGeneration: Long?) {
        val observedImmersiveGeneration = immersiveGeneration ?: immersiveReloadGate.current()
        if (mutableState.value.homeLoadState == HomeLoadState.RELOAD_FAILED) {
            mutableState.value = mutableState.value.copy(homeLoadState = HomeLoadState.INITIAL_LOADING, homeLoadError = null)
        }
        val result =
            runCatching {
                withContext(Dispatchers.IO) {
                    testPayloadLoader?.invoke() ?: loadPayloadFromCore()
                }
            }
        result.onSuccess { payload ->
            if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@onSuccess
            val normalizedManualIds =
                ImmersiveGallerySettingsPolicy.normalizeIds(
                    payload.snapshot.immersiveGalleryIds,
                    payload.games.mapTo(linkedSetOf()) { it.id },
                )
            val snapshot =
                if (normalizedManualIds == payload.snapshot.immersiveGalleryIds) {
                    payload.snapshot
                } else {
                    payload.snapshot.copy(immersiveGalleryIds = normalizedManualIds)
                }
            if (!isTestMode && normalizedManualIds != payload.snapshot.immersiveGalleryIds) {
                core.settings.setImmersiveGalleryIds(normalizedManualIds)
            }
            withContext(Dispatchers.Main) {
                if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@withContext
                mutableState.value =
                    mutableState.value.copy(
                        games = payload.games,
                        removedGames = payload.removedGames,
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
                        pendingCoverReviewCount = pendingCoverReviewCount(payload.games, mutableState.value.coverQueue),
                        coverAttentionCount = coverAttentionCount(payload.games, mutableState.value.coverQueue),
                    )
            }
            if (!isTestMode && immersiveReloadGate.isCurrent(observedImmersiveGeneration)) {
                enqueueAutomaticCoversIfChanged(
                    payload.games,
                    CoverSourceMode.fromStored(snapshot.metadataSource),
                    snapshot.bangumiToken,
                    snapshot,
                )
            }
        }.onFailure { error ->
            if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@onFailure
            withContext(Dispatchers.Main) {
                if (!immersiveReloadGate.isCurrent(observedImmersiveGeneration)) return@withContext
                mutableState.value =
                    mutableState.value.copy(
                        busy = false,
                        homeLoadState = HomeLoadState.RELOAD_FAILED,
                        homeLoadError = error.message ?: "加载失败",
                    )
            }
        }
    }

    private suspend fun loadPayloadFromCore(): ReloadPayload {
        val games =
            core.listGames().filter {
                !it.hidden && it.engine in
                    setOf(
                        EngineType.KIRIKIRI,
                        EngineType.ONS,
                        EngineType.TYRANO,
                        EngineType.ARTEMIS,
                        EngineType.ANDROID_APP,
                        EngineType.WINDOWS,
                    )
            }
        val searchIndex =
            games.associate { game ->
                val aliases =
                    if (game.coverUri.isNullOrBlank()) {
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
        val removedGames =
            repository.loadAllIncludingHidden()
                .filter { it.hidden }
                .sortedByDescending { it.updatedAt }
        return ReloadPayload(
            games = games,
            snapshot = core.settings.snapshot(),
            trend = repository.dailyPlayTrend(30),
            searchIndex = searchIndex,
            removedGames = removedGames,
        )
    }

    private fun enqueueAutomaticCoversIfChanged(
        games: List<Game>,
        source: CoverSourceMode,
        credentialToken: String,
        settings: com.yuki.yukihub.settings.YukiSettingsStore.Snapshot,
    ) {
        if (!canRunAutomaticCoverSearch(source, settings.coverHybridAutoMatch)) return
        // Wi-Fi-only is checked before the scheduling gate so a metered reload does not consume the
        // gate and block a later unmetered pass.
        if (!com.yuki.yukihub.settings.CoverAutoMatchNetworkPolicy.shouldRun(
                host.isOnUnmeteredNetwork(),
                settings.coverAutoMatchWifiOnly
            ) ||
            !autoCoverGate.shouldSchedule(games, source, credentialToken)
        ) {
            return
        }
        enqueueAutomaticCovers(games, source, settings.coverHybridAutoMatch)
    }

    /**
     * Whether the automatic pass may run at all. In hybrid mode any anonymous source (Bangumi/VNDB)
     * is enough, so a default source that merely needs a login must not disable cover matching —
     * that is the "nothing is logged in" case. In the legacy single-source path the selected source
     * is the only one queried, so it must itself be searchable.
     */
    private fun canRunAutomaticCoverSearch(
        source: CoverSourceMode,
        hybridAutoMatch: Boolean,
    ): Boolean =
        if (hybridAutoMatch) {
            CoverSourceMode.active.any { accounts.canSearch(it) }
        } else {
            accounts.canSearch(source)
        }

    private fun enqueueAutomaticCovers(
        games: List<Game>,
        source: CoverSourceMode,
        hybridAutoMatch: Boolean,
    ) {
        if (!canRunAutomaticCoverSearch(source, hybridAutoMatch)) return
        CoverTaskSchedulingPolicy.requests(games, source)
            .filterNot { request ->
                coverNoMatchStore.contains(CoverNoMatchStore.key(request.gameId, request.title))
            }
            .forEach(coverQueue::enqueue)
    }
}

// ---------------------------------------------------------------------------------------------
// Cover task queue
// ---------------------------------------------------------------------------------------------
