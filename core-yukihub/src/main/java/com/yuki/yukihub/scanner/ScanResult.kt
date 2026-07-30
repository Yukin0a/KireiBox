package com.yuki.yukihub.scanner

import com.yuki.yukihub.model.EngineType

/**
 * 扫描阶段产出的游戏候选信息。由 [com.yuki.yukihub.scanner.GameScanner] 产出，
 * 在 [com.yuki.yukihub.data.GameRepository] 中合并到 [com.yuki.yukihub.model.Game]。
 *
 * - `@JvmField var` 保留 Java 直接字段访问（如 `result.launchTarget = candidate`）。
 * - `@JvmOverloads` 让 Java 调用方继续使用原 Java 的 6 个 telescoping 重载。
 * - `init` 块复刻原 10 参构造器的 null 归一化与 `xp3Candidates` 防御性拷贝。
 */
data class ScanResult @JvmOverloads constructor(
    @JvmField var title: String? = null,
    @JvmField var uri: String? = null,
    @JvmField var engine: EngineType? = null,
    @JvmField var confidence: Int = 0,
    @JvmField var launchTarget: String? = "",
    @JvmField var coverUri: String? = "",
    /** Multiple non-data XP3 candidates which require a user choice before import. */
    @JvmField var xp3Candidates: List<String>? = null
) {
    init {
        if (launchTarget == null) launchTarget = ""
        if (coverUri == null) coverUri = ""
        // 防御性拷贝：null → 空 ArrayList，非空 → 新 ArrayList 拷贝原列表元素。
        // 使用 `?: emptyList()` 统一处理可空类型，避免对 `var` 属性 smart-cast 失败的告警。
        xp3Candidates = ArrayList(xp3Candidates ?: emptyList())
    }
}
