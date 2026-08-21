package com.kireibox.ui

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apps.theme.AppleMotion
import com.apps.theme.AppleTypography
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

internal data class DockHazeConfiguration(
    val useOpaqueFallback: Boolean,
    val inputScale: HazeInputScale?,
    val backgroundColor: Color,
    val tint: Color?,
)

internal object DockHazePolicy {
    const val FIXED_INPUT_SCALE = .5f

    fun useOpaqueFallback(
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Boolean = reducedTransparency || !hazeAvailable || !apiSupportsHaze

    fun inputScale(
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): HazeInputScale? = if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
        null
    } else {
        HazeInputScale.Fixed(FIXED_INPUT_SCALE)
    }

    fun backgroundColor(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Color = if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
        if (isDark) Color(0xF20C101C) else Color(0xFFF4F2FA)
    } else {
        Color.Transparent
    }

    fun tint(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): Color? = if (useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze)) {
        null
    } else {
        if (isDark) Color(0x59141620) else Color(0x45FFFFFF)
    }

    fun configuration(
        isDark: Boolean,
        reducedTransparency: Boolean,
        hazeAvailable: Boolean,
        apiSupportsHaze: Boolean,
    ): DockHazeConfiguration = DockHazeConfiguration(
        useOpaqueFallback = useOpaqueFallback(reducedTransparency, hazeAvailable, apiSupportsHaze),
        inputScale = inputScale(reducedTransparency, hazeAvailable, apiSupportsHaze),
        backgroundColor = backgroundColor(isDark, reducedTransparency, hazeAvailable, apiSupportsHaze),
        tint = tint(isDark, reducedTransparency, hazeAvailable, apiSupportsHaze),
    )
}

/**
 * Standalone liquid-glass action button for component-page controls and recent-games
 * actions. On API 31+ with Haze available it samples the bounded background, applies a
 * theme tint, a thin edge highlight and press deformation. Older API levels and reduced
 * transparency fall back to an opaque theme button with the same semantics.
 */
@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun HazeLiquidActionButton(
    colors: KireiColors,
    haze: HazeState?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
    showBorder: Boolean = true,
) {
    val resolvedInputScale = DockHazePolicy.inputScale(
        reducedTransparency = false,
        hazeAvailable = haze != null,
        apiSupportsHaze = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
    val glassEnabled = resolvedInputScale != null && haze != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .94f else 1f,
        animationSpec = AppleMotion.Spring.Press,
        label = "liquidActionPress",
    )
    val shape = if (label != null) RoundedCornerShape(18.dp) else CircleShape
    val tint = if (glassEnabled) colors.accent else colors.onAccent
    val base = if (colors.isDark) Color(0xE62C2C2E) else Color(0xF2FFFFFF)
    val highlight = if (glassEnabled) Color.White.copy(alpha = .30f) else Color.Transparent
    Box(
        modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (glassEnabled) {
                    // The effect node samples only what is behind this button's own bounds,
                    // then blurs it with a theme tint over a matching background.
                    Modifier.hazeEffect(haze!!) {
                        blurRadius = 24.dp
                        inputScale = resolvedInputScale ?: HazeInputScale.None
                        backgroundColor = colors.background
                        tints = listOf(HazeTint(base))
                    }
                } else {
                    Modifier.background(colors.accent, shape)
                }
            )
            .then(if (showBorder) Modifier.border(.7.dp, highlight, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (label != null) {
                Text(
                    label,
                    color = tint,
                    style = AppleTypography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp),
                )
            }
        }
    }
}
