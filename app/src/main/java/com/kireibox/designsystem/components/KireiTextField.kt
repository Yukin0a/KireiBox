package com.kireibox.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens

/** Input text: 16sp regular so typing never looks like a list-row title. */
private val InputTextStyle = AppleTypography.bodyLarge.copy(fontWeight = FontWeight.Normal)
private val FieldMinHeight = 48.dp
private val LeadingIconSize = 20.dp

/**
 * Spec §8 text field: surface3 fill, md radius, strokeStrong hairline that becomes an accent focus
 * ring, secondary placeholder, accent cursor and selection handles. An optional persistent
 * [KireiTextFieldOptions.label] sits above the input inside the field's own node, so it stays
 * visible and is announced together with the text (a placeholder disappears once text exists).
 */
@Composable
internal fun KireiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    options: KireiTextFieldOptions = KireiTextFieldOptions(),
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reducedMotion = kireiReducedMotion()
    val ring by animateColorAsState(if (focused) colors.accent else colors.strokeStrong, kireiFadeSpec(reducedMotion), label = "kireiFieldRing")
    val ringWidth by animateDpAsState(
        if (focused) KireiElevation.focusStrokeWidth else KireiElevation.strokeWidth,
        kireiFadeSpec(reducedMotion),
        label = "kireiFieldRingWidth",
    )
    val selection = remember(colors.accent, colors.accentContainer) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accentContainer) }
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (options.enabled) 1f else KIREI_DISABLED_ALPHA },
            enabled = options.enabled,
            textStyle = InputTextStyle.copy(color = colors.text),
            keyboardOptions = options.keyboardOptions,
            singleLine = options.singleLine,
            visualTransformation = if (options.secret) PasswordVisualTransformation() else VisualTransformation.None,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Column(Modifier.fillMaxWidth()) {
                    options.label?.let { label ->
                        Text(
                            label,
                            color = if (focused) colors.accent else colors.secondary,
                            style = AppleTypography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = KireiSpacing.xxs, bottom = KireiSpacing.xs),
                        )
                    }
                    KireiTextFieldFrame(value, options, colors, FieldRing(ring, ringWidth), innerTextField)
                }
            },
        )
    }
}

/** Focus ring color and width, animated by the field. */
private class FieldRing(
    val color: Color,
    val width: Dp,
)

@Composable
private fun KireiTextFieldFrame(
    value: String,
    options: KireiTextFieldOptions,
    colors: KireiColors,
    ring: FieldRing,
    innerTextField: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = FieldMinHeight)
            .background(colors.surface3, KireiShapes.md)
            .border(ring.width, ring.color, KireiShapes.md)
            .padding(horizontal = KireiSpacing.sm + KireiSpacing.xxs / 2, vertical = KireiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.leadingIcon?.let {
            Icon(it, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(LeadingIconSize))
            Spacer(Modifier.width(KireiSpacing.xs))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && options.placeholder != null) {
                // Not tertiary (~2:1 on surface3); in light even secondary is only 4.2:1 on the
                // surface3 fill, so light fields use the deeper placeholder ink (spec §10 amendment).
                Text(options.placeholder, color = placeholderInk(colors), style = InputTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            innerTextField()
        }
    }
}

/** Placeholder ink on the surface3 field fill: ≥ 4.5:1 in every theme and contrast mode. */
internal fun placeholderInk(colors: KireiColors): Color = if (colors.isDark || colors.highContrast) colors.secondary else KireiTokens.PlaceholderLight

internal data class KireiTextFieldOptions(
    /** Persistent field name shown above the input (and announced with it); null for none. */
    val label: String? = null,
    val placeholder: String? = null,
    val singleLine: Boolean = true,
    /** Masks the input (tokens, passwords). */
    val secret: Boolean = false,
    val leadingIcon: ImageVector? = null,
    val keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    val enabled: Boolean = true,
)
