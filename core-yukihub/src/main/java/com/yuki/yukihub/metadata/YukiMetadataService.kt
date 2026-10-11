package com.yuki.yukihub.metadata
import android.content.Context
import com.yuki.yukihub.metadata.client.AccountCatalogClient
import com.yuki.yukihub.metadata.client.BangumiClient
import com.yuki.yukihub.metadata.client.VndbClient
import com.yuki.yukihub.metadata.match.CoverMatchConfidence
import com.yuki.yukihub.metadata.match.CoverMatchDecision
import com.yuki.yukihub.metadata.match.CoverSourcePriority
import com.yuki.yukihub.metadata.match.CoverWorkEvidence
import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.metadata.match.TitleQueryPlanner
import com.yuki.yukihub.metadata.model.CoverSourceHealth
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeoutOrNull

data class RankedCoverCandidate(
    val metadata: VnMetadata,
    val score: Double,
    /**
     * 1-based position this work held in its source's own result list *before* local re-ranking.
     * The source's ordering is evidence: providers rank the correct entry first even when the
     * local lexical scorer cannot see the alias. Defaults to 0 for candidates with no source rank.
     */
    val sourceRank: Int = 0,
    /** Catalog this candidate came from in a hybrid search; null for single-source/legacy results. */
    val source: CoverSourceMode? = null,
    /** Every catalog that contributed a merged hybrid work; empty for an unmerged candidate. */
    val sources: List<CoverSourceMode> = emptyList(),
    /** Which catalog each merged cover URL came from, so a download uses that catalog's headers. */
    val coverSources: Map<String, CoverSourceMode> = emptyMap(),
    /** Confidence reasons from [CoverMatchConfidence.evaluate]; empty until the result is scored. */
    val matchReasons: List<CoverMatchDecision.Reason> = emptyList(),
)

data class RankedCoverSearchResult(
    val candidates: List<RankedCoverCandidate>,
    val attemptedQueries: List<String>,
    val automatic: Boolean,
    /** Confidence decision from the hybrid search; null for the legacy single-source path. */
    val evidence: CoverMatchDecision? = null,
)

/**
 * Incremental hybrid-search progress: which sources were probed, which never answered, and the
 * cumulative candidates collected so far. Lets the picker show "VNDB 3 · Bangumi 不可达" instead
 * of a silent empty screen.
 */
data class HybridCoverProgress(
    val attempted: Set<CoverSourceMode>,
    val failed: Set<CoverSourceMode>,
    val candidates: List<Pair<CoverSourceMode, RankedCoverCandidate>>,
)

/** Source-neutral search/save API used by the Compose cover page. */
class YukiMetadataService(context: Context) {
    private val accounts = com.yuki.yukihub.auth.CoverAccounts.get(context)
    private val catalog = AccountCatalogClient(accounts)
    private val repository = com.yuki.yukihub.data.MetadataRepository(context)
    private val controller = YukiMetadataController(context.applicationContext)
    private val settings = com.yuki.yukihub.settings.YukiSettingsStore(context.applicationContext)
    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, RankedCoverSearchResult>>()

    /** Last base that answered, so the working host is tried first on the next search. */
    @Volatile private var bangumiBase: String? = null

    /**
     * Bangumi is blocked outright on some networks and its community mirrors rotate domains as
     * each is blocked, so the search walks the configured base (or official → mirror) until one
     * answers, remembering the winner for next time.
     */
    private fun searchBangumi(
        keyword: String,
        limit: Int,
    ): List<VnMetadata> {
        val configured = settings.snapshot().bangumiApiBase.trim()
        val bases =
            buildList {
                bangumiBase?.let(::add)
                if (configured.isNotBlank()) {
                    add(configured)
                } else {
                    add(BangumiClient.OFFICIAL_BASE)
                    add(BangumiClient.MIRROR_BASE)
                }
            }.distinct()
        var lastError: Throwable? = null
        for (base in bases) {
            try {
                val hits =
                    BangumiClient.searchCandidates(
                        keyword,
                        controller.bangumiToken(),
                        limit,
                        base,
                        accounts.includesAdult(),
                    )
                bangumiBase = base
                return hits
            } catch (cancelled: java.util.concurrent.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: java.io.IOException("Bangumi 暂时不可用，请在设置中更换镜像地址")
    }

    fun search(
        source: CoverSourceMode,
        keyword: String,
        limit: Int = 8,
    ): List<VnMetadata> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        val bounded = limit.coerceIn(1, 10)
        return when (source) {
            CoverSourceMode.VNDB -> VndbClient.searchCandidates(query, bounded, accounts.includesAdult()).orEmpty()
            CoverSourceMode.BANGUMI ->
                try {
                    searchBangumi(query, bounded)
                } catch (error: com.yuki.yukihub.net.HttpClient.HttpFailure) {
                    if (error.status == 401) {
                        accounts.markExpired(source)
                        throw java.io.IOException("Bangumi Token 已失效，请重新连接或断开后匿名搜索")
                    }
                    throw error
                }
            CoverSourceMode.BANGUMI_MIRROR, CoverSourceMode.YMGAL -> error("该历史来源已停止联网")
            CoverSourceMode.NEXTMOE -> catalog.searchNextmoe(query, bounded)
            CoverSourceMode.HIKARINAGI -> catalog.searchHikarinagi(query, bounded)
        }
    }

    fun searchRanked(
        source: CoverSourceMode,
        keyword: String,
        limit: Int = 8,
    ): List<RankedCoverCandidate> {
        val query = keyword.trim()
        if (query.isEmpty()) return emptyList()
        return rankCandidates(query, search(source, query, limit))
    }

    /** Sequential bounded search. Stops as soon as the established automatic threshold is met. */
    suspend fun searchCancellable(
        source: CoverSourceMode,
        keyword: String,
        gameId: Long = 0,
        maxQueries: Int = DEFAULT_MAX_QUERIES,
    ): RankedCoverSearchResult {
        // Repeat searches (reopening the picker, flipping sources back and forth) are common and a
        // remote search costs 1–3s, so a short-lived per-source cache makes them instant.
        val key = cacheKey(source, keyword, gameId, maxQueries)
        searchCache[key]?.let { (storedAt, cached) ->
            if (System.currentTimeMillis() - storedAt < SEARCH_CACHE_TTL_MS) return cached
        }
        val result =
            com.yuki.yukihub.net.MetadataRequests.run(source) {
                if (source == CoverSourceMode.NEXTMOE && gameId > 0) {
                    val refs =
                        listOf(CoverSourceMode.VNDB, CoverSourceMode.BANGUMI).mapNotNull { provider ->
                            repository.get(gameId, provider)?.id?.takeIf { it.isNotBlank() }?.let { "${provider.key}:$it" }
                        }.joinToString(",")
                    if (refs.isNotBlank()) {
                        val matches = catalog.searchNextmoe(keyword, 8, refs)
                        val ranked = rankCandidates(keyword, matches)
                        if (ranked.isNotEmpty()) {
                            return@run RankedCoverSearchResult(
                                ranked,
                                listOf(keyword),
                                matches.size == 1 && ranked.size == 1,
                            )
                        }
                    }
                }
                searchRankedStaged(source, keyword, maxQueries = maxQueries)
            }
        if (searchCache.size >= SEARCH_CACHE_MAX_ENTRIES) searchCache.clear()
        searchCache[key] = System.currentTimeMillis() to result
        return result
    }

    /**
     * Hybrid background probe: walks [CoverSourcePriority.searchOrder] in dynamic priority order,
     * skipping sources that are unhealthy or need a missing login, and bounds each source with its
     * own timeout (VNDB is slow, so it gets the shorter one). It stops early once a work already
     * carries [CoverMatchConfidence]-strength evidence (exact lexical or cross-source rank1
     * agreement) so a slow tail source cannot delay a confident match.
     *
     * Candidates are returned still tagged with the [CoverSourceMode] they came from
     * ([RankedCoverCandidate.source]) so the caller can merge them and download each cover with
     * that catalog's headers. The result carries the confidence [RankedCoverSearchResult.evidence].
     */
    suspend fun searchHybridCancellable(
        keyword: String,
        gameId: Long = 0,
        sources: List<CoverSourceMode> = CoverSourceMode.active,
        connected: (CoverSourceMode) -> Boolean = { accounts.canSearch(it) },
    ): RankedCoverSearchResult {
        val query = keyword.trim()
        if (query.isEmpty()) return RankedCoverSearchResult(emptyList(), emptyList(), false)
        val searched = sources.toHashSet()
        val order = CoverSourcePriority.searchOrder(connected).filter { it in searched }
        val attempted = LinkedHashSet<CoverSourceMode>()
        val collected = ArrayList<Pair<CoverSourceMode, RankedCoverCandidate>>()
        var lastError: Throwable? = null
        for (source in order) {
            if (!CoverSourceHealth.isAvailable(source) || !connected(source)) continue
            attempted += source
            var failed = false
            val hits =
                try {
                    withTimeoutOrNull(hybridTimeoutFor(source)) {
                        searchCancellable(source, query, gameId, maxQueries = hybridQueryBudgetFor(source))
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failed = true
                    lastError = error
                    null
                }
            if (hits == null) {
                if (!failed) lastError = lastError ?: java.io.IOException("${source.label} 搜索超时")
                CoverSourceHealth.recordFailure(source)
                continue
            }
            CoverSourceHealth.recordSuccess(source)
            collected += hits.candidates.map { source to it.copy(source = source) }
            if (CoverWorkEvidence.hasAutoBind(collected, query)) break
        }
        if (collected.isEmpty()) {
            // Nothing answered. Distinguish "no usable source could even be tried" (all cooling
            // down / not logged in) and "every tried source failed" from a genuine empty result,
            // so the task centre does not claim「未找到」when the real cause is an unreachable
            // network — that misleading message is what made retries look permanently broken.
            val failure =
                when {
                    attempted.isEmpty() ->
                        java.io.IOException("封面来源暂时不可用（网络受限或正在冷却），请稍后重试")
                    lastError != null -> lastError
                    else -> null
                }
            if (failure != null) throw failure
            return RankedCoverSearchResult(emptyList(), listOf(query), false)
        }
        val decision = CoverWorkEvidence.evaluate(collected, query)
        return RankedCoverSearchResult(
            candidates = collected.map { it.second },
            attemptedQueries = listOf(query),
            automatic = decision.autoBind,
            evidence = decision,
        )
    }

    private fun cacheKey(
        source: CoverSourceMode,
        keyword: String,
        gameId: Long,
        maxQueries: Int,
    ): String = "${source.key}|${keyword.trim().lowercase()}|$gameId|$maxQueries|${accounts.includesAdult()}"

    /**
     * Searches several sources concurrently and returns their candidates tagged with the source
     * they came from.
     *
     * Guards against the two ways one bad source hurts a hybrid search:
     * - **Latency**: each source gets its own soft timeout, so an unreachable host (a blocked
     *   network path costs a full connect timeout) cannot hold up the whole result.
     * - **Cost**: the fan-out is bounded to [DEFAULT_HYBRID_MAX_QUERIES] query variant per source
     *   instead of the full ladder every source would otherwise walk.
     *
     * Failures are remembered via [CoverSourceHealth] so the next search skips that source
     * instead of paying its timeout again.
     */
    suspend fun searchAllCancellable(
        sources: List<CoverSourceMode>,
        keyword: String,
        gameId: Long = 0,
        perSourceTimeoutMs: Long = DEFAULT_PER_SOURCE_TIMEOUT_MS,
        maxQueriesPerSource: Int = DEFAULT_HYBRID_MAX_QUERIES,
    ): List<Pair<CoverSourceMode, RankedCoverCandidate>> =
        coroutineScope {
            val lexical =
                sources
                    .distinct()
                    .filter { CoverSourceHealth.isAvailable(it) }
                    .map { source ->
                        async {
                            val hits =
                                runCatching {
                                    withTimeoutOrNull(perSourceTimeoutMs) {
                                        searchCancellable(source, keyword, gameId, maxQueries = maxQueriesPerSource)
                                    }
                                }.getOrNull()
                            if (hits == null) {
                                CoverSourceHealth.recordFailure(source)
                                emptyList()
                            } else {
                                CoverSourceHealth.recordSuccess(source)
                                hits.candidates.map { source to it }
                            }
                        }
                    }
                    .awaitAll()
                    .flatten()
            lexical + resolveNextmoeBridge(sources, keyword, lexical, perSourceTimeoutMs)
        }

    /**
     * Streaming variant of [searchAllCancellable]: emits the **cumulative** candidate list every
     * time a source answers, so the picker can render VNDB's results while a slower or unreachable
     * source is still being probed, instead of waiting for the slowest one.
     *
     * The final emission is the complete set; if every source fails there are no emissions and the
     * caller should surface its own error.
     */
    fun searchAllStream(
        sources: List<CoverSourceMode>,
        keyword: String,
        gameId: Long = 0,
        perSourceTimeoutMs: Long = DEFAULT_PER_SOURCE_TIMEOUT_MS,
        maxQueriesPerSource: Int = DEFAULT_HYBRID_MAX_QUERIES,
    ): kotlinx.coroutines.flow.Flow<HybridCoverProgress> =
        kotlinx.coroutines.flow.channelFlow {
            val attempted = LinkedHashSet<CoverSourceMode>()
            val failed = LinkedHashSet<CoverSourceMode>()
            val collected = ArrayList<Pair<CoverSourceMode, RankedCoverCandidate>>()
            sources
                .distinct()
                .filter { CoverSourceHealth.isAvailable(it) }
                .map { source ->
                    kotlinx.coroutines.flow.flow {
                        val hits =
                            runCatching {
                                withTimeoutOrNull(perSourceTimeoutMs) {
                                    searchCancellable(source, keyword, gameId, maxQueries = maxQueriesPerSource)
                                }
                            }.getOrNull()
                        // null means the source never answered (timeout / network), which is
                        // reported separately from "answered with no match".
                        if (hits == null) {
                            CoverSourceHealth.recordFailure(source)
                        } else {
                            CoverSourceHealth.recordSuccess(source)
                        }
                        emit(source to hits?.candidates)
                    }
                }
                .merge()
                .collect { (source, hits) ->
                    val snapshot: HybridCoverProgress
                    synchronized(collected) {
                        attempted += source
                        if (hits == null) failed += source else collected += hits.map { source to it }
                        snapshot = HybridCoverProgress(attempted.toSet(), failed.toSet(), collected.toList())
                    }
                    send(snapshot)
                }

            // Cross-source id bridge: once the lexical phase is done, resolve any vndb/bangumi id
            // it found into its NextMoe work. NextMoe's own keyword search is weak for CJK, but its
            // refs= lane is exact, so this recovers the multi-cover set the title search missed.
            if (CoverSourceMode.NEXTMOE in sources && CoverSourceMode.NEXTMOE !in failed) {
                val lexical = synchronized(collected) { collected.toList() }
                val bridged = resolveNextmoeBridge(sources, keyword, lexical, perSourceTimeoutMs)
                if (bridged.isNotEmpty()) {
                    val snapshot =
                        synchronized(collected) {
                            collected += bridged
                            attempted += CoverSourceMode.NEXTMOE
                            failed -= CoverSourceMode.NEXTMOE
                            HybridCoverProgress(attempted.toSet(), failed.toSet(), collected.toList())
                        }
                    CoverSourceHealth.recordSuccess(CoverSourceMode.NEXTMOE)
                    send(snapshot)
                }
            }
        }

    /**
     * Resolves ids the lexical sources just found into their NextMoe works. NextMoe's keyword
     * search is weak for Chinese/Japanese titles, but its `refs=` batch lane is exact: one
     * `source:external_id` is enough to pull the work and its multi-cover set even when the title
     * search returned nothing. The result is scored as a confirmed match because the id matched.
     */
    private suspend fun resolveNextmoeBridge(
        sources: List<CoverSourceMode>,
        keyword: String,
        lexical: List<Pair<CoverSourceMode, RankedCoverCandidate>>,
        timeoutMs: Long,
    ): List<Pair<CoverSourceMode, RankedCoverCandidate>> {
        if (CoverSourceMode.NEXTMOE !in sources) return emptyList()
        val refs = bridgeRefs(lexical)
        if (refs.isEmpty()) return emptyList()
        val matches =
            runCatching {
                withTimeoutOrNull(timeoutMs) {
                    com.yuki.yukihub.net.MetadataRequests.run(CoverSourceMode.NEXTMOE) {
                        catalog.searchNextmoe(keyword, NEXTMOE_BRIDGE_LIMIT, refs.joinToString(","))
                    }
                }
            }.getOrNull().orEmpty()
        if (matches.isEmpty()) return emptyList()
        // Drop works NextMoe already returned lexically so the picker does not show two cards.
        val existing =
            lexical.asSequence()
                .filter { it.first == CoverSourceMode.NEXTMOE }
                .map { it.second.metadata.id }
                .toSet()
        return rankCandidates(keyword, matches)
            .filter { it.metadata.id !in existing }
            .map { CoverSourceMode.NEXTMOE to it.copy(score = maxOf(it.score, BRIDGED_SCORE)) }
    }

    fun searchRankedStaged(
        source: CoverSourceMode,
        keyword: String,
        limit: Int = 8,
        maxQueries: Int = 3,
    ): RankedCoverSearchResult {
        val queries = TitleQueryPlanner.networkQueries(keyword, maxQueries)
        if (queries.isEmpty()) return RankedCoverSearchResult(emptyList(), emptyList(), false)
        val attempted = ArrayList<String>()
        // Preserve each work's *source* rank: `search()` returns the provider's own ordering, and
        // that ordering is evidence the local scorer cannot reproduce for alias-only titles.
        val sourceRanks = HashMap<String, Int>()
        val candidates = LinkedHashMap<String, VnMetadata>()
        var ranked = emptyList<RankedCoverCandidate>()

        fun runQuery(query: String): Boolean {
            attempted += query
            search(source, query, limit).forEachIndexed { index, metadata ->
                val key =
                    metadata.id.ifBlank {
                        "${metadata.romanTitle}|${metadata.originalTitle}|${metadata.chineseTitle}|${metadata.coverUrl}"
                    }
                sourceRanks.putIfAbsent(key, index + 1)
                candidates.putIfAbsent(key, metadata)
            }
            ranked =
                rankCandidates(attempted, candidates.values.toList())
                    .map { it.copy(sourceRank = sourceRanks[it.metadata.id] ?: 0) }
            return TitleMatchScorer.isAutomaticMatch(ranked.map { TitleMatchScorer.Match(it.metadata, it.score) })
        }

        for (query in queries) {
            if (runQuery(query)) return RankedCoverSearchResult(ranked, attempted, true)
        }
        // Recall fallback: the bounded variants found nothing, so spend one extra pass on the full
        // ladder (shorter CJK fragments) before giving up. Titles that already answered never pay
        // for it, so it only trades latency in the failing case.
        if (candidates.isEmpty()) {
            for (query in TitleQueryPlanner.networkQueries(keyword, DEFAULT_MAX_QUERIES)) {
                if (query in attempted) continue
                if (runQuery(query)) return RankedCoverSearchResult(ranked, attempted, true)
            }
        }
        return RankedCoverSearchResult(ranked, attempted, false)
    }

    fun save(
        gameId: Long,
        source: CoverSourceMode,
        metadata: VnMetadata,
    ) {
        controller.saveMetadata(gameId, source, metadata)
    }

    fun visibleSource(gameId: Long): CoverSourceMode? = controller.visibleSource(gameId)

    fun setVisibleSource(
        gameId: Long,
        source: CoverSourceMode?,
    ) = controller.setVisibleSource(gameId, source)

    companion object {
        /** Variants the single-source path may walk (full ladder). */
        const val DEFAULT_MAX_QUERIES = 3

        /** Hybrid bounds each source to one variant so total latency stays predictable. */
        const val DEFAULT_HYBRID_MAX_QUERIES = 1

        /**
         * Per-source timeout for the background hybrid probe. Long enough for the recall fallback
         * (a second/third query variant) to finish on a slow source; titles that answer on the
         * first variant return long before it, so this only lengthens the genuinely failing case.
         */
        const val HYBRID_VNDB_TIMEOUT_MS = 7_000L
        const val HYBRID_DEFAULT_TIMEOUT_MS = 7_000L

        /** Bound for one source in the background probe (VNDB and Bangumi share the fallback budget). */
        @JvmStatic
        fun hybridTimeoutFor(source: CoverSourceMode): Long = if (source == CoverSourceMode.VNDB) HYBRID_VNDB_TIMEOUT_MS else HYBRID_DEFAULT_TIMEOUT_MS

        /**
         * Query variants the background hybrid probe may walk per source.
         *
         * Real folder names are decorated and composite ("【KR】绽放青春全力向前冲 ／ かけぬけ★青春
         * スパーキング"), and the single concatenated variant never matches; the useful one is the
         * CJK fragment the planner emits second. Bangumi's mirror is a fast legacy GET and fits a
         * second variant inside its window, so it gets two. VNDB stays at one: its 1.1s throttle
         * makes a second request likely to blow the 4s window and discard the first result too.
         */
        @JvmStatic
        fun hybridQueryBudgetFor(source: CoverSourceMode): Int = if (source == CoverSourceMode.VNDB) DEFAULT_HYBRID_MAX_QUERIES else 2

        /**
         * A source that has not answered within this window is dropped from the result set.
         * Deliberately longer than the 3s connect timeout so a merely *slow* source is not cut
         * off and then parked by [CoverSourceHealth] — only genuinely unreachable hosts fail
         * faster than this.
         */
        const val DEFAULT_PER_SOURCE_TIMEOUT_MS = 8_000L

        /** How long a successful per-source search result may be reused. */
        private const val SEARCH_CACHE_TTL_MS = 10 * 60_000L
        private const val SEARCH_CACHE_MAX_ENTRIES = 64

        /** NextMoe `refs=` batch cap (the API rejects more than 100 anchors). */
        private const val MAX_BRIDGE_REFS = 100

        /** Bridges ask for the same page size as a normal NextMoe search. */
        private const val NEXTMOE_BRIDGE_LIMIT = 8

        /** A refs-confirmed hit is the same work another source found, so it outranks lexical noise. */
        private const val BRIDGED_SCORE = 92.0

        /**
         * Extracts the `source:external_id` anchors a bridge call can use from the candidates the
         * lexical sources produced. Only VNDB and Bangumi ids are meaningful to NextMoe's `refs=`;
         * NextMoe ids are its own catalog ids and would only resolve to themselves.
         */
        @JvmStatic
        fun bridgeRefs(candidates: List<Pair<CoverSourceMode, RankedCoverCandidate>>): List<String> =
            candidates.asSequence()
                .filter { (source, _) ->
                    source == CoverSourceMode.VNDB || source == CoverSourceMode.BANGUMI
                }
                .flatMap { (source, ranked) ->
                    val direct =
                        ranked.metadata.id.trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let { sequenceOf("${source.key}:$it") }
                            ?: emptySequence()
                    val declared =
                        ranked.metadata.externalRefs.split(',')
                            .asSequence()
                            .map(String::trim)
                            .filter { it.startsWith("vndb:") || it.startsWith("bangumi:") }
                    direct + declared
                }
                .filter { it.substringAfter(':', "").isNotEmpty() }
                .distinct()
                .take(MAX_BRIDGE_REFS)
                .toList()

        @JvmStatic
        fun rankCandidates(
            keyword: String,
            candidates: List<VnMetadata>,
        ): List<RankedCoverCandidate> {
            val query = keyword.trim()
            if (query.isEmpty()) return emptyList()
            val variants = TitleQueryPlanner.variants(query).ifEmpty { listOf(query) }
            return TitleMatchScorer.ranked(variants, candidates)
                .filter { it.metadata.coverUrl.isNotBlank() }
                .map { RankedCoverCandidate(it.metadata, it.score.coerceIn(0.0, 100.0)) }
        }

        @JvmStatic
        fun rankCandidates(
            keywords: List<String>,
            candidates: List<VnMetadata>,
        ): List<RankedCoverCandidate> {
            val queries = keywords.flatMap(TitleQueryPlanner::variants).distinct()
            if (queries.isEmpty()) return emptyList()
            return TitleMatchScorer.ranked(queries, candidates)
                .filter { it.metadata.coverUrl.isNotBlank() }
                .map { RankedCoverCandidate(it.metadata, it.score.coerceIn(0.0, 100.0)) }
        }
    }
}
