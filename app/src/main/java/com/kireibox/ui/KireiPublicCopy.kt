package com.kireibox.ui

import com.yuki.yukihub.metadata.CoverSourceMode

/** User-facing project and cover-source copy kept separate from developer diagnostics. */
internal object KireiPublicCopy {
    const val aboutText: String = """Rinne Mobile的分支版本
QQ交流群:163603635
开源地址:
https://github.com/Yukin0a/KireiBox"""

    fun coverSourceDescription(source: CoverSourceMode): String = when (source) {
        CoverSourceMode.VNDB -> "公开数据源 · 无需 Token；中文条目覆盖可能有限。"
        CoverSourceMode.BANGUMI -> "Bangumi 官方接口 · 需要在下方填写个人 Token。"
        CoverSourceMode.BANGUMI_MIRROR -> "公开镜像接口 · 无需个人 Token；可用性取决于镜像服务。"
        CoverSourceMode.YMGAL -> "月幕 Gal 公开服务 · 用户无需 Token。"
        CoverSourceMode.HIKARINAGI -> "Hikarinagi 公开服务 · 用户无需 Token。"
    }

    /** Copy shared by the dashboard and settings UI; developer-log implementation details stay private. */
    fun dashboardAndLogLabels(): List<String> = listOf(
        "游玩时长",
        "打开游戏数",
        "最近 7 天 · 结果",
        "最近 30 天 · 结果",
        "诊断日志",
        "复制日志",
    )
}
