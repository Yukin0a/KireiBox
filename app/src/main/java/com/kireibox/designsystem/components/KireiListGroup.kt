package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/**
 * Whether the row currently being composed inside a [KireiListGroup] is followed by another row.
 * Rows read it to draw their own hairline divider inset to their text start (spec §8); it is false
 * for the last row and outside any group.
 */
internal val LocalKireiListDivider = compositionLocalOf { false }

/** DSL of [KireiListGroup]: every [item] is one row of the group, in order. */
internal class KireiListGroupScope internal constructor() {
    internal val items = mutableListOf<KireiListGroupItem>()

    /** Extra modifier for the card itself (inside its rounded clip), e.g. a highlight over the rows only. */
    var cardModifier: Modifier = Modifier

    /** Adds one row. [key] keeps row state stable when rows are inserted or removed. */
    fun item(
        key: Any? = null,
        content: @Composable () -> Unit,
    ) {
        items += KireiListGroupItem(key, content)
    }
}

internal class KireiListGroupItem(
    val key: Any?,
    val content: @Composable () -> Unit,
)

/**
 * e1 list group (spec §4/§5/§8): optional caption above (labelLarge, secondary), an lg-rounded
 * surface card with a hairline stroke, optional footer below (bodySmall, secondary: footers carry
 * real explanations, so they need AA contrast; spec §10 amendment). Rows separate
 * themselves with dividers that start at the text (never under the icon tile) and the last row
 * has none. The group adds no outer spacing; callers space groups with [KireiSpacing.groupGap].
 * A group without rows shows only its caption and footer.
 */
@Composable
internal fun KireiListGroup(
    colors: KireiColors,
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: KireiListGroupScope.() -> Unit,
) {
    val scope = KireiListGroupScope().apply(content)
    val rows = scope.items
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                color = colors.secondary,
                style = AppleTypography.labelLarge,
                modifier =
                    Modifier.padding(start = KireiSpacing.groupInner, end = KireiSpacing.groupInner, bottom = KireiSpacing.groupCaptionGap)
                        .semantics { heading() },
            )
        }
        // A group without rows draws no card. Pages give an empty list an empty-state row instead
        // (spec §10: 「没有待确认的封面」, 「还没有扫描目录」), so a caption never floats over a footer.
        if (rows.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(KireiShapes.lg)
                    .background(colors.surface)
                    .border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.lg)
                    .then(scope.cardModifier),
            ) {
                rows.forEachIndexed { index, row ->
                    key(row.key ?: index) {
                        CompositionLocalProvider(LocalKireiListDivider provides (index < rows.lastIndex)) {
                            row.content()
                        }
                    }
                }
            }
        }
        if (footer != null) {
            Text(
                footer,
                color = colors.secondary,
                style = AppleTypography.bodySmall,
                modifier =
                    Modifier.padding(
                        start = KireiSpacing.groupInner,
                        end = KireiSpacing.groupInner,
                        top = KireiSpacing.groupCaptionGap,
                    ),
            )
        }
    }
}

/**
 * Draws the group divider under custom (non-[KireiListRow]) content when the group says another
 * row follows. [startInset] is where the hairline begins: the content's text start.
 */
internal fun Modifier.kireiListDivider(
    visible: Boolean,
    colors: KireiColors,
    startInset: Dp,
): Modifier =
    if (!visible) {
        this
    } else {
        drawBehind {
            val stroke = KireiElevation.strokeWidth.toPx()
            val y = size.height - stroke / 2f
            drawLine(colors.stroke, Offset(startInset.toPx(), y), Offset(size.width, y), stroke)
        }
    }
