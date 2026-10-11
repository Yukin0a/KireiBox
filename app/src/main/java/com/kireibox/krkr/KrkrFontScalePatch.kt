package com.kireibox.krkr

import android.content.Context
import android.util.Log
import com.yuki.yukihub.launcherbridge.LauncherKrkrGameOverrides
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageNodes
import java.math.BigDecimal
import java.nio.charset.Charset

/**
 * Tyranor's 「调整字体大小」 for KRKR: a TJS block appended to the game's own `patch.tjs` (Kirikiroid2 runs it
 * at startup) that multiplies every `Font.height` the game sets. Only the marked block is ever rewritten, so a
 * translation patch already in `patch.tjs` is kept as it is.
 */
object KrkrFontScalePatch {
    /** The same six steps Tyranor offers; 1.0 means no patch. */
    val Presets = listOf(.25f, .5f, .75f, 1.25f, 1.5f, 2f)
    const val OFF = 1f

    private const val TAG = "KrkrFontScale"
    private const val FILE_NAME = "patch.tjs"
    private const val BEGIN = "// >>> KireiBox font-scale begin (do not edit) >>>"
    private const val END = "// <<< KireiBox font-scale end <<<"
    private val BlockRegex = Regex("(?s)\r?\n?${Regex.escape(BEGIN)}.*?${Regex.escape(END)}[ \t]*")

    /** The nearest step, or [OFF] for a value that is no step. */
    fun snap(value: Float): Float = Presets.firstOrNull { kotlin.math.abs(it - value) < .001f } ?: OFF

    fun label(value: Float): String = if (snap(value) == OFF) "关闭" else "${formatFactor(value)} 倍"

    fun formatFactor(value: Float): String = BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

    /** Tyranor's hook (`W6/d.i`), with the original-height property renamed so it cannot collide with a Tyranor-written block. */
    fun buildBlock(factor: Float): String =
        listOf(
            BEGIN,
            "System.setArgument(\"-debugwin\",\"no\");",
            "Plugins.link(\"kirikiroid2.dll\");",
            "with (Font) {",
            "    global._kireiOrigFontHeightProp = &.height;",
            "    property hook_kirei_font_height {",
            "        setter(v) { global._kireiOrigFontHeightProp = v * ${formatFactor(factor)}; }",
            "        getter { return global._kireiOrigFontHeightProp; }",
            "    }",
            "    &.height = &(hook_kirei_font_height incontextof null);",
            "}",
            END,
        ).joinToString("\n")

    fun stripBlock(text: String): String = BlockRegex.replace(text, "")

    /** [existing] with the block set to [factor]; a null or [OFF] factor removes it. A text without our block and no factor is returned as is. */
    fun merge(
        existing: String?,
        factor: Float?,
    ): String {
        val text = existing.orEmpty()
        val enabled = factor != null && snap(factor) != OFF
        if (!enabled && !text.contains(BEGIN)) return text
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        val kept = stripBlock(text).trimEnd('\r', '\n').let { if (it.isBlank()) "" else it + eol }
        return if (enabled) kept + buildBlock(requireNotNull(factor)).replace("\n", eol) + eol else kept
    }

    /** Writes [factor] into the game's `patch.tjs` (creating or, when turned off with nothing else left, emptying it). */
    fun apply(
        context: Context,
        game: Game,
        factor: Float,
    ): Result<Unit> =
        runCatching {
            val rootUri = game.rootUri?.takeIf(String::isNotBlank) ?: error("无法访问游戏目录")
            val root = StorageNodes.resolve(context, rootUri) ?: error("无法访问游戏目录")
            val file = root.findChild(FILE_NAME)
            val enabled = snap(factor) != OFF
            if (file == null && !enabled) return@runCatching
            val bytes = file?.openInputStream(context)?.use { it.readBytes() } ?: ByteArray(0)
            val codec = TextCodec.of(bytes)
            val old = codec.decode(bytes)
            val updated = merge(old, factor)
            if (updated == old && file != null) return@runCatching
            val target = file ?: root.createFile(context, "application/octet-stream", FILE_NAME) ?: error("无法在游戏目录创建 patch.tjs")
            val output = target.openOutputStream(context) ?: error("无法写入 patch.tjs")
            output.use { it.write(codec.encode(updated)) }
        }

    /** Re-applies the game's stored size before it starts (the folder may have been restored from a backup); a game never set is left alone. */
    fun syncAtLaunch(
        context: Context,
        game: Game,
    ) {
        val stored = LauncherKrkrGameOverrides.fontScale(context, game.id) ?: return
        apply(context, game, stored).onFailure { Log.w(TAG, "font scale patch failed for game ${game.id}", it) }
    }

    /** UTF-16LE (BOM FF FE) is kept as the file has it, a UTF-8 BOM too; anything else is UTF-8. */
    private class TextCodec(private val charset: Charset, private val bom: ByteArray) {
        fun decode(bytes: ByteArray): String = String(bytes, bom.size, bytes.size - bom.size, charset)

        fun encode(text: String): ByteArray = bom + text.toByteArray(charset)

        companion object {
            private val Utf16Bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            private val Utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

            fun of(bytes: ByteArray): TextCodec =
                when {
                    bytes.size >= 2 && bytes[0] == Utf16Bom[0] && bytes[1] == Utf16Bom[1] -> TextCodec(Charsets.UTF_16LE, Utf16Bom)
                    bytes.size >= 3 && bytes[0] == Utf8Bom[0] && bytes[1] == Utf8Bom[1] && bytes[2] == Utf8Bom[2] -> TextCodec(Charsets.UTF_8, Utf8Bom)
                    else -> TextCodec(Charsets.UTF_8, ByteArray(0))
                }
        }
    }
}
