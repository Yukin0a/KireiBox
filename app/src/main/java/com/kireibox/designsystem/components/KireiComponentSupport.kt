package com.kireibox.designsystem.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.yuki.yukihub.settings.MotionMode

/*
 * Shared plumbing for the pure-appearance Kirei components (doc/2026-10-02-ui-alpha17-design-spec.md
 * §5 elevation, §7 motion). Components read colors from the KireiColors they are handed and only
 * use the tokens in com.kireibox.designsystem; nothing here knows about view models or pages.
 */

/** True when the resolved motion mode is REDUCED: selection moves snap and shimmers stay static (spec §7). */
@Composable
@ReadOnlyComposable
internal fun kireiReducedMotion(): Boolean = LocalResolvedMotionMode.current == MotionMode.REDUCED

/** Selection/indicator spring (Spring.Emphasized), or an instant snap under reduced motion. */
internal fun kireiSelectionSpec(reducedMotion: Boolean): AnimationSpec<Float> = if (reducedMotion) snap() else AppleMotion.Spring.Emphasized

/** Short color/opacity transition (spec §7 Duration.SHORT); 0ms under reduced motion. */
internal fun <T> kireiFadeSpec(reducedMotion: Boolean): AnimationSpec<T> = tween(if (reducedMotion) 0 else AppleMotion.Duration.SHORT)

/** 0 → 1 press progress for [interaction], driven by Spring.Press (spec §7); instant under reduced motion. */
@Composable
internal fun rememberPressProgress(interaction: InteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = kireiReducedMotion()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else AppleMotion.Spring.Press,
        label = "kireiPress",
    )
    return progress
}

/** Scale applied while pressed (spec §7: 0.97 at full press). */
internal fun kireiPressScale(progress: Float): Float = 1f - PRESS_SCALE_DEPTH * progress

private const val PRESS_SCALE_DEPTH = .03f

/** Opacity of disabled controls. */
internal const val KIREI_DISABLED_ALPHA = .4f

/** Elevation shadow with the spec alpha for the current theme (spec §5). */
internal fun Modifier.kireiShadow(
    elevation: Dp,
    shape: Shape,
    alpha: Float,
): Modifier {
    val ink: Color = KireiElevation.shadowColor(alpha)
    return shadow(elevation, shape, clip = false, ambientColor = ink, spotColor = ink)
}

/** e2: floating cards and covers. */
internal fun Modifier.kireiE2Shadow(
    shape: Shape,
    isDark: Boolean,
): Modifier = kireiShadow(KireiElevation.e2Shadow, shape, KireiElevation.e2ShadowAlpha(isDark))

/** e3: dock, sheets, dialogs, menus and toasts. */
internal fun Modifier.kireiE3Shadow(
    shape: Shape,
    isDark: Boolean,
): Modifier = kireiShadow(KireiElevation.e3Shadow, shape, KireiElevation.e3ShadowAlpha(isDark))
