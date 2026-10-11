package com.kireibox.ui

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import com.yuki.yukihub.settings.MotionMode
import com.yuki.yukihub.settings.YukiSettingsStore

/** Accessibility and device conditions that downgrade immersive effects (plan §6.4). */
@Immutable
internal data class ImmersiveFxEnvironment(
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
    val increasedContrast: Boolean = false,
    val lowRamDevice: Boolean = false,
    /** Debug fixture only: clocks and sensors stay still so screenshots are deterministic. */
    val frozenMotion: Boolean = false,
)

/**
 * Immersive effects after the plan §6.4 degradation matrix. The renderer reads only this object,
 * so every effect follows the same rules.
 */
@Immutable
internal data class ResolvedImmersiveFx(
    val backdrop: ImmersiveBackdropStyle,
    val intensity: FxLevel,
    val kenBurns: Boolean,
    val auroraDrift: Boolean,
    val grain: Boolean,
    val vignette: Boolean,
    val cardDepth: FxLevel,
    val glow: Boolean,
    val rimLight: Boolean,
    val sheen: Boolean,
    val dynamicTint: Boolean,
    val ambient: ImmersiveAmbient,
    val gyroParallax: Boolean,
    /** 切换方式 (portrait only); reduced motion always crossfades, so it resolves to 缓和. */
    val cardSwitch: ImmersiveCardSwitch,
    val environment: ImmersiveFxEnvironment,
    val source: ImmersiveVisualSettings,
) {
    val reducedTransparency: Boolean get() = environment.reducedTransparency

    val increasedContrast: Boolean get() = environment.increasedContrast

    /** False under reduced motion; decorative transitions then snap. */
    val motionEnabled: Boolean get() = !environment.reducedMotion

    /** Whether a frame clock must tick: Ken Burns, aurora drift or falling particles. */
    val needsMotionClock: Boolean
        get() = !environment.frozenMotion && (kenBurns || auroraDrift || ambient != ImmersiveAmbient.NONE)

    /** Re-applies the per-call accessibility flags a caller passes explicitly (fixtures, legacy params). */
    fun reconciled(
        reducedMotion: Boolean,
        reducedTransparency: Boolean,
    ): ResolvedImmersiveFx {
        val next =
            environment.copy(
                reducedMotion = environment.reducedMotion || reducedMotion,
                reducedTransparency = environment.reducedTransparency || reducedTransparency,
            )
        return if (next == environment) this else resolveImmersiveFx(source, next)
    }
}

/**
 * The single place where persisted settings meet the degradation matrix:
 * - low-RAM devices are capped at the standard preset;
 * - reduced motion stops Ken Burns, aurora drift, particles, sheen and the gyroscope;
 * - reduced transparency swaps image backdrops (封面氛围, 封面虚化, 流光极光) for the tone backdrop and drops grain;
 * - increased contrast is carried through for deeper scrims and title shadows.
 */
internal fun resolveImmersiveFx(
    settings: ImmersiveVisualSettings,
    environment: ImmersiveFxEnvironment = ImmersiveFxEnvironment(),
): ResolvedImmersiveFx {
    val capped = if (environment.lowRamDevice) settings.cappedAtStandard() else settings
    val motion = !environment.reducedMotion
    val live = motion && !environment.frozenMotion
    val backdrop = if (environment.reducedTransparency) capped.backdrop.withoutImagery() else capped.backdrop
    return ResolvedImmersiveFx(
        backdrop = backdrop,
        intensity = capped.backdropIntensity,
        kenBurns = capped.kenBurns && motion && backdrop.drawsCoverImage(environment.reducedTransparency),
        auroraDrift = motion && backdrop == ImmersiveBackdropStyle.AURORA,
        grain = capped.grain && !environment.reducedTransparency,
        vignette = capped.vignette,
        cardDepth = capped.cardDepth,
        glow = capped.glow,
        rimLight = capped.rimLight,
        sheen = capped.sheen && live,
        dynamicTint = capped.dynamicTint,
        ambient = if (motion) capped.ambient else ImmersiveAmbient.NONE,
        gyroParallax = capped.gyroParallax && live,
        cardSwitch = if (motion) capped.cardSwitch else ImmersiveCardSwitch.SOFT,
        environment = environment,
        source = settings,
    )
}

/** Low-RAM ceiling: nothing above the standard preset (plan §6.4). */
private fun ImmersiveVisualSettings.cappedAtStandard(): ImmersiveVisualSettings =
    copy(
        backdrop = if (backdrop == ImmersiveBackdropStyle.AURORA) ImmersiveBackdropStyle.BLUR_COVER else backdrop,
        backdropIntensity = backdropIntensity.atMost(FxLevel.MEDIUM),
        cardDepth = cardDepth.atMost(FxLevel.MEDIUM),
        kenBurns = false,
        grain = false,
        sheen = false,
        ambient = ImmersiveAmbient.NONE,
        gyroParallax = false,
    )

private fun FxLevel.atMost(limit: FxLevel): FxLevel = if (ordinal > limit.ordinal) limit else this

/**
 * Reduced transparency: the tone backdrop (without its faint cover, so a solid tinted base) replaces
 * the faint cover of 封面氛围, blurred and aurora imagery — no cover pixels at all.
 */
private fun ImmersiveBackdropStyle.withoutImagery(): ImmersiveBackdropStyle =
    when (this) {
        ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveBackdropStyle.BLUR_COVER, ImmersiveBackdropStyle.AURORA -> ImmersiveBackdropStyle.TONE
        ImmersiveBackdropStyle.TONE, ImmersiveBackdropStyle.SOLID -> this
    }

/** Whether the backdrop paints cover pixels that Ken Burns and parallax can move. */
internal fun ImmersiveBackdropStyle.drawsCoverImage(reducedTransparency: Boolean): Boolean = this != ImmersiveBackdropStyle.SOLID && !reducedTransparency

/** rotationY multiplier for the side-card tilt: 平 / 标准 / 强 (MEDIUM keeps the existing tilt). */
internal fun FxLevel.cardDepthScale(): Float =
    when (this) {
        FxLevel.LOW -> .5f
        FxLevel.MEDIUM -> 1f
        FxLevel.HIGH -> 1.3f
    }

/** Backdrop layer alpha multiplier (every layer, the raw and blurred cover included): LOW / MEDIUM / HIGH map the blurred cover to .45 / .55 / .65 before the dark-theme depth scale. */
internal fun FxLevel.backdropIntensityScale(): Float =
    when (this) {
        FxLevel.LOW -> .82f
        FxLevel.MEDIUM -> 1f
        FxLevel.HIGH -> 1.18f
    }

/** App-wide resolved effects; [rememberImmersiveFx] provides the real value from KireiBoxApp. */
internal val LocalImmersiveFx = staticCompositionLocalOf { resolveImmersiveFx(ImmersiveVisualSettings()) }

/** Resolves the persisted visual settings against the current accessibility state and device class. */
@Composable
internal fun rememberImmersiveFx(
    settings: YukiSettingsStore.Snapshot?,
    resolvedMotion: MotionMode,
): ResolvedImmersiveFx {
    val context = LocalContext.current
    val lowRam = remember(context) { context.isLowRamDevice() }
    val visual = settings?.immersiveVisual ?: ImmersiveVisualSettings()
    val environment =
        ImmersiveFxEnvironment(
            reducedMotion = resolvedMotion == MotionMode.REDUCED,
            reducedTransparency = settings?.reducedTransparency == true,
            increasedContrast = settings?.increasedContrast == true,
            lowRamDevice = lowRam,
        )
    return remember(visual, environment) { resolveImmersiveFx(visual, environment) }
}

private fun Context.isLowRamDevice(): Boolean = (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
