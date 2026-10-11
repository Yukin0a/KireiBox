package com.kireibox.ui
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.components.KireiBadge
import com.kireibox.designsystem.components.KireiBadgeTone
import com.kireibox.designsystem.components.KireiMenu
import com.kireibox.designsystem.components.KireiMenuItem
import com.kireibox.designsystem.components.KireiMenuItemSpec
import com.kireibox.designsystem.components.KireiSkeleton
import com.kireibox.designsystem.components.kireiE2Shadow
import com.kireibox.designsystem.components.kireiPressScale
import com.kireibox.designsystem.components.kireiShadow
import com.kireibox.designsystem.components.rememberPressProgress
import com.kireibox.designsystem.onDanger
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

// Library sort menu and the game card.

/**
 * Library sort trigger plus its KireiMenu: a 48dp glass icon button like its neighbours, the same in
 * the browse header and in edit mode. The menu marks the current choice with a check.
 */
@Composable
internal fun LibraryEditSortMenu(
    value: String,
    colors: KireiColors,
    onChange: (String) -> Unit,
    haze: HazeState? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = value.ifBlank { "recent" }
    Box {
        GlassIconButton(Icons.AutoMirrored.Rounded.Sort, "排序：${choiceLabel(selected)}", colors, haze, onClick = { expanded = true }, opaqueFill = true)
        KireiMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            colors = colors,
            modifier = Modifier.widthIn(min = 180.dp),
        ) {
            listOf("recent", "newest", "name", YukiSettingsStore.SORT_MANUAL).forEach { option ->
                KireiMenuItem(KireiMenuItemSpec(choiceLabel(option), selected = selected == option), colors) {
                    expanded = false
                    onChange(option)
                }
            }
        }
    }
}

/** Engine glyph of a filter chip and of the no-cover fallback. */
internal fun libraryEngineIcon(engine: EngineType?): ImageVector =
    when (engine) {
        EngineType.KIRIKIRI -> EngineIcons.Kirikiri
        EngineType.ONS -> EngineIcons.Ons
        EngineType.TYRANO -> EngineIcons.Tyrano
        EngineType.ARTEMIS -> EngineIcons.Artemis
        EngineType.ANDROID_APP -> Icons.Rounded.Android
        EngineType.WINDOWS -> Icons.Rounded.DesktopWindows
        else -> Icons.Rounded.Gamepad
    }

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun GameCard(
    game: Game,
    colors: KireiColors,
    modifier: Modifier = Modifier,
    editMode: Boolean,
    removing: Boolean,
    reducedMotion: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    customOrderPosition: Int?,
    customSelectionActive: Boolean,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit,
    onRemove: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    // Read in the layer scope below: the jiggle never recomposes the card. Reduced motion: no jiggle.
    val jiggle =
        if (editMode && !reducedMotion) {
            rememberInfiniteTransition(label = "libraryEdit").animateFloat(
                initialValue = -EDIT_JIGGLE_DEGREES,
                targetValue = EDIT_JIGGLE_DEGREES,
                animationSpec = infiniteRepeatable(tween(125), RepeatMode.Reverse),
                label = "cardJiggle"
            )
        } else {
            null
        }
    val removeProgress by animateFloatAsState(
        targetValue = if (removing) 0f else 1f,
        animationSpec = tween(if (reducedMotion) 120 else 180),
        label = "cardRemoval"
    )
    val engineLabel = libraryCardEngineLabel(game.engine)
    val nsfwBlurred = game.nsfw && LocalNsfwBlurEnabled.current
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer {
                val scale = kireiPressScale(press) * (REMOVE_MIN_SCALE + (1f - REMOVE_MIN_SCALE) * removeProgress)
                rotationZ = jiggle?.value ?: 0f
                alpha = removeProgress
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (!editMode) {
                        onOpen()
                    } else if (customSelectionActive) {
                        onEditClick()
                    }
                },
                onLongClick = onLongClick
            )
    ) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(LibraryCardLayoutPolicy.coverAspectRatio(compact))
                .libraryCoverFrame(KireiShapes.lg, colors)
        ) {
            LibraryCardCover(
                game = game,
                colors = colors,
                role = CoverImageRole.LIBRARY_CARD,
                blurred = nsfwBlurred,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.TopStart).padding(if (compact) KireiSpacing.xs else KireiSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editMode && customOrderPosition != null) {
                    KireiBadge(customOrderPosition.toString(), colors, tone = KireiBadgeTone.ACCENT)
                }
                if (game.favorite) LibraryCoverBadge(Icons.Rounded.Favorite, "已收藏", compact)
                if (nsfwBlurred) LibraryCoverBadge(Icons.Rounded.VisibilityOff, "已模糊", compact)
            }
            if (editMode) LibraryRemoveButton(colors, compact, Modifier.align(Alignment.TopEnd), onRemove)
        }
        Text(
            game.title.orEmpty(),
            color = colors.text,
            maxLines = LibraryCardLayoutPolicy.titleMaxLines(compact),
            overflow = TextOverflow.Ellipsis,
            style = if (compact) CompactCardTitleStyle else CardTitleStyle,
            modifier = Modifier.padding(top = LibraryCardLayoutPolicy.titleGapDp(compact).dp),
        )
        val now = remember { System.currentTimeMillis() }
        Text(
            "$engineLabel · ${LibraryLastPlayedLabel.of(game.lastPlayedAt, now)}",
            color = colors.secondary,
            style = if (compact) AppleTypography.labelMedium else AppleTypography.labelLarge,
            modifier = Modifier.padding(top = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The cover: the decoded bitmap, a skeleton while it decodes, and the engine fallback for a game
 * without a (decodable) cover. A cover that never arrives falls back after [COVER_SKELETON_LIMIT_MS]
 * instead of shimmering forever.
 */
@Composable
private fun LibraryCardCover(
    game: Game,
    colors: KireiColors,
    role: CoverImageRole,
    blurred: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val bitmap =
            rememberCover(
                uri = game.coverUri,
                role = role,
                componentWidthPx = with(density) { maxWidth.roundToPx() },
                componentHeightPx = with(density) { maxHeight.roundToPx() },
                blurred = blurred,
            )
        val hasCover = !game.coverUri.isNullOrBlank()
        var gaveUp by remember(game.coverUri) { mutableStateOf(false) }
        if (bitmap == null && hasCover && !gaveUp) {
            LaunchedEffect(game.coverUri) {
                delay(COVER_SKELETON_LIMIT_MS)
                gaveUp = true
            }
        }
        when {
            bitmap != null ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = CoverDecodePolicy.filterQuality(role),
                )
            hasCover && !gaveUp -> KireiSkeleton(colors, Modifier.fillMaxSize(), RectangleShape)
            else -> LibraryCoverFallback(game.engine, Modifier.fillMaxSize())
        }
    }
}

/** No cover: the engine's default gradient with its glyph centered; the engine name is the card's meta line. */
@Composable
private fun LibraryCoverFallback(
    engine: EngineType?,
    modifier: Modifier,
) {
    val brush =
        remember(engine) {
            val (start, end) = engineCoverColors(engine)
            Brush.linearGradient(listOf(start, end))
        }
    Box(modifier.background(brush), contentAlignment = Alignment.Center) {
        Icon(
            libraryEngineIcon(engine),
            contentDescription = null,
            tint = KireiTokens.OnImage.ink.copy(alpha = FALLBACK_GLYPH_ALPHA),
            modifier = Modifier.size(FALLBACK_GLYPH_SIZE),
        )
    }
}

/**
 * Small glass badge on the cover (favourite, NSFW-blurred): a light scrim with a hairline and white
 * ink, ≥ 3:1 over any cover (WCAG 1.4.11); a page-colored glass with an accent heart was ~2.2:1.
 */
@Composable
private fun LibraryCoverBadge(
    icon: ImageVector,
    description: String,
    compact: Boolean,
) {
    Box(
        Modifier.size(if (compact) COMPACT_BADGE_SIZE else BADGE_SIZE)
            .background(KireiTokens.OnImage.labelScrim.copy(alpha = BADGE_SCRIM_ALPHA), KireiShapes.full)
            .border(KireiElevation.strokeWidth, KireiTokens.OnImage.ink.copy(alpha = BADGE_STROKE_ALPHA), KireiShapes.full),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = KireiTokens.OnImage.ink, modifier = Modifier.size(if (compact) COMPACT_BADGE_GLYPH else BADGE_GLYPH))
    }
}

/** Edit-mode 移出游戏库: a 28dp danger disc inside a 48dp touch target in the cover corner. */
@Composable
private fun LibraryRemoveButton(
    colors: KireiColors,
    compact: Boolean,
    modifier: Modifier,
    onRemove: () -> Unit,
) {
    Box(modifier.size(REMOVE_TOUCH_SIZE).clickable(role = Role.Button, onClick = onRemove), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(if (compact) COMPACT_REMOVE_SIZE else REMOVE_SIZE)
                .kireiShadow(KireiElevation.controlShadow, KireiShapes.full, KireiElevation.e2ShadowAlpha(colors.isDark))
                .background(colors.danger, KireiShapes.full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Remove, "移出游戏库", tint = colors.onDanger, modifier = Modifier.size(if (compact) COMPACT_REMOVE_GLYPH else REMOVE_GLYPH))
        }
    }
}

/**
 * Library cover frame (plan P4): lg-rounded cover with the e2 lift and a hairline edge, so a
 * pale cover still separates from the light page instead of melting into it.
 */
private fun Modifier.libraryCoverFrame(
    shape: Shape,
    colors: KireiColors,
): Modifier = kireiE2Shadow(shape, colors.isDark).clip(shape).border(KireiElevation.strokeWidth, colors.stroke, shape)

/** Edit-mode wobble: just enough to read as "movable"; the card scales to [REMOVE_MIN_SCALE] while it is removed. */
private const val EDIT_JIGGLE_DEGREES = .45f
private const val REMOVE_MIN_SCALE = .92f
private const val COVER_SKELETON_LIMIT_MS = 4_000L
private const val FALLBACK_GLYPH_ALPHA = .6f
private const val BADGE_SCRIM_ALPHA = .44f
private const val BADGE_STROKE_ALPHA = .28f
private val FALLBACK_GLYPH_SIZE = 40.dp
private val BADGE_SIZE = 24.dp
private val COMPACT_BADGE_SIZE = 20.dp
private val BADGE_GLYPH = 14.dp
private val COMPACT_BADGE_GLYPH = 12.dp
private val REMOVE_TOUCH_SIZE = 48.dp
private val REMOVE_SIZE = 28.dp
private val COMPACT_REMOVE_SIZE = 24.dp
private val REMOVE_GLYPH = 18.dp
private val COMPACT_REMOVE_GLYPH = 16.dp

/**
 * Card titles use whole line boxes (no first/last-line trim): Latin and CJK fonts have different
 * ascent/descent, and trimming made a one-line "Riddle Joker" shorter than "千恋＊万花", so the
 * meta lines of neighbouring cards sat at different heights.
 */
private val UntrimmedLines = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
private val CardTitleStyle = AppleTypography.bodyMedium.copy(lineHeightStyle = UntrimmedLines)
private val CompactCardTitleStyle = AppleTypography.labelLarge.copy(lineHeightStyle = UntrimmedLines)
