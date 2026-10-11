package com.kireibox.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.settings.ImmersiveAmbient
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

// Immersive effect primitives (plan §6): the frame clock, grain texture, aurora, particles,
// vignette and sheen. Everything here draws without allocating per frame; brushes that depend on
// size or color are built in drawWithCache blocks by the callers.

/**
 * Frame clock for decorative immersive motion. Read [seconds] only inside draw lambdas: a tick then
 * re-runs draws, never compositions. It only ticks while running and the host is RESUMED.
 */
@Stable
internal class ImmersiveMotionClock {
    var seconds by mutableFloatStateOf(IMMERSIVE_IDLE_SECONDS)
        internal set
}

/** Phase shown while motion is off or frozen: mid-cycle, so Ken Burns and aurora rest centered. */
internal const val IMMERSIVE_IDLE_SECONDS = 6f
private const val CLOCK_WRAP_SECONDS = 3_600f
internal const val KEN_BURNS_PERIOD_SECONDS = 24f

internal val LocalImmersiveMotionClock = staticCompositionLocalOf { ImmersiveMotionClock() }

@Composable
internal fun rememberImmersiveMotionClock(running: Boolean): ImmersiveMotionClock {
    val clock = remember { ImmersiveMotionClock() }
    val lifecycleOwner = LocalLifecycleOwner.current
    if (running) {
        LaunchedEffect(clock, lifecycleOwner) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val resumeAt = clock.seconds
                val start = withFrameNanos { it }
                while (isActive) {
                    withFrameNanos { nanos -> clock.seconds = (resumeAt + (nanos - start) / 1e9f) % CLOCK_WRAP_SECONDS }
                }
            }
        }
    }
    return clock
}

/** 0 → 1 → 0 over [periodSeconds]. */
internal fun immersiveTriangleWave(
    seconds: Float,
    periodSeconds: Float,
): Float {
    val cycle = immersiveSawtooth(seconds, periodSeconds)
    return if (cycle <= .5f) cycle * 2f else 2f - cycle * 2f
}

/** Monotonic 0 → 1 ramp over [periodSeconds] that wraps, for one-directional particle travel. */
internal fun immersiveSawtooth(
    seconds: Float,
    periodSeconds: Float,
): Float {
    if (!seconds.isFinite() || periodSeconds <= 0f) return 0f
    val cycle = (seconds / periodSeconds) % 1f
    return if (cycle < 0f) cycle + 1f else cycle
}

/** Ken Burns zoom: 1.05 ↔ 1.12 over 24 s (plan §6.1). */
internal fun immersiveKenBurnsScale(seconds: Float): Float = 1.05f + .07f * immersiveTriangleWave(seconds, KEN_BURNS_PERIOD_SECONDS)

/** Deterministic integer hash → [0, 1); stable across frames and allocation free. */
internal fun immersivePseudoUnit(value: Int): Float {
    var x = value * 0x45d9f3b
    x = (x xor (x ushr 16)) * 0x45d9f3b
    x = x xor (x ushr 16)
    return (x and 0x7fffffff) / 2_147_483_648f
}

/** 128px film-grain tile generated once at runtime (no APK asset) and tiled with an ImageShader. */
internal object ImmersiveGrainTexture {
    private const val SIZE = 128
    private const val MAX_ALPHA = 120

    val image: ImageBitmap by lazy {
        val light = KireiTokens.OnImage.ink.toArgb() and 0x00FFFFFF
        val dark = KireiTokens.Immersive.VignetteDark.toArgb() and 0x00FFFFFF
        val pixels =
            IntArray(SIZE * SIZE) { index ->
                val rgb = if (immersivePseudoUnit(index * 5 + 7) > .5f) light else dark
                val alpha = (immersivePseudoUnit(index * 3 + 11) * MAX_ALPHA).toInt()
                (alpha shl 24) or rgb
            }
        Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** One shared tiling brush: the shader is created once, never per frame. */
    val brush: Brush by lazy { ShaderBrush(ImageShader(image, TileMode.Repeated, TileMode.Repeated)) }
}

/** Grain alpha: subtle on light pages, a little stronger on dark ones (plan §6.1: 4–6%). */
internal fun immersiveGrainAlpha(
    isDark: Boolean,
    intensity: Float,
): Float = (if (isDark) .055f else .035f) * intensity

/**
 * Vignette strength: a real darkening on dark pages (r4: .24 at 中 — the near-black base already
 * carries the depth, a deeper ring only crushed the corners below alpha.17); on light pages only a
 * breath (≤ ~.07) so the corners of the light home never turn into a dim grey room.
 */
internal fun immersiveVignetteAlpha(
    isDark: Boolean,
    increasedContrast: Boolean,
    intensity: Float,
): Float = (if (increasedContrast) .38f else .28f) * intensity * (if (isDark) DARK_VIGNETTE_SCALE else LIGHT_VIGNETTE_SCALE)

private const val DARK_VIGNETTE_SCALE = .85f
private const val LIGHT_VIGNETTE_SCALE = .2f

/** Vignette brush (radial, transparent center); built once per size in drawWithCache. */
internal fun immersiveVignetteBrush(
    size: Size,
    isDark: Boolean,
    alpha: Float,
): Brush {
    val ink = if (isDark) KireiTokens.Immersive.VignetteDark else KireiTokens.Immersive.VignetteLight
    return Brush.radialGradient(
        colors = listOf(Color.Transparent, ink.copy(alpha = alpha.coerceIn(0f, .72f))),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = maxOf(size.width, size.height) * .76f,
    )
}

/** Three slow aurora blobs (plan §6.1); centers drift by translation so the brushes stay cached. */
internal class ImmersiveAuroraBrushes(
    size: Size,
    colors: List<Color>,
    intensity: Float,
) {
    val blobs: List<Brush> =
        colors.take(AURORA_ANCHORS.size).mapIndexed { index, color ->
            val anchor = AURORA_ANCHORS[index]
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = (AURORA_ALPHAS[index] * intensity).coerceIn(0f, .6f)), Color.Transparent),
                center = Offset(size.width * anchor.x, size.height * anchor.y),
                radius = maxOf(size.width, size.height) * .62f,
            )
        }

    private companion object {
        val AURORA_ANCHORS = listOf(Offset(.22f, .24f), Offset(.82f, .38f), Offset(.46f, .84f))
        val AURORA_ALPHAS = floatArrayOf(.34f, .24f, .20f)
    }
}

private val AuroraPeriods = floatArrayOf(22f, 27f, 31f)

internal fun DrawScope.drawImmersiveAurora(
    aurora: ImmersiveAuroraBrushes,
    seconds: Float,
    alpha: Float = 1f,
) {
    if (alpha <= 0f) return
    val blobs = aurora.blobs
    for (index in blobs.indices) {
        val angle = 2f * PI.toFloat() * immersiveSawtooth(seconds, AuroraPeriods[index]) + index * 2.1f
        val dx = sin(angle) * size.width * .08f
        val dy = sin(angle * .5f + 1.3f) * size.height * .05f
        translate(left = dx, top = dy) {
            drawRect(blobs[index], topLeft = Offset(-size.width * .1f, -size.height * .1f), size = Size(size.width * 1.2f, size.height * 1.2f), alpha = alpha)
        }
    }
}

/**
 * Diagonal highlight band centered on x = 0, built once per card size and tint; the sweep moves
 * it with a translation, so the 300 ms animation allocates nothing per frame.
 */
internal fun immersiveSheenBrush(
    size: Size,
    tint: Color,
    alpha: Float,
): Brush {
    val band = size.width * SHEEN_BAND_FRACTION
    return Brush.linearGradient(
        colors = listOf(Color.Transparent, KireiTokens.OnImage.ink.copy(alpha = alpha), tint.copy(alpha = alpha * .35f), Color.Transparent),
        start = Offset(-band, size.height),
        end = Offset(band, 0f),
    )
}

/** One-shot diagonal highlight swept across the focused card after it settles. */
internal fun DrawScope.drawImmersiveCardSheen(
    progress: Float,
    brush: Brush,
) {
    val t = progress.takeIf(Float::isFinite) ?: 0f
    val sweeping = size.width > 0f && size.height > 0f && t > 0f && t < 1f
    if (!sweeping) return
    val centerX = size.width * (-.35f + t * 1.7f)
    translate(left = centerX) {
        drawRect(brush = brush, topLeft = Offset(-centerX, 0f), size = size)
    }
}

private const val SHEEN_BAND_FRACTION = .22f

/**
 * Immersive title shadow (plan §6.1 大标题加文字阴影 in every preset): a soft shadow lifts titles and
 * the wordmark off busy artwork; increased contrast (plan §6.4) makes it stronger.
 */
internal fun TextStyle.withImmersiveTitleShadow(
    increasedContrast: Boolean,
    isDark: Boolean,
): TextStyle {
    val ink = if (isDark) KireiTokens.Immersive.VignetteDark else KireiTokens.Immersive.LightBase
    return if (increasedContrast) {
        copy(shadow = Shadow(color = ink.copy(alpha = .72f), offset = Offset(0f, 2f), blurRadius = 10f))
    } else {
        copy(shadow = Shadow(color = ink.copy(alpha = .45f), offset = Offset(0f, 1f), blurRadius = 8f))
    }
}

/**
 * Ambient particles (plan §6.3): two light canvases composed only while an ambient is on (reduced
 * motion resolves the ambient to NONE). The depth field draws above the backdrop but behind the
 * cards, so motes never sit on cover art; a few large, soft out-of-focus motes float in front for
 * depth. Positions come from a monotonic sawtooth of the shared clock (snow and petals only fall,
 * dust only rises) and drawing allocates nothing per frame.
 */
@Composable
internal fun ImmersiveAmbientLayer(
    ambient: ImmersiveAmbient,
    isDark: Boolean,
    clock: ImmersiveMotionClock,
) {
    val spec = ImmersiveAmbientSpec.of(ambient) ?: return
    val sprites = remember(spec, isDark) { ImmersiveParticleSprites(spec, isDark) }
    Spacer(Modifier.fillMaxSize().zIndex(0f).drawBehind { drawImmersiveParticleField(spec, sprites, clock.seconds) })
    Spacer(Modifier.fillMaxSize().zIndex(.5f).drawBehind { drawImmersiveForegroundMotes(spec, sprites, clock.seconds) })
}
