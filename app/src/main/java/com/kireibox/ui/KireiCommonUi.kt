package com.kireibox.ui
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kireibox.designsystem.AppleMotion
import com.kireibox.designsystem.AppleTypography
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiElevation
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.LocalResolvedMotionMode
import com.kireibox.designsystem.components.KireiBadge
import com.yuki.yukihub.settings.MotionDecisionInputs
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.MotionModePolicy
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

// Shared shell widgets: glass controls, dock, notices and settings rows.

@Composable
internal fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    colors: KireiColors,
    haze: HazeState?,
    onClick: () -> Unit,
    opaqueFill: Boolean = false,
) {
    GlassSurface(
        colors,
        haze,
        Modifier.size(48.dp),
        CircleShape,
        KireiGlassRole.ICON,
        onClick,
        opaqueFill = opaqueFill,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, description, tint = colors.text) }
    }
}

@Composable
internal fun PlayTriangleButton(
    colors: KireiColors,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val effectiveClick: () -> Unit =
        if (enabled) {
            onClick
        } else {
            { }
        }
    PressScaleBox(
        Modifier.size(size).semantics {
            contentDescription = description
            if (!enabled) disabled()
        },
        CircleShape,
        effectiveClick,
        pressedScale = .9f,
        enabled = enabled,
    ) {
        Box(
            Modifier.fillMaxSize().background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                null,
                tint = colors.onAccent,
                modifier = Modifier.size(size * .56f).graphicsLayer { translationX = size.value * .035f }
            )
        }
    }
}

/**
 * What the shared Dock shows: the selected tab, the 设置 tab's badge (cover reviews or the one-time
 * 快速设置 dot, [DockSettingsBadgePolicy]), its measured frame and whether glass is allowed.
 */
internal data class MainDockSpec(
    val tab: KireiTab,
    val settingsBadge: DockTabBadge,
    val metrics: AdaptiveUiMetrics,
    val reducedTransparency: Boolean,
)

@Composable
internal fun KireiMainDock(
    spec: MainDockSpec,
    colors: KireiColors,
    haze: HazeState?,
    viewModel: KireiViewModel,
    modifier: Modifier = Modifier,
) {
    val tab = spec.tab
    val metrics = spec.metrics
    val reducedTransparency = spec.reducedTransparency
    val rect = resolveDockRenderRect(metrics.dockRect)
    val presentation =
        resolveDockPresentation(
            rect.w,
            rect.h,
            forceVertical = metrics.orientation == HomeOrientation.PORTRAIT,
        )
    val shape = RoundedCornerShape((rect.h / 2f).dp)
    val dockHaze =
        Alpha11KireiBoxAppIntegration.dockHaze(
            isDark = colors.isDark,
            // Increased contrast swaps glass for the solid surfaceOverlay (spec §2.1), same as reduced transparency.
            reducedTransparency = reducedTransparency || colors.highContrast,
            hazeAvailable = haze != null,
            apiSupportsHaze = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    Box(modifier.fillMaxSize()) {
        GlassSurface(
            colors = colors,
            haze = if (dockHaze.useOpaqueFallback) null else haze,
            modifier =
                Modifier.offset(rect.x.dp, rect.y.dp).size(rect.w.dp, rect.h.dp)
                    .testTag("kirei-main-dock"),
            shape = shape,
            role = KireiGlassRole.DOCK,
            containerColor = dockHaze.backgroundColor,
            tint = dockHaze.tint,
            hazeInputScale = dockHaze.inputScale,
        ) {
            Box(Modifier.fillMaxSize()) {
                // Sliding selection capsule behind the selected tab (spec §7 Spring.Emphasized), drawn
                // under the unchanged button row so the dock geometry stays exactly as measured.
                DockSelectionCapsule(DockTabs.indexOf(tab), presentation, rect.h, colors)
                Row(
                    Modifier.fillMaxSize().padding(horizontal = presentation.horizontalPaddingDp.dp),
                    horizontalArrangement = Arrangement.spacedBy(presentation.slotGapDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DockButton(DockTabSpec.HOME, tab == KireiTab.HOME, colors, presentation) { viewModel.selectTab(KireiTab.HOME) }
                    DockButton(DockTabSpec.GAMES, tab == KireiTab.GAMES, colors, presentation) { viewModel.selectTab(KireiTab.GAMES) }
                    DockButton(
                        DockTabSpec.SETTINGS.copy(badge = spec.settingsBadge),
                        tab == KireiTab.SETTINGS,
                        colors,
                        presentation,
                    ) { viewModel.selectTab(KireiTab.SETTINGS) }
                }
            }
        }
    }
}

/** Dock tab order; the capsule slot of a tab is its index here. */
private val DockTabs = listOf(KireiTab.HOME, KireiTab.GAMES, KireiTab.SETTINGS)

/** One dock tab: label, filled icon when selected, outlined icon otherwise, optional badge. */
internal data class DockTabSpec(
    val label: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badge: DockTabBadge = DockTabBadge.None,
) {
    companion object {
        val HOME = DockTabSpec("首页", Icons.Rounded.Home, Icons.Outlined.Home)
        val GAMES = DockTabSpec("游戏", Icons.Rounded.Gamepad, Icons.Outlined.Gamepad)
        val SETTINGS = DockTabSpec("设置", Icons.Rounded.Settings, Icons.Outlined.Settings)
    }
}

/** Accent-container capsule that slides between dock slots (instant under reduced motion). */
@Composable
private fun BoxScope.DockSelectionCapsule(
    selectedIndex: Int,
    presentation: DockPresentation,
    dockHeightDp: Float,
    colors: KireiColors,
) {
    if (selectedIndex < 0) return
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val slot =
        animateFloatAsState(
            targetValue = selectedIndex.toFloat(),
            animationSpec = if (reducedMotion) snap() else AppleMotion.Spring.Emphasized,
            label = "dockCapsule",
        )
    // Size never depends on the slot; the slide is read in the placement lambda (no recomposition).
    val frame = DockSelectionCapsulePolicy.geometry(presentation, dockHeightDp, selectedIndex.toFloat())
    Box(
        Modifier.align(Alignment.CenterStart)
            .offset { IntOffset(DockSelectionCapsulePolicy.geometry(presentation, dockHeightDp, slot.value).xDp.dp.roundToPx(), 0) }
            .size(frame.widthDp.dp, frame.heightDp.dp)
            .background(colors.accentContainer, KireiShapes.full),
    )
}

/** Selection capsule frame inside the dock, in dp relative to the dock's left edge. */
internal data class DockCapsuleGeometry(
    val xDp: Float,
    val widthDp: Float,
    val heightDp: Float,
)

/**
 * Pure geometry of the dock selection capsule. It never changes the dock: the capsule sits behind
 * the button at [slot] (fractional while sliding), centered on it and widened so the outer slots
 * keep the same inset from the dock edge as the top and bottom (at most one slot gap per side).
 */
internal object DockSelectionCapsulePolicy {
    const val VERTICAL_INSET_DP = 6f
    const val HORIZONTAL_INSET_DP = 5f

    fun geometry(
        presentation: DockPresentation,
        dockHeightDp: Float,
        slot: Float,
    ): DockCapsuleGeometry {
        val inset = if (presentation.layout == DockPresentationLayout.VERTICAL) VERTICAL_INSET_DP else HORIZONTAL_INSET_DP
        val grow = (presentation.horizontalPaddingDp - inset).coerceIn(0f, presentation.slotGapDp)
        val slotX = presentation.horizontalPaddingDp + slot * (presentation.buttonWidthDp + presentation.slotGapDp)
        return DockCapsuleGeometry(
            xDp = slotX - grow,
            widthDp = presentation.buttonWidthDp + grow * 2f,
            heightDp = (dockHeightDp - inset * 2f).coerceAtLeast(0f),
        )
    }
}

internal enum class DockPresentationLayout { VERTICAL, HORIZONTAL }

internal data class DockPresentation(
    val layout: DockPresentationLayout,
    val touchHeightDp: Float,
    val contentHeightDp: Float,
    val buttonWidthDp: Float,
    val slotGapDp: Float,
    val iconSizeDp: Float,
    val labelHeightDp: Float,
    val labelFontSizeSp: Float,
    val gapDp: Float,
    val verticalPaddingDp: Float,
    val horizontalPaddingDp: Float,
)

/**
 * Resolves the shared dock presentation from the measured dock rect. Alpha 8 dock
 * contract: the three buttons keep fixed widths (82dp vertical / 96dp horizontal) and the
 * row centers the group; only when the rect cannot fit the fixed group are the buttons
 * shrunk evenly so the dock never overflows.
 */
internal fun resolveDockPresentation(
    rectWidthDp: Float,
    rectHeightDp: Float,
    forceVertical: Boolean = false,
): DockPresentation {
    val width = rectWidthDp.coerceAtLeast(1f)
    val height = rectHeightDp.coerceAtLeast(1f)
    val slotGap = 12f
    val minimumButtonWidth = 48f
    // Alpha 10: portrait docks are always vertical icon+label so every phone ratio renders
    // the same layout; the short-height horizontal fallback stays available for landscape.
    val horizontal = !forceVertical && height < 64f
    val fixedButtonWidth = if (horizontal) 96f else 82f
    val minPadding = if (horizontal) (height * .16f).coerceIn(6f, 10f) else (height * .22f).coerceIn(10f, 18f)
    val reserved = fixedButtonWidth * 3f + slotGap * 2f
    val fitsFixed = width >= reserved + minPadding * 2f
    val compactButtonWidth = ((width - minPadding * 2f - slotGap * 2f) / 3f).coerceAtLeast(0f)
    val minimumGroupWidth = minimumButtonWidth * 3f + slotGap * 2f
    val buttonWidth =
        when {
            fitsFixed -> fixedButtonWidth
            compactButtonWidth >= minimumButtonWidth -> compactButtonWidth
            width >= minimumGroupWidth -> minimumButtonWidth
            else -> (width - minPadding * 2f - slotGap * 2f).coerceAtLeast(0f) / 3f
        }
    val horizontalPadding =
        if (fitsFixed) {
            (width - reserved) / 2f
        } else {
            ((width - buttonWidth * 3f - slotGap * 2f) / 2f).coerceAtLeast(0f)
        }
    if (horizontal) {
        val icon = (height * .36f).coerceIn(14f, 18f)
        val font = (height * .27f).coerceIn(10f, 13f)
        val labelHeight = font * 1.2f
        val gap = (height * .08f).coerceIn(2f, 5f)
        return DockPresentation(
            layout = DockPresentationLayout.HORIZONTAL,
            touchHeightDp = maxOf(48f, height),
            contentHeightDp = (maxOf(icon, labelHeight) + gap).coerceAtMost(height),
            buttonWidthDp = buttonWidth,
            slotGapDp = slotGap,
            iconSizeDp = icon,
            labelHeightDp = labelHeight,
            labelFontSizeSp = font,
            gapDp = gap,
            verticalPaddingDp = 0f,
            horizontalPaddingDp = horizontalPadding,
        )
    }
    val icon = (height * .23f).coerceIn(18f, 23f)
    val font = (height * .13f).coerceIn(11f, 14f)
    val labelHeight = font * 1.2f
    val gap = (height * .025f).coerceIn(1f, 3f)
    val padding = ((height - icon - labelHeight - gap) / 2f).coerceAtLeast(0f)
    return DockPresentation(
        layout = DockPresentationLayout.VERTICAL,
        touchHeightDp = maxOf(48f, height),
        contentHeightDp = icon + labelHeight + gap + padding * 2f,
        buttonWidthDp = buttonWidth,
        slotGapDp = slotGap,
        iconSizeDp = icon,
        labelHeightDp = labelHeight,
        labelFontSizeSp = font,
        gapDp = gap,
        verticalPaddingDp = padding,
        horizontalPaddingDp = horizontalPadding,
    )
}

/**
 * Keeps the shared dock's hit surface and its label row usable on short landscape windows.
 * The caller's measured dock remains the visual anchor; expansion is centered so switching
 * between the immersive and component pages cannot move the dock's optical center.
 */
internal fun resolveDockRenderRect(rect: HomeRect): HomeRect {
    val minimumHeight = 48f
    if (rect.h >= minimumHeight) return rect
    val extra = minimumHeight - rect.h
    return rect.copy(y = rect.y - extra / 2f, h = minimumHeight)
}

@Composable
internal fun DockButton(
    tabSpec: DockTabSpec,
    selected: Boolean,
    colors: KireiColors,
    presentation: DockPresentation,
    onClick: () -> Unit,
) {
    val reducedMotion = LocalResolvedMotionMode.current == MotionMode.REDUCED
    val tint by animateColorAsState(
        if (selected) colors.accent else colors.secondary,
        tween(if (reducedMotion) 0 else AppleMotion.Duration.MEDIUM),
        label = "dockTint",
    )
    val icon = if (selected) tabSpec.selectedIcon else tabSpec.icon
    val labelStyle =
        AppleTypography.labelMedium.copy(
            fontSize = presentation.labelFontSizeSp.sp,
            lineHeight = presentation.labelHeightDp.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    // r5 · B: the one-time 快速设置 dot rides on the icon; the name then mentions it (「设置，有新的设置建议」).
    val description = DockSettingsBadgePolicy.description(tabSpec.label, tabSpec.badge)
    val glyph: @Composable () -> Unit = {
        Box {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(presentation.iconSizeDp.dp))
            if (tabSpec.badge == DockTabBadge.Suggestion) DockSuggestionDot(colors, Modifier.align(Alignment.TopEnd))
        }
    }
    Box(Modifier.width(presentation.buttonWidthDp.dp).heightIn(min = presentation.touchHeightDp.dp)) {
        // Selected state and tab role live on the clickable node itself, the one TalkBack focuses.
        // The visible label names the tab; the icon is decorative (no "首页 首页").
        PressScaleBox(
            Modifier.fillMaxSize().semantics {
                this.selected = selected
                role = Role.Tab
                description?.let { contentDescription = it }
            },
            KireiShapes.full,
            onClick,
            pressedScale = .92f,
        ) {
            if (presentation.layout == DockPresentationLayout.HORIZONTAL) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    glyph()
                    Text(
                        tabSpec.label,
                        color = tint,
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = presentation.gapDp.dp),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = presentation.verticalPaddingDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    glyph()
                    Text(
                        tabSpec.label,
                        color = tint,
                        style = labelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        CountBadge(
            count = (tabSpec.badge as? DockTabBadge.Count)?.count ?: 0,
            colors = colors,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
        )
    }
}

@Composable
internal fun CountBadge(
    count: Int,
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val presentation = CoverTaskBadgePolicy.presentation(count)
    if (!presentation.visible) return
    KireiBadge(
        text = presentation.label,
        colors = colors,
        modifier =
            modifier.height(presentation.heightDp.dp)
                .sizeIn(minWidth = presentation.minWidthDp.dp)
                .clearAndSetSemantics { contentDescription = "${presentation.accessibilityCount} 个封面待处理" },
    )
}

internal enum class KireiGlassRole { CONTENT, DOCK, ICON, NOTICE, ALERT }

/** Fill and lift of a [GlassSurface] (spec §5). */
internal object GlassFillPolicy {
    /**
     * Light-theme icon buttons are opaque surfaceOverlay, like the opaque header/overflow buttons
     * next to them: on a pale backdrop the white glass reads the same, and an opaque fill can carry
     * its lift without the shadow showing through.
     */
    fun opaque(
        role: KireiGlassRole,
        opaqueFill: Boolean,
        isDark: Boolean,
    ): Boolean = opaqueFill || (role == KireiGlassRole.ICON && !isDark)

    /**
     * e1 content has no shadow, icon buttons a light lift, the floating roles the e3 shadow. A
     * translucent icon fill gets none: its outline shadow would show through the glass as a hard
     * polygon (the light-theme "octagon"); the strokeStrong hairline outlines it instead.
     */
    fun elevation(
        role: KireiGlassRole,
        translucent: Boolean,
    ): Dp =
        when (role) {
            KireiGlassRole.CONTENT -> 0.dp
            KireiGlassRole.ICON -> if (translucent) 0.dp else ICON_LIFT
            else -> KireiElevation.e3Shadow
        }

    private val ICON_LIFT = 4.dp
}

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun GlassSurface(
    colors: KireiColors,
    haze: HazeState?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    role: KireiGlassRole = KireiGlassRole.CONTENT,
    onClick: (() -> Unit)? = null,
    reducedMotion: Boolean = false,
    containerColor: Color? = null,
    tint: Color? = null,
    hazeInputScale: HazeInputScale? = null,
    opaqueFill: Boolean = false,
    content: @Composable () -> Unit
) {
    // Spec §5: CONTENT is an e1 surface; the floating roles are e3 glass in both themes (glass
    // token under Haze on API 31+, opaque surfaceOverlay without it) with a strokeStrong edge.
    val contentRole = role == KireiGlassRole.CONTENT
    val base = containerColor ?: if (contentRole) colors.surface else colors.glass
    val solidBase = containerColor ?: if (contentRole) colors.surface else colors.surfaceOverlay
    val hazeBackgroundColor = containerColor?.takeUnless { it.alpha == 0f } ?: colors.background
    val blur =
        if (role == KireiGlassRole.CONTENT) {
            0.dp
        } else if (role == KireiGlassRole.DOCK) {
            28.dp
        } else {
            24.dp
        }
    val border = if (contentRole) colors.stroke else colors.strokeStrong
    val opaque = GlassFillPolicy.opaque(role, opaqueFill, colors.isDark)
    val elevation = GlassFillPolicy.elevation(role, translucent = !opaque && haze != null)
    val shadowInk = KireiElevation.shadowColor(KireiElevation.e3ShadowAlpha(colors.isDark))
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale =
        if (reducedMotion) {
            1f
        } else {
            animateFloatAsState(if (pressed) .97f else 1f, AppleMotion.Spring.Press, label = "glassPress").value
        }
    Box(
        modifier.shadow(elevation, shape, clip = false, ambientColor = shadowInk, spotColor = shadowInk)
            .then(
                if (onClick != null) {
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (opaque) {
                    Modifier.background(solidBase.copy(alpha = 1f))
                } else if (haze != null && role != KireiGlassRole.CONTENT) {
                    Modifier.hazeEffect(haze) {
                        blurRadius = blur
                        inputScale = hazeInputScale ?: HazeInputScale.Auto
                        noiseFactor = 0f
                        backgroundColor = hazeBackgroundColor
                        tints = listOf(dev.chrisbanes.haze.HazeTint(tint ?: base))
                    }
                } else {
                    Modifier.background(solidBase)
                }
            )
            .border(.6.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
internal fun PressScaleBox(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    pressedScale: Float = .97f,
    enabled: Boolean = true,
    exposeClickSemantics: Boolean = true,
    onPressedChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) { onPressedChanged(pressed) }
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, AppleMotion.Spring.Press, label = "pressScale")
    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .then(if (enabled) Modifier else Modifier.semantics { disabled() })
            .then(if (exposeClickSemantics) Modifier else Modifier.clearAndSetSemantics { })
    ) { content() }
}

internal fun resolveMotionMode(
    context: Context,
    requested: MotionMode
): MotionMode {
    val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return MotionModePolicy.resolve(
        requested,
        MotionDecisionInputs(
            animatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
            lowRamDevice = activity.isLowRamDevice,
            memoryClassMb = activity.memoryClass,
            powerSaveMode = power.isPowerSaveMode
        )
    )
}
