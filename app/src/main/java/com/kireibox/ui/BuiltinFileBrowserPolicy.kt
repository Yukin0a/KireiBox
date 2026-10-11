package com.kireibox.ui

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure rules for the built-in file browser: breadcrumb visibility and row details. */
internal object BuiltinFileBrowserPolicy {
    private val SAME_YEAR_FORMAT = DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
    private val OTHER_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

    /** The crumb row carries no information for a single (root-only) crumb. */
    fun shouldHideCrumbRow(crumbCount: Int): Boolean = crumbCount <= 1

    /** Canonical path when resolvable, otherwise the absolute path; never throws. */
    fun safeCanonicalPath(file: File): File = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }

    /** 10月7日 17:20 within the current year, 2025年10月7日 before it; blank when the time is unknown. */
    fun modifiedLabel(
        modifiedMillis: Long,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (modifiedMillis <= 0L) return ""
        val modified = Instant.ofEpochMilli(modifiedMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        return (if (modified.year == now.year) SAME_YEAR_FORMAT else OTHER_YEAR_FORMAT).format(modified)
    }

    /** Second line of a browser row: the item count of a folder, size and date of a file. */
    fun detailLabel(
        file: File,
        nowMillis: Long = System.currentTimeMillis(),
    ): String =
        if (file.isDirectory) {
            "${runCatching { file.list()?.size }.getOrNull() ?: 0} 项"
        } else {
            listOf(formatFileSize(file.length()), modifiedLabel(file.lastModified(), nowMillis)).filter(String::isNotEmpty).joinToString(" · ")
        }
}
