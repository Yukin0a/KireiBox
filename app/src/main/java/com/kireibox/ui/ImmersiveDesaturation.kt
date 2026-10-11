package com.kireibox.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import kotlin.math.roundToInt

/** Saturation steps of 5%: a swipe re-creates the color filter about 20 times, not every frame. */
private const val SATURATION_STEPS = 20f

/** Quantizes a requested saturation to the filter steps (1 = untouched artwork). */
internal fun immersiveSaturationStep(saturation: Float): Float {
    val safe = saturation.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f
    return (safe * SATURATION_STEPS).roundToInt() / SATURATION_STEPS
}

/**
 * Side-card desaturation for the immersive gallery (plan §6.1 侧卡去色) on every API level: a
 * ColorMatrix saveLayer around the artwork. The node keeps one Paint and only rebuilds its filter
 * when the quantized saturation changes; at full saturation it draws straight through. It is
 * separate from KireiShell.saturationEffect, which components mode keeps unchanged.
 */
internal fun Modifier.immersiveDesaturate(saturation: Float): Modifier = this then ImmersiveDesaturateElement(immersiveSaturationStep(saturation))

private data class ImmersiveDesaturateElement(
    val saturation: Float,
) : ModifierNodeElement<ImmersiveDesaturateNode>() {
    override fun create() = ImmersiveDesaturateNode(saturation)

    override fun update(node: ImmersiveDesaturateNode) = node.update(saturation)

    override fun InspectorInfo.inspectableProperties() {
        name = "immersiveDesaturate"
        properties["saturation"] = saturation
    }
}

private class ImmersiveDesaturateNode(
    private var saturation: Float,
) : Modifier.Node(), DrawModifierNode {
    private val paint = Paint()
    private val matrix = ColorMatrix()

    init {
        applyFilter()
    }

    fun update(next: Float) {
        if (next == saturation) return
        saturation = next
        applyFilter()
        if (isAttached) invalidateDraw()
    }

    private fun applyFilter() {
        matrix.setToSaturation(saturation)
        paint.colorFilter = ColorFilter.colorMatrix(matrix)
    }

    override fun ContentDrawScope.draw() {
        if (saturation >= 1f) {
            drawContent()
            return
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(size.toRect(), paint)
            drawContent()
            canvas.restore()
        }
    }
}
