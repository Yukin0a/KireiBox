package com.kireibox.ui

import android.view.Surface
import com.yuki.yukihub.settings.FxLevel
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ImmersiveVisualSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Plan §6.4 degradation matrix and the allocation-free effect primitives behind it. */
class ImmersiveFxRuntimeTest {
    private val standard = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.STANDARD)
    private val rich = ImmersiveVisualSettings.forPreset(ImmersiveFxPreset.RICH)

    @Test
    fun standardIsCalmAndNeedsNoFrameClock() {
        val fx = resolveImmersiveFx(standard)

        // r5 (user decision, supersedes r4's 氛围色): the default preset is 封面氛围 — the alpha.16
        // background; still calm: nothing moves it, and no vignette darkens its corners.
        assertEquals(ImmersiveBackdropStyle.COVER_ZOOM, fx.backdrop)
        assertEquals(ImmersiveBackdropCoverSource.ATMOSPHERE, immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency))
        assertTrue(fx.glow && fx.rimLight && fx.dynamicTint)
        assertFalse(fx.kenBurns || fx.auroraDrift || fx.grain || fx.vignette || fx.sheen || fx.gyroParallax)
        assertEquals(ImmersiveAmbient.NONE, fx.ambient)
        assertFalse("nothing animates by default, so no frame clock ticks", fx.needsMotionClock)
        assertEquals("the standard depth keeps the existing side-card tilt", 1f, fx.cardDepth.cardDepthScale(), 0f)
    }

    @Test
    fun richTurnsOnEveryImplementedEffect() {
        val fx = resolveImmersiveFx(rich)

        assertEquals(ImmersiveBackdropStyle.AURORA, fx.backdrop)
        assertTrue(fx.kenBurns && fx.auroraDrift && fx.grain && fx.sheen && fx.gyroParallax)
        assertEquals(ImmersiveAmbient.DUST, fx.ambient)
        assertTrue(fx.needsMotionClock)
        assertTrue("aurora draws over the blurred cover, so Ken Burns has pixels to move", ImmersiveBackdropStyle.AURORA.drawsCoverImage(false))
    }

    @Test
    fun reducedMotionStopsEveryDecorativeMotion() {
        val fx = resolveImmersiveFx(rich, ImmersiveFxEnvironment(reducedMotion = true))

        assertFalse(fx.kenBurns || fx.auroraDrift || fx.sheen || fx.gyroParallax)
        assertEquals("particles are off, not frozen", ImmersiveAmbient.NONE, fx.ambient)
        assertFalse(fx.needsMotionClock)
        assertFalse(fx.motionEnabled)
        assertEquals("static styling stays", ImmersiveBackdropStyle.AURORA, fx.backdrop)
        assertTrue(fx.grain && fx.glow)
    }

    @Test
    fun reducedTransparencyReplacesImageryWithTheToneBackdrop() {
        // r5: no cover pixels either — 封面氛围 becomes the solid tinted tone backdrop.
        listOf(ImmersiveBackdropStyle.COVER_ZOOM, ImmersiveBackdropStyle.BLUR_COVER, ImmersiveBackdropStyle.AURORA).forEach { style ->
            val fx = resolveImmersiveFx(rich.copy(backdrop = style), ImmersiveFxEnvironment(reducedTransparency = true))
            assertEquals(ImmersiveBackdropStyle.TONE, fx.backdrop)
            assertFalse("grain is dropped", fx.grain)
            assertFalse("no cover pixels to pan", fx.kenBurns)
            assertFalse(fx.auroraDrift)
            assertEquals(ImmersiveBackdropCoverSource.NONE, immersiveBackdropCoverSource(fx.backdrop, fx.reducedTransparency))
        }
        val solid = resolveImmersiveFx(standard.copy(backdrop = ImmersiveBackdropStyle.SOLID), ImmersiveFxEnvironment(reducedTransparency = true))
        assertEquals(ImmersiveBackdropStyle.SOLID, solid.backdrop)
    }

    @Test
    fun kenBurnsNeedsACoverLayer() {
        val solid = resolveImmersiveFx(rich.copy(backdrop = ImmersiveBackdropStyle.SOLID))
        assertFalse(solid.kenBurns)
        assertTrue(resolveImmersiveFx(rich.copy(backdrop = ImmersiveBackdropStyle.TONE)).kenBurns)
    }

    @Test
    fun lowRamDevicesAreCappedAtTheStandardPreset() {
        val fx = resolveImmersiveFx(rich, ImmersiveFxEnvironment(lowRamDevice = true))

        assertEquals(ImmersiveBackdropStyle.BLUR_COVER, fx.backdrop)
        assertEquals(FxLevel.MEDIUM, fx.intensity)
        assertEquals(FxLevel.MEDIUM, fx.cardDepth)
        assertFalse(fx.kenBurns || fx.grain || fx.sheen || fx.gyroParallax || fx.auroraDrift)
        assertEquals(ImmersiveAmbient.NONE, fx.ambient)
        assertTrue("standard effects survive the cap", fx.glow && fx.rimLight && fx.dynamicTint)
        assertEquals("the stored settings are untouched", rich, fx.source)
    }

    @Test
    fun increasedContrastIsCarriedToTheRenderer() {
        assertTrue(resolveImmersiveFx(standard, ImmersiveFxEnvironment(increasedContrast = true)).increasedContrast)
        assertFalse(resolveImmersiveFx(standard).increasedContrast)
    }

    @Test
    fun frozenFixtureMotionKeepsStylingButStopsClocksAndSensors() {
        val fx = resolveImmersiveFx(rich, ImmersiveFxEnvironment(frozenMotion = true))

        assertTrue("Ken Burns rests at its idle phase", fx.kenBurns)
        assertFalse(fx.needsMotionClock)
        assertFalse(fx.gyroParallax || fx.sheen)
    }

    @Test
    fun explicitCallerFlagsCanOnlyDegrade() {
        val app = resolveImmersiveFx(rich)

        assertSame("unchanged flags keep the instance", app, app.reconciled(reducedMotion = false, reducedTransparency = false))
        val reduced = app.reconciled(reducedMotion = true, reducedTransparency = true)
        assertEquals(ImmersiveBackdropStyle.TONE, reduced.backdrop)
        assertFalse(reduced.needsMotionClock)
        val alreadyReduced = resolveImmersiveFx(rich, ImmersiveFxEnvironment(reducedMotion = true))
        assertFalse("a caller cannot re-enable motion", alreadyReduced.reconciled(reducedMotion = false, reducedTransparency = false).motionEnabled)
    }

    @Test
    fun levelScalesFollowThePlanWording() {
        assertTrue(FxLevel.LOW.cardDepthScale() < 1f && FxLevel.HIGH.cardDepthScale() > 1f)
        assertEquals(.45f, IMMERSIVE_BACKDROP_BLUR_ALPHA * FxLevel.LOW.backdropIntensityScale(), .01f)
        assertEquals(.55f, IMMERSIVE_BACKDROP_BLUR_ALPHA * FxLevel.MEDIUM.backdropIntensityScale(), .0001f)
        assertEquals(.65f, IMMERSIVE_BACKDROP_BLUR_ALPHA * FxLevel.HIGH.backdropIntensityScale(), .01f)
    }

    @Test
    fun motionWavesAreBoundedPeriodicAndSafe() {
        assertEquals(0f, immersiveSawtooth(0f, 10f), 0f)
        assertEquals(.25f, immersiveSawtooth(2.5f, 10f), .0001f)
        assertEquals("wraps instead of reversing", .1f, immersiveSawtooth(11f, 10f), .0001f)
        assertEquals(0f, immersiveSawtooth(Float.NaN, 10f), 0f)
        assertEquals(0f, immersiveSawtooth(3f, 0f), 0f)
        assertEquals(1f, immersiveTriangleWave(5f, 10f), .0001f)
        assertEquals(0f, immersiveTriangleWave(10f, 10f), .0001f)
        (0..240).map { immersiveKenBurnsScale(it / 4f) }.forEach { scale -> assertTrue(scale in 1.05f..1.12f) }
        val samples = (0..99).map { immersivePseudoUnit(it) }
        assertTrue(samples.all { it >= 0f && it < 1f })
        assertEquals(samples, (0..99).map { immersivePseudoUnit(it) })
    }

    @Test
    fun blurSamplingKeepsTheCoverAspectRatio() {
        val portrait = immersiveBlurSampleSize(1080, 1920)
        assertEquals(64, portrait.width)
        assertEquals(1920f / 1080f, portrait.height / portrait.width.toFloat(), .02f)
        val landscape = immersiveBlurSampleSize(1920, 1080)
        assertEquals(64, landscape.height)
        assertEquals(1920f / 1080f, landscape.width / landscape.height.toFloat(), .02f)
        assertEquals(64, immersiveBlurSampleSize(800, 800).width)
        assertEquals(64, immersiveBlurSampleSize(800, 800).height)
        assertEquals("small covers are not upscaled", 20, immersiveBlurSampleSize(10, 20).height)
        val strip = immersiveBlurSampleSize(100, 10_000)
        assertTrue(strip.height <= 128 && strip.width >= 1)
        assertEquals(1, immersiveBlurSampleSize(0, 50).width)
    }

    @Test
    fun boxBlurPreservesFlatColorAndSpreadsDetail() {
        val flat = IntArray(16) { 0xFF336699.toInt() }
        assertTrue(boxBlurArgb(flat, 4, 4, 2).all { it == 0xFF336699.toInt() })
        val dot = IntArray(25) { 0xFF000000.toInt() }.also { it[12] = 0xFFFFFFFF.toInt() }
        val blurred = boxBlurArgb(dot, 5, 5, 1)
        assertTrue("the bright pixel spreads to its neighbour", (blurred[11] and 0xFF) > 0)
        assertTrue((blurred[12] and 0xFF) < 0xFF)
        assertTrue(boxBlurArgb(dot, 5, 5, 0).contentEquals(dot))
    }

    @Test
    fun sideCardDesaturationIsQuantizedSoSwipesRarelyRebuildTheFilter() {
        assertEquals(1f, immersiveSaturationStep(1f), 0f)
        assertEquals(.95f, immersiveSaturationStep(.97f), .0001f)
        assertEquals(.5f, immersiveSaturationStep(.51f), .0001f)
        assertEquals(1f, immersiveSaturationStep(Float.NaN), 0f)
        assertEquals(0f, immersiveSaturationStep(-3f), 0f)
        val distinct = (0..1000).map { immersiveSaturationStep(it / 1000f) }.distinct()
        assertEquals(21, distinct.size)
    }

    @Test
    fun tiltIsMeasuredFromTheGripNotFromTheHorizon() {
        val filter = ImmersiveTiltFilter()
        // A typical upright grip: pitched ~60° about the device X axis.
        val grip = quaternionAboutX(1.05f)
        repeat(30) { filter.update(grip[0], grip[1], grip[2], grip[3], Surface.ROTATION_0) }
        assertEquals("holding still reads as centered", 0f, filter.x, .0001f)
        assertEquals(0f, filter.y, .0001f)
        // The same pose with the opposite quaternion sign is the same rotation.
        filter.update(-grip[0], -grip[1], -grip[2], -grip[3], Surface.ROTATION_0)
        assertEquals(0f, filter.y, .0001f)
    }

    @Test
    fun turningAroundTheScreenAxesPansTheMatchingDirection() {
        val portrait = ImmersiveTiltFilter()
        portrait.update(0f, 0f, 0f, 1f, Surface.ROTATION_0)
        val yaw = quaternionAboutY(.2f)
        repeat(20) { portrait.update(yaw[0], yaw[1], yaw[2], yaw[3], Surface.ROTATION_0) }
        assertTrue("turning about the vertical axis pans horizontally", portrait.x > .3f)
        assertTrue(abs(portrait.y) < .01f)

        val landscape = ImmersiveTiltFilter()
        landscape.update(0f, 0f, 0f, 1f, Surface.ROTATION_90)
        repeat(20) { landscape.update(yaw[0], yaw[1], yaw[2], yaw[3], Surface.ROTATION_90) }
        assertTrue("display rotation remaps the axes", abs(landscape.y) > .3f)
        assertTrue(abs(landscape.x) < .01f)

        val pitch = ImmersiveTiltFilter()
        pitch.update(0f, 0f, 0f, 1f, Surface.ROTATION_0)
        val far = quaternionAboutX(1.4f)
        repeat(200) { pitch.update(far[0], far[1], far[2], far[3], Surface.ROTATION_0) }
        assertTrue("output stays normalized", abs(pitch.y) <= 1f)
        pitch.reset()
        assertEquals(0f, pitch.y, 0f)
    }

    @Test
    fun theBaselineSlowlyRecentresSoDriftDoesNotPinTheParallax() {
        val filter = ImmersiveTiltFilter()
        filter.update(0f, 0f, 0f, 1f, Surface.ROTATION_0)
        val yaw = quaternionAboutY(.2f)
        repeat(30) { filter.update(yaw[0], yaw[1], yaw[2], yaw[3], Surface.ROTATION_0) }
        val early = filter.x
        repeat(2_000) { filter.update(yaw[0], yaw[1], yaw[2], yaw[3], Surface.ROTATION_0) }
        assertTrue("a held pose eases back toward center", filter.x < early * .2f)
    }

    private fun quaternionAboutX(angle: Float) = floatArrayOf(sin(angle / 2f), 0f, 0f, cos(angle / 2f))

    private fun quaternionAboutY(angle: Float) = floatArrayOf(0f, sin(angle / 2f), 0f, cos(angle / 2f))
}
