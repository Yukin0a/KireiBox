package com.kireibox.ui

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal class KireiCoverPickerController(host: KireiViewModel) : KireiViewModelController(host) {
    /**
     * Opens the cover picker in hybrid mode. The picker always queries every usable source: a
     * single unavailable provider — e.g. Bangumi being unreachable on some networks — would
     * otherwise leave it empty, and merging one card per work needs all of them.
     */
    fun openCoverSearch(
        game: Game,
        origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU
    ) {
        searchCoverAllSources(game, game.title.orEmpty(), origin)
    }

    /**
     * Searches every usable source at once and merges the results, so a single unavailable or
     * unauthenticated provider (e.g. Bangumi being unreachable) cannot leave the picker empty.
     * Candidates stay tagged with their source, and are ordered by the source priority
     * [CoverSourceMode.active] and then by match score.
     */
    fun searchCoverAllSources(
        game: Game,
        keyword: String,
        origin: CoverPickerOrigin = CoverPickerOrigin.GAME_MENU,
    ) {
        coverSearchJob?.cancel()
        if (mutableState.value.coverSearch?.saving == true) return
        val query = keyword.trim().ifEmpty { game.title.orEmpty() }
        val previous = mutableState.value.coverSearch?.takeIf { it.game?.id == game.id }
        val originalMode = previous?.originalBindingMode ?: game.vndbBindingMode
        val hadCover = previous?.hadCoverOnEntry ?: !game.coverUri.isNullOrBlank()
        val primary = mutableState.value.coverSearch?.source ?: CoverSourceMode.BANGUMI
        // A user-opened search must always probe: the background auto-match may have parked a
        // source for its health cooldown, which would otherwise leave the picker showing every
        // source as idle and no results until the cooldown expired.
        com.yuki.yukihub.metadata.model.CoverSourceHealth.reset()
        val usable = CoverSourceMode.active.filter { accounts.canSearch(it) }
        // Off = legacy behaviour: fixed `active` order and no confidence re-ranking/badges.
        val hybridAutoMatch = mutableState.value.settings?.coverHybridAutoMatch != false
        val order =
            if (hybridAutoMatch) {
                CoverSourcePriority.searchOrder { accounts.canSearch(it) }
            } else {
                CoverSourceMode.active
            }
        mutableState.value =
            mutableState.value.copy(
                coverSearch =
                    CoverSearchUi(
                        game = game,
                        source = primary,
                        keyword = query,
                        loading = true,
                        origin = origin,
                        originalBindingMode = originalMode,
                        hadCoverOnEntry = hadCover,
                        searchAllSources = true,
                    ),
            )
        if (usable.isEmpty()) {
            mutableState.value =
                mutableState.value.copy(
                    coverSearch =
                        mutableState.value.coverSearch?.copy(
                            loading = false,
                            error = "请先连接至少一个封面来源账号",
                        ),
                )
            return
        }
        coverSearchJob =
            viewModelScope.launch(Dispatchers.IO) {
                var emitted = false
                try {
                    coverSearchSemaphore.withPermit {
                        metadata.searchAllStream(usable, query, game.id).collect { progress ->
                            emitted = true
                            val aggregated =
                                CoverCandidateAggregator.aggregate(
                                    progress.candidates
                                        .sortedWith(compareBy({ order.indexOf(it.first).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { -it.second.score }))
                                        .map { (source, ranked) -> ranked.toUi(source) },
                                    order,
                                ).distinctBy { it.key }
                            val candidates =
                                if (hybridAutoMatch) {
                                    CoverCandidateEvidence.decorate(query, aggregated, order)
                                } else {
                                    aggregated
                                }
                            val hits = progress.candidates.groupingBy { it.first }.eachCount()
                            val sourceHits =
                                CoverSourceMode.active
                                    .filter { it in progress.attempted }
                                    .associateWith { hits[it] ?: 0 }
                            withContext(Dispatchers.Main) {
                                // Render as soon as the first source answers; slower sources append.
                                val current = mutableState.value.coverSearch ?: return@withContext
                                mutableState.value =
                                    mutableState.value.copy(
                                        coverSearch =
                                            current.copy(
                                                candidates = candidates,
                                                selectedKey =
                                                    current.selectedKey
                                                        ?.takeIf { key -> candidates.any { it.key == key } }
                                                        ?: candidates.firstOrNull()?.key,
                                                loading = false,
                                                attemptedKeywords = listOf(query),
                                                error = null,
                                                sourceHits = sourceHits,
                                                sourceFailed = progress.failed,
                                            ),
                                    )
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    withContext(Dispatchers.Main) {
                        val current = mutableState.value.coverSearch ?: return@withContext
                        mutableState.value =
                            mutableState.value.copy(
                                coverSearch =
                                    current.copy(
                                        loading = false,
                                        error = com.kireibox.cover.CoverTimeoutRetryPolicy.normalizeFinalError(error).message,
                                    ),
                            )
                    }
                }
                if (!emitted) {
                    withContext(Dispatchers.Main) {
                        val current = mutableState.value.coverSearch ?: return@withContext
                        if (current.candidates.isEmpty()) {
                            mutableState.value =
                                mutableState.value.copy(
                                    coverSearch = current.copy(loading = false, error = "全部来源均未响应，请检查网络或稍后重试"),
                                )
                        }
                    }
                }
            }
    }

    fun updateCoverKeyword(value: String) {
        mutableState.value.coverSearch?.let { mutableState.value = mutableState.value.copy(coverSearch = it.copy(keyword = value)) }
    }

    fun selectCoverCandidate(key: String) {
        mutableState.value.coverSearch?.let { mutableState.value = mutableState.value.copy(coverSearch = it.copy(selectedKey = key, error = null)) }
    }

    fun selectCoverVariant(
        candidateKey: String,
        url: String
    ) {
        val search = mutableState.value.coverSearch ?: return
        if (search.saving) return
        val candidate = search.candidates.firstOrNull { it.key == candidateKey } ?: return
        val image = candidate.metadata.covers.firstOrNull { it.url == url } ?: return
        val copy = VnMetadata.fromJson(candidate.metadata.toJson().toString()) ?: return
        copy.coverUrl = image.url
        copy.fullCoverUrl = image.url
        copy.coverSexual = com.yuki.yukihub.metadata.client.AccountCatalogClient.sexualScore(image.sexual)
        val updated = candidate.copy(metadata = copy)
        mutableState.value =
            mutableState.value.copy(
                coverSearch =
                    search.copy(
                        candidates = search.candidates.map { if (it.key == candidateKey) updated else it }, selectedKey = updated.key
                    )
            )
    }

    /** Re-runs the hybrid search, probing every source again after a cooldown. */
    fun retryCoverSearch() {
        // An explicit retry should probe every source again instead of honouring the cooldown that
        // parked a previously-unreachable one.
        com.yuki.yukihub.metadata.model.CoverSourceHealth.reset()
        val search = mutableState.value.coverSearch ?: return
        val game = search.game ?: return
        searchCoverAllSources(game, search.keyword, search.origin)
    }

    fun confirmSelectedCover() {
        val search = mutableState.value.coverSearch ?: return
        val game = search.game ?: return
        val candidate = search.candidates.firstOrNull { it.key == search.selectedKey } ?: return
        // A hybrid card merges covers from several catalogs; download the chosen one with the
        // headers of the catalog it actually came from (VNDB needs its cookie, Bangumi its mirror).
        val downloadSource = candidate.coverSources[candidate.metadata.coverUrl] ?: candidate.source
        mutableState.value = mutableState.value.copy(coverSearch = search.copy(saving = true, error = null))
        coverSaveJob =
            viewModelScope.launch(Dispatchers.IO) {
                val result = coverStore.confirm(game.id, downloadSource, candidate.metadata, "MANUAL", candidate.score)
                val persistedGame = if (result.isSuccess) repository.findById(game.id) else null
                withContext(Dispatchers.Main) {
                    mutableState.value =
                        if (result.isSuccess) {
                            host.resolvePersistedCoverImmediately(
                                gameId = game.id,
                                mode = "MANUAL",
                                persistedCoverUri = result.getOrNull(),
                                keepCompletedRow = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
                                nsfw = persistedGame?.nsfw,
                            ).copy(
                                coverSearch = null,
                                taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
                                notice = "封面已保存",
                            )
                        } else {
                            mutableState.value.copy(
                                coverSearch =
                                    mutableState.value.coverSearch?.copy(
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
        val search = mutableState.value.coverSearch ?: return
        val game = search.game ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = coverStore.useDefault(game.id)
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        host.resolvePersistedCoverImmediately(
                            gameId = game.id,
                            mode = "DISABLED",
                            persistedCoverUri = null,
                            keepCompletedRow = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
                        ).copy(
                            coverSearch = null,
                            taskCenterOpen = CoverPickerNavigationPolicy.shouldRestoreTaskCenter(search.origin),
                            notice = "已使用 ${game.engine?.displayName ?: "引擎"} 默认封面",
                        )
                    } else {
                        mutableState.value.copy(coverSearch = search.copy(error = result.exceptionOrNull()?.message ?: "默认封面设置失败"))
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun importLocalCover(uri: Uri) {
        val gameId = mutableState.value.coverSearch?.game?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                mutableState.value =
                    mutableState.value.copy(
                        coverSearch = mutableState.value.coverSearch?.copy(saving = true, error = null),
                    )
            }
            val result = coverStore.importLocal(gameId, uri)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    mutableState.value =
                        mutableState.value.copy(
                            coverSearch = null,
                            notice = "已使用本地封面",
                        )
                } else {
                    mutableState.value =
                        mutableState.value.copy(
                            coverSearch =
                                mutableState.value.coverSearch?.copy(
                                    saving = false,
                                    error = result.exceptionOrNull()?.message ?: "无法使用所选图片",
                                ),
                        )
                }
            }
            if (result.isSuccess) reloadInternal()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------
