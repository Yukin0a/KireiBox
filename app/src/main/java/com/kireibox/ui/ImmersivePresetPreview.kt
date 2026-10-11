package com.kireibox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveVisualSettings

// Drawn mini immersive home for the 视觉效果 preset cards (r4 S1): the immersive base, the chosen
// backdrop (封面氛围 / 氛围色 / 封面虚化 / 流光极光 / 纯色) at its strength, the center card with its glow, rim
// light and sheen, tilted side cards for the 3D depth, the vignette and the ambient
// particles. There is no real cover here, so a sample cover is derived from the app accent's hue;
// base, glow, depth ink, vignette and particle colors are the immersive palette tokens.

private const val DARK_DEPTH_ALPHA = .40f
private const val LIGHT_DEPTH_ALPHA = .35f
private const val DARK_VIGNETTE_ALPHA = .55f
private const val LIGHT_VIGNETTE_ALPHA = .24f
private const val COMPANION_HUE_SHIFT = 48f
private const val AURORA_ACCENT_SINK = .35f

@Composable
internal fun rememberPresetPreviewPalette(colors: KireiColors): PresetPreviewPalette = remember(colors.isDark, colors.accent, colors.onAccent) { presetPreviewPalette(colors) }

/** Sample cover tones follow the accent hue (like the empty immersive stage); the rest are tokens. */
internal fun presetPreviewPalette(colors: KireiColors): PresetPreviewPalette {
    val immersive = ImmersivePaletteResolver.resolve(colors.isDark, colors.accent, colors.onAccent)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colors.accent.toArgb(), hsv)
    val hue = hsv[0]
    val companionHue = (hue + COMPANION_HUE_SHIFT) % 360f
    val dark = colors.isDark
    val tone = if (dark) Color.hsv(hue, .42f, .40f) else Color.hsv(hue, .20f, .93f)
    return PresetPreviewPalette(
        isDark = dark,
        base = immersive.base,
        solid = if (dark) KireiTokens.Immersive.SolidDark else immersive.base,
        tone = tone,
        companion = if (dark) Color.hsv(companionHue, .45f, .46f) else Color.hsv(companionHue, .22f, .92f),
        accent = if (dark) lerp(immersive.accent, immersive.base, AURORA_ACCENT_SINK) else immersive.accent,
        // Dark: the floor glow leans to the deep accent atmosphere like the real dark home (r4 · V1).
        glow = lerp(tone, if (dark) immersive.atmosphere else immersive.accentBright, ImmersiveDarkDepth.GLOW_ACCENT_MIX),
        coverTop = Color.hsv(companionHue, .30f, .90f),
        coverBottom = Color.hsv((hue + 340f) % 360f, .55f, .40f),
        depthInk = if (dark) KireiTokens.Immersive.CardDepthDark else KireiTokens.Immersive.CardDepthLight,
        vignette = if (dark) KireiTokens.Immersive.VignetteDark else KireiTokens.Immersive.VignetteLight,
        edge = immersive.glassBorder,
        immersive = immersive,
    )
}

/** Backdrop strength multiplier of the preview (低 / 中 / 高). */
internal fun presetIntensityScale(level: FxLevel): Float =
    when (level) {
        FxLevel.LOW -> .65f
        FxLevel.MEDIUM -> 1f
        FxLevel.HIGH -> 1.3f
    }

/** Side-card look for a 3D depth: size relative to the center card and how far the outer edge recedes. */
private fun sideCardScale(level: FxLevel): Float =
    when (level) {
        FxLevel.LOW -> .90f
        FxLevel.MEDIUM -> .82f
        FxLevel.HIGH -> .74f
    }

private fun sideCardRecede(level: FxLevel): Float =
    when (level) {
        FxLevel.LOW -> 0f
        FxLevel.MEDIUM -> .14f
        FxLevel.HIGH -> .24f
    }

internal fun DrawScope.drawPresetPreview(
    look: ImmersiveVisualSettings,
    palette: PresetPreviewPalette,
) {
    drawRect(presetBaseColor(look, palette))
    drawPresetBackdrop(look, palette)
    if (look.grain) drawPresetGrain(palette)
    val cardWidth = size.width * .30f
    val cardHeight = cardWidth * 1.42f
    val card = Rect(Offset((size.width - cardWidth) / 2f, size.height * .45f - cardHeight / 2f), Size(cardWidth, cardHeight))
    if (look.glow) drawPresetGlow(card, palette)
    drawPresetSideCards(card, look.cardDepth, palette)
    drawPresetCenterCard(card, look, palette)
    if (look.vignette) drawPresetVignette(palette)
    drawPresetParticles(look.ambient, palette)
}

/** 封面氛围 sits on alpha.16's own base (a blue-black / lavender-white), the others on the app's immersive base. */
private fun presetBaseColor(
    look: ImmersiveVisualSettings,
    palette: PresetPreviewPalette,
): Color =
    when (look.backdrop) {
        ImmersiveBackdropStyle.SOLID -> palette.solid
        ImmersiveBackdropStyle.COVER_ZOOM -> ImmersiveCoverAtmosphere.tokens(palette.isDark).base
        else -> palette.base
    }

private fun DrawScope.drawPresetBackdrop(
    look: ImmersiveVisualSettings,
    palette: PresetPreviewPalette,
) {
    val k = presetIntensityScale(look.backdropIntensity)
    val reach = maxOf(size.width, size.height)
    when (look.backdrop) {
        ImmersiveBackdropStyle.COVER_ZOOM -> drawPresetCoverAtmosphere(look.backdropIntensity, palette)
        ImmersiveBackdropStyle.TONE ->
            drawRect(Brush.radialGradient(listOf(palette.tone.copy(alpha = (.9f * k).coerceAtMost(1f)), palette.tone.copy(alpha = 0f)), Offset(size.width * .5f, size.height * .35f), reach * .8f))
        ImmersiveBackdropStyle.BLUR_COVER -> {
            drawRect(Brush.verticalGradient(listOf(palette.coverTop.copy(alpha = (.5f * k).coerceAtMost(1f)), palette.coverBottom.copy(alpha = (.5f * k).coerceAtMost(1f)))))
            // The same base veil the dark immersive home lays over its blurred cover (spec §10 r4).
            drawRect(palette.base, alpha = if (palette.isDark) .45f else .38f)
        }
        ImmersiveBackdropStyle.AURORA -> {
            drawAuroraBlob(palette.accent, Offset(size.width * .22f, size.height * .30f), reach * .55f, .42f * k)
            drawAuroraBlob(palette.companion, Offset(size.width * .82f, size.height * .22f), reach * .50f, .40f * k)
            drawAuroraBlob(palette.tone, Offset(size.width * .55f, size.height * .95f), reach * .65f, .55f * k)
        }
        ImmersiveBackdropStyle.SOLID -> Unit
    }
}

/**
 * 封面氛围: alpha.16's layer stack in miniature (ImmersiveCoverAtmosphere): the sample cover at its
 * faint alpha, the cover-tone blend radial, the horizontal scrim and the wide cover-tone radial, with
 * the real alphas and radii relative to the screen's long side.
 */
private fun DrawScope.drawPresetCoverAtmosphere(
    level: FxLevel,
    palette: PresetPreviewPalette,
) {
    val tokens = ImmersiveCoverAtmosphere.tokens(palette.isDark)
    val k = level.backdropIntensityScale()
    val reach = maxOf(size.width, size.height)
    drawRect(Brush.verticalGradient(listOf(palette.coverTop, palette.coverBottom)), alpha = IMMERSIVE_BACKDROP_BITMAP_ALPHA * k)
    drawRect(Brush.radialGradient(listOf(palette.tone.copy(alpha = tokens.blendAlpha * k), Color.Transparent), radius = reach * PREVIEW_BLEND_RADIUS))
    drawRect(Brush.horizontalGradient(listOf(tokens.scrimStart, tokens.scrimMid, Color.Transparent)))
    drawRect(Brush.radialGradient(listOf(palette.tone.copy(alpha = tokens.radialAlpha * k), Color.Transparent), radius = reach * PREVIEW_RADIAL_RADIUS))
}

/** The 900 / 1500 design-unit radii of the real backdrop on a 1846-unit-tall portrait screen. */
private const val PREVIEW_BLEND_RADIUS = .49f
private const val PREVIEW_RADIAL_RADIUS = .81f

private fun DrawScope.drawAuroraBlob(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceAtMost(1f)), color.copy(alpha = 0f)), center, radius), radius, center)
}

/** Film grain: a fixed scatter of faint specks (deterministic, so the preview never flickers). */
private fun DrawScope.drawPresetGrain(palette: PresetPreviewPalette) {
    val ink = if (palette.isDark) KireiTokens.OnSolid else KireiTokens.Immersive.VignetteLight
    val speck = .6.dp.toPx()
    var seed = GRAIN_SEED
    repeat(GRAIN_SPECKS) {
        seed = seed * 1_103_515_245 + 12_345
        val x = ((seed ushr 8) and 0xFFFF) / 65_535f
        seed = seed * 1_103_515_245 + 12_345
        val y = ((seed ushr 8) and 0xFFFF) / 65_535f
        drawCircle(ink, speck, Offset(x * size.width, y * size.height), alpha = .16f)
    }
}

private const val GRAIN_SEED = 7
private const val GRAIN_SPECKS = 90

private fun DrawScope.drawPresetGlow(
    card: Rect,
    palette: PresetPreviewPalette,
) {
    val radius = card.width * 1.15f
    val center = Offset(card.center.x, card.bottom)
    // Light previews need a clearly visible glow, or 简约 and 标准 look alike at thumbnail size.
    val alpha = if (palette.isDark) .62f else .50f
    drawOval(
        Brush.radialGradient(listOf(palette.glow.copy(alpha = alpha), palette.glow.copy(alpha = 0f)), center, radius),
        topLeft = Offset(center.x - radius, center.y - card.height * .22f),
        size = Size(radius * 2f, card.height * .55f),
    )
}

/** Side cards: smaller, set back and (with more 3D depth) receding toward their outer edge. */
private fun DrawScope.drawPresetSideCards(
    card: Rect,
    depth: FxLevel,
    palette: PresetPreviewPalette,
) {
    val scale = sideCardScale(depth)
    val recede = sideCardRecede(depth)
    val width = card.width * scale
    val height = card.height * scale
    val gap = card.width * .08f
    val brush = Brush.verticalGradient(listOf(palette.coverTop, palette.coverBottom), card.center.y - height / 2f, card.center.y + height / 2f)
    listOf(-1f, 1f).forEach { side ->
        val inner = if (side < 0f) card.left - gap else card.right + gap
        val outer = inner + side * width
        val top = card.center.y - height / 2f
        val bottom = card.center.y + height / 2f
        val inset = height * recede / 2f
        val path =
            Path().apply {
                moveTo(inner, top)
                lineTo(outer, top + inset)
                lineTo(outer, bottom - inset)
                lineTo(inner, bottom)
                close()
            }
        drawPath(path, brush)
        drawPath(path, palette.depthInk, alpha = if (palette.isDark) DARK_DEPTH_ALPHA else LIGHT_DEPTH_ALPHA)
    }
}

private fun DrawScope.drawPresetCenterCard(
    card: Rect,
    look: ImmersiveVisualSettings,
    palette: PresetPreviewPalette,
) {
    val radius = CornerRadius(card.width * .10f)
    drawRoundRect(Brush.verticalGradient(listOf(palette.coverTop, palette.coverBottom), card.top, card.bottom), card.topLeft, card.size, radius)
    // Title area: the always-dark local gradient and two title bars, like a real cover card.
    drawRoundRect(
        Brush.verticalGradient(
            listOf(KireiTokens.Immersive.CoverOverlayBottom.copy(alpha = 0f), KireiTokens.Immersive.CoverOverlayBottom.copy(alpha = .7f)),
            card.top + card.height * .55f,
            card.bottom
        ),
        card.topLeft,
        card.size,
        radius,
    )
    val barHeight = maxOf(card.height * .045f, 1.5.dp.toPx())
    drawRoundRect(KireiTokens.OnSolid, Offset(card.left + card.width * .12f, card.bottom - card.height * .20f), Size(card.width * .62f, barHeight), CornerRadius(barHeight / 2f), alpha = .9f)
    drawRoundRect(KireiTokens.OnSolid, Offset(card.left + card.width * .12f, card.bottom - card.height * .12f), Size(card.width * .40f, barHeight * .8f), CornerRadius(barHeight / 2f), alpha = .6f)
    if (look.sheen) {
        drawRoundRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                .42f to Color.Transparent,
                .5f to KireiTokens.OnSolid.copy(alpha = .38f),
                .58f to Color.Transparent,
                1f to Color.Transparent,
                start = card.topLeft,
                end = card.bottomRight,
            ),
            card.topLeft,
            card.size,
            radius,
        )
    }
    val stroke = if (look.rimLight) Stroke(1.5.dp.toPx()) else Stroke(.8.dp.toPx())
    drawRoundRect(if (look.rimLight) palette.glow else palette.edge, card.topLeft, card.size, radius, style = stroke, alpha = if (look.rimLight) 1f else .7f)
}

private fun DrawScope.drawPresetVignette(palette: PresetPreviewPalette) {
    val alpha = if (palette.isDark) DARK_VIGNETTE_ALPHA else LIGHT_VIGNETTE_ALPHA
    drawRect(Brush.radialGradient(listOf(palette.vignette.copy(alpha = 0f), palette.vignette.copy(alpha = alpha)), center, maxOf(size.width, size.height) * .72f))
}

/** Particle spots as (x, y) fractions and a radius in dp; drawn in the ambient's token color. */
private val PresetParticleSpots =
    listOf(
        floatArrayOf(.10f, .16f, 1.4f),
        floatArrayOf(.22f, .58f, 1.0f),
        floatArrayOf(.30f, .30f, 1.8f),
        floatArrayOf(.70f, .12f, 1.2f),
        floatArrayOf(.78f, .46f, 1.6f),
        floatArrayOf(.88f, .74f, 1.1f),
        floatArrayOf(.14f, .84f, 1.5f),
        floatArrayOf(.60f, .86f, 1.0f),
        floatArrayOf(.93f, .30f, 1.3f),
        floatArrayOf(.44f, .08f, 1.1f),
    )

private fun DrawScope.drawPresetParticles(
    ambient: ImmersiveAmbient,
    palette: PresetPreviewPalette,
) {
    val color =
        when (ambient) {
            ImmersiveAmbient.NONE -> return
            ImmersiveAmbient.DUST -> if (palette.isDark) KireiTokens.Immersive.ParticleDust else KireiTokens.Immersive.ParticleDustLight
            ImmersiveAmbient.SAKURA -> KireiTokens.Immersive.ParticleSakura
            ImmersiveAmbient.SNOW -> if (palette.isDark) KireiTokens.Immersive.ParticleSnow else KireiTokens.Immersive.ParticleSnowLight
        }
    PresetParticleSpots.forEach { (x, y, r) ->
        val center = Offset(x * size.width, y * size.height)
        val radius = r.dp.toPx()
        if (ambient == ImmersiveAmbient.SAKURA) {
            drawOval(color, Offset(center.x - radius * 1.4f, center.y - radius * .8f), Size(radius * 2.8f, radius * 1.6f), alpha = .9f)
        } else {
            drawCircle(color, radius, center, alpha = .85f)
        }
    }
}
