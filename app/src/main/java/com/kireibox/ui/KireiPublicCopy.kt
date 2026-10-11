package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode

/** User-facing project and cover-source copy kept separate from developer diagnostics. */
internal object KireiPublicCopy {
    /**
     * The project blurb (KireiPublicCopyTest pins it as user-facing copy). Since P3 the 关于 page
     * shows this information as rows (GitHub, 第三方声明), so no dialog prints it any more.
     */
    const val ABOUT_TEXT: String = """Rinne Mobile的分支版本 QwQ
开源地址:
https://github.com/Yukin0a/KireiBox"""

    fun coverSourceDescription(source: CoverSourceMode): String =
        when (source) {
            CoverSourceMode.NEXTMOE -> "需登录 NextMoe 账号"
            CoverSourceMode.VNDB -> "公开数据，无需登录"
            CoverSourceMode.BANGUMI -> "可匿名搜索"
            CoverSourceMode.BANGUMI_MIRROR -> "历史镜像，已停止联网"
            CoverSourceMode.YMGAL -> "历史资料来源，已停止联网"
            CoverSourceMode.HIKARINAGI -> "需登录 Hikarinagi 账号"
        }

    /** Copy shared by the dashboard and settings UI; developer-log implementation details stay private. */
    fun dashboardAndLogLabels(): List<String> =
        listOf(
            "游玩时长",
            "打开游戏数",
            "最近 7 天 · 结果",
            "最近 30 天 · 结果",
            "诊断日志",
            "复制日志",
        )
}
