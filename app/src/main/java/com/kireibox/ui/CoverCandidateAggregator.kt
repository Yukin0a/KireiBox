package com.kireibox.ui

import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.MetadataCover
import com.yuki.yukihub.metadata.model.VnMetadata

/**
 * Merges the per-source candidates of a hybrid search into one card per work.
 *
 * Two candidates are treated as the same work when they share a strong identity anchor:
 * - a `source:external_id` ref (VNDB/Bangumi carry their own id; NextMoe publishes every upstream
 *   anchor in its `refs`, so it bridges the catalogs that do not reference each other), or
 * - the same normalized title **and** a compatible developer.
 *
 * Anything weaker is left as a separate card: a wrong merge hides the correct entry, which is
 * worse than showing a duplicate. The representative slot is taken from the highest-priority
 * source in [order] (dynamic [com.yuki.yukihub.metadata.match.CoverSourcePriority.searchOrder] at call
 * sites; [CoverSourceMode.active] is the legacy default). Every contributor stays as a badge and
 * its covers are unioned so the variant strip offers all of them. Each cover remembers its catalog
 * in [CoverCandidateUi.coverSources] so a chosen variant is downloaded with the right headers.
 *
 * The merged card also records the evidence [com.yuki.yukihub.metadata.match.CoverMatchConfidence] needs:
 * the contributing [CoverCandidateUi.sources], the best (lowest) result rank
 * [CoverCandidateUi.bestRank], and whether the merge was anchored by an id rather than a title
 * ([CoverCandidateUi.linkedById]).
 */
internal object CoverCandidateAggregator {
    fun aggregate(
        candidates: List<CoverCandidateUi>,
        order: List<CoverSourceMode> = CoverSourceMode.active,
    ): List<CoverCandidateUi> {
        if (candidates.size <= 1) return candidates
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
            b: Int
        ) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        // Tracks which candidates were connected by an id/ref anchor (as opposed to a title match),
        // so the merged card can report `linkedById` to the confidence scorer.
        val anchorLinked = BooleanArray(candidates.size)

        // 1. Exact id / ref anchors — the strongest evidence.
        val byAnchor = HashMap<String, Int>()
        candidates.forEachIndexed { index, candidate ->
            refAnchors(candidate).forEach { anchor ->
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

        // 2. Same title + compatible developer.
        val byTitle = HashMap<String, MutableList<Int>>()
        candidates.forEachIndexed { index, candidate ->
            titleKeys(candidate).forEach { key -> byTitle.getOrPut(key) { ArrayList() }.add(index) }
        }
        byTitle.values.forEach { members ->
            for (i in members.indices) {
                for (j in i + 1 until members.size) {
                    if (developersCompatible(candidates[members[i]], candidates[members[j]])) {
                        union(members[i], members[j])
                    }
                }
            }
        }

        return candidates.indices.groupBy(::find).values
            .map { members -> merge(members, candidates, order, anchorLinked) }
    }

    private fun refAnchors(candidate: CoverCandidateUi): List<String> {
        val out = LinkedHashSet<String>()
        val id = candidate.metadata.id.trim()
        if (id.isNotEmpty()) out += "${candidate.source.key}:$id"
        candidate.metadata.externalRefs.split(',')
            .map(String::trim)
            .filter { it.contains(':') && it.substringAfter(':').isNotEmpty() }
            .forEach(out::add)
        return out.toList()
    }

    private fun titleKeys(candidate: CoverCandidateUi): List<String> {
        val metadata = candidate.metadata
        return sequenceOf(metadata.chineseTitle, metadata.originalTitle, metadata.romanTitle)
            .map { TitleMatchScorer.normalize(it) }
            .filter { it.length >= 2 }
            .distinct()
            .toList()
    }

    private fun developersCompatible(
        a: CoverCandidateUi,
        b: CoverCandidateUi
    ): Boolean {
        val left = TitleMatchScorer.normalize(a.metadata.developer)
        val right = TitleMatchScorer.normalize(b.metadata.developer)
        return left.isEmpty() || right.isEmpty() || left == right
    }

    private fun sourceRank(
        source: CoverSourceMode,
        order: List<CoverSourceMode>
    ): Int = order.indexOf(source).let { if (it < 0) Int.MAX_VALUE else it }

    private fun merge(
        members: List<Int>,
        candidates: List<CoverCandidateUi>,
        order: List<CoverSourceMode>,
        anchorLinked: BooleanArray,
    ): CoverCandidateUi {
        val group = members.map(candidates::get)
        val ordered = group.sortedBy { sourceRank(it.source, order) }
        val representative = ordered.first()
        val merged = VnMetadata.fromJson(representative.metadata.toJson().toString()) ?: representative.metadata

        merged.developer = merged.developer.ifBlank { firstNonBlank(ordered) { it.metadata.developer } }
        merged.description = merged.description.ifBlank { firstNonBlank(ordered) { it.metadata.description } }
        merged.translatedDescription =
            merged.translatedDescription.ifBlank { firstNonBlank(ordered) { it.metadata.translatedDescription } }
        merged.ratingText = merged.ratingText.ifBlank { firstNonBlank(ordered) { it.metadata.ratingText } }
        merged.tagsText = merged.tagsText.ifBlank { firstNonBlank(ordered) { it.metadata.tagsText } }
        merged.released = merged.released.ifBlank { firstNonBlank(ordered) { it.metadata.released } }
        merged.lengthText = merged.lengthText.ifBlank { firstNonBlank(ordered) { it.metadata.lengthText } }
        if (merged.lengthMinutes <= 0) merged.lengthMinutes = ordered.maxOfOrNull { it.metadata.lengthMinutes } ?: 0
        if (merged.lengthVotes <= 0) merged.lengthVotes = ordered.maxOfOrNull { it.metadata.lengthVotes } ?: 0

        val covers = LinkedHashMap<String, MetadataCover>()
        val coverSources = LinkedHashMap<String, CoverSourceMode>()
        ordered.forEach { candidate ->
            candidate.metadata.covers.forEach { cover ->
                if (cover.url.isNotBlank()) {
                    covers.putIfAbsent(cover.url, cover)
                    coverSources.putIfAbsent(cover.url, candidate.source)
                }
            }
            val url = candidate.metadata.coverUrl
            if (url.isNotBlank()) {
                covers.putIfAbsent(url, MetadataCover(url))
                coverSources.putIfAbsent(url, candidate.source)
            }
        }
        val sortedCovers =
            covers.values.sortedWith(
                compareByDescending<MetadataCover> { it.height > it.width && it.width > 0 }
                    .thenByDescending { it.width.toLong() * it.height }
            )
        merged.covers.clear()
        merged.covers.addAll(sortedCovers)
        if (merged.coverUrl.isBlank()) {
            sortedCovers.firstOrNull()?.let {
                merged.coverUrl = it.url
                merged.fullCoverUrl = it.url
            }
        }
        if (merged.coverUrl.isNotBlank()) coverSources.putIfAbsent(merged.coverUrl, representative.source)

        // Keep every upstream anchor: the merged card is a better bridge seed than any one source.
        val refs = LinkedHashSet<String>()
        ordered.forEach { candidate ->
            val id = candidate.metadata.id.trim()
            if (id.isNotEmpty()) refs += "${candidate.source.key}:$id"
            candidate.metadata.externalRefs.split(',')
                .map(String::trim)
                .filter { it.contains(':') }
                .forEach(refs::add)
        }
        merged.externalRefs = refs.joinToString(",")
        merged.titleAliases.clear()
        merged.titleAliases.addAll(ordered.flatMap { it.metadata.titleAliases }.distinct())

        val badges = ordered.map { it.source }.distinctBy { it.key }.sortedBy { sourceRank(it, order) }
        val bestRank = group.asSequence().map { it.bestRank }.filter { it > 0 }.minOrNull() ?: 0
        return CoverCandidateUi(
            metadata = merged,
            source = representative.source,
            score = group.maxOf { it.score },
            sources = badges,
            coverSources = coverSources,
            bestRank = bestRank,
            linkedById = members.any { anchorLinked[it] },
            matchReasons = group.flatMap { it.matchReasons }.distinct(),
            aliasHit = group.any { it.aliasHit },
        )
    }

    private inline fun firstNonBlank(
        group: List<CoverCandidateUi>,
        value: (CoverCandidateUi) -> String
    ): String = group.firstNotNullOfOrNull { value(it).takeIf(String::isNotBlank) } ?: ""
}
