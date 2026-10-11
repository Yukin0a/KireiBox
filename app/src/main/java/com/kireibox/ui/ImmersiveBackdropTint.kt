package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kireibox.designsystem.KireiTokens

/*
 * Color decisions of the immersive backdrop and card edge (plan §6.1 氛围色 / 边缘光 / 动态取色).
 * Pure functions of the cover tone and the palette, so they are unit-tested without rendering.
 */

/** With 动态取色 off the app accent is only a hint mixed into the cover tone, never the whole tint. */
internal const val IMMERSIVE_ACCENT_HINT = .10f

/** Graphite used when a cover has no usable tone (no cover yet, decode failure). */
internal fun immersiveNeutralTone(isDark: Boolean): Color =
    if (isDark) {
        lerp(KireiTokens.Immersive.DarkBase, KireiTokens.DarkNeutrals.text, NEUTRAL_DARK_LIFT)
    } else {
        lerp(KireiTokens.Immersive.LightBase, KireiTokens.LightNeutrals.text, NEUTRAL_LIGHT_DEPTH)
    }

/**
 * Backdrop tint for one hero: the cover tone (a neutral graphite without a cover), with the
 * accent mixed in by [IMMERSIVE_ACCENT_HINT] only when dynamic tint is off. In the light theme
 * the tone is lifted to a pastel so it tints the light base instead of greying it.
 */
internal fun immersiveBackdropTint(
    tone: CoverTone?,
    palette: ImmersivePalette,
    dynamicTint: Boolean,
): Color {
    val cover = tone?.takeUnless { it.usedFallback }?.let { Color(it.argb) } ?: immersiveNeutralTone(palette.isDark)
    // The hint shifts hue and saturation only: a light accent (purple, sakura) must not lift the
    // dark backdrop (r4: 简约 is never brighter than alpha.17).
    val hinted = if (dynamicTint) cover else lerp(cover, palette.accent, IMMERSIVE_ACCENT_HINT).atValueOf(cover)
    return if (palette.isDark) hinted else immersivePastel(hinted)
}

/** This color with [reference]'s HSV value (brightness). */
private fun Color.atValueOf(reference: Color): Color {
    val hsv = CoverToneExtractor.rgbToHsv(red, green, blue)
    val value = CoverToneExtractor.rgbToHsv(reference.red, reference.green, reference.blue)[2]
    return Color.hsv(hsv[0], hsv[1], value)
}

/** Light-theme tint: same hue, at most moderate saturation, high value. */
internal fun immersivePastel(color: Color): Color {
    val hsv = CoverToneExtractor.rgbToHsv(color.red, color.green, color.blue)
    return Color.hsv(hsv[0], hsv[1].coerceAtMost(PASTEL_MAX_SATURATION), PASTEL_VALUE)
}

/**
 * Rim light (plan §6.1 边缘光): the hairline leans toward a lifted copy of [source] (the cover tone
 * is a deliberately dark backdrop tone, value .22–.38, which would darken the edge). The result
 * keeps the neutral edge's alpha, so the rim stays a hairline rather than an opaque outline.
 */
internal fun immersiveRimHighlight(source: Color): Color {
    val edge = KireiTokens.Immersive.CardEdge
    val hsv = CoverToneExtractor.rgbToHsv(source.red, source.green, source.blue)
    val lifted = Color.hsv(hsv[0], hsv[1].coerceAtMost(RIM_MAX_SATURATION), hsv[2].coerceAtLeast(RIM_MIN_VALUE))
    return lerp(edge, lifted.copy(alpha = edge.alpha), RIM_LIGHT_MIX)
}

private const val NEUTRAL_DARK_LIFT = .22f
private const val NEUTRAL_LIGHT_DEPTH = .14f
private const val PASTEL_MAX_SATURATION = .32f
private const val PASTEL_VALUE = .93f
private const val RIM_MAX_SATURATION = .45f
private const val RIM_MIN_VALUE = .85f
private const val RIM_LIGHT_MIX = .42f
