package com.kireibox.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Apple Design 字体系统
 * 严格遵循 apple-design skill 的 tracking / leading 规范：
 * - 大字号用负字距（tighten headings）
 * - 小字号用微正字距（body near 0，caption slightly positive）
 * - 行高随字号反向变化（tight on large，loose on body）
 *
 * alpha.17 校准（doc/2026-10-02-ui-alpha17-design-spec.md §6）：系统字体；displayLarge 34/41 Bold 大标题，
 * headlineMedium 18 SemiBold 子页/Sheet 标题，bodyLarge 16 Medium 列表行标题，bodySmall 13 副标题/脚注，
 * labelLarge 12 Medium +0.02em 组标题；numeric* 为等宽数字（tnum），用于时长、计数、版本号。
 */
object AppleTypography {
    /** OpenType feature for fixed-width digits. */
    const val TABULAR_FIGURES = "tnum"

    // Display / Large Title
    val displayLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 34.sp,
            // tight leading
            lineHeight = 41.sp,
            // negative tracking
            letterSpacing = (-0.02).sp
        )

    val displayMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = (-0.015).sp
        )

    val displaySmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.01).sp
        )

    // Headline
    val headlineLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.005).sp
        )

    val headlineMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        )

    val headlineSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )

    // Body
    val bodyLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            // list-row titles (spec §6)
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            // comfortable leading
            lineHeight = 24.sp,
            letterSpacing = 0.005.sp
        )

    val bodyMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.005.sp
        )

    val bodySmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.005.sp
        )

    // Label / Caption
    val labelLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            // group captions: uppercase tracking +0.02em (spec §6)
            letterSpacing = 0.02.em
        )

    val labelMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.01.sp
        )

    val labelSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            // slightly positive for small text
            letterSpacing = 0.015.sp
        )

    // Numeric（tabular figures：时长、计数、版本号不跳动）
    val numericLarge =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontFeatureSettings = TABULAR_FIGURES
        )

    val numericMedium =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontFeatureSettings = TABULAR_FIGURES
        )

    val numericSmall =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = TABULAR_FIGURES
        )

    // Emphasis variants（weight 建立层级，而非单纯字号）
    val bodyLargeEmphasis = bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val bodyMediumEmphasis = bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    val bodySmallEmphasis = bodySmall.copy(fontWeight = FontWeight.SemiBold)

    val materialTypography =
        Typography(
            displayLarge = displayLarge,
            displayMedium = displayMedium,
            displaySmall = displaySmall,
            headlineLarge = headlineLarge,
            headlineMedium = headlineMedium,
            headlineSmall = headlineSmall,
            bodyLarge = bodyLarge,
            bodyMedium = bodyMedium,
            bodySmall = bodySmall,
            labelLarge = labelLarge,
            labelMedium = labelMedium,
            labelSmall = labelSmall,
        )
}
