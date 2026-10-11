package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/** What a [KireiStatusChip] reports: granted / signed in, needs attention, available, or off. */
internal enum class KireiStatusTone { POSITIVE, WARNING, ACCENT, NEUTRAL }

internal object KireiStatusChipDefaults {
    val Height = 24.dp
    val DotSize = 6.dp
    val HorizontalPadding = 10.dp

    /** The dot carries the tone; the label always uses the primary ink so it reads at AA. */
    fun dotColor(
        tone: KireiStatusTone,
        colors: KireiColors,
    ): Color =
        when (tone) {
            KireiStatusTone.POSITIVE -> colors.success
            KireiStatusTone.WARNING -> colors.warning
            KireiStatusTone.ACCENT -> colors.accent
            KireiStatusTone.NEUTRAL -> colors.tertiary
        }
}

/**
 * Small status capsule (账号「已登录」, 目录「已授权」): surfaceRaised fill, a colored dot for the
 * tone and a labelLarge label in the primary text color. Colored text on a tinted fill would fall
 * below AA at 12sp for success/warning, so color never carries the meaning alone.
 */
@Composable
internal fun KireiStatusChip(
    label: String,
    tone: KireiStatusTone,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.heightIn(min = KireiStatusChipDefaults.Height)
            .background(colors.surfaceRaised, KireiShapes.full)
            .padding(horizontal = KireiStatusChipDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs + KireiSpacing.xxs / 2),
    ) {
        Box(Modifier.size(KireiStatusChipDefaults.DotSize).background(KireiStatusChipDefaults.dotColor(tone, colors), KireiShapes.full))
        Text(label, color = colors.text, style = AppleTypography.labelLarge, maxLines = 1)
    }
}
