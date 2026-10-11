package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.dangerContainer
import com.kireibox.designsystem.dangerSolid

/**
 * Row emphasis: [PROMINENT] sits on accentContainer; [DESTRUCTIVE] tints tile and title with danger;
 * [ACTION] is a command row (添加目录, 查看全部…) whose title is drawn in the accent color.
 */
internal enum class KireiRowTone { NORMAL, PROMINENT, DESTRUCTIVE, ACTION }

/** Trailing side of a [KireiListRow] (spec §8). */
internal sealed interface KireiRowTrailing {
    data object None : KireiRowTrailing

    data object Chevron : KireiRowTrailing

    /**
     * Accent check for the selected choice of a picker list. Visual only: the row that shows it
     * carries the selected state (radio-button semantics), so the glyph is not announced twice.
     */
    data object Check : KireiRowTrailing

    /** Current value in secondary text, optionally followed by a chevron. */
    data class Value(
        val text: String,
        val chevron: Boolean = true,
    ) : KireiRowTrailing

    /** A [KireiSwitch]; the whole row toggles it (switch semantics). */
    data class Toggle(
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : KireiRowTrailing

    /** A count badge followed by a chevron. */
    data class Badge(
        val count: Int,
    ) : KireiRowTrailing

    /** A secondary glyph such as an expand arrow. */
    data class Glyph(
        val icon: ImageVector,
    ) : KireiRowTrailing

    class Custom(
        val content: @Composable () -> Unit,
    ) : KireiRowTrailing
}

/**
 * Appearance of one list row. [tile] paints a tonal category tile: tinted container, same-hue glyph (spec §2.3);
 * without it the icon sits on an accent (or danger, for [KireiRowTone.DESTRUCTIVE]) tinted tile.
 * [leading] replaces the icon tile with custom content (a cover thumbnail) of [tileSize] width.
 * [reserveIconSpace] keeps icon-less rows aligned with icon rows of the same group (spec §8).
 * [minHeight] and [verticalPadding] override the spec metrics for compact (landscape) lists.
 */
internal data class KireiListRowSpec(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
    val tile: KireiTileColor? = null,
    val trailing: KireiRowTrailing = KireiRowTrailing.None,
    val tone: KireiRowTone = KireiRowTone.NORMAL,
    val enabled: Boolean = true,
    val reserveIconSpace: Boolean = false,
    val tileSize: Dp = KireiSpacing.iconTileSize,
    val horizontalPadding: Dp = KireiSpacing.groupInner,
    val subtitleMaxLines: Int = 2,
    val leading: (@Composable () -> Unit)? = null,
    val minHeight: Dp? = null,
    val verticalPadding: Dp = KireiSpacing.rowVerticalPadding,
)

/** Metrics shared by rows and their dividers (spec §4). */
internal object KireiListRowDefaults {
    fun minHeight(spec: KireiListRowSpec): Dp = spec.minHeight ?: if (spec.subtitle.isNullOrBlank()) KireiSpacing.rowMinHeightSingleLine else KireiSpacing.rowMinHeightTwoLine

    /** Whether the row starts with an icon tile, custom leading content or the reserved tile slot. */
    fun hasLeading(spec: KireiListRowSpec): Boolean = spec.icon != null || spec.leading != null || spec.reserveIconSpace

    /** Where the row text (and therefore its divider) starts. */
    fun textStart(spec: KireiListRowSpec): Dp =
        if (hasLeading(spec)) {
            spec.horizontalPadding + spec.tileSize + KireiSpacing.iconTileTextGap
        } else {
            spec.horizontalPadding
        }

    /** Trailing values never take more than this, so a long value cannot squeeze the title away. */
    val ValueMaxWidth: Dp = 168.dp

    const val GLYPH_SCALE = .6f
}

/**
 * Settings-style list row (spec §4/§8): optional leading icon tile, title (bodyLarge) and
 * subtitle (bodySmall, secondary), then the [KireiRowTrailing] content. Pressing paints
 * surfaceRaised with the Spring.Press curve; keyboard / D-pad focus paints it too, plus an accent
 * ring (WCAG 2.4.7). Inside a [KireiListGroup] the row draws the divider below itself, inset to its
 * text start. A display-only row (no click, no toggle) is one TalkBack stop: title, subtitle and
 * trailing status are merged.
 */
@Composable
internal fun KireiListRow(
    spec: KireiListRowSpec,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    val focused by interaction.collectIsFocusedAsState()
    val base = if (spec.tone == KireiRowTone.PROMINENT) colors.accentContainer else Color.Transparent
    val toggle = spec.trailing as? KireiRowTrailing.Toggle
    val interactive =
        when {
            toggle != null ->
                Modifier.toggleable(
                    value = toggle.checked,
                    interactionSource = interaction,
                    indication = null,
                    enabled = spec.enabled,
                    role = Role.Switch,
                    onValueChange = toggle.onCheckedChange,
                )
            onClick != null -> Modifier.clickable(interaction, indication = null, enabled = spec.enabled, role = Role.Button, onClick = onClick)
            else -> Modifier.semantics(mergeDescendants = true) {}
        }
    Row(
        modifier.fillMaxWidth()
            .heightIn(min = KireiListRowDefaults.minHeight(spec))
            .drawBehind {
                if (spec.tone == KireiRowTone.PROMINENT) drawProminentBand(base) else drawRect(base)
                val highlight = if (focused) 1f else press
                if (highlight > 0f) drawRect(colors.surfaceRaised, alpha = highlight)
                if (focused) drawFocusRing(colors.accent)
            }
            .kireiListDivider(LocalKireiListDivider.current && spec.tone != KireiRowTone.PROMINENT, colors, KireiListRowDefaults.textStart(spec))
            .then(interactive)
            .padding(horizontal = spec.horizontalPadding, vertical = spec.verticalPadding)
            .graphicsLayer { alpha = if (spec.enabled) 1f else KIREI_DISABLED_ALPHA },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KireiRowLeading(spec, colors)
        Column(Modifier.weight(1f)) {
            Text(
                spec.title,
                color = titleColor(spec.tone, colors),
                style = AppleTypography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            spec.subtitle?.takeIf(String::isNotBlank)?.let {
                Text(it, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = spec.subtitleMaxLines, overflow = TextOverflow.Ellipsis)
            }
        }
        if (spec.trailing != KireiRowTrailing.None) {
            Spacer(Modifier.width(KireiSpacing.sm))
            KireiRowTrailingContent(spec, colors)
        }
    }
}

private fun titleColor(
    tone: KireiRowTone,
    colors: KireiColors,
): Color =
    when (tone) {
        // The AA danger ink (light #CF2E33): danger itself is 4.2:1 on white.
        KireiRowTone.DESTRUCTIVE -> colors.dangerSolid.accent
        KireiRowTone.ACTION -> colors.accent
        else -> colors.text
    }

/** Keyboard focus: an md-rounded accent ring just inside the row (focus is never shown for touch). */
private fun DrawScope.drawFocusRing(color: Color) {
    val width = KireiElevation.focusRingWidth.toPx()
    val radius = KireiShapes.Radius.md.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(width, width),
        size = Size((size.width - width * 2f).coerceAtLeast(0f), (size.height - width * 2f).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width),
    )
}

/**
 * The PROMINENT highlight: a band inset evenly on all four sides from the container edge, with a
 * radius concentric to the lg card it sits in (card radius minus the inset), so the gap to the
 * card outline is the same everywhere, corners included.
 */
private fun DrawScope.drawProminentBand(color: Color) {
    val inset = KireiSpacing.xxs.toPx()
    val radius = (KireiShapes.Radius.lg - KireiSpacing.xxs).toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size((size.width - inset * 2f).coerceAtLeast(0f), (size.height - inset * 2f).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius),
    )
}

@Composable
private fun KireiRowLeading(
    spec: KireiListRowSpec,
    colors: KireiColors,
) {
    val icon = spec.icon
    if (!KireiListRowDefaults.hasLeading(spec)) return
    val leading = spec.leading
    if (leading != null) {
        Box(Modifier.width(spec.tileSize), contentAlignment = Alignment.Center) { leading() }
    } else if (icon == null) {
        Spacer(Modifier.width(spec.tileSize))
    } else {
        val (fill, glyph) =
            when {
                spec.tile != null -> spec.tile.container(colors.isDark) to spec.tile.glyph(colors.isDark)
                spec.tone == KireiRowTone.DESTRUCTIVE -> colors.dangerContainer to colors.dangerSolid.accent
                else -> colors.accentContainer to colors.accent
            }
        KireiIconTile(icon, fill, glyph, spec.tileSize)
    }
    Spacer(Modifier.width(KireiSpacing.iconTileTextGap))
}

/** sm-rounded icon tile (spec §3/§8) shared by list rows and sheet actions. */
@Composable
internal fun KireiIconTile(
    icon: ImageVector,
    fill: Color,
    glyph: Color,
    size: Dp = KireiSpacing.iconTileSize,
) {
    Box(Modifier.size(size).background(fill, KireiShapes.sm), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(size * KireiListRowDefaults.GLYPH_SCALE))
    }
}

@Composable
private fun KireiRowTrailingContent(
    spec: KireiListRowSpec,
    colors: KireiColors,
) {
    when (val trailing = spec.trailing) {
        KireiRowTrailing.None -> Unit
        KireiRowTrailing.Chevron -> KireiChevron(colors)
        KireiRowTrailing.Check -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accent)
        is KireiRowTrailing.Value ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
                Text(
                    trailing.text,
                    color = colors.secondary,
                    style = AppleTypography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = KireiListRowDefaults.ValueMaxWidth),
                )
                if (trailing.chevron) KireiChevron(colors)
            }
        is KireiRowTrailing.Toggle -> KireiSwitch(trailing.checked, onCheckedChange = null, colors = colors, enabled = spec.enabled)
        is KireiRowTrailing.Badge ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
                KireiCountBadge(trailing.count, colors)
                KireiChevron(colors)
            }
        is KireiRowTrailing.Glyph -> Icon(trailing.icon, contentDescription = null, tint = colors.secondary)
        is KireiRowTrailing.Custom -> trailing.content()
    }
}

@Composable
private fun KireiChevron(colors: KireiColors) {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.tertiary)
}
