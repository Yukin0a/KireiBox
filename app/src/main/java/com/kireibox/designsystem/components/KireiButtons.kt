package com.kireibox.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.accentStrong
import com.kireibox.designsystem.dangerSolid

/**
 * Button roles (spec §8). PRIMARY = accent fill + onAccent; SECONDARY = tonal accentContainer +
 * accent; TEXT = accent label only; PLAIN = secondary label only (cancel/dismiss); DESTRUCTIVE =
 * the AA danger pair ([dangerSolid]: deeper red + white in light, danger + dark ink in dark).
 */
internal enum class KireiButtonStyle { PRIMARY, SECONDARY, TEXT, PLAIN, DESTRUCTIVE }

/** Visual heights; every size keeps a 48dp touch target. */
internal enum class KireiButtonSize(
    val height: Dp,
    val horizontalPadding: Dp,
) {
    SMALL(32.dp, 14.dp),
    MEDIUM(40.dp, 18.dp),
    LARGE(48.dp, 22.dp),
}

internal data class KireiButtonOptions(
    val style: KireiButtonStyle = KireiButtonStyle.PRIMARY,
    val size: KireiButtonSize = KireiButtonSize.LARGE,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    /** Capsule (spec §3 full); false uses the md radius. */
    val pill: Boolean = true,
)

private data class KireiButtonPalette(
    val container: Color,
    val pressed: Color,
    val content: Color,
)

private fun buttonPalette(
    style: KireiButtonStyle,
    colors: KireiColors,
): KireiButtonPalette =
    when (style) {
        KireiButtonStyle.PRIMARY -> KireiButtonPalette(colors.accent, colors.accentStrong, colors.onAccent)
        KireiButtonStyle.SECONDARY -> KireiButtonPalette(colors.accentContainer, colors.accentContainer, colors.accent)
        KireiButtonStyle.TEXT -> KireiButtonPalette(Color.Transparent, colors.accentContainer, colors.accent)
        KireiButtonStyle.PLAIN -> KireiButtonPalette(Color.Transparent, colors.surfaceRaised, colors.secondary)
        KireiButtonStyle.DESTRUCTIVE -> colors.dangerSolid.let { KireiButtonPalette(it.accent, it.accent, it.onAccent) }
    }

private fun buttonTextStyle(size: KireiButtonSize): TextStyle =
    when (size) {
        KireiButtonSize.SMALL -> AppleTypography.bodySmallEmphasis
        KireiButtonSize.MEDIUM -> AppleTypography.bodyMediumEmphasis
        KireiButtonSize.LARGE -> AppleTypography.bodyLargeEmphasis
    }

/**
 * Spec §8 button: 48dp capsule by default, press scale 0.97 on Spring.Press, pressed primary fill
 * = accentStrong. Smaller sizes keep a 48dp touch target around the visual pill.
 */
@Composable
internal fun KireiButton(
    label: String,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    options: KireiButtonOptions = KireiButtonOptions(),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val palette = buttonPalette(options.style, colors)
    val shape = if (options.pill) KireiShapes.full else KireiShapes.md
    Box(
        modifier.minimumInteractiveComponentSize()
            .graphicsLayer {
                val scale = kireiPressScale(press)
                scaleX = scale
                scaleY = scale
                alpha = if (options.enabled) 1f else KIREI_DISABLED_ALPHA
            }
            .heightIn(min = options.size.height)
            .clip(shape)
            .drawBehind {
                drawRect(palette.container)
                if (press > 0f) drawRect(palette.pressed, alpha = press)
            }
            .clickable(interaction, indication = null, enabled = options.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = options.size.horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            options.icon?.let { Icon(it, contentDescription = null, tint = palette.content, modifier = Modifier.size(ICON_SIZE)) }
            Text(label, color = palette.content, style = buttonTextStyle(options.size), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val ICON_SIZE = 18.dp

/**
 * Spec §8 chip: capsule; selected = accent fill + onAccent, unselected = surfaceRaised + secondary.
 * An optional [icon] (14dp, e.g. an engine glyph) sits in front of the label in the label's colour.
 */
@Composable
@Suppress("LongParameterList")
internal fun KireiChip(
    label: String,
    selected: Boolean,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val reducedMotion = kireiReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val container by animateColorAsState(if (selected) colors.accent else colors.surfaceRaised, kireiFadeSpec(reducedMotion), label = "kireiChip")
    val content by animateColorAsState(if (selected) colors.onAccent else colors.secondary, kireiFadeSpec(reducedMotion), label = "kireiChipText")
    Box(
        modifier.minimumInteractiveComponentSize()
            .graphicsLayer {
                val scale = kireiPressScale(press)
                scaleX = scale
                scaleY = scale
            }
            .clip(KireiShapes.full)
            .background(container)
            // selectable (not clickable) so TalkBack announces the chip's selected state.
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = CHIP_HORIZONTAL_PADDING, vertical = CHIP_VERTICAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
            icon?.let { Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(CHIP_ICON_SIZE)) }
            Text(label, color = content, style = AppleTypography.labelLarge, maxLines = 1)
        }
    }
}

private val CHIP_HORIZONTAL_PADDING = 14.dp
private val CHIP_VERTICAL_PADDING = 9.dp
private val CHIP_ICON_SIZE = 14.dp

/** Fill behind a badge. */
internal enum class KireiBadgeTone { DANGER, ACCENT, NEUTRAL }

private fun badgeColors(
    tone: KireiBadgeTone,
    colors: KireiColors,
): Pair<Color, Color> =
    when (tone) {
        // 11sp digits need AA 4.5:1: a deeper light fill, dark ink on the bright dark-theme danger.
        KireiBadgeTone.DANGER -> KireiTokens.badgeDanger(colors.isDark).let { it.accent to it.onAccent }
        KireiBadgeTone.ACCENT -> colors.accent to colors.onAccent
        KireiBadgeTone.NEUTRAL -> colors.surface3 to colors.text
    }

internal object KireiBadgeDefaults {
    val Height = 20.dp
    val HorizontalPadding = 6.dp
    val DotSize = 8.dp

    /** Count label: hidden at 0, capped at "99+". */
    fun label(count: Int): String =
        when {
            count <= 0 -> ""
            count >= MAX_COUNT -> "99+"
            else -> count.toString()
        }

    private const val MAX_COUNT = 100
}

/** Pill badge with a short label (spec §3: capsule, labelSmall SemiBold ink). */
@Composable
internal fun KireiBadge(
    text: String,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    tone: KireiBadgeTone = KireiBadgeTone.DANGER,
) {
    val (fill, ink) = badgeColors(tone, colors)
    Box(
        modifier.heightIn(min = KireiBadgeDefaults.Height)
            .widthIn(min = KireiBadgeDefaults.Height)
            .background(fill, KireiShapes.full)
            .padding(horizontal = KireiBadgeDefaults.HorizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = ink, style = AppleTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

/** Count badge; renders nothing for 0. */
@Composable
internal fun KireiCountBadge(
    count: Int,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    tone: KireiBadgeTone = KireiBadgeTone.DANGER,
) {
    if (count <= 0) return
    KireiBadge(KireiBadgeDefaults.label(count), colors, modifier, tone)
}

/** Unlabeled status dot. */
@Composable
internal fun KireiDotBadge(
    colors: KireiColors,
    modifier: Modifier = Modifier,
    tone: KireiBadgeTone = KireiBadgeTone.DANGER,
) {
    val (fill, _) = badgeColors(tone, colors)
    Box(modifier.size(KireiBadgeDefaults.DotSize).background(fill, KireiShapes.full))
}
