package com.kireibox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.yuki.yukihub.settings.AccentChoice

/**
 * App palette passed to every page. The original eleven fields keep their names and map to the
 * graphite tokens (`surfaceRaised` = surface2, `outline` = opaque mid stroke). Fields added for
 * the token system default from [KireiTokens] so existing named-argument constructors compile.
 */
@Immutable
internal data class KireiColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val success: Color,
    val outline: Color,
    val onAccent: Color,
    val danger: Color,
    val isDark: Boolean,
    val surface3: Color = KireiTokens.neutrals(isDark).surface3,
    val surfaceOverlay: Color = KireiTokens.neutrals(isDark).surfaceOverlay,
    val stroke: Color = KireiTokens.neutrals(isDark).stroke,
    val strokeStrong: Color = KireiTokens.neutrals(isDark).strokeStrong,
    val tertiary: Color = KireiTokens.neutrals(isDark).tertiary,
    val accentContainer: Color = KireiTokens.accentContainer(accent, isDark),
    val warning: Color = KireiTokens.neutrals(isDark).warning,
    val scrim: Color = KireiTokens.neutrals(isDark).scrim,
    val glass: Color = KireiTokens.neutrals(isDark).glass,
    val highContrast: Boolean = false,
)

/** Pressed accent (spec §2.2): accent mixed 12% toward the primary text color. */
internal val KireiColors.accentStrong: Color get() = KireiTokens.accentStrong(accent, text)

/** Destructive tint for icon wells and error banners: the accentContainer alphas applied to danger. */
internal val KireiColors.dangerContainer: Color get() = KireiTokens.accentContainer(danger, isDark)

/** Ink on solid danger fills (badges, remove buttons). */
internal val KireiColors.onDanger: Color get() = KireiTokens.OnSolid

/**
 * Danger wherever it carries text (spec §2.1 amendment): destructive button fills with their ink,
 * and destructive row / menu titles. Light uses the deeper #CF2E33 (white on it 5.1:1, it on the
 * page 4.7:1); dark keeps danger with dark ink (white on #FF5A5F is only 3.05:1). Same pair as the
 * danger count badge.
 */
internal val KireiColors.dangerSolid: KireiAccentTokens get() = KireiTokens.badgeDanger(isDark)

/** Builds the app palette for one theme straight from the tokens. */
internal fun kireiColorsFromTokens(
    isDark: Boolean,
    accent: AccentChoice = AccentChoice.DEFAULT,
    highContrast: Boolean = false,
): KireiColors {
    val neutral = KireiTokens.neutrals(isDark, highContrast)
    val tone = KireiTokens.accent(accent, isDark)
    return KireiColors(
        background = neutral.background,
        surface = neutral.surface,
        surfaceRaised = neutral.surface2,
        text = neutral.text,
        secondary = neutral.secondary,
        accent = tone.accent,
        success = neutral.success,
        outline = neutral.outline,
        onAccent = tone.onAccent,
        danger = neutral.danger,
        isDark = isDark,
        surface3 = neutral.surface3,
        surfaceOverlay = neutral.surfaceOverlay,
        stroke = neutral.stroke,
        strokeStrong = neutral.strokeStrong,
        tertiary = neutral.tertiary,
        accentContainer = KireiTokens.accentContainer(tone.accent, isDark),
        warning = neutral.warning,
        scrim = neutral.scrim,
        glass = neutral.glass,
        highContrast = highContrast,
    )
}

/**
 * Dark graphite palette for chrome that always sits on the dark immersive backdrop: the dark
 * tokens on the immersive base (spec §2.4) with the default purple accent.
 */
internal fun darkKireiColors(
    accent: AccentChoice = AccentChoice.DEFAULT,
    highContrast: Boolean = false,
): KireiColors = kireiColorsFromTokens(isDark = true, accent = accent, highContrast = highContrast).copy(background = KireiTokens.Immersive.DarkBase)

/**
 * Interpolates every color field; the boolean flags switch to [stop] immediately so icon and
 * contrast decisions never lag behind a theme change.
 */
internal fun lerpKireiColors(
    start: KireiColors,
    stop: KireiColors,
    fraction: Float,
): KireiColors {
    if (fraction >= 1f || start == stop) return stop
    val t = fraction.coerceIn(0f, 1f)
    return stop.copy(
        background = lerp(start.background, stop.background, t),
        surface = lerp(start.surface, stop.surface, t),
        surfaceRaised = lerp(start.surfaceRaised, stop.surfaceRaised, t),
        text = lerp(start.text, stop.text, t),
        secondary = lerp(start.secondary, stop.secondary, t),
        accent = lerp(start.accent, stop.accent, t),
        success = lerp(start.success, stop.success, t),
        outline = lerp(start.outline, stop.outline, t),
        onAccent = lerp(start.onAccent, stop.onAccent, t),
        danger = lerp(start.danger, stop.danger, t),
        surface3 = lerp(start.surface3, stop.surface3, t),
        surfaceOverlay = lerp(start.surfaceOverlay, stop.surfaceOverlay, t),
        stroke = lerp(start.stroke, stop.stroke, t),
        strokeStrong = lerp(start.strokeStrong, stop.strokeStrong, t),
        tertiary = lerp(start.tertiary, stop.tertiary, t),
        accentContainer = lerp(start.accentContainer, stop.accentContainer, t),
        warning = lerp(start.warning, stop.warning, t),
        scrim = lerp(start.scrim, stop.scrim, t),
        glass = lerp(start.glass, stop.glass, t),
    )
}

/**
 * Animates a whole palette toward [target] (theme, accent and contrast changes). An interrupted
 * transition restarts from the currently displayed colors; [durationMillis] <= 0 snaps.
 */
@Composable
internal fun animateKireiColorsAsState(
    target: KireiColors,
    durationMillis: Int,
): KireiColors {
    val progress = remember { Animatable(1f) }
    var start by remember { mutableStateOf(target) }
    var stop by remember { mutableStateOf(target) }
    LaunchedEffect(target, durationMillis) {
        if (target == stop && progress.value >= 1f) return@LaunchedEffect
        start = if (durationMillis > 0) lerpKireiColors(start, stop, progress.value) else target
        stop = target
        if (durationMillis > 0) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis))
        } else {
            progress.snapTo(1f)
        }
    }
    return lerpKireiColors(start, stop, progress.value)
}
