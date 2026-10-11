package com.kireibox.cover

import com.kireibox.ui.CoverCandidateAggregator
import com.kireibox.ui.CoverCandidateEvidence
import com.kireibox.ui.toRankedCandidate
import com.kireibox.ui.toUi
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.RankedCoverSearchResult
import com.yuki.yukihub.metadata.YukiMetadataService
import com.yuki.yukihub.metadata.match.CoverMatchConfidence
import com.yuki.yukihub.metadata.match.CoverMatchDecision
import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.match.CoverWorkEvidence
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.settings.YukiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class KireiCoverTaskBackend(
    private val metadata: YukiMetadataService,
    private val coverStore: KireiCoverStore,
    private val repository: GameRepository,
    private val settings: YukiSettingsStore,
) : CoverTaskBackend {
    override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val snapshot = settings.snapshot()
                if (snapshot.coverHybridAutoMatch) {
                    hybridSearch(request, snapshot.coverStrictL2)
                } else {
                    // Legacy single-source fallback: one catalog in fixed `active` priority order.
                    metadata.searchCancellable(
                        source = request.source,
                        keyword = request.title,
                        gameId = request.gameId,
                    )
                }
            }.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(CoverTimeoutRetryPolicy.normalizeFinalError(it)) },
            )
        }

    /**
     * Probes every usable source in dynamic priority order (core), merges the tagged candidates
     * into one card per work (app), and re-evaluates [CoverMatchConfidence] on the merged set so
     * the queue's `automatic` flag reflects the winning work's `autoBind` decision — a sole result
     * stays a review hint and never auto-binds. When [strict] is set, the final merged decision
     * disables the cross-source L3 auto-bind so only lexical evidence can bind.
     */
    private suspend fun hybridSearch(
        request: CoverTaskRequest,
        strict: Boolean,
    ): RankedCoverSearchResult {
        val hybrid = metadata.searchHybridCancellable(request.title, request.gameId)
        if (hybrid.candidates.isEmpty()) return hybrid
        val order = CoverSourcePriority.searchOrder()
        val tagged = hybrid.candidates.mapNotNull { candidate -> candidate.source?.let { it to candidate } }
        val merged =
            CoverCandidateEvidence.decorate(
                query = request.title,
                candidates = CoverCandidateAggregator.aggregate(tagged.map { (source, candidate) -> candidate.toUi(source) }, order),
                order = order,
            )
        val decision: CoverMatchDecision =
            if (merged.isEmpty()) {
                // The merged-card view is empty: fall back to the raw tagged evidence. In strict
                // mode the core probe's lenient decision must not leak a cross-source bind.
                if (strict && tagged.isNotEmpty()) {
                    CoverWorkEvidence.evaluate(tagged, request.title, strict = true)
                } else {
                    hybrid.evidence ?: CoverMatchDecision(0.0, false, emptyList())
                }
            } else {
                CoverMatchConfidence.evaluate(
                    merged.map { CoverCandidateEvidence.workMatch(request.title, it) },
                    strict,
                    request.title,
                )
            }
        return RankedCoverSearchResult(
            candidates = merged.map { it.toRankedCandidate() },
            attemptedQueries = hybrid.attemptedQueries,
            automatic = decision.autoBind,
            evidence = decision,
        )
    }

    override suspend fun save(
        request: CoverTaskRequest,
        candidate: RankedCoverCandidate,
        onProgress: (CoverDownloadProgress) -> Unit,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            // A merged hybrid card may show covers from several catalogs; download the chosen one with
            // the headers of the catalog it actually came from.
            val downloadSource: CoverSourceMode =
                candidate.coverSources[candidate.metadata.coverUrl] ?: candidate.source ?: request.source
            coverStore.confirm(
                gameId = request.gameId,
                source = downloadSource,
                candidate = candidate.metadata,
                bindingMode = "AUTO",
                matchScore = candidate.score,
                onProgress = onProgress,
            )
        }

    override suspend fun markBinding(
        gameId: Long,
        mode: String,
        score: Double
    ) = withContext(Dispatchers.IO) {
        repository.updateCoverBindingIfUnconfigured(gameId, mode, score)
        Unit
    }
}
