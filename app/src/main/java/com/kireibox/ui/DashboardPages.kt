package com.kireibox.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.LandscapeHomeStyle

// Cover artwork, dashboard widgets, insight rail and the dashboard editor.

@Composable
internal fun GameCoverArtwork(
    game: Game,
    modifier: Modifier,
    contentScale: ContentScale,
    cornerRadius: Dp = 12.dp,
    role: CoverImageRole = CoverImageRole.THUMBNAIL,
    showFallbackMetadata: Boolean = true,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius))) {
        val density = LocalDensity.current
        val bitmap =
            rememberCover(
                uri = game.coverUri,
                role = role,
                componentWidthPx = with(density) { maxWidth.roundToPx() },
                componentHeightPx = with(density) { maxHeight.roundToPx() },
                blurred = game.nsfw && LocalNsfwBlurEnabled.current,
            )
        if (bitmap == null) {
            EngineDefaultCover(
                engine = game.engine,
                modifier = Modifier.fillMaxSize(),
                showFallbackMetadata = showFallbackMetadata,
            )
        } else {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                filterQuality = CoverDecodePolicy.filterQuality(role),
            )
        }
    }
}

@Composable
internal fun EngineDefaultCover(
    engine: EngineType?,
    modifier: Modifier = Modifier,
    showFallbackMetadata: Boolean = true,
) {
    val (start, end) = engineCoverColors(engine)
    val ink = KireiTokens.OnImage.ink
    val icon =
        when (engine) {
            EngineType.KIRIKIRI -> Icons.Rounded.AutoStories
            EngineType.ONS -> Icons.Rounded.Description
            EngineType.TYRANO -> Icons.Rounded.Terminal
            else -> Icons.Rounded.Layers
        }
    Box(modifier.background(Brush.linearGradient(listOf(start, end)))) {
        Box(Modifier.size(96.dp).align(Alignment.TopEnd).background(ink.copy(alpha = .07f), KireiShapes.full))
        if (showFallbackMetadata) {
            Column(Modifier.align(Alignment.BottomStart).padding(KireiSpacing.sm)) {
                Icon(icon, null, tint = ink.copy(alpha = .9f), modifier = Modifier.size(26.dp))
                Text(engine?.displayName ?: "游戏", color = ink, style = AppleTypography.labelLarge, modifier = Modifier.padding(top = KireiSpacing.xxs))
                Text("KireiBox", color = ink.copy(alpha = .62f), style = AppleTypography.labelSmall)
            }
        } else {
            Icon(
                icon,
                null,
                tint = ink.copy(alpha = .9f),
                modifier = Modifier.align(Alignment.BottomStart).padding(KireiSpacing.sm).size(26.dp),
            )
        }
    }
}

/**
 * Engine default-cover gradients: the one allowed literal-color exception for pages (design spec
 * §9), because they are placeholder artwork rather than chrome. Each pair is a muted light→deep
 * ramp in the sakura/graphite family (no purple) that keeps the white placeholder ink readable.
 */
private object EngineCoverGradients {
    /** Kirikiri: sakura rose → deep wine. */
    val Kirikiri = Color(0xFFC75B7E) to Color(0xFF5B2A3D)

    /** ONScripter: ocean blue → deep navy slate. */
    val Ons = Color(0xFF4F7FC4) to Color(0xFF22395E)

    /** Tyrano: amber clay → deep umber. */
    val Tyrano = Color(0xFFC8845D) to Color(0xFF6E4532)

    /** Artemis: mint teal → deep pine. */
    val Artemis = Color(0xFF3F9A8A) to Color(0xFF1C4943)

    /** Android apps and unknown engines: neutral graphite. */
    val Neutral = Color(0xFF5E5E68) to Color(0xFF26262C)
}

/** Engine theme gradient shared by the missing-cover placeholder and portrait extension. */
internal fun engineCoverColors(engine: EngineType?): Pair<Color, Color> =
    when (engine) {
        EngineType.KIRIKIRI -> EngineCoverGradients.Kirikiri
        EngineType.ONS -> EngineCoverGradients.Ons
        EngineType.TYRANO -> EngineCoverGradients.Tyrano
        EngineType.ARTEMIS -> EngineCoverGradients.Artemis
        else -> EngineCoverGradients.Neutral
    }

/**
 * Style-aware hero cover artwork for the C/D focus stage.
 *
 * Matched-aspect sources draw one full-bleed Crop layer (single cover, no letterbox).
 * Severely mismatched sources (>= 1.35x) draw an adaptive stack: the same bitmap is
 * cropped, slightly enlarged, blurred (~16dp), desaturated and dimmed as the in-card
 * fill, with the complete artwork rendered on top via ContentScale.Fit. This keeps every
 * card full-bleed and never shows a half cover, black bars or a readable double cover:
 * the fill reads as atmosphere behind the full foreground image.
 */
@Composable
internal fun FocusCoverArtwork(
    game: Game,
    modifier: Modifier,
    cornerRadius: Dp,
    role: CoverImageRole = CoverImageRole.HERO,
    style: LandscapeHomeStyle,
    darkTheme: Boolean,
    focusAlpha: Float,
) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(cornerRadius))) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.roundToPx() }.toFloat()
        val targetHeightPx = with(density) { maxHeight.roundToPx() }.toFloat()
        val bitmap =
            rememberCover(
                uri = game.coverUri,
                role = role,
                componentWidthPx = with(density) { maxWidth.roundToPx() },
                componentHeightPx = with(density) { maxHeight.roundToPx() },
                blurred = game.nsfw && LocalNsfwBlurEnabled.current,
            )
        if (bitmap == null) {
            EngineDefaultCover(game.engine, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val plan =
            remember(
                bitmap.width,
                bitmap.height,
                targetWidthPx.toInt(),
                targetHeightPx.toInt(),
                style,
            ) {
                FocusCoverArtworkPolicy.plan(
                    srcWidthPx = bitmap.width,
                    srcHeightPx = bitmap.height,
                    targetWidthPx = targetWidthPx,
                    targetHeightPx = targetHeightPx,
                    style = style,
                )
            }
        if (plan.mode == FocusCoverMode.CROP) {
            androidx.compose.foundation.Image(
                bitmap,
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val blend =
                remember(focusAlpha) {
                    FocusCoverArtworkPolicy.adaptiveBlend(focusAlpha)
                }
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = FocusCoverArtworkPolicy.FILL_SCALE
                            scaleY = FocusCoverArtworkPolicy.FILL_SCALE
                            alpha = blend.blurredFillAlpha
                        }
                        .blur(FocusCoverArtworkPolicy.FILL_BLUR_DP.dp)
                        .saturationEffect(FocusCoverArtworkPolicy.FILL_SATURATION),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        if (darkTheme) {
                            Color.Black.copy(
                                alpha = FocusCoverArtworkPolicy.FILL_DARK_ALPHA * blend.overlayStrength,
                            )
                        } else {
                            Color.White.copy(
                                alpha = FocusCoverArtworkPolicy.FILL_LIGHT_ALPHA * blend.overlayStrength,
                            )
                        }
                    )
                )
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = blend.sharpCropAlpha
                    },
                    contentScale = ContentScale.Crop,
                )
                androidx.compose.foundation.Image(
                    bitmap,
                    null,
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = blend.fitAlpha
                    },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
internal fun HomeInsightRail(
    gameCount: Int,
    favoriteCount: Int,
    playTime: Long,
    colors: KireiColors,
    modifier: Modifier = Modifier.fillMaxWidth(),
    compact: Boolean = false,
    styleKey: String = DashboardStyles.OVERVIEW_SPLIT,
    onOpenLibrary: () -> Unit
) {
    val playValue = LibraryOverviewPolicy.summaryPlayTime(playTime)
    GlassSurface(colors, null, modifier, KireiShapes.lg, KireiGlassRole.CONTENT, onOpenLibrary) {
        if (styleKey == DashboardStyles.OVERVIEW_TILES) {
            Row(
                Modifier.fillMaxSize().padding(KireiSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(KireiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("游戏" to "$gameCount", "收藏" to "$favoriteCount", "时长" to playValue).forEach { (label, value) ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().background(colors.surfaceRaised, KireiShapes.md)
                            .padding(horizontal = KireiSpacing.xs),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HomeStatValue(value, colors, AppleTypography.numericMedium)
                        Text(label, color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1)
                    }
                }
            }
        } else if (styleKey == DashboardStyles.OVERVIEW_FOCUS) {
            Column(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalArrangement = Arrangement.Center) {
                HomeStatValue(playValue, colors, AppleTypography.numericLarge, numberColor = colors.accent)
                Text("累计游玩 · $gameCount 款游戏 · $favoriteCount 款收藏", color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 1)
            }
        } else if (styleKey == DashboardStyles.OVERVIEW_RING) {
            Row(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(if (compact) 48.dp else 64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { if (gameCount == 0) 0f else favoriteCount.toFloat() / gameCount },
                        color = colors.accent,
                        trackColor = colors.surface3,
                        strokeWidth = if (compact) 6.dp else 8.dp
                    )
                    Text("$favoriteCount", color = colors.text, style = AppleTypography.numericMedium)
                }
                Column(Modifier.padding(start = KireiSpacing.sm).weight(1f)) {
                    Text("累计游玩", color = colors.secondary, style = AppleTypography.labelLarge)
                    HomeStatValue(playValue, colors, if (compact) AppleTypography.numericMedium else AppleTypography.numericLarge)
                    Text("$gameCount 部游戏", color = colors.secondary, style = AppleTypography.labelLarge)
                }
            }
        } else if (compact || styleKey == DashboardStyles.OVERVIEW_MINIMAL) {
            Column(Modifier.fillMaxSize().padding(KireiSpacing.md), verticalArrangement = Arrangement.Center) {
                Text("累计游玩", color = colors.secondary, style = AppleTypography.labelLarge)
                HomeStatValue(playValue, colors, AppleTypography.numericLarge)
                Text("$gameCount 部游戏 · $favoriteCount 部收藏", color = colors.secondary, style = AppleTypography.labelLarge, maxLines = 2)
            }
        } else {
            // Label on top, value on the bottom edge: the content fills the card instead of floating in it.
            Row(Modifier.fillMaxSize().padding(KireiSpacing.md)) {
                Column(
                    Modifier.weight(1.65f).fillMaxHeight().padding(end = KireiSpacing.md),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("累计游玩", color = colors.secondary, style = AppleTypography.labelLarge)
                    HomeStatValue(playValue, colors, AppleTypography.numericLarge)
                }
                Box(Modifier.width(KireiElevation.strokeWidth).fillMaxHeight().background(colors.stroke))
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = KireiSpacing.md),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    HomeInsight("游戏", "$gameCount 部", colors)
                    HomeInsight("收藏", "$favoriteCount 部", colors)
                }
            }
        }
    }
}

@Composable
internal fun HomeInsight(
    label: String,
    value: String,
    colors: KireiColors,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.secondary, style = AppleTypography.labelLarge)
        HomeStatValue(value, colors, AppleTypography.numericMedium)
    }
}

/**
 * A stat value with its unit split off ("277 小时", "14 部"): the number in [numberStyle], the unit in small
 * secondary text on the same baseline, like the settings overview card.
 */
@Composable
private fun HomeStatValue(
    value: String,
    colors: KireiColors,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    numberColor: Color = colors.text,
) {
    val number = value.substringBefore(' ')
    val unit = value.substringAfter(' ', missingDelimiterValue = "")
    Row(modifier) {
        Text(number, color = numberColor, style = numberStyle, maxLines = 1, modifier = Modifier.alignByBaseline())
        if (unit.isNotEmpty()) {
            Text(
                unit,
                color = colors.secondary,
                style = AppleTypography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = KireiSpacing.xxs).alignByBaseline(),
            )
        }
    }
}
