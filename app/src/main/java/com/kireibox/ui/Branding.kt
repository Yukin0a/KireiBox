package com.kireibox.ui

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yuki.yukihub.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ChisaKi brand mark (the launcher tile artwork) for the about page, the settings overview and
 * empty states. It reuses the launcher foreground already in the APK instead of shipping a second
 * copy of the same image. Decorative uses pass a null [contentDescription].
 *
 * The 697 px source is decoded once per process, off the main thread and at half size (≈ 350 px,
 * enough for the largest 112 dp use), and shared: opening the Settings tab or an empty state no
 * longer pays a synchronous full-size PNG decode every time it is composed.
 */
@Composable
internal fun KireiMascot(
    modifier: Modifier = Modifier,
    contentDescription: String? = "ChisaKi 吉祥物",
) {
    val resources = LocalContext.current.resources
    val mark by produceState(initialValue = KireiMascotBitmap.cached, resources) {
        if (value == null) value = withContext(Dispatchers.IO) { KireiMascotBitmap.load(resources) }
    }
    val image = mark
    val description = contentDescription
    if (image == null) {
        // Same footprint and semantics while the first decode runs.
        Box(if (description == null) modifier else modifier.semantics { this.contentDescription = description })
    } else {
        Image(bitmap = image, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

/** Process-wide half-size decode of the brand mark. */
internal object KireiMascotBitmap {
    @Volatile
    var cached: ImageBitmap? = null
        private set

    fun load(resources: Resources): ImageBitmap? =
        cached ?: runCatching {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = SAMPLE_SIZE
                    inScaled = false
                }
            BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground, options)?.asImageBitmap()
        }.getOrNull()?.also { cached = it }

    private const val SAMPLE_SIZE = 2
}
