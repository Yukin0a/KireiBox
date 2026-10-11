package com.yuki.yukihub.metadata.match
import com.yuki.yukihub.metadata.RankedCoverCandidate
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata

/**
 * Groups per-source hybrid candidates into merged works and produces the [WorkMatch] evidence the
 * confidence scorer needs.
 *
 * This mirrors the app-side card merge (id/ref anchors first, then normalized title + compatible
 * developer) but lives in core so the background hybrid search can decide early-stop and automatic
 * binding without the UI layer. A wrong merge hides the correct work, so only strong identity
 * anchors are used.
 */
object CoverWorkEvidence {
    /** Merged works for [candidates], in stable first-seen order. */
    @JvmStatic
    fun merge(
        candidates: List<Pair<CoverSourceMode, RankedCoverCandidate>>,
        query: String,
    ): List<WorkMatch> {
        if (candidates.isEmpty()) return emptyList()
        val parent = IntArray(candidates.size) { it }

        fun find(index: Int): Int {
            var root = index
            while (parent[root] != root) {
                parent[root] = parent[parent[root]]
                root = parent[root]
            }
            return root
        }

        fun union(
            a: Int,
            b: Int,
        ) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val anchorLinked = BooleanArray(candidates.size)
        val byAnchor = HashMap<String, Int>()
        candidates.forEachIndexed { index, (source, candidate) ->
            anchors(source, candidate.metadata).forEach { anchor ->
                val existing = byAnchor[anchor]
                if (existing == null) {
                    byAnchor[anchor] = index
                } else {
                    union(existing, index)
                    anchorLinked[index] = true
                    anchorLinked[existing] = true
                }
            }
        }

        val byTitle = HashMap<String, MutableList<Int>>()
        candidates.forEachIndexed { index, (_, candidate) ->
            titleKeys(candidate.metadata).forEach { key -> byTitle.getOrPut(key) { ArrayList() }.add(index) }
        }
        byTitle.values.forEach { members ->
            for (i in members.indices) {
                for (j in i + 1 until members.size) {
                    if (developersCompatible(candidates[members[i]].second.metadata, candidates[members[j]].second.metadata)) {
                        union(members[i], members[j])
                    }
                }
            }
        }

        return candidates.indices.groupBy(::find).values.map { members ->
            val group = members.map(candidates::get)
            val representative =
                group.minByOrNull { CoverSourcePriority.rank(it.first) }?.second?.metadata
                    ?: group.first().second.metadata
            WorkMatch(
                metadata = representative,
                sources = group.map { it.first }.distinct(),
                bestRank = group.mapNotNull { it.second.sourceRank.takeIf { rank -> rank > 0 } }.minOrNull() ?: 0,
                lexicalScore = group.maxOf { lexicalScore(query, it.second.metadata) },
                linkedById = members.any { anchorLinked[it] },
            )
        }
    }

    @JvmStatic
    fun evaluate(
        candidates: List<Pair<CoverSourceMode, RankedCoverCandidate>>,
        query: String,
        strict: Boolean = false,
    ): CoverMatchDecision = CoverMatchConfidence.evaluate(merge(candidates, query), strict, query)

    /** True when the collected candidates already justify an automatic bind. */
    @JvmStatic
    fun hasAutoBind(
        candidates: List<Pair<CoverSourceMode, RankedCoverCandidate>>,
        query: String,
        strict: Boolean = false,
    ): Boolean = evaluate(candidates, query, strict).autoBind

    /** Best score of [query] across a work's primary titles and aliases. */
    @JvmStatic
    fun lexicalScore(
        query: String,
        metadata: VnMetadata,
    ): Double =
        (listOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle) + metadata.titleAliases)
            .maxOfOrNull { TitleMatchScorer.score(query, it) } ?: 0.0

    private fun anchors(
        source: CoverSourceMode,
        metadata: VnMetadata,
    ): List<String> {
        val out = LinkedHashSet<String>()
        val id = metadata.id.trim()
        if (id.isNotEmpty()) out += "${source.key}:$id"
        metadata.externalRefs.split(',')
            .map(String::trim)
            .filter { it.contains(':') && it.substringAfter(':').isNotEmpty() }
            .forEach(out::add)
        return out.toList()
    }

    private fun titleKeys(metadata: VnMetadata): List<String> =
        sequenceOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle)
            .map { TitleMatchScorer.normalize(it) }
            .filter { it.length >= 2 }
            .distinct()
            .toList()

    private fun developersCompatible(
        a: VnMetadata,
        b: VnMetadata,
    ): Boolean {
        val left = TitleMatchScorer.normalize(a.developer)
        val right = TitleMatchScorer.normalize(b.developer)
        return left.isEmpty() || right.isEmpty() || left == right
    }
}
