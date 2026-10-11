package com.kireibox.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.dangerSolid

/**
 * Kirei menu (spec §8): the anchored M3 DropdownMenu with the Kirei container — surfaceOverlay,
 * md radius, strokeStrong hairline, e3 shadow and no tonal tint. Place it in the same Box as its
 * anchor, exactly like DropdownMenu.
 */
@Composable
internal fun KireiMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        shape = KireiShapes.md,
        containerColor = colors.surfaceOverlay,
        tonalElevation = 0.dp,
        shadowElevation = KireiElevation.e3Shadow,
        border = BorderStroke(KireiElevation.strokeWidth, colors.strokeStrong),
    ) {
        // Menus anchored to grouped rows must not inherit the row's divider flag.
        CompositionLocalProvider(LocalKireiListDivider provides false) { content() }
    }
}

/** One menu entry. [selected] shows a trailing accent check instead of a text prefix (spec §8). */
internal data class KireiMenuItemSpec(
    val label: String,
    val supporting: String? = null,
    val selected: Boolean = false,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
)

private val MenuItemPadding = PaddingValues(horizontal = KireiSpacing.md, vertical = KireiSpacing.xxs)

@Composable
internal fun KireiMenuItem(
    spec: KireiMenuItemSpec,
    colors: KireiColors,
    onClick: () -> Unit,
) {
    val labelColor =
        when {
            spec.destructive -> colors.dangerSolid.accent
            spec.selected -> colors.accent
            else -> colors.text
        }
    DropdownMenuItem(
        text = {
            Column(Modifier.padding(vertical = KireiSpacing.xxs / 2)) {
                Text(
                    spec.label,
                    color = labelColor,
                    style = if (spec.selected) AppleTypography.bodyMediumEmphasis else AppleTypography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                spec.supporting?.let {
                    Text(it, color = colors.secondary, style = AppleTypography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        onClick = onClick,
        leadingIcon = spec.icon?.let { icon -> { Icon(icon, contentDescription = null, tint = if (spec.destructive) colors.dangerSolid.accent else colors.secondary) } },
        trailingIcon =
            if (spec.selected) {
                { Icon(Icons.Rounded.Check, contentDescription = "已选择", tint = colors.accent) }
            } else {
                null
            },
        colors = MenuDefaults.itemColors(textColor = colors.text),
        contentPadding = MenuItemPadding,
    )
}
