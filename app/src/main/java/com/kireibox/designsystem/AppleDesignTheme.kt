package com.kireibox.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.yuki.yukihub.settings.AccentChoice

private const val GLASS_LIGHT_SCALE = .85f
private const val GLASS_HEAVY_SCALE = 1.3f

private fun glassLevel(
    glass: Color,
    scale: Float,
): Color = glass.copy(alpha = (glass.alpha * scale).coerceIn(0f, 1f))

/**
 * Resolved theme palette exposed through [LocalAppleColors]. The legacy fields keep their names;
 * the tokenized fields mirror spec §2 one to one.
 */
@Immutable
data class AppleColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    /** surface2 (`KireiColors.surfaceRaised`). */
    val surfaceAlt: Color,
    val surfaceHeader: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    /** Hairline stroke. */
    val line: Color,
    val danger: Color,
    val glassLight: Color,
    val glassMedium: Color,
    val glassHeavy: Color,
    val surface3: Color,
    val surfaceOverlay: Color,
    val strokeStrong: Color,
    val outline: Color,
    /** accent α .16 (dark) / .12 (light). */
    val primaryContainer: Color,
    val success: Color,
    val warning: Color,
    val scrim: Color,
    val glass: Color,
    val accentChoice: AccentChoice,
    val highContrast: Boolean,
) {
    val stroke: Color get() = line

    companion object {
        fun resolve(
            isDark: Boolean,
            accent: AccentChoice = AccentChoice.DEFAULT,
            highContrast: Boolean = false,
        ): AppleColorPalette {
            val neutral = KireiTokens.neutrals(isDark, highContrast)
            val tone = KireiTokens.accent(accent, isDark)
            return AppleColorPalette(
                isDark = isDark,
                background = neutral.background,
                surface = neutral.surface,
                surfaceAlt = neutral.surface2,
                surfaceHeader = neutral.background,
                textPrimary = neutral.text,
                textSecondary = neutral.secondary,
                textTertiary = neutral.tertiary,
                primary = tone.accent,
                onPrimary = tone.onAccent,
                line = neutral.stroke,
                danger = neutral.danger,
                glassLight = glassLevel(neutral.glass, GLASS_LIGHT_SCALE),
                glassMedium = neutral.glass,
                glassHeavy = glassLevel(neutral.glass, GLASS_HEAVY_SCALE),
                surface3 = neutral.surface3,
                surfaceOverlay = neutral.surfaceOverlay,
                strokeStrong = neutral.strokeStrong,
                outline = neutral.outline,
                primaryContainer = KireiTokens.accentContainer(tone.accent, isDark),
                success = neutral.success,
                warning = neutral.warning,
                scrim = neutral.scrim,
                glass = neutral.glass,
                accentChoice = accent,
                highContrast = highContrast,
            )
        }
    }
}

val LocalAppleColors = staticCompositionLocalOf { AppleColorPalette.resolve(isDark = false) }

/**
 * Material3 scheme derived from the tokens so stock M3 components inherit graphite + accent.
 * Container roles are composited over the surface because M3 expects opaque containers, and the
 * surface tint is neutralized so tonal elevation never tints surfaces with the accent.
 */
fun AppleColorPalette.toMaterialColorScheme(): ColorScheme {
    val accentContainer = primaryContainer.compositeOver(surface)
    val errorContainer = KireiTokens.accentContainer(danger, isDark).compositeOver(surface)
    val inverseAccent = KireiTokens.accent(accentChoice, !isDark).accent
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = accentContainer,
        onPrimaryContainer = primary,
        inversePrimary = inverseAccent,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = accentContainer,
        onSecondaryContainer = primary,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = accentContainer,
        onTertiaryContainer = primary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textSecondary,
        surfaceTint = surface,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = danger,
        onError = KireiTokens.OnSolid,
        errorContainer = errorContainer,
        onErrorContainer = danger,
        outline = outline,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = surface3,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceOverlay,
        surfaceContainer = surfaceOverlay,
        surfaceContainerHigh = surfaceOverlay,
        surfaceContainerHighest = surface3,
    )
}

/**
 * KireiBox theme root. [accent] and [highContrast] select the token set; the palette is exposed
 * through [LocalAppleColors] and mirrored into Material3 colors, typography and shapes.
 */
@Composable
fun AppleDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentChoice = AccentChoice.DEFAULT,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = remember(darkTheme, accent, highContrast) { AppleColorPalette.resolve(darkTheme, accent, highContrast) }
    val materialScheme = remember(palette) { palette.toMaterialColorScheme() }

    CompositionLocalProvider(LocalAppleColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AppleTypography.materialTypography,
            shapes = KireiShapes.material,
            content = content,
        )
    }
}
