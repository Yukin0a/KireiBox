package com.yuki.yukihub.launch

import android.content.Context
import com.yuki.yukihub.storage.StorageNode
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Finds KRKR executables that carry their own XP3 archive, the usual shape of a 汉化 exe
 * (`game_chs.exe` beside the original data.xp3) and of single-exe releases.
 *
 * Mirrors krkr's TVPGetXP3ArchiveOffset: an "MZ" file whose XP3 mark sits on a 16-byte
 * boundary. Only the PE headers and the start of the data appended after the last section are
 * read, so plain Windows programs (no appended data) are rejected after the first block.
 */
object KrkrBoundExe {
    private val XP3_MARK =
        byteArrayOf(0x58, 0x50, 0x33, 0x0d, 0x0a, 0x20, 0x0a, 0x1a, 0x8b.toByte(), 0x67, 0x01)
    private const val HEADER_BYTES = 64 * 1024
    private const val PARAGRAPH = 16
    private const val PE_SIGNATURE = 0x00004550

    /** Appended data before the archive (signatures, installer stubs) that is still searched. */
    private const val OVERLAY_WINDOW = 4L * 1024 * 1024

    /** How far a file whose PE headers cannot be read is searched, like krkr's linear scan. */
    private const val FALLBACK_WINDOW = 32L * 1024 * 1024

    /** Exes worth opening: no uninstallers or redistributables. */
    fun isCandidateName(name: String): Boolean = name.lowercase(Locale.ROOT).endsWith(".exe") && !ExeEntrySelector.isAuxiliary(name)

    fun isBound(
        context: Context?,
        node: StorageNode,
    ): Boolean =
        context != null && node.isFile && isCandidateName(node.name) &&
            runCatching { node.openInputStream(context)?.use(::isBound) ?: false }.getOrDefault(false)

    fun isBound(input: InputStream): Boolean {
        val header = readUpTo(input, HEADER_BYTES)
        if (header.size < 2 || header[0] != 'M'.code.toByte() || header[1] != 'Z'.code.toByte()) return false
        val overlay = overlayStart(header)
        val start = if (overlay == null) PARAGRAPH.toLong() else maxOf(PARAGRAPH.toLong(), overlay - overlay % PARAGRAPH)
        return search(header, input, start, start + if (overlay == null) FALLBACK_WINDOW else OVERLAY_WINDOW)
    }

    /** End of the last section's raw data, where appended data begins; null when the PE headers are unreadable. */
    fun overlayStart(header: ByteArray): Long? =
        runCatching {
            val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val pe = bytes.getInt(0x3c)
            require(pe >= 0x40 && bytes.getInt(pe) == PE_SIGNATURE)
            val sections = bytes.getShort(pe + 6).toInt() and 0xffff
            val table = pe + 24 + (bytes.getShort(pe + 20).toInt() and 0xffff)
            (0 until sections).maxOfOrNull { index ->
                val entry = table + index * 40
                val rawSize = bytes.getInt(entry + 16).toLong() and 0xffffffffL
                if (rawSize == 0L) 0L else (bytes.getInt(entry + 20).toLong() and 0xffffffffL) + rawSize
            }
        }.getOrNull()?.takeIf { it > 0 }

    /** Checks every 16-byte boundary in [start, end); blocks are joined so a mark across two reads is still seen. */
    private fun search(
        header: ByteArray,
        input: InputStream,
        start: Long,
        end: Long,
    ): Boolean {
        var buffer = header
        var bufferStart = 0L
        if (start >= header.size) {
            buffer = if (skipFully(input, start - header.size)) readUpTo(input, HEADER_BYTES) else ByteArray(0)
            bufferStart = start
        }
        var position = start
        var found = false
        while (!found && position < end) {
            val offset = (position - bufferStart).toInt()
            if (offset + XP3_MARK.size <= buffer.size) {
                found = matchesMark(buffer, offset)
                position += PARAGRAPH
            } else {
                val next = readUpTo(input, HEADER_BYTES)
                if (next.isEmpty()) break
                buffer = buffer.copyOfRange(offset.coerceAtMost(buffer.size), buffer.size) + next
                bufferStart = position
            }
        }
        return found
    }

    private fun matchesMark(
        buffer: ByteArray,
        offset: Int,
    ): Boolean = XP3_MARK.indices.all { buffer[offset + it] == XP3_MARK[it] }

    private fun readUpTo(
        input: InputStream,
        count: Int,
    ): ByteArray {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = input.read(buffer, filled, count - filled)
            if (read < 0) break
            filled += read
        }
        return if (filled == count) buffer else buffer.copyOf(filled)
    }

    private fun skipFully(
        input: InputStream,
        count: Long,
    ): Boolean {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (input.read() < 0) {
                return false
            } else {
                remaining--
            }
        }
        return true
    }
}
