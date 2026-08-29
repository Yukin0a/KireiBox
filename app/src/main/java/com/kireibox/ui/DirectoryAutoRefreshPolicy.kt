package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary

internal object DirectoryAutoRefreshPolicy {
    fun enabledRoots(roots: List<String>, enabled: List<Boolean>): List<String> =
        roots.filterIndexed { index, _ -> enabled.getOrElse(index) { true } }

    fun notice(summary: ScanImportSummary, failedDirectoryCount: Int): String? {
        val added = summary.addedCount
        if (added == 0 && failedDirectoryCount == 0) return null
        if (failedDirectoryCount > 0) {
            return if (added == 0) {
                "自动更新：$failedDirectoryCount 个目录扫描失败"
            } else {
                "自动更新：添加 $added 个新游戏，$failedDirectoryCount 个目录扫描失败"
            }
        }
        if (added == 1) {
            val title = summary.addedGames.first().title?.takeIf { it.isNotBlank() } ?: "未命名游戏"
            return "自动更新：发现并添加 1 个新游戏：$title"
        }
        return "自动更新：添加 $added 个新游戏"
    }
}

internal class ForegroundRefreshGate(private val minIntervalMs: Long = 1_500L) {
    private var running = false
    private var lastStartMs = 0L

    fun tryStart(nowMs: Long): Boolean {
        if (running) return false
        if (lastStartMs != 0L && nowMs - lastStartMs < minIntervalMs) return false
        running = true
        lastStartMs = nowMs
        return true
    }

    fun finish() {
        running = false
    }
}
