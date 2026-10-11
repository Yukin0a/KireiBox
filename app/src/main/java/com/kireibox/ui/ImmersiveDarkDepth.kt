package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.FxLevel

/**
 * Dark-theme depth and mood of the immersive backdrop (r4, user request 1: the dark immersive home
 * had become too light, and its default must be 「氛围色」 with purple). The dark home is calibrated
 * against the alpha.17 screenshots (artifacts/ui-alpha17/before/): a near-black base that carries
 * the accent hue (alpha.17's #05070D was blue-black), a restrained cover-tone glow behind the cards
 * and an accent-led atmosphere around them, with no grey or washed-out areas. The mean luma of the
 * backdrop above the cards and below the pager stays within ±15% of alpha.17 and its hue reads as
 * the accent (purple by default) on warm covers too (spec §10, r4). Light pages are untouched.
 *
 * Image backdrops (封面虚化, and the blurred cover under 流光极光) are bright by nature, so the dark
 * theme lays them at a lower alpha and under a base-colored scrim, and the aurora blobs are
 * quieter — every preset keeps the dark, moody room instead of a grey-blue haze.
 */
internal object ImmersiveDarkDepth {
    /** Share of the accent atmosphere in the center-card floor glow: cover tone + accent glow. */
    const val GLOW_ACCENT_MIX = .5f

    /** Saturation of the accent-hued near-black base (alpha.17's #05070D carried S .62 of blue). */
    const val BASE_SATURATION = .5f

    /**
     * The atmosphere shade ([ImmersivePalette.atmosphere]): the accent hue, saturated, at the relative
     * luminance of the default purple at V .60 — every accent tints the room equally deep.
     */
    const val ATMOSPHERE_SATURATION = .55f
    const val ATMOSPHERE_LUMINANCE = .08f

    /**
     * Accent share of the far atmosphere (the wide radial around the cards): with 「动态取色」 the room
     * still leans to the accent while the cover tone colors the near glow; without it the room is the
     * accent. Either way the default reads purple, also on warm covers (r4 · V1).
     */
    const val ATMOSPHERE_SHARE_DYNAMIC = .65f
    const val ATMOSPHERE_SHARE_ACCENT = .85f

    /** Blurred-cover alpha multiplier (封面虚化 .55 → .275 at 中; under the aurora .34 → .17). */
    const val IMAGERY_ALPHA_SCALE = .5f

    /**
     * Raw-cover alpha multiplier of 氛围色 (.12 → .084 at 中): light, white-heavy artwork (logos,
     * pale key art) otherwise ghosts through as a grey haze.
     */
    const val RAW_COVER_ALPHA_SCALE = .7f

    /** Aurora blob alpha multiplier. */
    const val AURORA_ALPHA_SCALE = .4f

    /** Base-colored scrim over blurred imagery: stronger at 低, lighter at 高 (more of the cover shows). */
    fun imageryScrimAlpha(level: FxLevel): Float =
        when (level) {
            FxLevel.LOW -> .48f
            FxLevel.MEDIUM -> .42f
            FxLevel.HIGH -> .36f
        }

    /** The aurora's accent blob sinks this far toward the base: a deep accent, not the light highlight. */
    const val AURORA_ACCENT_DEPTH = .35f

    /**
     * Empty gallery tone: the accent hue at alpha.17's empty-room strength (its #5B5AC8 fallback: S .55,
     * relative luminance ≈ .11), so an empty dark home is alpha.17's indigo/purple room with the default
     * accent — and never a grey haze. Other accents tint the room equally deep.
     */
    const val EMPTY_TONE_SATURATION = .55f
    const val EMPTY_TONE_LUMINANCE = .11f

    /**
     * 氛围色's faint cover fades toward the base over the lower part of the screen (behind the play-time
     * pill, the pager and the Dock): from [COVER_FLOOR_START] of the height down to [COVER_FLOOR_ALPHA]
     * base at the bottom edge, so logos and credits never crowd the controls.
     */
    const val COVER_FLOOR_START = .65f
    const val COVER_FLOOR_ALPHA = .35f

    /** Value range of the aurora's hue-shifted blob (the light theme allows .35–.80). */
    const val AURORA_SHIFTED_MIN_VALUE = .28f
    const val AURORA_SHIFTED_MAX_VALUE = .45f
}

/**
 * Three aurora hues: the cover tone, an accent blob and the tone turned 40° around the wheel. Dark
 * pages use a deep accent and a deep shifted hue; light pages keep the light highlight.
 */
internal fun immersiveAuroraColors(
    tint: Color,
    palette: ImmersivePalette,
): List<Color> {
    val hsv = CoverToneExtractor.rgbToHsv(tint.red, tint.green, tint.blue)
    val hue = (hsv[0] + AURORA_HUE_SHIFT) % 360f
    val saturation = hsv[1].coerceAtLeast(AURORA_MIN_SATURATION)
    return if (palette.isDark) {
        val value = hsv[2].coerceIn(ImmersiveDarkDepth.AURORA_SHIFTED_MIN_VALUE, ImmersiveDarkDepth.AURORA_SHIFTED_MAX_VALUE)
        listOf(tint, lerp(palette.accent, palette.base, ImmersiveDarkDepth.AURORA_ACCENT_DEPTH), Color.hsv(hue, saturation, value))
    } else {
        listOf(tint, palette.accentBright, Color.hsv(hue, saturation, hsv[2].coerceIn(LIGHT_AURORA_MIN_VALUE, LIGHT_AURORA_MAX_VALUE)))
    }
}

private const val AURORA_HUE_SHIFT = 40f
private const val AURORA_MIN_SATURATION = .3f
private const val LIGHT_AURORA_MIN_VALUE = .35f
private const val LIGHT_AURORA_MAX_VALUE = .8f

/**
 * Floor-glow color: the cover tone leaning toward the accent atmosphere on dark pages (a saturated,
 * deep accent rather than the light accent, so the stage never turns grey-lavender); light pages
 * keep the tone.
 */
internal fun immersiveGlowTint(
    tint: Color,
    palette: ImmersivePalette,
): Color = if (palette.isDark) lerp(tint, palette.atmosphere, ImmersiveDarkDepth.GLOW_ACCENT_MIX) else tint

/**
 * The far atmosphere around the cards (the wide radial): on dark pages the tint leans to the accent
 * shade — the room reads as the accent (purple by default) even on warm covers, while the near glow
 * keeps the cover tone; light pages keep the tint.
 */
internal fun immersiveAtmosphereTint(
    tint: Color,
    palette: ImmersivePalette,
    dynamicTint: Boolean,
): Color {
    if (!palette.isDark) return tint
    val share = if (dynamicTint) ImmersiveDarkDepth.ATMOSPHERE_SHARE_DYNAMIC else ImmersiveDarkDepth.ATMOSPHERE_SHARE_ACCENT
    return lerp(tint, palette.atmosphere, share)
}

/** Backdrop tint of an empty gallery (no hero): alpha.17's accent room on dark pages, the neutral pastel on light ones. */
internal fun immersiveEmptyGalleryTint(
    palette: ImmersivePalette,
    dynamicTint: Boolean,
): Color =
    if (palette.isDark) {
        immersiveAccentShade(palette.accent, ImmersiveDarkDepth.EMPTY_TONE_SATURATION, ImmersiveDarkDepth.EMPTY_TONE_LUMINANCE)
    } else {
        immersiveBackdropTint(null, palette, dynamicTint)
    }

/** The dark immersive base: as dark as [KireiTokens.Immersive.DarkBase] (same luminance), in the accent's hue. */
internal fun immersiveDarkBase(accent: Color): Color = immersiveAccentShade(accent, ImmersiveDarkDepth.BASE_SATURATION, KireiTokens.Immersive.DarkBase.luminance())

/**
 * [accent]'s hue at [saturation], with the HSV value solved so the color's relative luminance is
 * [luminance]: a yellow-green accent needs a lower value than a blue one to look equally deep.
 */
internal fun immersiveAccentShade(
    accent: Color,
    saturation: Float,
    luminance: Float,
): Color {
    val hue = CoverToneExtractor.rgbToHsv(accent.red, accent.green, accent.blue)[0]
    var low = 0f
    var high = 1f
    repeat(SHADE_SEARCH_STEPS) {
        val mid = (low + high) / 2f
        if (Color.hsv(hue, saturation, mid).luminance() < luminance) low = mid else high = mid
    }
    return Color.hsv(hue, saturation, (low + high) / 2f)
}

private const val SHADE_SEARCH_STEPS = 18

/**
 * Whether 氛围色's faint cover is drawn from the softened (blurred) copy: on dark pages, so logos,
 * credits and line art never show through (r4 · V2); light pages keep the raw cover.
 */
internal fun immersiveSoftensRawCover(isDark: Boolean): Boolean = isDark

/** Alpha multiplier of the cover pixels a backdrop paints, for the theme (1 on light pages). */
internal fun immersiveImageryAlphaScale(
    isDark: Boolean,
    source: ImmersiveBackdropCoverSource,
): Float =
    when {
        !isDark || source == ImmersiveBackdropCoverSource.NONE -> 1f
        // 封面氛围 is alpha.16 verbatim: its raw cover is not held back (ImmersiveCoverAtmosphere).
        source == ImmersiveBackdropCoverSource.ATMOSPHERE -> 1f
        source == ImmersiveBackdropCoverSource.RAW -> ImmersiveDarkDepth.RAW_COVER_ALPHA_SCALE
        else -> ImmersiveDarkDepth.IMAGERY_ALPHA_SCALE
    }

/**
 * Base-colored wash over blurred imagery: the light-theme pastel wash ([IMMERSIVE_LIGHT_COVER_WASH])
 * or the dark-theme depth scrim; 0 for backdrops without blurred pixels.
 */
internal fun immersiveImageryWashAlpha(
    isDark: Boolean,
    source: ImmersiveBackdropCoverSource,
    level: FxLevel,
): Float =
    when {
        source != ImmersiveBackdropCoverSource.BLURRED -> 0f
        isDark -> ImmersiveDarkDepth.imageryScrimAlpha(level)
        else -> IMMERSIVE_LIGHT_COVER_WASH
    }

/** Aurora blob alpha multiplier for the theme. */
internal fun immersiveAuroraAlphaScale(isDark: Boolean): Float = if (isDark) ImmersiveDarkDepth.AURORA_ALPHA_SCALE else 1f
