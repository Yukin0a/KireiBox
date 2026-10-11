package com.kireibox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.yuki.yukihub.settings.ImmersiveBackdropStyle

/**
 * 封面氛围 (COVER_ZOOM, stored value cover_zoom, the 标准 default): the alpha.16 immersive backdrop,
 * layer for layer (user decision 2026-10-05, supersedes the r5 enlarged cover). One draw pass: the
 * page base, the raw cover centre-cropped at [IMMERSIVE_BACKDROP_BITMAP_ALPHA], the cover-tone blend
 * radial, the horizontal scrim, the cover-tone radial and the oval glow under the centre card.
 *
 * The tokens below are alpha.16's own palette values (a blue-black dark base, a lavender-white light
 * base), not the accent-hued r4 palette, and the cover tone is used raw: no accent hint, pastel lift,
 * atmosphere mix, soft blurred copy, zoom, ink, veil or dark-depth rescaling. Only the 背景强度 level
 * still scales the layer alphas (中 = 1, so the default is exactly alpha.16). Reduced transparency
 * resolves to 氛围色 without a cover (resolveImmersiveFx).
 */
internal object ImmersiveCoverAtmosphere {
    data class Tokens(
        val base: Color,
        val scrimStart: Color,
        val scrimMid: Color,
        val blendAlpha: Float,
        val radialAlpha: Float,
        val radialReducedAlpha: Float,
        val glowAlpha: Float,
        val glowReducedAlpha: Float,
    )

    val Dark =
        Tokens(
            base = Color(0xFF05070D),
            scrimStart = Color(0xF705070D),
            scrimMid = Color(0xB805070D),
            blendAlpha = .38f,
            radialAlpha = .28f,
            radialReducedAlpha = .20f,
            glowAlpha = .22f,
            glowReducedAlpha = .16f,
        )

    val Light =
        Tokens(
            base = Color(0xFFF4F2F9),
            scrimStart = Color(0xF7F6F3FC),
            scrimMid = Color(0xB8F6F3FC),
            blendAlpha = .26f,
            radialAlpha = .18f,
            radialReducedAlpha = .12f,
            glowAlpha = .14f,
            glowReducedAlpha = .10f,
        )

    fun tokens(isDark: Boolean): Tokens = if (isDark) Dark else Light

    /** Radii of the blend radial and of the wide radial, times the layout scale. */
    const val BLEND_RADIUS = 900f
    const val RADIAL_RADIUS = 1500f

    /** Glow oval, as fractions of the centre card: width, height, left overhang, top offset and radius (of the larger side). */
    const val GLOW_WIDTH = 2.24f
    const val GLOW_HEIGHT = .34f
    const val GLOW_LEFT = .62f
    const val GLOW_TOP = .78f
    const val GLOW_RADIUS = .50f
}

/** The theme palette with alpha.16's backdrop tokens; everything else (ink, glass, accent) stays the app's. */
internal fun ImmersivePalette.withCoverAtmosphere(): ImmersivePalette {
    val tokens = ImmersiveCoverAtmosphere.tokens(isDark)
    return copy(
        base = tokens.base,
        scrimStart = tokens.scrimStart,
        scrimMid = tokens.scrimMid,
        blendAlpha = tokens.blendAlpha,
        radialAlpha = tokens.radialAlpha,
        radialReducedAlpha = tokens.radialReducedAlpha,
        glowAlpha = tokens.glowAlpha,
        glowReducedAlpha = tokens.glowReducedAlpha,
    )
}

/** The cover tone exactly as alpha.16 drew it. */
internal fun immersiveCoverAtmosphereTint(tone: CoverTone): Color = Color(tone.argb)

/**
 * alpha.16's glow: an oval under the centre card with a radial gradient of [radius], drawn with
 * drawOval, so the gradient is centred on the whole canvas and the oval clips it (the soft horizon
 * under the cards of the alpha.16 screenshots).
 */
internal class ImmersiveCoverAtmosphereGlow(
    center: HomeRect,
) {
    val size = Size(center.w * ImmersiveCoverAtmosphere.GLOW_WIDTH, center.h * ImmersiveCoverAtmosphere.GLOW_HEIGHT)
    val topLeft = Offset(center.x - center.w * ImmersiveCoverAtmosphere.GLOW_LEFT, center.y + center.h * ImmersiveCoverAtmosphere.GLOW_TOP)
    val radius = maxOf(size.width, size.height) * ImmersiveCoverAtmosphere.GLOW_RADIUS
}

/**
 * Without a hero (an empty gallery) there is no cover to show: 封面氛围 falls back to the tone
 * backdrop, whose dark page is alpha.17's accent stage under the empty-state content (r4).
 */
internal fun ResolvedImmersiveFx.forHero(hasHero: Boolean): ResolvedImmersiveFx = if (!hasHero && backdrop == ImmersiveBackdropStyle.COVER_ZOOM) copy(backdrop = ImmersiveBackdropStyle.TONE) else this
