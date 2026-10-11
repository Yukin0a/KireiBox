package com.yuki.yukihub.metadata.policy
import com.yuki.yukihub.metadata.model.CoverSourceMode

/** YukiHub-compatible image-level NSFW detection policy. */
object NsfwCoverPolicy {
    const val SEXUAL_THRESHOLD = 0.5

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        coverSexual: Double,
    ): Boolean =
        (source == CoverSourceMode.VNDB || source == CoverSourceMode.HIKARINAGI || source == CoverSourceMode.NEXTMOE) &&
            coverSexual.isFinite() && coverSexual > SEXUAL_THRESHOLD

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        coverSexual: Double,
        alreadyNsfw: Boolean,
        detectionHandled: Boolean,
    ): Boolean = !alreadyNsfw && !detectionHandled && shouldAutoMark(source, coverSexual)
}
