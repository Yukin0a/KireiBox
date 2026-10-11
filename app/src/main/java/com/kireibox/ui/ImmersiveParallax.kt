package com.kireibox.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Normalized device tilt in [-1, 1]. The holder identity never changes, so providing it does not
 * recompose anything: read [x]/[y] only inside `graphicsLayer {}` or draw lambdas, which then
 * re-run as layers/draws instead of recompositions.
 */
@Stable
internal class ImmersiveParallaxState {
    var x by mutableFloatStateOf(0f)
        internal set
    var y by mutableFloatStateOf(0f)
        internal set

    companion object {
        /** Shared never-moving instance used when the gyroscope effect is off. */
        val Still = ImmersiveParallaxState()
    }
}

internal val LocalImmersiveParallax = staticCompositionLocalOf { ImmersiveParallaxState.Still }

/** Background pan (plan §6.1: ±10dp) and card offset (±4dp) at full tilt. */
internal const val IMMERSIVE_PARALLAX_BACKDROP_DP = 10f
internal const val IMMERSIVE_PARALLAX_CARD_DP = 4f

/**
 * Turns rotation-vector quaternions into a screen-space tilt relative to how the user is holding
 * the device. The first sample becomes the neutral pose and the baseline slowly follows the
 * current pose, so a normal upright grip reads as centered and long-term drift recentres. The
 * output is low-passed. Pure arithmetic with no allocation per sample.
 */
internal class ImmersiveTiltFilter(
    private val smoothing: Float = .12f,
    private val recentre: Float = .004f,
    private val rangeRadians: Float = .35f,
) {
    private val baseline = FloatArray(4)
    private var hasBaseline = false

    var x = 0f
        private set
    var y = 0f
        private set

    fun reset() {
        hasBaseline = false
        x = 0f
        y = 0f
    }

    /** [qx]..[qw] is the device-to-world unit quaternion; [rotation] is a `Surface.ROTATION_*` value. */
    fun update(
        qx: Float,
        qy: Float,
        qz: Float,
        qw: Float,
        rotation: Int,
    ) {
        if (!hasBaseline) {
            setBaseline(qx, qy, qz, qw)
            hasBaseline = true
        }
        val sign = if (baseline[0] * qx + baseline[1] * qy + baseline[2] * qz + baseline[3] * qw < 0f) -1f else 1f
        val cx = qx * sign
        val cy = qy * sign
        val cz = qz * sign
        val cw = qw * sign
        // Relative rotation in the baseline device frame: conj(baseline) * current. For small
        // angles its vector part is half the rotation vector.
        val bx = -baseline[0]
        val by = -baseline[1]
        val bz = -baseline[2]
        val bw = baseline[3]
        val rx = 2f * (bw * cx + bx * cw + by * cz - bz * cy)
        val ry = 2f * (bw * cy - bx * cz + by * cw + bz * cx)
        val screenX = screenAxisX(rx, ry, rotation)
        val screenY = screenAxisY(rx, ry, rotation)
        // Turning around the screen's vertical axis pans horizontally and vice versa.
        val targetX = (screenY / rangeRadians).coerceIn(-1f, 1f)
        val targetY = (screenX / rangeRadians).coerceIn(-1f, 1f)
        x += (targetX - x) * smoothing
        y += (targetY - y) * smoothing
        setBaseline(
            baseline[0] + (cx - baseline[0]) * recentre,
            baseline[1] + (cy - baseline[1]) * recentre,
            baseline[2] + (cz - baseline[2]) * recentre,
            baseline[3] + (cw - baseline[3]) * recentre,
        )
    }

    private fun setBaseline(
        qx: Float,
        qy: Float,
        qz: Float,
        qw: Float,
    ) {
        val length = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).takeIf { it > 1e-6f } ?: 1f
        baseline[0] = qx / length
        baseline[1] = qy / length
        baseline[2] = qz / length
        baseline[3] = qw / length
    }

    private fun screenAxisX(
        rx: Float,
        ry: Float,
        rotation: Int,
    ): Float =
        when (rotation) {
            Surface.ROTATION_90 -> ry
            Surface.ROTATION_180 -> -rx
            Surface.ROTATION_270 -> -ry
            else -> rx
        }

    private fun screenAxisY(
        rx: Float,
        ry: Float,
        rotation: Int,
    ): Float =
        when (rotation) {
            Surface.ROTATION_90 -> -rx
            Surface.ROTATION_180 -> -ry
            Surface.ROTATION_270 -> rx
            else -> ry
        }
}

/**
 * Reads the game rotation vector only while [enabled] and the host is RESUMED; pauses, stops and
 * disposal unregister it. Re-registers (with a fresh neutral pose) after resume and rotation.
 */
@Composable
internal fun rememberImmersiveParallax(enabled: Boolean): ImmersiveParallaxState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val orientation = LocalConfiguration.current.orientation
    val state = remember { ImmersiveParallaxState() }
    DisposableEffect(context, lifecycleOwner, enabled, orientation) {
        state.x = 0f
        state.y = 0f
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor =
            manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (!enabled || manager == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = ImmersiveTiltListener(state, defaultDisplay(context), immersiveParallaxPublishEpsilon(context.resources.displayMetrics.density))
        var registered = false

        fun register() {
            if (!registered && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                listener.filter.reset()
                registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        fun unregister() {
            if (registered) manager.unregisterListener(listener)
            registered = false
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> register()
                    Lifecycle.Event.ON_PAUSE -> unregister()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        register()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
            state.x = 0f
            state.y = 0f
        }
    }
    return if (enabled) state else ImmersiveParallaxState.Still
}

/**
 * Smallest tilt change worth publishing: half a pixel of backdrop pan (the larger of the two
 * parallax ranges). Below it the image would not move, so hand tremor no longer redraws the
 * backdrop, the cards and the dock blur at the full sensor rate.
 */
internal fun immersiveParallaxPublishEpsilon(density: Float): Float = (HALF_PIXEL / (IMMERSIVE_PARALLAX_BACKDROP_DP * density.coerceAtLeast(.5f))).coerceAtLeast(MIN_PUBLISH_EPSILON)

private const val HALF_PIXEL = .5f
private const val MIN_PUBLISH_EPSILON = .002f

private class ImmersiveTiltListener(
    private val state: ImmersiveParallaxState,
    private val display: Display?,
    private val publishEpsilon: Float,
) : SensorEventListener {
    val filter = ImmersiveTiltFilter()

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        if (values.size < 3) return
        val qx = values[0]
        val qy = values[1]
        val qz = values[2]
        val qw = if (values.size >= 4) values[3] else sqrt((1f - qx * qx - qy * qy - qz * qz).coerceAtLeast(0f))
        // Read per sample: a 90° ↔ 270° flip changes no configuration and recreates nothing, so a
        // rotation captured at registration would invert the tilt.
        filter.update(qx, qy, qz, qw, display?.rotation ?: Surface.ROTATION_0)
        if (abs(filter.x - state.x) > publishEpsilon || abs(filter.y - state.y) > publishEpsilon) {
            state.x = filter.x
            state.y = filter.y
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit
}

/** The default display (the launcher's), whose rotation is read on every sensor sample. */
private fun defaultDisplay(context: Context): Display? = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
