package com.kireibox.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiShapes
import com.kireibox.designsystem.KireiTileColor
import com.kireibox.designsystem.components.KireiIconTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Launcher icons of the Android app picker: decoded off the main thread, only for the rows that
// are on screen, and kept in a small LRU that the picker clears when it closes.

internal object AndroidAppIcons {
    private const val MAX_ICONS = 128
    private const val ICON_PX = 128
    private val cache = LruCache<String, ImageBitmap>(MAX_ICONS)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    fun clear() = cache.evictAll()

    suspend fun load(
        context: Context,
        entry: AndroidAppEntry,
    ): ImageBitmap? {
        peek(entry.componentKey)?.let { return it }
        val icon =
            withContext(Dispatchers.IO) {
                runCatching {
                    val drawable = context.packageManager.getActivityIcon(ComponentName(entry.packageName, entry.activityName))
                    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, ICON_PX, ICON_PX)
                    drawable.draw(Canvas(bitmap))
                    bitmap.asImageBitmap()
                }.getOrNull()
            }
        return icon?.also { cache.put(entry.componentKey, it) }
    }
}

/** The app's own launcher icon; a neutral app tile while it loads or when the package cannot be resolved. */
@Composable
internal fun AndroidAppIcon(
    entry: AndroidAppEntry,
    colors: KireiColors,
    size: Dp,
) {
    val context = LocalContext.current
    val icon by produceState(AndroidAppIcons.peek(entry.componentKey), entry.componentKey) {
        if (value == null) value = AndroidAppIcons.load(context.applicationContext, entry)
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(KireiShapes.sm))
    } else {
        val tile = KireiTileColor.GRAY
        KireiIconTile(Icons.Rounded.Android, tile.container(colors.isDark), tile.glyph(colors.isDark), size)
    }
}
