package com.kireibox.ui

import androidx.compose.ui.graphics.Color

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
    /** Glass border (subtle white in dark, visible cool-purple in light). */
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
)

object ImmersivePaletteResolver {
    fun resolve(isDark: Boolean): ImmersivePalette = if (isDark) dark() else light()

    private fun dark(): ImmersivePalette = ImmersivePalette(
        isDark = true,
        base = Color(0xFF05070D),
        primary = Color.White,
        secondary = Color.White.copy(alpha = .70f),
        glass = Color(0x66121720),
        glassBorder = Color.White.copy(alpha = .28f),
        accent = Color(0xFF9B8CFF),
        accentBright = Color(0xFFB8A7FF),
        playInk = Color(0xFF21164E),
        scrimStart = Color(0xF705070D),
        scrimMid = Color(0xB805070D),
        cardOverlayBottom = Color(0xF005070D),
        blendAlpha = .38f,
        radialAlpha = .28f,
        radialReducedAlpha = .20f,
        glowAlpha = .22f,
        glowReducedAlpha = .16f,
    )

    private fun light(): ImmersivePalette = ImmersivePalette(
        isDark = false,
        base = Color(0xFFF4F2F9),
        primary = Color(0xFF18151F),
        secondary = Color(0xFF6E6778),
        glass = Color(0xB3FFFFFF),
        glassBorder = Color(0x996750D8),
        accent = Color(0xFF6C5CE7),
        accentBright = Color(0xFF8F7BFF),
        playInk = Color.White,
        scrimStart = Color(0xF7F6F3FC),
        scrimMid = Color(0xB8F6F3FC),
        cardOverlayBottom = Color(0xF005070D),
        blendAlpha = .26f,
        radialAlpha = .18f,
        radialReducedAlpha = .12f,
        glowAlpha = .14f,
        glowReducedAlpha = .10f,
    )
}

/**
 * Immersive chrome (global Dock) palette: the dark theme keeps the chrome-dark palette
 * that always sits on the dark immersive backdrop; the light theme uses the app palette
 * so the Dock matches the light immersive page instead of being forced dark.
 */
internal fun immersiveDockKireiColors(appColors: KireiColors): KireiColors =
    if (appColors.isDark) darkKireiColors() else appColors
