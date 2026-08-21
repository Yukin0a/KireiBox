package com.kireibox.cover

import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.RankedCoverSearchResult
import com.yuki.yukihub.metadata.YukiMetadataService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class KireiCoverTaskBackend(
    private val metadata: YukiMetadataService,
    private val coverStore: KireiCoverStore,
    private val repository: GameRepository,
) : CoverTaskBackend {
    override suspend fun search(request: CoverTaskRequest): Result<RankedCoverSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            metadata.searchRankedStaged(
                source = request.source,
                keyword = request.title,
                limit = 8,
                maxQueries = 3,
            )
        }
    }

    override suspend fun save(
        request: CoverTaskRequest,
        candidate: RankedCoverCandidate,
        onProgress: (CoverDownloadProgress) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        coverStore.confirm(
            gameId = request.gameId,
            source = request.source,
            candidate = candidate.metadata,
            bindingMode = "AUTO",
            matchScore = candidate.score,
            onProgress = onProgress,
        )
    }

    override suspend fun markBinding(gameId: Long, mode: String, score: Double) = withContext(Dispatchers.IO) {
        repository.findById(gameId)?.let { game ->
            game.vndbBindingMode = mode
            game.vndbMatchScore = score.coerceIn(0.0, 100.0)
            game.updatedAt = System.currentTimeMillis()
            repository.update(game)
        }
        Unit
    }
}
