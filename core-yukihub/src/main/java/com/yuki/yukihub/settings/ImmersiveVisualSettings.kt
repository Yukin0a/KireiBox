package com.yuki.yukihub.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * The visual-effects preset used by the immersive home.
 *
 * Presets are intentionally data-only. The renderer is responsible for applying the appropriate
 * runtime downgrade for reduced motion, reduced transparency, memory pressure, and API level.
 */
enum class ImmersiveFxPreset(
    val storedValue: String,
    val displayName: String,
) {
    MINIMAL("minimal", "简约"),
    STANDARD("standard", "标准"),
    RICH("rich", "华丽"),
    CUSTOM("custom", "自定义"),
    ;

    companion object {
        fun fromStored(value: String?): ImmersiveFxPreset {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: STANDARD
        }
    }
}

/**
 * Background treatment for the immersive home.
 *
 * [COVER_ZOOM] (r5, user decision 2026-10-05) is the default: the alpha.16 immersive background —
 * a near-black base, the current game's cover as a faint layer and a glow of its colour (the stored
 * value and the name stay from r5's enlarged-cover attempt). Stored values are stable; the entry
 * order is the order the settings list shows.
 */
enum class ImmersiveBackdropStyle(
    val storedValue: String,
    val displayName: String,
) {
    COVER_ZOOM("cover_zoom", "封面氛围"),
    BLUR_COVER("blur_cover", "封面虚化"),
    AURORA("aurora", "流光极光"),
    TONE("tone", "氛围色"),

    /** Flat base plus vignette: true black in the dark theme, the light base in the light theme. */
    SOLID("solid", "纯色"),
    ;

    companion object {
        fun fromStored(value: String?): ImmersiveBackdropStyle {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: COVER_ZOOM
        }
    }
}

/** Three effect-strength levels shared by backdrop and card depth controls. */
enum class FxLevel(
    val storedValue: String,
    val displayName: String,
) {
    LOW("low", "低"),
    MEDIUM("medium", "中"),
    HIGH("high", "高"),
    ;

    companion object {
        fun fromStored(value: String?): FxLevel {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: MEDIUM
        }
    }
}

/** How portrait gallery cards change places (切换方式); chosen apart from the presets. */
enum class ImmersiveCardSwitch(
    val storedValue: String,
    val displayName: String,
) {
    /** Cards slide straight between their slots. */
    SOFT("soft", "缓和"),

    /** Cards swing apart as they pass and tuck back in. */
    STAGGER("stagger", "交错"),
    ;

    companion object {
        fun fromStored(value: String?): ImmersiveCardSwitch {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: SOFT
        }
    }
}

/** Optional atmosphere drawn above the immersive gallery. */
enum class ImmersiveAmbient(
    val storedValue: String,
    val displayName: String,
) {
    NONE("none", "无"),
    DUST("dust", "光尘"),
    SAKURA("sakura", "樱花"),
    SNOW("snow", "雪"),
    ;

    companion object {
        fun fromStored(value: String?): ImmersiveAmbient {
            val normalized = value?.trim()
            return entries.firstOrNull {
                it.storedValue.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            } ?: NONE
        }
    }
}

/**
 * Persisted visual settings for the immersive home.
 *
 * The standard preset is deliberately conservative: the 封面氛围 backdrop (the alpha.16 background;
 * r5 user decision, it supersedes r4's 氛围色 default), a medium card depth, glow/rim light and
 * dynamic tint, while keeping the vignette (alpha.16 had none, it would darken the default look),
 * particles and the expensive sheen effect off. 氛围色 (简约), 封面虚化 and 流光极光
 * (华丽) stay available. The renderer can still downgrade this state at runtime.
 */
data class ImmersiveVisualSettings(
    val preset: ImmersiveFxPreset = ImmersiveFxPreset.STANDARD,
    val backdrop: ImmersiveBackdropStyle = ImmersiveBackdropStyle.COVER_ZOOM,
    val backdropIntensity: FxLevel = FxLevel.MEDIUM,
    val kenBurns: Boolean = false,
    val grain: Boolean = false,
    val vignette: Boolean = false,
    val cardDepth: FxLevel = FxLevel.MEDIUM,
    val glow: Boolean = true,
    val rimLight: Boolean = true,
    val sheen: Boolean = false,
    val dynamicTint: Boolean = true,
    val ambient: ImmersiveAmbient = ImmersiveAmbient.NONE,
    val gyroParallax: Boolean = false,
    /** 切换方式: a general choice kept across preset changes, so it is not a preset detail ([sameDetailsAs]). */
    val cardSwitch: ImmersiveCardSwitch = ImmersiveCardSwitch.SOFT,
) {
    /**
     * A detail edit is a custom configuration. A preset remains named only while all of its
     * details still match the canonical preset values. This keeps a UI `copy(...)` edit from
     * silently displaying a stale preset label.
     */
    fun normalized(): ImmersiveVisualSettings {
        if (preset == ImmersiveFxPreset.CUSTOM) return this
        val canonical = forPreset(preset)
        return if (sameDetailsAs(canonical)) this else copy(preset = ImmersiveFxPreset.CUSTOM)
    }

    /** Returns whether every effect detail (excluding the preset label and [cardSwitch]) matches [other]. */
    fun sameDetailsAs(other: ImmersiveVisualSettings): Boolean = copy(preset = other.preset, cardSwitch = other.cardSwitch) == other

    /** [preset]'s canonical effects with this state's [cardSwitch] kept. */
    fun withPreset(preset: ImmersiveFxPreset): ImmersiveVisualSettings = forPreset(preset).copy(cardSwitch = cardSwitch)

    companion object {
        /**
         * Builds a complete canonical state for a preset, including [CUSTOM] as standard-like.
         * Values follow the preset table of doc/2026-10-02-ui-redesign-plan.md §6.1 (简约 / 标准 / 华丽):
         * backdrop 氛围色/封面氛围/流光极光 (r5), intensity 低/中/高, depth 平/标准/强, RICH layers
         * the aurora over the blurred cover.
         */
        fun forPreset(preset: ImmersiveFxPreset): ImmersiveVisualSettings =
            when (preset) {
                ImmersiveFxPreset.MINIMAL ->
                    ImmersiveVisualSettings(
                        preset = preset,
                        backdrop = ImmersiveBackdropStyle.TONE,
                        // r4: the calmest atmosphere; without a vignette a 中 tone made 简约 the
                        // brightest dark preset, now it is never brighter than alpha.17.
                        backdropIntensity = FxLevel.LOW,
                        vignette = false,
                        cardDepth = FxLevel.LOW,
                        glow = false,
                        rimLight = false,
                        dynamicTint = false,
                    )
                ImmersiveFxPreset.STANDARD -> ImmersiveVisualSettings()
                ImmersiveFxPreset.RICH ->
                    ImmersiveVisualSettings(
                        preset = preset,
                        backdrop = ImmersiveBackdropStyle.AURORA,
                        backdropIntensity = FxLevel.HIGH,
                        kenBurns = true,
                        grain = true,
                        vignette = true,
                        cardDepth = FxLevel.HIGH,
                        glow = true,
                        rimLight = true,
                        sheen = true,
                        dynamicTint = true,
                        ambient = ImmersiveAmbient.DUST,
                        gyroParallax = true,
                    )
                ImmersiveFxPreset.CUSTOM -> ImmersiveVisualSettings(preset = preset)
            }
    }
}

/**
 * SharedPreferences adapter for [ImmersiveVisualSettings].
 *
 * This deliberately lives outside [YukiSettingsStore] so the large legacy store remains below the
 * source-file size gate. It uses the same preferences file and therefore participates in the same
 * backup/migration lifecycle without introducing a second state system.
 */
class ImmersiveVisualSettingsStore internal constructor(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(YukiSettingsStore.PREFS_NAME, Context.MODE_PRIVATE),
    )

    /**
     * Reads a complete state, applying safe defaults when a key is absent or malformed.
     *
     * A named preset is stored only while its details equal the canonical ones ([write] normalizes),
     * so its detail keys carry no information: it always reads back as today's canonical preset
     * (plus the 切换方式, which is not a preset detail).
     * That keeps 简约/标准/华丽 users on their preset when a later build adds a detail key or retunes
     * a preset, instead of silently relabelling them 自定义 and freezing the old values. Only a
     * CUSTOM state reads its details, each falling back to the standard value when absent.
     */
    fun read(): ImmersiveVisualSettings {
        val preset = ImmersiveFxPreset.fromStored(readString(KEY_PRESET))
        val cardSwitch = ImmersiveCardSwitch.fromStored(readString(KEY_CARD_SWITCH))
        if (preset != ImmersiveFxPreset.CUSTOM) return ImmersiveVisualSettings.forPreset(preset).copy(cardSwitch = cardSwitch)
        val base = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)
        return ImmersiveVisualSettings(
            preset = ImmersiveFxPreset.CUSTOM,
            backdrop = readString(KEY_BACKDROP)?.let(ImmersiveBackdropStyle::fromStored) ?: base.backdrop,
            backdropIntensity = readString(KEY_BACKDROP_INTENSITY)?.let(FxLevel::fromStored) ?: base.backdropIntensity,
            kenBurns = readBoolean(KEY_KEN_BURNS, base.kenBurns),
            grain = readBoolean(KEY_GRAIN, base.grain),
            vignette = readBoolean(KEY_VIGNETTE, base.vignette),
            cardDepth = readString(KEY_CARD_DEPTH)?.let(FxLevel::fromStored) ?: base.cardDepth,
            glow = readBoolean(KEY_GLOW, base.glow),
            rimLight = readBoolean(KEY_RIM_LIGHT, base.rimLight),
            sheen = readBoolean(KEY_SHEEN, base.sheen),
            dynamicTint = readBoolean(KEY_DYNAMIC_TINT, base.dynamicTint),
            ambient = readString(KEY_AMBIENT)?.let(ImmersiveAmbient::fromStored) ?: base.ambient,
            gyroParallax = readBoolean(KEY_GYRO_PARALLAX, base.gyroParallax),
            cardSwitch = cardSwitch,
        )
    }

    /** Writes all keys atomically and returns the canonical state that was persisted. */
    fun write(next: ImmersiveVisualSettings): ImmersiveVisualSettings {
        val value = next.normalized()
        prefs.edit()
            .putString(KEY_PRESET, value.preset.storedValue)
            .putString(KEY_BACKDROP, value.backdrop.storedValue)
            .putString(KEY_BACKDROP_INTENSITY, value.backdropIntensity.storedValue)
            .putBoolean(KEY_KEN_BURNS, value.kenBurns)
            .putBoolean(KEY_GRAIN, value.grain)
            .putBoolean(KEY_VIGNETTE, value.vignette)
            .putString(KEY_CARD_DEPTH, value.cardDepth.storedValue)
            .putBoolean(KEY_GLOW, value.glow)
            .putBoolean(KEY_RIM_LIGHT, value.rimLight)
            .putBoolean(KEY_SHEEN, value.sheen)
            .putBoolean(KEY_DYNAMIC_TINT, value.dynamicTint)
            .putString(KEY_AMBIENT, value.ambient.storedValue)
            .putBoolean(KEY_GYRO_PARALLAX, value.gyroParallax)
            .putString(KEY_CARD_SWITCH, value.cardSwitch.storedValue)
            .apply()
        return value
    }

    private fun readString(key: String): String? = runCatching { prefs.getString(key, null) }.getOrNull()

    private fun readBoolean(
        key: String,
        default: Boolean,
    ): Boolean = runCatching { prefs.getBoolean(key, default) }.getOrDefault(default)

    companion object {
        const val KEY_PREFIX = "kireibox_immersive_fx_"
        const val KEY_PRESET = "${KEY_PREFIX}preset"
        const val KEY_BACKDROP = "${KEY_PREFIX}backdrop"
        const val KEY_BACKDROP_INTENSITY = "${KEY_PREFIX}backdrop_intensity"
        const val KEY_KEN_BURNS = "${KEY_PREFIX}ken_burns"
        const val KEY_GRAIN = "${KEY_PREFIX}grain"
        const val KEY_VIGNETTE = "${KEY_PREFIX}vignette"
        const val KEY_CARD_DEPTH = "${KEY_PREFIX}card_depth"
        const val KEY_GLOW = "${KEY_PREFIX}glow"
        const val KEY_RIM_LIGHT = "${KEY_PREFIX}rim_light"
        const val KEY_SHEEN = "${KEY_PREFIX}sheen"
        const val KEY_DYNAMIC_TINT = "${KEY_PREFIX}dynamic_tint"
        const val KEY_AMBIENT = "${KEY_PREFIX}ambient"
        const val KEY_GYRO_PARALLAX = "${KEY_PREFIX}gyro_parallax"
        const val KEY_CARD_SWITCH = "${KEY_PREFIX}card_switch"
    }
}
