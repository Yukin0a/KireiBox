package com.kireibox.ui

import android.content.Context
import android.content.Intent
import com.yuki.yukihub.BuildConfig
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings

internal object ImmersiveVisualFixture {
    const val EXTRA_IMMERSIVE_FIXTURE = "com.kireibox.debug.IMMERSIVE_FIXTURE"
    const val EXTRA_IMMERSIVE_PROGRESS = "com.kireibox.debug.IMMERSIVE_PROGRESS"
    const val EXTRA_NSFW_COVERS = "com.kireibox.debug.NSFW_COVERS"
    const val EXTRA_NSFW_GLOBAL_ENABLED = "com.kireibox.debug.NSFW_GLOBAL_ENABLED"
    const val EXTRA_IMMERSIVE_PRESET = "com.kireibox.debug.IMMERSIVE_PRESET"
    const val EXTRA_IMMERSIVE_BACKDROP = "com.kireibox.debug.IMMERSIVE_BACKDROP"
    const val EXTRA_IMMERSIVE_AMBIENT = "com.kireibox.debug.IMMERSIVE_AMBIENT"
    const val EXTRA_IMMERSIVE_REDUCED_MOTION = "com.kireibox.debug.IMMERSIVE_REDUCED_MOTION"
    const val EXTRA_IMMERSIVE_REDUCED_TRANSPARENCY = "com.kireibox.debug.IMMERSIVE_REDUCED_TRANSPARENCY"
    const val EXTRA_IMMERSIVE_INCREASED_CONTRAST = "com.kireibox.debug.IMMERSIVE_INCREASED_CONTRAST"
    const val EXTRA_IMMERSIVE_FREEZE_MOTION = "com.kireibox.debug.IMMERSIVE_FREEZE_MOTION"

    /** 切换方式 on top of the preset: "soft" (缓和, default) or "stagger" (交错). */
    const val EXTRA_IMMERSIVE_CARD_SWITCH = "com.kireibox.debug.IMMERSIVE_CARD_SWITCH"

    /** Play time (minutes) given to the first fixture game, so landscape/portrait screenshots can show 「已游玩 …」 labels. */
    const val EXTRA_IMMERSIVE_PLAY_MINUTES = "com.kireibox.debug.IMMERSIVE_PLAY_MINUTES"

    /** How many of the five fixture games to show (1–5; absent = 5), for the small-gallery track (r5). */
    const val EXTRA_IMMERSIVE_GAME_COUNT = "com.kireibox.debug.IMMERSIVE_GAME_COUNT"
    private const val FIXTURE_GAME_COUNT = 5

    /** Replaces the first fixture game's title (absent = 「9nine新章」), to check long CJK titles in the landscape information column (r5). */
    const val EXTRA_IMMERSIVE_FIRST_TITLE = "com.kireibox.debug.IMMERSIVE_FIRST_TITLE"

    /** Legacy extras kept for existing adb scripts; they mean the same as the IMMERSIVE_REDUCED_* extras. */
    const val EXTRA_LEGACY_REDUCED_MOTION = "reduced_motion"
    const val EXTRA_LEGACY_REDUCED_TRANSPARENCY = "reduced_transparency"

    /**
     * Exact percent values accepted by [EXTRA_IMMERSIVE_PROGRESS]. Anything else is
     * rejected with null so a frozen Golden frame is never silently clamped to a
     * different animation position.
     */
    val SUPPORTED_FROZEN_PROGRESS_PERCENT: Set<Int> = setOf(0, 25, 50, 75, 100)

    private const val MILLIS_PER_MINUTE = 60_000L

    /** Sentinel used by [frozenTrackProgress] when the intent extra is absent. */
    private const val MISSING_PROGRESS_PERCENT = -1

    fun enabled(intent: Intent?): Boolean = BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_IMMERSIVE_FIXTURE, false) == true

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

    /**
     * Resolves a fixture preset without touching app preferences. Optional backdrop/ambient extras
     * override single effects (the result is then a CUSTOM state, exactly as in settings).
     */
    fun visualSettings(intent: Intent?): ImmersiveVisualSettings {
        val preset = ImmersiveFxPreset.fromStored(intent?.getStringExtra(EXTRA_IMMERSIVE_PRESET))
        val base = ImmersiveVisualSettings.forPreset(preset)
        val backdrop = intent?.getStringExtra(EXTRA_IMMERSIVE_BACKDROP)?.let(ImmersiveBackdropStyle::fromStored) ?: base.backdrop
        val ambient = intent?.getStringExtra(EXTRA_IMMERSIVE_AMBIENT)?.let(ImmersiveAmbient::fromStored) ?: base.ambient
        val cardSwitch = intent?.getStringExtra(EXTRA_IMMERSIVE_CARD_SWITCH)?.let(ImmersiveCardSwitch::fromStored) ?: base.cardSwitch
        return base.copy(backdrop = backdrop, ambient = ambient, cardSwitch = cardSwitch).normalized()
    }

    /** Legacy and namespaced reduced-motion extras are one switch for both the cards and the backdrop. */
    fun reducedMotion(intent: Intent?): Boolean = intent.flag(EXTRA_LEGACY_REDUCED_MOTION) || intent.flag(EXTRA_IMMERSIVE_REDUCED_MOTION)

    fun reducedTransparency(intent: Intent?): Boolean = intent.flag(EXTRA_LEGACY_REDUCED_TRANSPARENCY) || intent.flag(EXTRA_IMMERSIVE_REDUCED_TRANSPARENCY)

    /** The single resolved effect set the fixture renders, after the same degradation as the app. */
    fun resolvedFx(intent: Intent?): ResolvedImmersiveFx =
        resolveImmersiveFx(
            visualSettings(intent),
            ImmersiveFxEnvironment(
                reducedMotion = reducedMotion(intent),
                reducedTransparency = reducedTransparency(intent),
                increasedContrast = intent.flag(EXTRA_IMMERSIVE_INCREASED_CONTRAST),
                // Screenshots must be deterministic: freeze clocks and sensors on request.
                frozenMotion = intent.flag(EXTRA_IMMERSIVE_FREEZE_MOTION),
            ),
        )

    private fun Intent?.flag(name: String): Boolean = this?.getBooleanExtra(name, false) == true

    /** Minutes of play time for the first fixture game (absent or negative = 0, the 「尚未游玩」 state). */
    fun playTimeMinutes(intent: Intent?): Long = (intent?.getIntExtra(EXTRA_IMMERSIVE_PLAY_MINUTES, 0) ?: 0).coerceAtLeast(0).toLong()

    /** Number of fixture games to show, clamped to 1–5. */
    fun gameCount(intent: Intent?): Int = (intent?.getIntExtra(EXTRA_IMMERSIVE_GAME_COUNT, FIXTURE_GAME_COUNT) ?: FIXTURE_GAME_COUNT).coerceIn(1, FIXTURE_GAME_COUNT)

    /** The first game's title override, or null when the extra is absent or blank. */
    fun firstTitle(intent: Intent?): String? = intent?.getStringExtra(EXTRA_IMMERSIVE_FIRST_TITLE)?.takeIf { it.isNotBlank() }

    /** Pure percent-to-progress mapping: accepts only 0/25/50/75/100, rejects the rest with null. */
    fun immersiveProgress(percent: Int?): Float? = if (percent != null && percent in SUPPORTED_FROZEN_PROGRESS_PERCENT) percent / 100f else null

    fun games(
        context: Context,
        nsfwCovers: Boolean = false,
        firstGamePlayMinutes: Long = 0L,
        count: Int = FIXTURE_GAME_COUNT,
        firstTitle: String? = null,
    ): List<Game> =
        List(5) { index ->
            val resourceName = "fixture_cover_${index + 1}"
            val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            Game(
                id = 80_000L + index,
                title = firstTitle?.takeIf { index == 0 } ?: listOf("9nine新章", "樱色彼方", "月影回廊", "紫苑之庭", "晴空轨迹")[index],
                engine =
                    listOf(
                        EngineType.KIRIKIRI,
                        EngineType.ONS,
                        EngineType.TYRANO,
                        EngineType.ARTEMIS,
                        EngineType.KIRIKIRI,
                    )[index],
                coverUri = "android.resource://${context.packageName}/$resourceId",
                rootUri = "debug-fixture://$index",
                nsfw = nsfwCovers,
                totalPlayTime = if (index == 0) firstGamePlayMinutes * MILLIS_PER_MINUTE else 0L,
            )
        }.take(count.coerceIn(1, FIXTURE_GAME_COUNT))
}
