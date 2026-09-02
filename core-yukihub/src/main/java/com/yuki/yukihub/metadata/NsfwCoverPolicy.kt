package com.yuki.yukihub.metadata

/** YukiHub-compatible image-level NSFW detection policy. */
object NsfwCoverPolicy {
    const val SEXUAL_THRESHOLD = 0.5

    @JvmStatic
    fun shouldAutoMark(source: CoverSourceMode, coverSexual: Double): Boolean =
        (source == CoverSourceMode.VNDB || source == CoverSourceMode.HIKARINAGI) &&
            coverSexual.isFinite() && coverSexual > SEXUAL_THRESHOLD

    @JvmStatic
    fun shouldAutoMark(
        source: CoverSourceMode,
        coverSexual: Double,
        alreadyNsfw: Boolean,
        detectionHandled: Boolean,
    ): Boolean = !alreadyNsfw && !detectionHandled && shouldAutoMark(source, coverSexual)
}
