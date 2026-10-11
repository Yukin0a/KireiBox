package com.kireibox.cover

internal object LocalCoverImportPolicy {
    const val MAX_BYTES = 20L * 1024L * 1024L
    const val MAX_PIXELS = 60_000_000L

    fun acceptsByteCount(byteCount: Long): Boolean = byteCount in 1..MAX_BYTES

    fun acceptsDimensions(
        width: Int,
        height: Int
    ): Boolean = width > 0 && height > 0 && width.toLong() * height.toLong() <= MAX_PIXELS

    fun extensionFor(mimeType: String?): String =
        when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "img"
        }
}
