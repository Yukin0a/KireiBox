package com.kireibox.ui

import android.content.Context
import android.content.Intent
import com.yuki.yukihub.BuildConfig
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game

internal object ImmersiveVisualFixture {
    const val EXTRA_IMMERSIVE_FIXTURE = "com.kireibox.debug.IMMERSIVE_FIXTURE"
    const val EXTRA_IMMERSIVE_PROGRESS = "com.kireibox.debug.IMMERSIVE_PROGRESS"
    const val EXTRA_NSFW_COVERS = "com.kireibox.debug.NSFW_COVERS"
    const val EXTRA_NSFW_GLOBAL_ENABLED = "com.kireibox.debug.NSFW_GLOBAL_ENABLED"

    /**
     * Exact percent values accepted by [EXTRA_IMMERSIVE_PROGRESS]. Anything else is
     * rejected with null so a frozen Golden frame is never silently clamped to a
     * different animation position.
     */
    val SUPPORTED_FROZEN_PROGRESS_PERCENT: Set<Int> = setOf(0, 25, 50, 75, 100)

    /** Sentinel used by [frozenTrackProgress] when the intent extra is absent. */
    private const val MISSING_PROGRESS_PERCENT = -1

    fun enabled(intent: Intent?): Boolean =
        BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_IMMERSIVE_FIXTURE, false) == true

    /**
     * Returns the frozen track progress for [intent] as a [0f, 1f] value, or null when
     * the extra is absent or invalid. The extra is only read in DEBUG builds while the
     * fixture is enabled; this object lives entirely in the debug source set, so release
     * APKs never carry the entry point or the parser.
     */
    fun frozenTrackProgress(intent: Intent?): Float? =
        if (BuildConfig.DEBUG && enabled(intent)) {
            immersiveProgress(intent?.getIntExtra(EXTRA_IMMERSIVE_PROGRESS, MISSING_PROGRESS_PERCENT))
        } else {
            null
        }

    /** Pure percent-to-progress mapping: accepts only 0/25/50/75/100, rejects the rest with null. */
    fun immersiveProgress(percent: Int?): Float? =
        if (percent != null && percent in SUPPORTED_FROZEN_PROGRESS_PERCENT) percent / 100f else null

    fun games(context: Context, nsfwCovers: Boolean = false): List<Game> = List(5) { index ->
        val resourceName = "fixture_cover_${index + 1}"
        val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        Game(
            id = 80_000L + index,
            title = listOf("9nine新章", "樱色彼方", "月影回廊", "紫苑之庭", "晴空轨迹")[index],
            engine = listOf(
                EngineType.KIRIKIRI,
                EngineType.ONS,
                EngineType.TYRANO,
                EngineType.ARTEMIS,
                EngineType.KIRIKIRI,
            )[index],
            coverUri = "android.resource://${context.packageName}/$resourceId",
            rootUri = "debug-fixture://$index",
            nsfw = nsfwCovers,
        )
    }
}
