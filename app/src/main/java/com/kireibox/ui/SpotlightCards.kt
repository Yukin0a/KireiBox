package com.kireibox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiSpacing
import com.kireibox.designsystem.KireiTokens
import com.kireibox.designsystem.components.KireiButton
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.DashboardStyles
import com.yuki.yukihub.settings.LandscapeHomeStyle

@Composable
internal fun LandscapeSpotlightPage(
    game: Game,
    colors: KireiColors,
    style: LandscapeHomeStyle,
    safeContentPadding: Dp,
    reducedTransparency: Boolean,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
    onSelect: () -> Unit = {},
    onPressedChanged: (Boolean) -> Unit = {},
    cardHeightDp: Float? = null,
    cardBottomGapDp: Float = 0f,
    isCenterCard: Boolean = true,
    interactionEnabled: Boolean = true,
    exposeSwitchSemantics: Boolean = !isCenterCard,
    centerActionEnabled: Boolean = isCenterCard,
    applySideBlur: Boolean = !isCenterCard,
    cardWidthDp: Float = 0f,
    radiusDp: Float = LandscapeHeroSizingPolicy.CENTER_RADIUS_DP,
    cardScale: Float = 1f,
    cardAlpha: Float = 1f,
    cardSaturation: Float = 1f,
    infoAlpha: Float = 1f,
    /** False when the FocusGalleryStage outer card already owns the artwork (D stage). */
    drawArtwork: Boolean = true,
    /** False when the outer stage already draws the D style border. */
    drawStyleBorder: Boolean = true,
) {
    // C and D share the same focus-card pipeline; the style controls geometry and whether
    // the immersive 16:9 card edge highlight is drawn.
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = if (cardHeightDp == null) Alignment.Center else Alignment.BottomCenter,
    ) {
        PressScaleBox(
            Modifier
                .then(
                    if (cardHeightDp == null) {
                        Modifier.fillMaxHeight()
                    } else {
                        Modifier.height(cardHeightDp.dp)
                    }
                )
                .offset(y = -cardBottomGapDp.dp)
                .width(cardWidthDp.dp)
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .then(
                    if (!applySideBlur) {
                        Modifier
                    } else {
                        Modifier.blur(LandscapeHeroSizingPolicy.SIDE_BLUR_DP.dp)
                            .saturationEffect(cardSaturation)
                    }
                )
                .then(
                    if (centerActionEnabled) {
                        Modifier.semantics {
                            contentDescription = "查看 ${game.title.orEmpty()}"
                        }
                    } else if (exposeSwitchSemantics) {
                        Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "切换到 ${game.title.orEmpty()}"
                            role = Role.Button
                        }
                    } else {
                        Modifier
                    }
                ),
            RoundedCornerShape(radiusDp.dp),
            if (centerActionEnabled) onOpen else onSelect,
            pressedScale = .992f,
            enabled = interactionEnabled,
            exposeClickSemantics = centerActionEnabled || exposeSwitchSemantics || infoAlpha > 0f,
            onPressedChanged = onPressedChanged,
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radiusDp.dp))) {
                if (drawArtwork) {
                    FocusCoverArtwork(
                        game = game,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = radiusDp.dp,
                        role = CoverImageRole.HERO,
                        style = style,
                        darkTheme = colors.isDark,
                        focusAlpha = infoAlpha,
                    )
                }
                if (drawStyleBorder && style == LandscapeHomeStyle.IMMERSIVE_COVER) {
                    FocusStageDOutline(
                        radiusDp = radiusDp.dp,
                        infoAlpha = infoAlpha,
                        darkTheme = colors.isDark,
                    )
                }
                val focused = centerActionEnabled && infoAlpha >= .99f
                if (infoAlpha > 0f) {
                    // Local bottom-26% gradient capped at .78 keeps the metadata readable
                    // without a half-black card.
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .then(
                                if (cardHeightDp == null) {
                                    Modifier.fillMaxHeight(.46f)
                                } else {
                                    Modifier.height(cardHeightDp.dp * .46f)
                                },
                            )
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = .78f),
                                    ),
                                ),
                            )
                            .graphicsLayer { alpha = infoAlpha },
                    )
                    // The launch control leads the title stack. The title owns the full card
                    // width, so normal titles keep two unobstructed lines instead of sharing a
                    // row with the play button.
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .graphicsLayer { alpha = infoAlpha },
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            PlayTriangleButton(
                                colors = colors,
                                description = "启动 ${game.title.orEmpty()}",
                                size = 54.dp,
                                onClick = onLaunch,
                                enabled = focused,
                            )
                        }
                        Text(
                            game.engine?.displayName ?: "游戏",
                            color = Color.White.copy(alpha = .78f),
                            style = AppleTypography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            game.title.orEmpty(),
                            color = Color.White,
                            style =
                                AppleTypography.headlineMedium.copy(
                                    shadow = Shadow(Color.Black.copy(alpha = .65f), blurRadius = 2f),
                                ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One shared play-button size rule for both home modes. It mirrors the SpotlightPage cell
 * rule exactly: cells narrower than 240dp use 48dp, cells shorter than 216dp use 54dp,
 * and regular cells use 62dp. The immersive home resolves its center card frame through
 * the same resolver so both modes agree on the same device.
 */
internal object SharedControlSizePolicy {
    const val NARROW_CELL_WIDTH_DP = 240f
    const val COMPACT_CELL_HEIGHT_DP = 216f
    const val PLAY_NARROW_DP = 48f
    const val PLAY_COMPACT_DP = 54f
    const val PLAY_REGULAR_DP = 62f

    fun playSizeDp(
        cellWidthDp: Float,
        cellHeightDp: Float
    ): Float =
        when {
            cellWidthDp < NARROW_CELL_WIDTH_DP -> PLAY_NARROW_DP
            cellHeightDp < COMPACT_CELL_HEIGHT_DP -> PLAY_COMPACT_DP
            else -> PLAY_REGULAR_DP
        }
}

@Composable
internal fun SpotlightPage(
    game: Game,
    colors: KireiColors,
    styleKey: String,
    onOpen: () -> Unit,
    onLaunch: () -> Unit,
) {
    if (styleKey == DashboardStyles.SPOTLIGHT_HORIZONTAL) {
        PressScaleBox(Modifier.fillMaxSize(), KireiShapes.lg, onOpen, pressedScale = .985f) {
            Row(
                Modifier.fillMaxSize().background(colors.surface, KireiShapes.lg)
                    .border(KireiElevation.strokeWidth, colors.stroke, KireiShapes.lg).padding(KireiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GameCoverArtwork(
                    game,
                    Modifier.fillMaxHeight().widthIn(min = 72.dp, max = 150.dp).aspectRatio(.9f),
                    ContentScale.Crop,
                    KireiShapes.Radius.md,
                    role = CoverImageRole.HERO,
                )
                Column(Modifier.weight(1f).padding(horizontal = KireiSpacing.sm)) {
                    Text(game.title.orEmpty(), color = colors.text, style = AppleTypography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(game.engine?.displayName.orEmpty(), color = colors.secondary, style = AppleTypography.labelLarge)
                }
                PlayTriangleButton(colors, "启动 ${game.title.orEmpty()}", 48.dp, onClick = onLaunch)
            }
        }
        return
    }
    PressScaleBox(Modifier.fillMaxSize(), KireiShapes.lg, onOpen, pressedScale = .985f) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
            val narrow = maxWidth < 240.dp
            val compactHeight = maxHeight < 216.dp
            val inset =
                if (narrow) {
                    16.dp
                } else if (compactHeight) {
                    20.dp
                } else {
                    28.dp
                }
            val playSize = SharedControlSizePolicy.playSizeDp(maxWidth.value, maxHeight.value).dp
            val ink = KireiTokens.OnImage.ink
            val scrim = KireiTokens.Immersive.DarkBase
            GameCoverArtwork(
                game,
                Modifier.fillMaxSize(),
                if (styleKey == DashboardStyles.SPOTLIGHT_POSTER) ContentScale.Fit else ContentScale.Crop,
                role = CoverImageRole.HERO,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(scrim.copy(alpha = .04f), scrim.copy(alpha = .18f), scrim.copy(alpha = .91f))
                    )
                )
            )
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = inset, top = inset, end = inset + playSize + 12.dp, bottom = inset)
            ) {
                Text(game.engine?.displayName ?: "游戏", color = ink.copy(alpha = .78f), style = AppleTypography.bodySmall)
                Text(
                    game.title.orEmpty(),
                    color = ink,
                    style = if (narrow || compactHeight) AppleTypography.headlineSmall else AppleTypography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = KireiSpacing.xxs)
                )
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(inset)) {
                PlayTriangleButton(
                    colors = colors,
                    description = "启动 ${game.title.orEmpty()}",
                    size = playSize,
                    onClick = onLaunch
                )
            }
        }
    }
}

@Composable
internal fun HomeEmptySpotlight(
    colors: KireiColors,
    emptyState: ImmersiveGalleryEmptyState,
    onChooseDirectory: () -> Unit,
    onOpenSpotlightEditor: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(310.dp)
) {
    GlassSurface(colors, null, modifier, KireiShapes.lg) {
        Column(Modifier.fillMaxSize().padding(KireiSpacing.xl), verticalArrangement = Arrangement.Center) {
            when (emptyState) {
                ImmersiveGalleryEmptyState.BUILD_LIBRARY -> {
                    Icon(Icons.Rounded.CollectionsBookmark, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Text("开始建立游戏库", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = KireiSpacing.md))
                    Text(
                        "选择目录后，YukiHub 会识别 KRKR、ONS、Tyrano 与 Artemis。",
                        color = colors.secondary,
                        style = AppleTypography.bodyMedium,
                        modifier = Modifier.padding(top = KireiSpacing.xs),
                    )
                    KireiButton(
                        label = "选择游戏目录",
                        colors = colors,
                        modifier = Modifier.padding(top = KireiSpacing.lg),
                        onClick = onChooseDirectory,
                    )
                }
                ImmersiveGalleryEmptyState.SELECT_GAMES -> {
                    Icon(Icons.Rounded.Edit, null, tint = colors.accent, modifier = Modifier.size(48.dp))
                    Text("请选择游戏", color = colors.text, style = AppleTypography.headlineSmall, modifier = Modifier.padding(top = KireiSpacing.md))
                    Text("在设置中选择要展示的游戏。", color = colors.secondary, style = AppleTypography.bodyMedium, modifier = Modifier.padding(top = KireiSpacing.xs))
                    KireiButton(
                        label = "打开设置",
                        colors = colors,
                        modifier = Modifier.padding(top = KireiSpacing.lg),
                        onClick = onOpenSpotlightEditor,
                    )
                }
                ImmersiveGalleryEmptyState.NO_PLAY_HISTORY -> {
                    Text("暂无游玩记录", color = colors.text, style = AppleTypography.headlineSmall)
                    Text("游玩任意游戏后，这里会显示最近游玩。", color = colors.secondary, style = AppleTypography.bodyMedium, modifier = Modifier.padding(top = KireiSpacing.xs))
                }
                ImmersiveGalleryEmptyState.NO_FAVORITES -> {
                    Text("暂无收藏", color = colors.text, style = AppleTypography.headlineSmall)
                }
            }
        }
    }
}
