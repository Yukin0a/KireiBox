package com.kireibox.ons

import android.content.Context
import com.yuki.yukihub.ons.OnsSettings
import java.util.Locale

/** The ONS engine's global defaults, as the settings page edits them (engine-side store: [OnsSettings]). */
data class OnsGlobalSettings(
    val encoding: String = "gbk",
    val stretchFull: Boolean = false,
    val ignoreCutout: Boolean = true,
    val sharpness: Boolean = false,
    val sharpnessValue: Float = DEFAULT_SHARPNESS,
    val disableVideo: Boolean = false,
    val keepScreenOn: Boolean = true,
    val scopedSaveDir: Boolean = true,
) {
    companion object {
        const val DEFAULT_SHARPNESS = 2f
        const val MIN_SHARPNESS = .5f
        const val MAX_SHARPNESS = 5f
        val ENCODINGS = listOf("gbk", "utf8", "sjis")

        /** Engine value -> slider value; anything unreadable or outside the engine's 0.1..10 range is the default. */
        fun parseSharpness(value: String?): Float = value?.trim()?.toFloatOrNull()?.takeIf { it in .1f..10f } ?: DEFAULT_SHARPNESS

        /** Slider value -> engine value ("2", "2.5"). */
        fun formatSharpness(value: Float): String {
            val rounded = (value * 10).toInt() / 10f
            return if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.ROOT, "%.1f", rounded)
        }

        fun load(context: Context): OnsGlobalSettings = from(OnsSettings.load(context))

        fun save(
            context: Context,
            settings: OnsGlobalSettings,
        ) {
            // Start from the stored object so fields this page does not edit (alloweditargs) survive.
            OnsSettings.load(context).also { settings.applyTo(it) }.save(context)
        }

        internal fun from(engine: OnsSettings) =
            OnsGlobalSettings(
                encoding = OnsSettings.normalizeEncoding(engine.encoding),
                stretchFull = engine.stretchFull,
                ignoreCutout = engine.ignoreCutout,
                sharpness = engine.sharpness,
                sharpnessValue = parseSharpness(engine.sharpnessValue),
                disableVideo = engine.disableVideo,
                keepScreenOn = engine.keepScreenOn,
                scopedSaveDir = engine.scopedSaveDir,
            )
    }

    internal fun applyTo(engine: OnsSettings) {
        engine.encoding = encoding
        engine.stretchFull = stretchFull
        engine.ignoreCutout = ignoreCutout
        engine.sharpness = sharpness
        engine.sharpnessValue = formatSharpness(sharpnessValue)
        engine.disableVideo = disableVideo
        engine.keepScreenOn = keepScreenOn
        engine.scopedSaveDir = scopedSaveDir
    }
}
