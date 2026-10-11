package com.kireibox.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.darkKireiColors
import com.yuki.yukihub.settings.AccentChoice

/**
 * Immersive home palette tokens. Dark and light are fully distinct so the immersive
 * gallery reads differently in each theme. Cover-internal overlays intentionally keep
 * the dark local gradient and white text in both themes for cover readability; every
 * outside-card surface (backdrop scrims, brand, metadata, controls, pager, empty
 * states) uses these resolved tokens.
 */
data class ImmersivePalette(
    val isDark: Boolean,
    /** Page background behind the cover backdrop. */
    val base: Color,
    /** Primary ink (brand, titles outside cards). */
    val primary: Color,
    /** Muted ink (subtitles, helper text). */
    val secondary: Color,
    /** Glass surface fill. */
    val glass: Color,
    /** Glass border (subtle white in dark, neutral strong stroke in light). */
    val glassBorder: Color,
    /** Accent fill for controls. */
    val accent: Color,
    /** Lighter accent used for highlights, dots and engine labels. */
    val accentBright: Color,
    /** Ink drawn on accent fills (play icon, directory button). */
    val playInk: Color,
    /** Horizontal backdrop scrim start (left edge). */
    val scrimStart: Color,
    /** Horizontal backdrop scrim mid. */
    val scrimMid: Color,
    /** Always-dark local gradient bottom used inside cover cards in both themes. */
    val cardOverlayBottom: Color,
    /** Cover-tone radial blend alpha behind the horizontal scrims (non-reduced). */
    val blendAlpha: Float,
    /** Cover-tone radial alpha (non-reduced transparency). */
    val radialAlpha: Float,
    /** Cover-tone radial alpha under reduced transparency. */
    val radialReducedAlpha: Float,
    /** Center-card glow alpha (non-reduced transparency). */
    val glowAlpha: Float,
    /** Center-card glow alpha under reduced transparency. */
    val glowReducedAlpha: Float,
    /**
     * Dark pages: the accent's hue as a deep, saturated shade — what the dark room leans to (r4:
     * 「氛围色」 + 紫色, see [ImmersiveDarkDepth]). Light pages: the accent (the light backdrop keeps
     * the cover tone).
     */
    val atmosphere: Color = accent,
)

object ImmersivePaletteResolver {
    /** Token palette for [accent] (default purple); the app passes its resolved colors instead. */
    fun resolve(
        isDark: Boolean,
        accent: AccentChoice = AccentChoice.DEFAULT,
    ): ImmersivePalette {
        val tone = KireiTokens.accent(accent, isDark)
        return resolve(isDark, tone.accent, tone.onAccent)
    }

    /**
     * Graphite palette whose accent, highlight and play ink follow the app accent ([accent] /
     * [onAccent] from the resolved app palette), so the immersive home never hardcodes a hue.
     */
    fun resolve(
        isDark: Boolean,
        accent: Color,
        onAccent: Color,
    ): ImmersivePalette = if (isDark) dark(accent, onAccent) else light(accent, onAccent)

    private fun dark(
        accent: Color,
        onAccent: Color,
    ): ImmersivePalette {
        // As dark as the graphite DarkBase, in the accent's hue (alpha.17's #05070D was blue-black).
        val base = immersiveDarkBase(accent)
        val ink = KireiTokens.DarkNeutrals.text
        return ImmersivePalette(
            isDark = true,
            base = base,
            primary = ink,
            secondary = ink.copy(alpha = .70f),
            glass = KireiTokens.Immersive.DarkGlass,
            glassBorder = Color.White.copy(alpha = .28f),
            accent = accent,
            accentBright = lerp(accent, Color.White, DARK_HIGHLIGHT_MIX),
            playInk = onAccent,
            scrimStart = base.copy(alpha = .97f),
            scrimMid = base.copy(alpha = .72f),
            // Cover-internal overlay: the neutral graphite in both themes (cover readability).
            cardOverlayBottom = KireiTokens.Immersive.DarkBase.copy(alpha = CARD_OVERLAY_ALPHA),
            blendAlpha = .38f,
            radialAlpha = .28f,
            radialReducedAlpha = .20f,
            glowAlpha = .22f,
            glowReducedAlpha = .16f,
            atmosphere = immersiveAccentShade(accent, ImmersiveDarkDepth.ATMOSPHERE_SATURATION, ImmersiveDarkDepth.ATMOSPHERE_LUMINANCE),
        )
    }

    private fun light(
        accent: Color,
        onAccent: Color,
    ): ImmersivePalette {
        val base = KireiTokens.Immersive.LightBase
        return ImmersivePalette(
            isDark = false,
            base = base,
            primary = KireiTokens.LightNeutrals.text,
            secondary = KireiTokens.LightNeutrals.secondary,
            glass = KireiTokens.Immersive.LightGlass,
            glassBorder = KireiTokens.LightNeutrals.strokeStrong,
            accent = accent,
            accentBright = lerp(accent, Color.White, LIGHT_HIGHLIGHT_MIX),
            playInk = onAccent,
            scrimStart = base.copy(alpha = .97f),
            scrimMid = base.copy(alpha = .72f),
            // Cover-internal overlay stays dark in both themes for cover readability.
            cardOverlayBottom = KireiTokens.Immersive.DarkBase.copy(alpha = CARD_OVERLAY_ALPHA),
            blendAlpha = .26f,
            radialAlpha = .18f,
            radialReducedAlpha = .12f,
            glowAlpha = .14f,
            glowReducedAlpha = .10f,
        )
    }

    private const val DARK_HIGHLIGHT_MIX = .22f
    private const val LIGHT_HIGHLIGHT_MIX = .18f
    private const val CARD_OVERLAY_ALPHA = .94f
}

/**
 * Immersive chrome (global Dock) palette: the dark theme uses the graphite dark palette on the
 * immersive base (keeping the app's accent and contrast), the light theme uses the app palette
 * so the Dock matches the light immersive page instead of being forced dark.
 */
internal fun immersiveDockKireiColors(appColors: KireiColors): KireiColors =
    if (appColors.isDark) {
        darkKireiColors(highContrast = appColors.highContrast).copy(
            accent = appColors.accent,
            onAccent = appColors.onAccent,
            accentContainer = appColors.accentContainer,
        )
    } else {
        appColors
    }
