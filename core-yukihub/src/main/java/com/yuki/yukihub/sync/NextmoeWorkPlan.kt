package com.yuki.yukihub.sync

/**
 * What the library knows about one game's identity, gathered locally (no network): how its cover was
 * bound, which source shows, and the work ids cached per source.
 */
data class GameBinding(
    val gameId: Long,
    val visibleSource: String?,
    val bindingMode: String,
    val nextmoeId: String?,
    val vndbId: String?,
    val bangumiId: String?,
)

/** A resolved answer for one game: the work its time goes to, or a candidate the user must confirm. */
sealed interface WorkDecision {
    data class Bound(val workId: String) : WorkDecision

    data class Candidate(val workId: String) : WorkDecision

    data object Unmatched : WorkDecision
}

/**
 * Which NextMoe work a game's playtime may be written to. Time on the wrong work is the one mistake
 * that cannot be undone quietly, so only evidence the user chose — or two independent sources that
 * agree — binds automatically; anything weaker becomes a candidate for the user to confirm.
 */
object NextmoeWorkPlan {
    private const val MANUAL = "MANUAL"

    /** `source:external_id` refs this game can be resolved through, in a fixed order. */
    fun refs(binding: GameBinding): List<String> =
        listOfNotNull(
            binding.vndbId?.takeIf { it.isNotBlank() }?.let { "vndb:$it" },
            binding.bangumiId?.takeIf { it.isNotBlank() }?.let { "bangumi:$it" },
        )

    /** A work the user picked as this game's NextMoe cover. Needs no request and overrides any earlier answer. */
    fun manualWork(binding: GameBinding): String? =
        binding.nextmoeId?.takeIf {
            it.isNotBlank() && binding.bindingMode == MANUAL && binding.visibleSource == "nextmoe"
        }

    /** The single ref the user picked by hand (a manual VNDB/Bangumi cover), which alone is trusted. */
    fun manualRef(binding: GameBinding): String? {
        if (binding.bindingMode != MANUAL) return null
        return when (binding.visibleSource) {
            "vndb" -> refs(binding).firstOrNull { it.startsWith("vndb:") }
            "bangumi", "bangumi_mirror" -> refs(binding).firstOrNull { it.startsWith("bangumi:") }
            else -> null
        }
    }

    /**
     * Decides from the refs NextMoe resolved ([resolved]: ref → work id). A manual ref binds alone.
     * Otherwise every piece of evidence (each resolved ref, plus an automatically cached NextMoe id)
     * must name the same work and there must be at least two pieces; a lone or conflicting answer
     * is only a candidate.
     */
    fun decide(
        binding: GameBinding,
        resolved: Map<String, String>,
    ): WorkDecision {
        val manual = manualWork(binding)
        val manualRef = manualRef(binding)
        val evidence = refs(binding).mapNotNull(resolved::get) + listOfNotNull(binding.nextmoeId?.takeIf { it.isNotBlank() })
        return when {
            manual != null -> WorkDecision.Bound(manual)
            manualRef != null -> resolved[manualRef]?.let(WorkDecision::Bound) ?: WorkDecision.Unmatched
            evidence.isEmpty() -> WorkDecision.Unmatched
            evidence.size >= 2 && evidence.distinct().size == 1 -> WorkDecision.Bound(evidence.first())
            else -> WorkDecision.Candidate(evidence.first())
        }
    }
}
