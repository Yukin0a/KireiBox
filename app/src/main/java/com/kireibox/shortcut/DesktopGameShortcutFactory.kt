package com.kireibox.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import com.kireibox.app.LauncherActivity
import com.yuki.yukihub.R
import com.yuki.yukihub.model.Game
import java.io.File
import java.io.InputStream

/** Creates only KireiBox-owned pinned shortcuts; it never writes launcher databases. */
object DesktopGameShortcutFactory {
    const val ACTION_LAUNCH_GAME = "com.kireibox.action.LAUNCH_GAME"
    const val EXTRA_GAME_ID = "com.kireibox.extra.GAME_ID"
    private const val ID_PREFIX = "kireibox-game-"

    enum class PinOutcome { REQUESTED, UPDATED }

    fun shortcutId(gameId: Long): String = "$ID_PREFIX$gameId"

    fun launchIntent(
        context: Context,
        gameId: Long
    ): Intent =
        Intent(ACTION_LAUNCH_GAME).apply {
            component = ComponentName(context, LauncherActivity::class.java)
            putExtra(EXTRA_GAME_ID, gameId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

    fun requestPin(
        context: Context,
        game: Game
    ): Result<PinOutcome> =
        runCatching {
            require(game.id > 0L) { "游戏尚未保存，无法添加桌面快捷方式。" }
            val manager =
                context.getSystemService(ShortcutManager::class.java)
                    ?: error("系统不支持桌面快捷方式。")
            check(manager.isRequestPinShortcutSupported) { "当前桌面不支持固定快捷方式。" }
            val id = shortcutId(game.id)
            val info =
                ShortcutInfo.Builder(context, id)
                    .setShortLabel(game.title?.trim().orEmpty().ifEmpty { "KireiBox 游戏" })
                    .setLongLabel(game.title?.trim().orEmpty().ifEmpty { "KireiBox 游戏" })
                    .setIcon(coverIcon(context, game) ?: Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(launchIntent(context, game.id))
                    .build()
            if (manager.pinnedShortcuts.any { it.id == id }) {
                check(manager.updateShortcuts(listOf(info))) { "桌面拒绝更新现有快捷方式。" }
                PinOutcome.UPDATED
            } else {
                check(manager.requestPinShortcut(info, null)) { "桌面拒绝了固定快捷方式请求。" }
                PinOutcome.REQUESTED
            }
        }

    private fun coverIcon(
        context: Context,
        game: Game
    ): Icon? =
        runCatching {
            val source = decodeCover(context, game.coverUri) ?: return null
            val size = 256
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
            val width = source.width * scale
            val height = source.height * scale
            val destination =
                RectF(
                    (size - width) / 2f,
                    (size - height) / 2f,
                    (size + width) / 2f,
                    (size + height) / 2f,
                )
            val clip = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 46f, 46f, Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(source, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            if (source !== output) source.recycle()
            Icon.createWithBitmap(output)
        }.getOrNull()

    private fun decodeCover(
        context: Context,
        raw: String?
    ): Bitmap? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return if (value.startsWith("content://") || value.startsWith("file://")) {
            val uri = Uri.parse(value)
            decodeSampled { context.contentResolver.openInputStream(uri) }
        } else {
            decodeSampled { File(value).inputStream() }
        }
    }

    private fun decodeSampled(open: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > 512) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        return open()?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}
