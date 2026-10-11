package com.kireibox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.components.KireiEmptyState
import com.kireibox.designsystem.components.KireiSkeleton
import com.kireibox.designsystem.components.kireiPressScale
import com.kireibox.designsystem.components.kireiReducedMotion
import com.kireibox.designsystem.components.rememberPressProgress
import com.yuki.yukihub.metadata.model.MetadataCover

// Cover picker results (r5 · B): loading skeletons, the empty state, and the candidate cards in
// rows of one to three, with the selected work's cover versions under its row. Candidate artwork is
// never NSFW-blurred: the picker always shows the true cover so the choice is informed.

private val ThumbWidth = 72.dp
private val ThumbHeight = 96.dp
private val VariantWidth = 80.dp
private val VariantHeight = 107.dp
private val CardPadding = 10.dp

/** Loading, empty or the candidate rows, sized to whatever height the arrangement leaves. */
@Composable
internal fun CoverResults(
    scope: CoverPickerScope,
    modifier: Modifier,
) {
    val search = scope.search
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = CoverPickerLayoutPolicy.columns(maxWidth.value)
        when {
            search.loading && search.candidates.isEmpty() -> CoverResultsLoading(scope, columns)
            search.candidates.isEmpty() -> CoverResultsEmpty(scope)
            else -> CoverResultsList(scope, columns)
        }
    }
}

@Composable
private fun CoverResultsList(
    scope: CoverPickerScope,
    columns: Int,
) {
    val search = scope.search
    val rows = remember(search.candidates, columns, search.selectedKey) { CoverPickerLayoutPolicy.rows(search.candidates, columns, search.selectedKey) }
    // Only a tap scrolls the new cover versions into view; the first automatic selection does not.
    var revealKey by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = KireiSpacing.sm), verticalArrangement = Arrangement.spacedBy(KireiSpacing.sm)) {
        item(key = "count") {
            Text(
                "找到 ${search.candidates.size} 部作品",
                color = scope.colors.secondary,
                style = AppleTypography.labelLarge,
                modifier = Modifier.padding(start = KireiSpacing.xxs).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is CoverResultRow.Cards ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm)) {
                        row.candidates.forEach { candidate ->
                            CoverCandidateCard(scope, candidate, Modifier.weight(1f).fillMaxHeight()) {
                                revealKey = candidate.key
                                scope.viewModel.selectCoverCandidate(candidate.key)
                            }
                        }
                        repeat(columns - row.candidates.size) { Spacer(Modifier.weight(1f)) }
                    }
                is CoverResultRow.Variants -> CoverVariantStrip(scope, row.candidate, reveal = revealKey == row.candidate.key) { revealKey = null }
            }
        }
    }
}

/** One work: its cover, title, sources and match score, confidence tags and developer; a radio button. */
@Composable
private fun CoverCandidateCard(
    scope: CoverPickerScope,
    candidate: CoverCandidateUi,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val colors = scope.colors
    val selected = scope.search.selectedKey == candidate.key
    val reducedMotion = kireiReducedMotion()
    val progress by animateFloatAsState(if (selected) 1f else 0f, selectionSpec(reducedMotion), label = "coverCandidateSelection")
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val press = rememberPressProgress(interaction)
    val image = rememberCover(candidate.metadata.coverUrl, sourceKey = (candidate.coverSources[candidate.metadata.coverUrl] ?: candidate.source).key)
    Box(
        modifier
            .graphicsLayer {
                val scale = kireiPressScale(press)
                scaleX = scale
                scaleY = scale
            }
            .clip(KireiShapes.lg)
            .background(scope.cardFill)
            .border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.lg)
            .selectable(selected, interaction, indication = null, enabled = !scope.search.saving, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(Modifier.fillMaxWidth().padding(CardPadding)) {
            CoverThumbnail(image, colors, progress, Modifier.size(ThumbWidth, ThumbHeight))
            CoverCandidateText(candidate, colors, Modifier.weight(1f).padding(start = KireiSpacing.sm))
        }
        SelectionRing(progress, colors.accent, KireiShapes.lg, Modifier.matchParentSize())
        FocusRing(focused, colors, KireiShapes.lg, Modifier.matchParentSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverCandidateText(
    candidate: CoverCandidateUi,
    colors: KireiColors,
    modifier: Modifier,
) {
    val metadata = candidate.metadata
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
        Text(
            metadata.chineseTitle.ifBlank { metadata.originalTitle.ifBlank { metadata.romanTitle } },
            color = colors.text,
            style = AppleTypography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${candidate.sourceBadges.joinToString(" · ") { it.label }} · 匹配度 ${candidate.score.toInt()}%",
            color = colors.secondary,
            style = AppleTypography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (candidate.matchBadges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xxs)) {
                candidate.matchBadges.forEach { badge ->
                    Text(
                        badge,
                        color = colors.accent,
                        style = AppleTypography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.background(colors.accentContainer, KireiShapes.full).padding(horizontal = KireiSpacing.xs, vertical = 2.dp),
                    )
                }
            }
        }
        if (metadata.developer.isNotBlank()) {
            Text(metadata.developer, color = colors.secondary, style = AppleTypography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A 3:4 cover with a hairline; a quiet image glyph until (or unless) it loads; the check pops in on selection. */
@Composable
private fun CoverThumbnail(
    image: ImageBitmap?,
    colors: KireiColors,
    selection: Float,
    modifier: Modifier,
) {
    Box(modifier) {
        Box(
            Modifier.matchParentSize().clip(KireiShapes.md).background(colors.surface3).border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.md),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(PLACEHOLDER_ICON_SIZE))
            }
        }
        if (selection > 0f) CheckBadge(selection, colors, Modifier.align(Alignment.TopEnd).offset(x = CHECK_OVERHANG, y = -CHECK_OVERHANG))
    }
}

private val PLACEHOLDER_ICON_SIZE = 24.dp
private val CHECK_OVERHANG = 6.dp

/**
 * The selected work's cover versions, under its row. A tap scrolls them into view once ([onRevealed]
 * consumes the request), so scrolling back to a strip later never makes the list jump.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverVariantStrip(
    scope: CoverPickerScope,
    candidate: CoverCandidateUi,
    reveal: Boolean,
    onRevealed: () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reveal, candidate.key) {
        if (reveal) {
            requester.bringIntoView()
            onRevealed()
        }
    }
    val covers = candidate.metadata.covers
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester).clip(KireiShapes.lg).background(scope.cardFill)
            .border(KireiElevation.strokeWidth, scope.colors.stroke, KireiShapes.lg).padding(vertical = KireiSpacing.sm),
    ) {
        Text(
            "可选封面 · ${covers.size} 张",
            color = scope.colors.secondary,
            style = AppleTypography.labelLarge,
            modifier = Modifier.padding(horizontal = KireiSpacing.sm),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = KireiSpacing.sm).padding(top = KireiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm),
        ) {
            covers.forEachIndexed { index, cover -> CoverVariantTile(scope, candidate, cover, index) }
        }
    }
}

@Composable
private fun CoverVariantTile(
    scope: CoverPickerScope,
    candidate: CoverCandidateUi,
    cover: MetadataCover,
    index: Int,
) {
    val colors = scope.colors
    val selected = cover.url == candidate.metadata.coverUrl
    val progress by animateFloatAsState(if (selected) 1f else 0f, selectionSpec(kireiReducedMotion()), label = "coverVariantSelection")
    val caption = CoverVariantCaption.text(cover)
    val preview = rememberCover(cover.url, sourceKey = (candidate.coverSources[cover.url] ?: candidate.source).key)
    Column(
        Modifier.width(VariantWidth)
            .selectable(selected, enabled = !scope.search.saving, role = Role.RadioButton) { scope.viewModel.selectCoverVariant(candidate.key, cover.url) }
            .semantics(mergeDescendants = true) { contentDescription = "封面 ${index + 1}${if (caption.isEmpty()) "" else "，$caption"}" },
    ) {
        Box(Modifier.size(VariantWidth, VariantHeight)) {
            CoverThumbnail(preview, colors, progress, Modifier.matchParentSize())
            SelectionRing(progress, colors.accent, KireiShapes.md, Modifier.matchParentSize())
        }
        if (caption.isNotEmpty()) {
            Text(caption, color = colors.secondary, style = AppleTypography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = KireiSpacing.xxs))
        }
    }
}

/** Until the first source answers: two rows of card skeletons (static under reduced motion). */
@Composable
private fun CoverResultsLoading(
    scope: CoverPickerScope,
    columns: Int,
) {
    Column(Modifier.fillMaxSize().clipToBounds(), verticalArrangement = Arrangement.spacedBy(KireiSpacing.sm)) {
        Text(
            "正在查询全部来源…",
            color = scope.colors.secondary,
            style = AppleTypography.labelLarge,
            modifier = Modifier.padding(start = KireiSpacing.xxs).semantics { liveRegion = LiveRegionMode.Polite },
        )
        repeat(SKELETON_ROWS) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(KireiSpacing.sm)) {
                repeat(columns) { CoverSkeletonCard(scope, Modifier.weight(1f)) }
            }
        }
    }
}

private const val SKELETON_ROWS = 2

@Composable
private fun CoverSkeletonCard(
    scope: CoverPickerScope,
    modifier: Modifier,
) {
    Row(
        modifier.clip(KireiShapes.lg).background(scope.cardFill).border(KireiElevation.strokeWidth, scope.colors.stroke, KireiShapes.lg).padding(CardPadding),
    ) {
        KireiSkeleton(scope.colors, Modifier.size(ThumbWidth, ThumbHeight), KireiShapes.md)
        Column(Modifier.weight(1f).padding(start = KireiSpacing.sm, top = KireiSpacing.xxs), verticalArrangement = Arrangement.spacedBy(KireiSpacing.xs)) {
            KireiSkeleton(scope.colors, Modifier.fillMaxWidth(.8f).height(SKELETON_TITLE), KireiShapes.xs)
            KireiSkeleton(scope.colors, Modifier.fillMaxWidth(.55f).height(SKELETON_LINE), KireiShapes.xs)
            KireiSkeleton(scope.colors, Modifier.fillMaxWidth(.4f).height(SKELETON_LINE), KireiShapes.xs)
        }
    }
}

private val SKELETON_TITLE = 16.dp
private val SKELETON_LINE = 12.dp

/** Nothing found (or nothing reachable): say why and offer to search again; scrolls in short panes. */
@Composable
private fun CoverResultsEmpty(scope: CoverPickerScope) {
    val unreachable = CoverPickerLayoutPolicy.allUnreachable(scope.search)
    val spec = CoverPickerLayoutPolicy.emptyState(unreachable, scope.state.includeAdult)
    val colors = scope.colors
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            verticalArrangement = Arrangement.Center,
        ) {
            KireiEmptyState(spec, colors, onAction = scope.viewModel::retryCoverSearch) {
                Box(Modifier.size(EMPTY_WELL).background(colors.surfaceRaised, KireiShapes.full), contentAlignment = Alignment.Center) {
                    Icon(if (unreachable) Icons.Rounded.CloudOff else Icons.Rounded.ImageSearch, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(EMPTY_ICON))
                }
            }
        }
    }
}

private val EMPTY_WELL = 56.dp
private val EMPTY_ICON = 28.dp
