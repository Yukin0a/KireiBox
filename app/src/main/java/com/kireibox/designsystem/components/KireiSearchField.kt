package com.kireibox.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/** Same input text as [KireiTextField]: 16sp regular. */
private val SearchTextStyle = AppleTypography.bodyLarge.copy(fontWeight = FontWeight.Normal)

/** 48dp: the clear button's touch target fits inside, so the capsule never grows when text appears. */
private val SearchFieldHeight = 48.dp
private val SearchIconSize = 20.dp
private val ClearIconSize = 18.dp

/**
 * What a [KireiSearchField] shows and reports. [onSearch] runs on the keyboard's search action;
 * [onFocusChange] reports focus so callers can show 「取消」 while the field is active.
 * [onComposingChange] reports whether the keyboard is still composing (pinyin 「shen」 before it
 * becomes 「深」), so a caller can hold results until the text is committed.
 */
internal data class KireiSearchFieldOptions(
    val placeholder: String,
    val focusRequester: FocusRequester? = null,
    val onFocusChange: (Boolean) -> Unit = {},
    val onSearch: () -> Unit = {},
    val clearLabel: String = "清除搜索内容",
    val onComposingChange: (Boolean) -> Unit = {},
)

/**
 * Search capsule (spec §8 input field, full radius): surface3 fill, strokeStrong hairline that
 * becomes the accent focus ring, a leading magnifier, placeholder ink and a clear button once
 * there is text. Single line with the keyboard's 「搜索」 action.
 */
@Composable
internal fun KireiSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    options: KireiSearchFieldOptions,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reducedMotion = kireiReducedMotion()
    val ring by animateColorAsState(if (focused) colors.accent else colors.strokeStrong, kireiFadeSpec(reducedMotion), label = "kireiSearchRing")
    val ringWidth by animateDpAsState(if (focused) KireiElevation.focusStrokeWidth else KireiElevation.strokeWidth, kireiFadeSpec(reducedMotion), label = "kireiSearchRingWidth")
    val selection = remember(colors.accent, colors.accentContainer) { TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accentContainer) }
    // The TextFieldValue form keeps the IME composition visible to us; a text set from outside (clear,
    // a suggestion) replaces it with the cursor at the end.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    SideEffect {
        if (shown != field) {
            field = shown
            options.onComposingChange(false)
        }
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selection) {
        BasicTextField(
            value = shown,
            onValueChange = { next ->
                field = next
                options.onComposingChange(next.composition != null)
                if (next.text != value) onValueChange(next.text)
            },
            modifier =
                modifier.fillMaxWidth()
                    .then(options.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                    .onFocusChanged { options.onFocusChange(it.isFocused) },
            textStyle = SearchTextStyle.copy(color = colors.text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { options.onSearch() }),
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = SearchFieldHeight)
                        .background(colors.surface3, KireiShapes.full)
                        .border(ringWidth, ring, KireiShapes.full)
                        .padding(start = KireiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = placeholderInk(colors), modifier = Modifier.size(SearchIconSize))
                    Spacer(Modifier.width(KireiSpacing.xs))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(options.placeholder, color = placeholderInk(colors), style = SearchTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            // The field keeps its name for TalkBack once the placeholder is gone.
                            Box(Modifier.semantics { text = AnnotatedString(options.placeholder) })
                        }
                        innerTextField()
                    }
                    SearchClearButton(visible = value.isNotEmpty(), options.clearLabel, colors) { onValueChange("") }
                }
            },
        )
    }
}

/** The clear glyph: a 48dp touch target around an 18dp secondary icon, only while there is text. */
@Composable
private fun SearchClearButton(
    visible: Boolean,
    label: String,
    colors: KireiColors,
    onClear: () -> Unit,
) {
    if (!visible) {
        Spacer(Modifier.width(KireiSpacing.sm))
        return
    }
    Box(
        Modifier.minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClear)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Cancel, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(ClearIconSize))
    }
}
