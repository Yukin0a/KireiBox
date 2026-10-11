package com.kireibox.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing

/**
 * Layout options of a [KireiSheet]. [expanded] sheets take 88% of the window and give the content
 * the remaining height; otherwise the content is capped at [maxContentHeight]. Disable
 * [scrollable] when the content owns its own lazy list. [skipPartiallyExpanded] opens a tall sheet
 * at its full height instead of the half-height stop (a guided flow whose buttons sit at the bottom).
 * A [grouped] sheet holds grouped list cards: in light mode it takes the grouped page background, so
 * the white cards stand off it the way they do on a settings page (dark keeps surfaceOverlay).
 */
internal data class KireiSheetSpec(
    val title: String? = null,
    val expanded: Boolean = false,
    val scrollable: Boolean = true,
    val maxContentHeight: Dp = 560.dp,
    val skipPartiallyExpanded: Boolean = false,
    val grouped: Boolean = false,
)

internal object KireiSheetDefaults {
    val HandleWidth = 36.dp
    val HandleHeight = 5.dp
    const val EXPANDED_HEIGHT_FRACTION = .88f
}

/**
 * The one bottom-sheet look (spec §5/§8): surfaceOverlay container with xl top corners and a
 * strokeStrong hairline, 36×5 tertiary handle, scrim-token backdrop and a centered headlineMedium
 * title. Window insets are applied here so every sheet clears the navigation bar the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KireiSheet(
    colors: KireiColors,
    onDismiss: () -> Unit,
    spec: KireiSheetSpec = KireiSheetSpec(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetColor = if (spec.grouped && !colors.isDark) colors.background else colors.surfaceOverlay
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = spec.skipPartiallyExpanded),
        containerColor = sheetColor,
        contentColor = colors.text,
        tonalElevation = 0.dp,
        scrimColor = colors.scrim,
        dragHandle = null,
        shape = KireiShapes.sheet,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (spec.expanded) Modifier.fillMaxHeight(KireiSheetDefaults.EXPANDED_HEIGHT_FRACTION) else Modifier)
                .background(sheetColor, KireiShapes.sheet)
                .border(KireiElevation.strokeWidth, colors.strokeStrong, KireiShapes.sheet)
                .navigationBarsPadding()
                .padding(bottom = KireiSpacing.md),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = KireiSpacing.xs, bottom = KireiSpacing.sm)
                    .size(KireiSheetDefaults.HandleWidth, KireiSheetDefaults.HandleHeight)
                    .background(colors.tertiary, KireiShapes.full),
            )
            spec.title?.let { title ->
                Text(
                    title,
                    color = colors.text,
                    style = AppleTypography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = KireiSpacing.xl, vertical = KireiSpacing.xxs / 2)
                            .semantics { heading() },
                )
            }
            val contentModifier =
                Modifier.fillMaxWidth()
                    .then(if (spec.expanded) Modifier.weight(1f) else Modifier.heightIn(max = spec.maxContentHeight))
                    .then(if (spec.scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            // A sheet opened from a grouped row must not inherit that row's "draw a divider" flag.
            CompositionLocalProvider(LocalKireiListDivider provides false) {
                Column(contentModifier, content = content)
            }
        }
    }
}
