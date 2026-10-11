package com.kireibox.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuki.yukihub.settings.AccentChoice

/*
 * KireiBox design tokens (doc/2026-10-02-ui-alpha17-design-spec.md). This file is the only
 * place where raw color, radius, spacing and elevation values are defined; pages consume the
 * resolved palette (KireiColors / AppleColorPalette) or the objects below.
 */

/** Accent-independent neutral colors for one theme (spec §2.1). */
@Immutable
data class KireiNeutralTokens(
    val background: Color,
    /** surface1: list groups and cards. */
    val surface: Color,
    /** surface2: pressed states, secondary containers, unselected chips. */
    val surface2: Color,
    /** surface3: inputs, segmented-control track, switch-off track. */
    val surface3: Color,
    /** Sheet / Dialog / Menu container. */
    val surfaceOverlay: Color,
    /** Hairline borders and dividers. */
    val stroke: Color,
    /** Input borders and emphasized boundaries. */
    val strokeStrong: Color,
    /** Opaque mid-tone boundary for legacy `KireiColors.outline` consumers. */
    val outline: Color,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val scrim: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** e3 glass fill; an opaque surfaceOverlay when contrast is increased. */
    val glass: Color,
    val isDark: Boolean,
    val highContrast: Boolean = false,
)

/** Accent pair for one theme (spec §2.2). */
@Immutable
data class KireiAccentTokens(
    val accent: Color,
    val onAccent: Color,
)

object KireiTokens {
    val DarkNeutrals =
        KireiNeutralTokens(
            background = Color(0xFF0E0E11),
            surface = Color(0xFF17171B),
            surface2 = Color(0xFF202026),
            surface3 = Color(0xFF2A2A31),
            surfaceOverlay = Color(0xFF1B1B20),
            stroke = Color.White.copy(alpha = .08f),
            strokeStrong = Color.White.copy(alpha = .14f),
            outline = Color(0xFF3A3A42),
            text = Color(0xFFF4F4F6),
            secondary = Color(0xFFA3A3AD),
            tertiary = Color(0xFF6C6C76),
            scrim = Color.Black.copy(alpha = .52f),
            success = Color(0xFF34C77B),
            warning = Color(0xFFFFB340),
            danger = Color(0xFFFF5A5F),
            glass = Color(0xFF16161B).copy(alpha = .62f),
            isDark = true,
        )

    val LightNeutrals =
        KireiNeutralTokens(
            background = Color(0xFFF5F5F7),
            surface = Color(0xFFFFFFFF),
            surface2 = Color(0xFFEFEFF2),
            surface3 = Color(0xFFE6E6EB),
            surfaceOverlay = Color(0xFFFFFFFF),
            stroke = Color.Black.copy(alpha = .07f),
            strokeStrong = Color.Black.copy(alpha = .12f),
            outline = Color(0xFFD8D8DE),
            text = Color(0xFF111114),
            secondary = Color(0xFF6B6B74),
            tertiary = Color(0xFFA0A0A8),
            scrim = Color.Black.copy(alpha = .28f),
            success = Color(0xFF1FA463),
            warning = Color(0xFFD98200),
            danger = Color(0xFFE5383B),
            glass = Color.White.copy(alpha = .72f),
            isDark = false,
        )

    /**
     * Increased contrast (spec §2.1): stronger secondary/tertiary ink, doubled hairline alpha
     * (strokeStrong doubles too so it stays above stroke) and an opaque glass fill.
     */
    private val DarkHighContrastNeutrals =
        DarkNeutrals.copy(
            secondary = Color(0xFFC4C4CC),
            tertiary = Color(0xFF8E8E98),
            stroke = Color.White.copy(alpha = .16f),
            strokeStrong = Color.White.copy(alpha = .28f),
            glass = DarkNeutrals.surfaceOverlay,
            highContrast = true,
        )

    private val LightHighContrastNeutrals =
        LightNeutrals.copy(
            secondary = Color(0xFF4A4A52),
            tertiary = Color(0xFF7A7A84),
            stroke = Color.Black.copy(alpha = .14f),
            strokeStrong = Color.Black.copy(alpha = .24f),
            glass = LightNeutrals.surfaceOverlay,
            highContrast = true,
        )

    fun neutrals(
        isDark: Boolean,
        highContrast: Boolean = false,
    ): KireiNeutralTokens =
        when {
            isDark && highContrast -> DarkHighContrastNeutrals
            isDark -> DarkNeutrals
            highContrast -> LightHighContrastNeutrals
            else -> LightNeutrals
        }

    private val darkAccents: Map<AccentChoice, KireiAccentTokens> =
        mapOf(
            AccentChoice.SAKURA to KireiAccentTokens(Color(0xFFFF8FAB), Color(0xFF3A0A1A)),
            AccentChoice.OCEAN to KireiAccentTokens(Color(0xFF6FB8FF), Color(0xFF04213D)),
            AccentChoice.AMBER to KireiAccentTokens(Color(0xFFFFB547), Color(0xFF3A2400)),
            AccentChoice.MINT to KireiAccentTokens(Color(0xFF5EDBB0), Color(0xFF00301F)),
            AccentChoice.CLASSIC to KireiAccentTokens(Color(0xFFB3ACFF), Color(0xFF211E47)),
        )

    /**
     * Light accents (spec §2.2, amended 2026-10-03): each keeps its hue but is deep enough that
     * white-on-accent, accent-on-background and accent-on-accentContainer all reach WCAG AA 4.5:1
     * for small text (chips, buttons, the selected Dock label).
     */
    private val lightAccents: Map<AccentChoice, KireiAccentTokens> =
        mapOf(
            AccentChoice.SAKURA to KireiAccentTokens(Color(0xFFBA2859), Color.White),
            AccentChoice.OCEAN to KireiAccentTokens(Color(0xFF1C63C0), Color.White),
            AccentChoice.AMBER to KireiAccentTokens(Color(0xFF955600), Color.White),
            AccentChoice.MINT to KireiAccentTokens(Color(0xFF097353), Color.White),
            AccentChoice.CLASSIC to KireiAccentTokens(Color(0xFF5B45C7), Color.White),
        )

    fun accent(
        choice: AccentChoice,
        isDark: Boolean,
    ): KireiAccentTokens = (if (isDark) darkAccents else lightAccents).getValue(choice)

    /** The single tint for selection/tinted fills: accent α .16 (dark) / .12 (light). */
    fun accentContainer(
        accent: Color,
        isDark: Boolean,
    ): Color = accent.copy(alpha = if (isDark) .16f else .12f)

    /** Pressed accent: accent mixed 12% toward the primary text color. */
    fun accentStrong(
        accent: Color,
        text: Color,
    ): Color = lerp(accent, text, .12f)

    /**
     * White ink and knobs on saturated fills whose color never follows the theme: danger badges,
     * category tiles (spec §2.3) and switch thumbs (spec §8).
     */
    val OnSolid: Color = Color.White

    /**
     * Count badges on the danger tone (spec §2.1 amendment): white digits on the light danger fail
     * AA at 11sp, so light badges use a deeper red; dark badges keep the danger fill with dark ink.
     */
    fun badgeDanger(isDark: Boolean): KireiAccentTokens = if (isDark) KireiAccentTokens(DarkNeutrals.danger, Color(0xFF3A0A0C)) else KireiAccentTokens(Color(0xFFCF2E33), OnSolid)

    /**
     * Light-theme boundary of off-state controls (switch track): ≥ 3:1 against the white group
     * surface (WCAG 1.4.11). The dark theme's white thumb already stands out on its track.
     */
    val ControlBorderLight: Color = Color(0xFF8E8E93)

    /**
     * Light-theme input placeholder ink: secondary is only 4.2:1 on the surface3 field fill, this
     * is 4.8:1 (spec §10 amendment). Dark fields keep secondary (5.7:1).
     */
    val PlaceholderLight: Color = Color(0xFF63636C)

    /**
     * Chrome drawn directly on cover artwork. The image decides the backdrop, so these stay light
     * ink over a dark scrim in both themes instead of following the page palette.
     */
    object OnImage {
        val ink: Color = Color.White

        /** Backing for small labels (engine tags) placed on cover art. */
        val labelScrim: Color = Color.Black.copy(alpha = .62f)
    }

    /**
     * Immersive home bases (spec §2.4): neutral graphite, no blue cast. The dark base is near-black
     * (r4): the alpha.17 depth (#05070D) without its blue cast, see ImmersiveDarkDepth.
     */
    object Immersive {
        val DarkBase = Color(0xFF0A0A0C)
        val LightBase = Color(0xFFF4F4F6)

        /** Translucent control glass on the immersive backdrop. */
        val DarkGlass = Color(0xFF16161B).copy(alpha = .40f)
        val LightGlass = Color.White.copy(alpha = .72f)

        /** Always-dark gradient bottom inside cover cards (both themes) for title readability. */
        val CoverOverlayBottom = DarkBase.copy(alpha = .94f)

        /** Hairline edge around immersive cover cards: neutral graphite, readable on light and dark. */
        val CardEdge = Color(0x887D7D86)

        /** SOLID backdrop in the dark theme: true OLED black (the light theme keeps [LightBase]). */
        val SolidDark = Color(0xFF000000)

        /** Vignette ink: black on dark pages, graphite on light pages so corners never turn muddy. */
        val VignetteDark = Color(0xFF000000)
        val VignetteLight = Color(0xFF3A3A44)

        /** Depth scrim inside side cards: darkens them on dark pages, washes them out on light pages. */
        val CardDepthDark = Color(0xFF000000)
        val CardDepthLight = Color(0xFFFFFFFF)

        /** Ambient particles (light dust, sakura petals, snow). */
        val ParticleDust = Color(0xFFFFF4E0)
        val ParticleSakura = Color(0xFFFFB8D1)

        /** Petal base / sakura bokeh: the deeper pink of a petal's root (two-tone petals). */
        val ParticleSakuraDeep = Color(0xFFF38BB0)
        val ParticleSnow = Color(0xFFFFFFFF)

        /**
         * Light-theme dust / snow: warm-white motes vanish on the pale backdrop (~#E4E8EE), so
         * light pages draw sunlit-amber dust and cool slate snow instead (spec §10 amendment).
         */
        val ParticleDustLight = Color(0xFFA27B3F)
        val ParticleSnowLight = Color(0xFF7F95B5)
    }
}

/**
 * Category tile colors (spec §2.3, r4): tonal tiles — a tinted container with a glyph in the same
 * hue — derived in OKLCH so every hue has the same perceived lightness and chroma:
 * dark container L .32 C .055 / glyph L .82 C .10, light container L .935 C .035 / glyph L .52 C .13
 * (chroma clipped to sRGB where needed; SLATE keeps 28% and GRAY 8% of it). Glyph on container is
 * ≥ 7:1 in dark and ≥ 4.3:1 in light (WCAG 1.4.11 asks 3:1). [hue] is the OKLCH hue in degrees.
 */
enum class KireiTileColor(
    val hue: Int,
    private val darkContainer: Color,
    private val darkGlyph: Color,
    private val lightContainer: Color,
    private val lightGlyph: Color,
) {
    PINK(350, Color(0xFF482737), Color(0xFFF6A9CD), Color(0xFFFDE1ED), Color(0xFF9E4573)),
    PURPLE(305, Color(0xFF392B49), Color(0xFFD3B4F9), Color(0xFFEFE4FD), Color(0xFF7B52A4)),
    INDIGO(275, Color(0xFF2B304F), Color(0xFFB3C0FF), Color(0xFFE3E8FF), Color(0xFF5460B3)),
    BLUE(245, Color(0xFF18354D), Color(0xFF8ECBFF), Color(0xFFD8ECFF), Color(0xFF036EAE)),
    TEAL(190, Color(0xFF003C39), Color(0xFF6ED9D2), Color(0xFFD0F2EF), Color(0xFF097974)),
    GREEN(150, Color(0xFF1C3B24), Color(0xFF95D7A2), Color(0xFFDAF1DD), Color(0xFF1D7D3E)),
    ORANGE(55, Color(0xFF482B16), Color(0xFFF7B385), Color(0xFFFDE4D4), Color(0xFF9F5102)),
    RED(25, Color(0xFF4B2724), Color(0xFFFFABA3), Color(0xFFFEE2DF), Color(0xFFA74541)),
    SLATE(255, Color(0xFF2E333B), Color(0xFFB8C5D6), Color(0xFFE5EAF0), Color(0xFF5B6A7E)),
    GRAY(286, Color(0xFF323235), Color(0xFFC3C3C9), Color(0xFFE9E9EB), Color(0xFF68686F)),
    ;

    /** Tile (or avatar) fill for the current theme. */
    fun container(isDark: Boolean): Color = if (isDark) darkContainer else lightContainer

    /** Glyph (or initial) drawn on [container]: the same hue, light on dark and deep on light. */
    fun glyph(isDark: Boolean): Color = if (isDark) darkGlyph else lightGlyph
}

/** Corner radius tokens (spec §3). Use these instead of `RoundedCornerShape(<literal>.dp)`. */
object KireiShapes {
    object Radius {
        val xs: Dp = 6.dp
        val sm: Dp = 10.dp
        val md: Dp = 14.dp
        val lg: Dp = 20.dp
        val xl: Dp = 28.dp
    }

    /** Small badges, engine tags. */
    val xs = RoundedCornerShape(Radius.xs)

    /** Icon tiles (30–36dp), small thumbnails. */
    val sm = RoundedCornerShape(Radius.sm)

    /** Inputs, non-pill buttons, cover thumbnails. */
    val md = RoundedCornerShape(Radius.md)

    /** List groups, cards, game covers. */
    val lg = RoundedCornerShape(Radius.lg)

    /** Dialogs. */
    val xl = RoundedCornerShape(Radius.xl)

    /** Bottom sheets: only the top corners are rounded. */
    val sheet = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

    /** Pills: capsule buttons, chips, switches, Dock. */
    val full = CircleShape

    /** Material3 shape scale so stock components inherit the Kirei radii. */
    val material =
        Shapes(
            extraSmall = xs,
            small = sm,
            medium = md,
            large = lg,
            extraLarge = xl,
        )
}

/** 4pt spacing grid and list metrics (spec §4). Page gutters stay in AdaptiveLayoutConstants. */
object KireiSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp

    /** Horizontal padding inside a list group. */
    val groupInner: Dp = md

    /** Vertical gap between list groups. */
    val groupGap: Dp = xl

    /** Gap between a group caption/footer and the group. */
    val groupCaptionGap: Dp = xs
    val rowMinHeightSingleLine: Dp = 52.dp
    val rowMinHeightTwoLine: Dp = 64.dp
    val rowVerticalPadding: Dp = sm
    val iconTileSize: Dp = 30.dp
    val iconTileTextGap: Dp = 14.dp
}

/** Elevation levels (spec §5): e1 hairline, e2 hairline + shadow, e3 glass + strong stroke + shadow. */
object KireiElevation {
    val strokeWidth: Dp = 0.6.dp
    val e2Shadow: Dp = 8.dp
    val e3Shadow: Dp = 16.dp

    /** Small lift under control knobs (switch thumb, segmented selection). */
    val controlShadow: Dp = 2.dp

    /** Focus ring width for inputs. */
    val focusStrokeWidth: Dp = 1.5.dp

    /** Keyboard / D-pad focus ring of rows, segments and the slider thumb (WCAG 2.4.7). */
    val focusRingWidth: Dp = 2.dp

    fun e2ShadowAlpha(isDark: Boolean): Float = if (isDark) .30f else .10f

    fun e3ShadowAlpha(isDark: Boolean): Float = if (isDark) .30f else .12f

    /**
     * Shadow ink for an elevation alpha. Android multiplies the outline shadow color with the
     * theme's own shadow alpha, so the spec alphas are applied relative to the strongest level
     * (dark .30 = full ink): light shadows stay proportionally softer.
     */
    fun shadowColor(alpha: Float): Color = ShadowInk.copy(alpha = (alpha / STRONGEST_SHADOW_ALPHA).coerceIn(0f, 1f))

    private val ShadowInk = Color.Black
    private const val STRONGEST_SHADOW_ALPHA = .30f
}
