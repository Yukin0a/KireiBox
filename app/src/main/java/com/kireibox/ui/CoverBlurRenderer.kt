package com.kireibox.ui

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

/** Bitmap-level blur used before a sensitive cover enters the shared role caches. */
@Suppress("DEPRECATION")
internal object CoverBlurRenderer {
    const val RADIUS = 22f
    private const val PIXELATION_DIVISOR = 8

    fun apply(
        context: Context,
        source: Bitmap
    ): Bitmap? {
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        if (mutable !== source && !source.isRecycled) source.recycle()
        return try {
            blurWithRenderScript(context.applicationContext, mutable)
        } catch (_: Throwable) {
            pixelate(mutable)
        }
    }

    private fun blurWithRenderScript(
        context: Context,
        bitmap: Bitmap
    ): Bitmap {
        val renderScript = RenderScript.create(context)
        var input: Allocation? = null
        var output: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            input = Allocation.createFromBitmap(renderScript, bitmap)
            output = Allocation.createTyped(renderScript, input.type)
            blur =
                ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript)).apply {
                    setInput(input)
                    setRadius(RADIUS)
                    forEach(output)
                }
            output.copyTo(bitmap)
            return bitmap
        } finally {
            blur?.destroy()
            output?.destroy()
            input?.destroy()
            renderScript.destroy()
        }
    }

    private fun pixelate(bitmap: Bitmap): Bitmap? =
        try {
            val small =
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width / PIXELATION_DIVISOR).coerceAtLeast(1),
                    (bitmap.height / PIXELATION_DIVISOR).coerceAtLeast(1),
                    true,
                )
            val pixelated = Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, false)
            if (small !== bitmap && !small.isRecycled) small.recycle()
            if (pixelated !== bitmap && !bitmap.isRecycled) bitmap.recycle()
            pixelated
        } catch (_: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            null
        }
}
