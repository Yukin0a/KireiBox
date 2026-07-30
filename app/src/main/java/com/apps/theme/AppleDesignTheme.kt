package com.apps.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Apple Design 色彩系统
 * 严格遵循 apple-design skill 的材质层级与深度规则。
 */
object AppleColors {
    // Light
    val LightBackground = Color(0xFFF5F4F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceAlt = Color(0xFFEDEBF2)
    val LightSurfaceHeader = Color(0xFFFAF9FC)
    val LightTextPrimary = Color(0xFF1C1C1E)
    val LightTextSecondary = Color(0xFF6E6E73)
    val LightTextTertiary = Color(0xFFAEAEB2)
    val LightPrimary = Color(0xFF5B45C7)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightLine = Color(0x1F3C3C43)
    val LightDanger = Color(0xFFFF3B30)

    // Dark
    val DarkBackground = Color(0xFF000000)
    val DarkSurface = Color(0xFF1C1C1E)
    val DarkSurfaceAlt = Color(0xFF2C2C2E)
    val DarkSurfaceHeader = Color(0xFF111113)
    val DarkTextPrimary = Color(0xFFF5F5F7)
    val DarkTextSecondary = Color(0x99EBEBF5)
    val DarkTextTertiary = Color(0x4DEBEBF5)
    val DarkPrimary = Color(0xFFB3ACFF)
    val DarkOnPrimary = Color(0xFF211E47)
    val DarkLine = Color(0x1FEBEBF5)
    val DarkDanger = Color(0xFFFF453A)

    // Glass / Blur 层级（material weight encodes hierarchy）
    val GlassLight = Color(0x99FFFFFF)      // 轻量材质：按钮、浮层
    val GlassMedium = Color(0x80FFFFFF)     // 中量材质：卡片、导航
    val GlassHeavy = Color(0x66FFFFFF)      // 重量材质：侧栏、模态
    val GlassDarkLight = Color(0xCC2C2C2E)
    val GlassDarkMedium = Color(0xB81C1C1E)
    val GlassDarkHeavy = Color(0xE61C1C1E)

    // 强调色
    val AccentBlue = Color(0xFF7CCBFF)
    val AccentOrange = Color(0xFFF4B36A)
    val AccentPink = Color(0xFFFF7BA5)
    val AccentPurple = Color(0xFFD8A9C9)

    // 排行榜
    val RankGold = Color(0xFFD6A300)
    val RankSilver = Color(0xFF8A98A3)
    val RankBronze = Color(0xFFB36B3C)
}

data class AppleColorPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHeader: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val line: Color,
    val danger: Color,
    val glassLight: Color,
    val glassMedium: Color,
    val glassHeavy: Color,
)

val LocalAppleColors = staticCompositionLocalOf {
    AppleColorPalette(
        isDark = false,
        background = AppleColors.LightBackground,
        surface = AppleColors.LightSurface,
        surfaceAlt = AppleColors.LightSurfaceAlt,
        surfaceHeader = AppleColors.LightSurfaceHeader,
        textPrimary = AppleColors.LightTextPrimary,
        textSecondary = AppleColors.LightTextSecondary,
        textTertiary = AppleColors.LightTextTertiary,
        primary = AppleColors.LightPrimary,
        onPrimary = AppleColors.LightOnPrimary,
        line = AppleColors.LightLine,
        danger = AppleColors.LightDanger,
        glassLight = AppleColors.GlassLight,
        glassMedium = AppleColors.GlassMedium,
        glassHeavy = AppleColors.GlassHeavy,
    )
}

private val LightApplePalette = AppleColorPalette(
    isDark = false,
    background = AppleColors.LightBackground,
    surface = AppleColors.LightSurface,
    surfaceAlt = AppleColors.LightSurfaceAlt,
    surfaceHeader = AppleColors.LightSurfaceHeader,
    textPrimary = AppleColors.LightTextPrimary,
    textSecondary = AppleColors.LightTextSecondary,
    textTertiary = AppleColors.LightTextTertiary,
    primary = AppleColors.LightPrimary,
    onPrimary = AppleColors.LightOnPrimary,
    line = AppleColors.LightLine,
    danger = AppleColors.LightDanger,
    glassLight = AppleColors.GlassLight,
    glassMedium = AppleColors.GlassMedium,
    glassHeavy = AppleColors.GlassHeavy,
)

private val DarkApplePalette = AppleColorPalette(
    isDark = true,
    background = AppleColors.DarkBackground,
    surface = AppleColors.DarkSurface,
    surfaceAlt = AppleColors.DarkSurfaceAlt,
    surfaceHeader = AppleColors.DarkSurfaceHeader,
    textPrimary = AppleColors.DarkTextPrimary,
    textSecondary = AppleColors.DarkTextSecondary,
    textTertiary = AppleColors.DarkTextTertiary,
    primary = AppleColors.DarkPrimary,
    onPrimary = AppleColors.DarkOnPrimary,
    line = AppleColors.DarkLine,
    danger = AppleColors.DarkDanger,
    glassLight = AppleColors.GlassDarkLight,
    glassMedium = AppleColors.GlassDarkMedium,
    glassHeavy = AppleColors.GlassDarkHeavy,
)

private val LightMaterialScheme = lightColorScheme(
    primary = AppleColors.LightPrimary,
    onPrimary = AppleColors.LightOnPrimary,
    background = AppleColors.LightBackground,
    onBackground = AppleColors.LightTextPrimary,
    surface = AppleColors.LightSurface,
    onSurface = AppleColors.LightTextPrimary,
    surfaceVariant = AppleColors.LightSurfaceAlt,
    onSurfaceVariant = AppleColors.LightTextSecondary,
    outline = AppleColors.LightLine,
    error = AppleColors.LightDanger,
    onError = AppleColors.LightOnPrimary,
)

private val DarkMaterialScheme = darkColorScheme(
    primary = AppleColors.DarkPrimary,
    onPrimary = AppleColors.DarkOnPrimary,
    background = AppleColors.DarkBackground,
    onBackground = AppleColors.DarkTextPrimary,
    surface = AppleColors.DarkSurface,
    onSurface = AppleColors.DarkTextPrimary,
    surfaceVariant = AppleColors.DarkSurfaceAlt,
    onSurfaceVariant = AppleColors.DarkTextSecondary,
    outline = AppleColors.DarkLine,
    error = AppleColors.DarkDanger,
    onError = AppleColors.DarkOnPrimary,
)

@Composable
fun AppleDesignTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkApplePalette else LightApplePalette
    val materialScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(LocalAppleColors provides palette) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AppleTypography.materialTypography,
            content = content
        )
    }
}
