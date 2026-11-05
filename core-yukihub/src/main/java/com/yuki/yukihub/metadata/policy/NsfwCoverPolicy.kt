package com.yuki.yukihub.metadata.policy

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata

/** Apply the original VNDB image threshold only when the selected image has trusted evidence. */
object NsfwCoverPolicy {
    const val SEXUAL_THRESHOLD = 0.5
    const val EXPLICIT_THRESHOLD = 1.5

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        coverSexual: Double
    ): Boolean =
        (source == CoverSourceMode.VNDB || source == CoverSourceMode.HIKARINAGI) &&
            coverSexual.isFinite() && coverSexual > SEXUAL_THRESHOLD && coverSexual <= 2.0

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        metadata: VnMetadata
    ): Boolean {
        val selected = metadata.covers.firstOrNull { it.url == metadata.coverUrl && it.url.isNotBlank() }
        val scoreSource: CoverSourceMode
        val score: Double?
        if (source == CoverSourceMode.NEXTMOE) {
            // NextMoe also carries Bangumi and AI-redrawn images. Their labels are not VNDB votes.
            val anchored = metadata.externalRefs.split(',').any { it.trim().matches(Regex("vndb:v[0-9]+")) }
            scoreSource = CoverSourceMode.VNDB
            score = if (anchored && selected?.source?.trim()?.lowercase() == "vndb") imageScore(selected.sexual) else null
        } else {
            // Legacy VNDB caches predate the image list. Never borrow a gallery neighbour's score.
            scoreSource = source
            score = if (metadata.covers.isEmpty()) metadata.coverSexual else imageScore(selected?.sexual.orEmpty())
        }
        return score?.let { shouldAutoMark(scoreSource, it) } ?: false
    }

    private fun imageScore(value: String): Double? =
        when (value.trim().lowercase()) {
            "safe" -> 0.0
            "suggestive" -> 1.0
            "explicit" -> 2.0
            else -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..2.0 }
        }

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        coverSexual: Double,
        alreadyNsfw: Boolean,
        detectionHandled: Boolean,
    ): Boolean = !alreadyNsfw && !detectionHandled && shouldAutoMark(source, coverSexual)
}
