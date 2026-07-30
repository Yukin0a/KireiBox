package com.yuki.yukihub.tyrano

import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

/** 供 Tyrano JavaScript 桥使用的、限制在单一存档目录内的文件存储。 */
internal object TyranoStorage {
    private const val TAG = "YukiTyrano"
    private const val MAX_SAVE_BYTES = 8L * 1024L * 1024L
    private val validKey = Regex("[A-Za-z0-9._-]+")

    @JvmStatic
    fun read(directory: File?, key: String?): String {
        return try {
            val file = resolveFile(directory, key) ?: return ""
            if (!file.isFile || file.length() !in 0..MAX_SAVE_BYTES) return ""
            val bytes = file.inputStream().buffered().use { input ->
                val output = java.io.ByteArrayOutputStream(file.length().toInt())
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_SAVE_BYTES) return ""
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            String(bytes, StandardCharsets.UTF_8)
        } catch (error: Throwable) {
            Log.w(TAG, "getStorage failed key=$key", error)
            ""
        }
    }

    @JvmStatic
    fun write(directory: File?, key: String?, value: String?) {
        try {
            val file = resolveFile(directory, key) ?: return
            val bytes = value.orEmpty().toByteArray(StandardCharsets.UTF_8)
            if (bytes.size > MAX_SAVE_BYTES) return
            file.outputStream().use { it.write(bytes) }
        } catch (error: Throwable) {
            Log.w(TAG, "setStorage failed key=$key", error)
        }
    }

    @JvmStatic
    fun resolveFile(directory: File?, key: String?): File? {
        if (directory == null || key == null) return null
        val clean = key.trim()
        if (clean.isEmpty() || clean.length > 128 || ".." in clean || !validKey.matches(clean)) {
            return null
        }
        val root = directory.canonicalFile
        val target = File(root, "$clean.sav").canonicalFile
        return target.takeIf { it.path.startsWith(root.path + File.separator) }
    }
}
