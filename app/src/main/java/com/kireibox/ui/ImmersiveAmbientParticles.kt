package com.kireibox.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.ImmersiveAmbient
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Ambient particles (plan §6.1 光尘 / 樱花 / 雪, ≤ 36 per ambient). Every particle gets a depth in
// [0, 1): far motes are small, faint and slow, near ones larger, brighter and faster. Sprites are
// soft gradients built once (ImmersiveParticleSprites); a frame only moves, rotates and scales them.

/** How a particle moves and what it looks like. */
internal enum class ImmersiveParticleKind(
    val rising: Boolean,
    val petal: Boolean,
) {
    RISING_DOT(rising = true, petal = false),
    FALLING_DOT(rising = false, petal = false),
    FALLING_PETAL(rising = false, petal = true),
}

/** Sprite radius range in dp before the depth scale. */
internal data class ImmersiveParticleSize(
    val minDp: Float,
    val rangeDp: Float,
)

/** Look of one ambient: depth-field count and sizes, the few foreground motes, travel and ink. */
internal enum class ImmersiveAmbientSpec(
    val count: Int,
    val size: ImmersiveParticleSize,
    val periodSeconds: Float,
    val kind: ImmersiveParticleKind,
    val frontCount: Int,
    val alpha: Float,
) {
    DUST(count = 26, size = ImmersiveParticleSize(2.2f, 3.4f), periodSeconds = 46f, kind = ImmersiveParticleKind.RISING_DOT, frontCount = 3, alpha = .62f),
    SAKURA(count = 16, size = ImmersiveParticleSize(4.6f, 3.6f), periodSeconds = 26f, kind = ImmersiveParticleKind.FALLING_PETAL, frontCount = 2, alpha = .82f),
    SNOW(count = 28, size = ImmersiveParticleSize(2.4f, 3.6f), periodSeconds = 30f, kind = ImmersiveParticleKind.FALLING_DOT, frontCount = 3, alpha = .78f),
    ;

    val rising: Boolean get() = kind.rising

    val petal: Boolean get() = kind.petal

    val color: Color
        get() =
            when (this) {
                SAKURA -> KireiTokens.Immersive.ParticleSakura
                SNOW -> KireiTokens.Immersive.ParticleSnow
                DUST -> KireiTokens.Immersive.ParticleDust
            }

    /** Mote ink for the theme: light pages get deeper dust / snow inks that read on a pale backdrop. */
    fun ink(isDark: Boolean): Color =
        when {
            isDark -> color
            this == DUST -> KireiTokens.Immersive.ParticleDustLight
            this == SNOW -> KireiTokens.Immersive.ParticleSnowLight
            else -> color
        }

    companion object {
        fun of(ambient: ImmersiveAmbient): ImmersiveAmbientSpec? =
            when (ambient) {
                ImmersiveAmbient.DUST -> DUST
                ImmersiveAmbient.SAKURA -> SAKURA
                ImmersiveAmbient.SNOW -> SNOW
                ImmersiveAmbient.NONE -> null
            }
    }
}

/** Radius of the unit sprites; drawing scales them to each particle's size. */
private const val SPRITE_RADIUS = 64f

/**
 * Sprites of one ambient and theme, built once per (ambient, theme): a soft radial dot (center
 * opaque, fading out, so motes read as light rather than discs), a wider bokeh for foreground
 * motes and a two-tone petal. Dark pages add particles' light (BlendMode.Plus) for a glow.
 */
internal class ImmersiveParticleSprites(
    spec: ImmersiveAmbientSpec,
    isDark: Boolean,
) {
    val alphaScale: Float = if (isDark) 1f else LIGHT_ALPHA_SCALE
    val blend: BlendMode = if (isDark && !spec.petal) BlendMode.Plus else BlendMode.SrcOver
    val dot: Brush = softSprite(spec.ink(isDark), core = .30f, mid = .55f)
    val bokeh: Brush = softSprite(if (spec.petal) KireiTokens.Immersive.ParticleSakuraDeep else spec.ink(isDark), core = .05f, mid = .38f)
    val petal: Path = ImmersivePetalShape.unit
    val petalBrush: Brush =
        Brush.linearGradient(
            0f to KireiTokens.Immersive.ParticleSakuraDeep,
            1f to KireiTokens.Immersive.ParticleSakura,
            start = Offset(0f, 1f),
            end = Offset(0f, -1f),
        )

    private fun softSprite(
        color: Color,
        core: Float,
        mid: Float,
    ): Brush =
        Brush.radialGradient(
            0f to color,
            core to color.copy(alpha = .85f),
            mid to color.copy(alpha = .32f),
            1f to color.copy(alpha = 0f),
            center = Offset.Zero,
            radius = SPRITE_RADIUS,
        )

    private companion object {
        const val LIGHT_ALPHA_SCALE = .75f
    }
}

/**
 * Sakura petal in unit space (≈1.5 wide, 2 tall): a narrow base at y = 1 widening into two lobes
 * with the characteristic notch at y = -1. Shared by every petal; never rebuilt.
 */
internal object ImmersivePetalShape {
    val unit: Path by lazy {
        Path().apply {
            moveTo(0f, 1f)
            cubicTo(-.55f, .62f, -.78f, -.32f, -.40f, -.9f)
            quadraticTo(-.2f, -1.06f, 0f, -.74f)
            quadraticTo(.2f, -1.06f, .40f, -.9f)
            cubicTo(.78f, -.32f, .55f, .62f, 0f, 1f)
            close()
        }
    }
}

/** The depth field behind the cards. */
internal fun DrawScope.drawImmersiveParticleField(
    spec: ImmersiveAmbientSpec,
    sprites: ImmersiveParticleSprites,
    seconds: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    for (index in 0 until spec.count) {
        val depth = immersivePseudoUnit(index * 13 + 7)
        val period = spec.periodSeconds * (1.45f - .75f * depth) * (.85f + immersivePseudoUnit(index * 17 + 3) * .3f)
        val travel = immersiveSawtooth(seconds + immersivePseudoUnit(index * 47 + 19) * period, period)
        val progress = if (spec.rising) 1f - travel else travel
        val sway = sin(2f * PI.toFloat() * travel * 2f + index) * (.012f + .03f * depth)
        val x = size.width * fract(immersivePseudoUnit(index * 31 + 5) + sway)
        val y = size.height * (progress * 1.12f - .06f)
        val radius = (spec.size.minDp + immersivePseudoUnit(index * 23) * spec.size.rangeDp) * (.55f + .9f * depth) * density
        // Far motes are faint; a slow shimmer keeps dust alive without hard flicker.
        val shimmer = if (spec.petal) 1f else .8f + .2f * sin(seconds * 1.3f + index * 1.7f)
        val alpha = spec.alpha * (.3f + .7f * depth) * (.75f + .25f * immersivePseudoUnit(index * 29)) * shimmer * sprites.alphaScale
        if (spec.petal) {
            // |cos| narrows the petal like one turning in the air.
            val width = radius * (.35f + .65f * abs(cos(seconds * (1.1f + depth) + index)))
            drawPetal(sprites, Offset(x, y), Size(width, radius), alpha, spin = travel * 400f + index * 37f)
        } else {
            drawSoftDot(sprites.dot, sprites.blend, Offset(x, y), radius, alpha)
        }
    }
}

/** A few large, very soft out-of-focus motes in front of the cards (depth without dirt). */
internal fun DrawScope.drawImmersiveForegroundMotes(
    spec: ImmersiveAmbientSpec,
    sprites: ImmersiveParticleSprites,
    seconds: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    for (index in 0 until spec.frontCount) {
        val period = spec.periodSeconds * (.7f + immersivePseudoUnit(index * 71 + 11) * .3f)
        val travel = immersiveSawtooth(seconds + immersivePseudoUnit(index * 53 + 29) * period, period)
        val progress = if (spec.rising) 1f - travel else travel
        val sway = sin(2f * PI.toFloat() * travel + index * 2.3f) * .05f
        val x = size.width * fract(.15f + index * .31f + immersivePseudoUnit(index * 37 + 13) * .2f + sway)
        val y = size.height * (progress * 1.3f - .15f)
        val radius = (FRONT_MIN_DP + immersivePseudoUnit(index * 41 + 2) * FRONT_RANGE_DP) * density
        val alpha = FRONT_ALPHA * sprites.alphaScale * (.7f + .3f * immersivePseudoUnit(index * 59))
        drawSoftDot(sprites.bokeh, sprites.blend, Offset(x, y), radius, alpha)
    }
}

private fun DrawScope.drawSoftDot(
    sprite: Brush,
    blend: BlendMode,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    translate(center.x, center.y) {
        scale(radius / SPRITE_RADIUS, pivot = Offset.Zero) {
            drawCircle(sprite, radius = SPRITE_RADIUS, center = Offset.Zero, alpha = alpha.coerceIn(0f, 1f), blendMode = blend)
        }
    }
}

/**
 * A tumbling petal of [extent] (half width, half height) over a faint soft glow, so its edge melts
 * into the backdrop instead of reading as a cut-out sticker.
 */
private fun DrawScope.drawPetal(
    sprites: ImmersiveParticleSprites,
    center: Offset,
    extent: Size,
    alpha: Float,
    spin: Float,
) {
    drawSoftDot(sprites.bokeh, BlendMode.SrcOver, center, extent.height * PETAL_GLOW, alpha * PETAL_GLOW_ALPHA)
    translate(center.x, center.y) {
        rotate(spin, pivot = Offset.Zero) {
            scale(extent.width, extent.height, pivot = Offset.Zero) {
                drawPath(sprites.petal, sprites.petalBrush, alpha = alpha.coerceIn(0f, 1f))
            }
        }
    }
}

private fun fract(value: Float): Float = ((value % 1f) + 1f) % 1f

private const val FRONT_MIN_DP = 16f
private const val FRONT_RANGE_DP = 14f
private const val FRONT_ALPHA = .13f
private const val PETAL_GLOW = 1.7f
private const val PETAL_GLOW_ALPHA = .16f
