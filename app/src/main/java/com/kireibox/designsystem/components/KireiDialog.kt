package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/** One dialog button. Confirm actions default to PRIMARY; pass PLAIN for a neutral dismiss. */
internal data class KireiDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val style: KireiButtonStyle = KireiButtonStyle.PRIMARY,
    val enabled: Boolean = true,
)

/** Copy and buttons of a dialog. [dismissible] = false ignores back presses and outside taps. */
internal data class KireiDialogSpec(
    val title: String,
    val message: String? = null,
    val confirm: KireiDialogAction? = null,
    val dismiss: KireiDialogAction? = null,
    val dismissible: Boolean = true,
)

internal object KireiDialogDefaults {
    val MaxWidth = 400.dp
    val WindowMargin = KireiSpacing.xl
    val ContentPadding = KireiSpacing.xl
    val MessageMaxHeight = 300.dp

    /** Vertical room around the card so the dialog window does not clip its e3 shadow. */
    val ShadowRoom = KireiSpacing.xl

    /** A single label longer than this (CJK characters) stacks the buttons full-width. */
    const val STACK_LABEL_LENGTH = 6

    /** Combined label length above which the side-by-side row would crowd the card. */
    const val STACK_TOTAL_LENGTH = 10

    fun stackActions(labels: List<String>): Boolean = labels.any { it.length > STACK_LABEL_LENGTH } || labels.sumOf { it.length } > STACK_TOTAL_LENGTH
}

/**
 * Kirei dialog (spec §8): replaces the stock M3 AlertDialog with a surfaceOverlay card (xl radius,
 * strokeStrong hairline, e3 shadow) over the scrim token, a headlineMedium title, a bodyMedium
 * message, optional custom [content] (e.g. a [KireiTextField]) and KireiButton actions.
 */
@Composable
internal fun KireiDialog(
    spec: KireiDialogSpec,
    colors: KireiColors,
    onDismissRequest: () -> Unit,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = spec.dismissible,
                dismissOnClickOutside = spec.dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        val view = LocalView.current
        SideEffect {
            // The dialog window's own dim is the backdrop: use the scrim token's strength (spec §2.1).
            (view.parent as? DialogWindowProvider)?.window?.setDimAmount(colors.scrim.alpha)
        }
        // Width-filling but height-wrapping: the dialog window ends at the card (plus room for its
        // e3 shadow), so taps above or below it reach the platform's outside-touch dismissal.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = KireiDialogDefaults.WindowMargin, vertical = KireiDialogDefaults.ShadowRoom),
            contentAlignment = Alignment.Center,
        ) {
            KireiDialogCard(colors, Modifier.widthIn(max = KireiDialogDefaults.MaxWidth)) {
                // A dialog opened from a grouped row must not inherit that row's divider flag.
                CompositionLocalProvider(LocalKireiListDivider provides false) {
                    KireiDialogBody(spec, colors, content)
                }
            }
        }
    }
}

/** The opaque dialog container: surfaceOverlay, xl radius, strokeStrong hairline, e3 shadow. */
@Composable
internal fun KireiDialogCard(
    colors: KireiColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .kireiE3Shadow(KireiShapes.xl, colors.isDark)
            .clip(KireiShapes.xl)
            .background(colors.surfaceOverlay)
            .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.xl)
            .padding(KireiDialogDefaults.ContentPadding),
        content = content,
    )
}

/** Title, scrollable message, custom content and actions; shared with the in-window prompt hosts. */
@Composable
internal fun ColumnScope.KireiDialogBody(
    spec: KireiDialogSpec,
    colors: KireiColors,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Text(spec.title, color = colors.text, style = AppleTypography.headlineMedium, modifier = Modifier.semantics { heading() })
    spec.message?.let { message ->
        Text(
            message,
            color = colors.secondary,
            style = AppleTypography.bodyMedium,
            modifier =
                Modifier.padding(top = KireiSpacing.xs)
                    .heightIn(max = KireiDialogDefaults.MessageMaxHeight)
                    .verticalScroll(rememberScrollState()),
        )
    }
    if (content != null) {
        Column(Modifier.fillMaxWidth().padding(top = KireiSpacing.md), content = content)
    }
    KireiDialogActions(spec.confirm, spec.dismiss, colors, Modifier.padding(top = KireiSpacing.xl))
}

/**
 * Dialog buttons: right-aligned [dismiss] then [confirm]; when the labels are long they stack
 * full-width with the confirm action on top.
 */
@Composable
internal fun KireiDialogActions(
    confirm: KireiDialogAction?,
    dismiss: KireiDialogAction?,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val actions = listOfNotNull(dismiss, confirm)
    if (actions.isEmpty()) return
    if (KireiDialogDefaults.stackActions(actions.map(KireiDialogAction::label))) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
            listOfNotNull(confirm, dismiss).forEach { action ->
                KireiButton(action.label, colors, Modifier.fillMaxWidth(), KireiButtonOptions(style = action.style, enabled = action.enabled), action.onClick)
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                KireiButton(
                    action.label,
                    colors,
                    options = KireiButtonOptions(style = action.style, size = KireiButtonSize.MEDIUM, enabled = action.enabled),
                    onClick = action.onClick,
                )
            }
        }
    }
}
