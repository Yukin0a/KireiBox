package com.kireibox.ui

import com.yuki.yukihub.data.ScanImportSummary

internal object DirectoryAutoRefreshPolicy {
    fun enabledRoots(roots: List<String>, enabled: List<Boolean>): List<String> =
        roots.filterIndexed { index, _ -> enabled.getOrElse(index) { true } }

    fun notice(summary: ScanImportSummary, failedDirectoryCount: Int, prunedCount: Int = 0): String? {
        val added = summary.addedCount
        if (added == 0 && failedDirectoryCount == 0 && prunedCount == 0) return null
        val parts = ArrayList<String>(4)
        when {
            added == 1 -> {
                val title = summary.addedGames.first().title?.takeIf { it.isNotBlank() } ?: "未命名游戏"
                parts += "发现并添加 1 个新游戏：$title"
            }
            added > 1 -> parts += "添加 $added 个新游戏"
        }
        if (prunedCount > 0) parts += "已清理 $prunedCount 张失效卡片"
        if (failedDirectoryCount > 0) parts += "$failedDirectoryCount 个目录扫描失败"
        return "自动更新：" + parts.joinToString("，")
    }
}

internal class ForegroundRefreshGate {
    private var running = false

    fun tryStart(): Boolean {
        if (running) return false
        running = true
        return true
    }

    fun finish() {
        running = false
    }
}
