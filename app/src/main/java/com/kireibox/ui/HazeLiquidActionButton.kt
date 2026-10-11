package com.kireibox.ui
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.kireibox.designsystem.KireiTokens
import dev.chrisbanes.haze.HazeInputScale

internal data class DockHazeConfiguration(
    val useOpaqueFallback: Boolean,
    val inputScale: HazeInputScale?,
    val backgroundColor: Color,
    val tint: Color?,
)

/**
 * Dock glass per spec §5 e3: the Haze path tints the sampled content with the glass token
 * (dark #16161B α .62 / light #FFFFFF α .72); every fallback is the opaque surfaceOverlay.
 */
internal object DockHazePolicy {
    const val FIXED_INPUT_SCALE = .5f

    fun useOpaqueFallback(
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Boolean = reducedTransparency || !hazeAvailable || !apiSupportsHaze

    fun inputScale(
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): HazeInputScale? =
        if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
            null
        } else {
            HazeInputScale.Fixed(FIXED_INPUT_SCALE)
        }

    fun backgroundColor(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Color =
        if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
            KireiTokens.neutrals(isDark).surfaceOverlay
        } else {
            Color.Transparent
        }

    fun tint(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Color? =
        if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
            null
        } else {
            KireiTokens.neutrals(isDark).glass
        }

    fun configuration(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): DockHazeConfiguration =
        DockHazeConfiguration(
            useOpaqueFallback = useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze),
            inputScale = inputScale(reducedTransparency, hazeAvailable, apiSupportsHaze),
            backgroundColor = backgroundColor(isDark, reducedTransparency, hazeAvailable, apiSupportsHaze),
            tint = tint(isDark, reducedTransparency, hazeAvailable, apiSupportsHaze),
        )
}
