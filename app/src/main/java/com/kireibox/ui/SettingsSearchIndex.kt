package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.settings.AccentChoice
import com.yuki.yukihub.settings.ImmersiveAmbient
import com.yuki.yukihub.settings.ImmersiveBackdropStyle
import com.yuki.yukihub.settings.ImmersiveCardSwitch
import com.yuki.yukihub.settings.ImmersiveFxPreset
import com.yuki.yukihub.settings.ThemeMode

// Settings search (r4 · S2): one entry per user-visible setting, data only. Each entry names the
// page it lives on, the path a result shows (「外观 › 显示」), the anchor its row registers with
// SettingsAnchor (the row title unless that changes at runtime) and synonyms people actually type:
// Chinese colloquialisms, English and abbreviations. SettingsSearchIndexContractTest keeps this list
// and the pages in step: every row title on a settings page is here (or excluded with a reason), and
// every anchor here is registered on its page.

/** When an entry's row exists on its page. */
internal enum class SettingsSearchScope {
    /** Always shown. */
    ALWAYS,

    /** Only while the home style is 沉浸式 (首页 › 展示内容 / 视觉效果). */
    IMMERSIVE,

    /** Immersive only, inside the collapsed 「高级选项」, which opens on arrival. */
    IMMERSIVE_ADVANCED,
}

/** Where an entry lives: its page, the path its result shows and when its row exists. */
internal data class SettingsSearchLocation(
    val page: KireiSettingsPage,
    val breadcrumb: String,
    val scope: SettingsSearchScope = SettingsSearchScope.ALWAYS,
)

/**
 * One searchable setting. [anchor] is the key its row registers with SettingsAnchor; null opens the
 * page itself (page entries). [subtitle], when set, replaces the path under the result.
 */
internal data class SettingsSearchEntry(
    val title: String,
    val location: SettingsSearchLocation,
    val keywords: List<String> = emptyList(),
    val anchor: String? = title,
    val subtitle: String? = null,
) {
    val page: KireiSettingsPage get() = location.page

    /** Stable, unique key of the result row. */
    val id: String get() = "${location.page.name}:${anchor ?: "page"}"

    /** The compact forms matching compares against, computed once per entry instead of per keystroke. */
    internal val forms: SettingsSearchForms by lazy { SettingsSearchForms(this) }
}

internal object SettingsSearchIndex {
    /** The 快速设置 entry opens the guide sheet on the root instead of a page row. */
    const val QUICK_SETUP_ANCHOR = "quick-setup"

    /** The 存储与权限 native-access row: its title depends on the API level, so it has a fixed key. */
    const val NATIVE_ACCESS_ANCHOR = "native-access"

    /** 首页样式: where immersive-only results lead while the home uses 组件模式. */
    const val HOME_STYLE_ANCHOR = "首页样式"

    const val SEPARATOR = " › "

    /** Shown when nothing matches: one word per area, each known to find something. */
    val suggestions = listOf("深色", "强调色", "封面", "KRKR", "日志")

    val entries: List<SettingsSearchEntry> by lazy {
        pageEntries() + appearanceEntries() + homeEntries() + effectEntries() + libraryEntries() + coverEntries() + engineEntries() + systemEntries() + aboutEntries()
    }

    private val appearance = settingsPageTitle(KireiSettingsPage.APPEARANCE)
    private val home = settingsPageTitle(KireiSettingsPage.INTERFACE)
    private val library = settingsPageTitle(KireiSettingsPage.LIBRARY_HUB)
    private val scanRoots = settingsPageTitle(KireiSettingsPage.LIBRARY)
    private val covers = settingsPageTitle(KireiSettingsPage.COVERS)
    private val engines = settingsPageTitle(KireiSettingsPage.ENGINES)
    private val krkr = settingsPageTitle(KireiSettingsPage.KRKR)
    private val ons = settingsPageTitle(KireiSettingsPage.ONS)
    private val artemis = settingsPageTitle(KireiSettingsPage.ARTEMIS)
    private val tyrano = settingsPageTitle(KireiSettingsPage.TYRANO)
    private val winlator = settingsPageTitle(KireiSettingsPage.WINLATOR)
    private val permissions = settingsPageTitle(KireiSettingsPage.PERMISSIONS)
    private val diagnostics = settingsPageTitle(KireiSettingsPage.DIAGNOSTICS)
    private val about = settingsPageTitle(KireiSettingsPage.ABOUT)
    private val root = settingsPageTitle(KireiSettingsPage.ROOT)

    private fun at(
        page: KireiSettingsPage,
        vararg path: String,
        scope: SettingsSearchScope = SettingsSearchScope.ALWAYS,
    ): SettingsSearchLocation = SettingsSearchLocation(page, path.joinToString(SEPARATOR), scope)

    private fun words(vararg words: String): List<String> = words.toList()

    /** Every page, opened as a whole; the path names the root group the page sits in. */
    private fun pageEntries(): List<SettingsSearchEntry> =
        listOf(
            SettingsSearchEntry(
                QUICK_SETUP_TITLE,
                at(KireiSettingsPage.ROOT, root),
                words("引导", "向导", "新手", "初始设置", "一键设置", "入门", "setup", "wizard", "guide", "onboarding"),
                anchor = QUICK_SETUP_ANCHOR,
            ),
            page(KireiSettingsPage.APPEARANCE, "个性化", words("界面", "样式", "皮肤", "外观设置", "appearance", "ui", "look")),
            page(KireiSettingsPage.INTERFACE, "个性化", words("主页", "桌面", "首页与沉浸式", "沉浸式首页", "home", "homepage")),
            page(KireiSettingsPage.LIBRARY_HUB, "游戏", words("游戏", "库", "library", "games")),
            SettingsSearchEntry(
                scanRoots,
                at(KireiSettingsPage.LIBRARY, library, "目录"),
                words("目录", "文件夹", "路径", "导入", "folder", "directory", "scan", "path"),
                anchor = null,
            ),
            page(KireiSettingsPage.COVERS, "游戏", words("元数据", "资料", "海报", "图片", "cover", "covers", "metadata", "artwork", "poster", "vndb", "bangumi", "bgm")),
            page(
                KireiSettingsPage.COVER_ACCOUNTS,
                "游戏",
                CoverAccountSources.joinToString("、") { it.label },
                words("账号", "账户", "登录", "退出", "令牌", "封面", "来源", "token", "account", "login", "cover", "covers", "source", "nextmoe", "hikarinagi", "bangumi"),
            ),
            page(KireiSettingsPage.PLAYTIME_SYNC, "游戏", words("同步", "时长", "游玩时间", "上传", "nextmoe", "sync", "playtime")),
            page(KireiSettingsPage.ENGINES, "游戏", words("引擎", "模拟器", "全局", "默认", "engine", "engines", "emulator", "global")),
            // Per-game renderer / FPS / font settings live on a game's own KRKR page (opened from its
            // sheet); this page's footer says where, so those words lead here.
            page(
                KireiSettingsPage.KRKR,
                "游戏",
                words("krkr2", "kirikiri", "kirikiroid", "kirikiroid2", "吉里吉里", "引擎", "模拟器", "engine", "emulator", "渲染", "fps", "帧率", "字体", "字号", "放大", "光标"),
            ),
            page(KireiSettingsPage.ONS, "游戏", words("onscripter", "onsyuri", "ons2", "引擎", "模拟器", "engine", "emulator", "编码", "锐化", "刘海")),
            page(KireiSettingsPage.ARTEMIS, "游戏", words("引擎", "模拟器", "engine", "emulator", "存档")),
            page(KireiSettingsPage.TYRANO, "游戏", words("tyranor", "tyrano script", "引擎", "模拟器", "engine", "emulator", "存档")),
            page(KireiSettingsPage.WINLATOR, "游戏", words("winlator", "winlatorcn", "winlator-cn", "windows", "exe", "pc", "电脑", "引擎", "模拟器", "engine", "emulator")),
            page(KireiSettingsPage.PERMISSIONS, "系统", words("权限", "存储", "授权", "访问", "所有文件", "saf", "permission", "permissions", "storage", "access")),
            page(KireiSettingsPage.DIAGNOSTICS, "系统", words("日志", "诊断", "崩溃", "闪退", "报错", "反馈", "bug", "log", "logs", "debug", "crash", "diagnostics")),
            SettingsSearchEntry(
                "关于 KireiBox",
                at(KireiSettingsPage.ABOUT, root, about),
                words(about, "版本", "开源", "kireibox", "about", "version"),
                anchor = null,
            ),
        )

    private fun page(
        page: KireiSettingsPage,
        group: String,
        keywords: List<String>,
    ): SettingsSearchEntry = SettingsSearchEntry(settingsPageTitle(page), at(page, root, group), keywords, anchor = null)

    private fun page(
        page: KireiSettingsPage,
        group: String,
        subtitle: String,
        keywords: List<String>,
    ): SettingsSearchEntry = SettingsSearchEntry(settingsPageTitle(page), at(page, root, group), keywords, anchor = null, subtitle = subtitle)

    private fun appearanceEntries(): List<SettingsSearchEntry> {
        val display = at(KireiSettingsPage.APPEARANCE, appearance, "显示")
        return listOf(
            SettingsSearchEntry(
                "主题",
                at(KireiSettingsPage.APPEARANCE, appearance, "主题"),
                ThemeMode.entries.map { it.displayName } +
                    words("深色模式", "夜间", "夜间模式", "暗色", "黑暗模式", "暗黑模式", "黑色", "亮色", "白天", "日间", "主题模式", "dark", "light", "night", "darkmode", "theme"),
            ),
            SettingsSearchEntry(
                "强调色",
                at(KireiSettingsPage.APPEARANCE, appearance, "强调色"),
                AccentChoice.entries.map { it.displayName } + words("颜色", "配色", "主题色", "色彩", "粉色", "蓝色", "橙色", "绿色", "accent", "color", "colour"),
            ),
            SettingsSearchEntry("动画效果", display, words("动画", "动效", "过渡", "减少动态", "精简", "省电", "卡顿", "animation", "animations", "motion")),
            SettingsSearchEntry("屏幕方向", display, words("旋转", "自动旋转", "横屏", "竖屏", "方向", "锁定方向", "rotation", "rotate", "orientation", "landscape", "portrait")),
            SettingsSearchEntry("减少透明度", display, words("模糊", "毛玻璃", "透明", "玻璃", "半透明", "实色背景", "blur", "glass", "frosted", "transparency")),
            SettingsSearchEntry("增强对比度", display, words("对比度", "高对比", "看不清", "文字太淡", "可读性", "无障碍", "contrast", "accessibility", "a11y")),
        )
    }

    private fun homeEntries(): List<SettingsSearchEntry> {
        val content = at(KireiSettingsPage.INTERFACE, home, "展示内容", scope = SettingsSearchScope.IMMERSIVE)
        val effects = at(KireiSettingsPage.INTERFACE, home, "视觉效果", scope = SettingsSearchScope.IMMERSIVE)
        return listOf(
            SettingsSearchEntry(
                HOME_STYLE_ANCHOR,
                at(KireiSettingsPage.INTERFACE, home),
                words("沉浸式", "组件模式", "组件", "小组件", "仪表盘", "布局", "画廊", "immersive", "widget", "widgets", "dashboard", "layout"),
            ),
            SettingsSearchEntry(
                "展示的游戏",
                content,
                words("展示内容", "轮播", "展示图", "编辑沉浸式展示图", "最近打开", "收藏", "随机", "手动挑选", "carousel", "gallery", "showcase"),
            ),
            SettingsSearchEntry("显示游玩时长", content, words("游玩时长", "时长", "游戏时间", "游玩时间", "playtime", "play time")),
            SettingsSearchEntry(
                "视觉效果",
                effects,
                ImmersiveFxPreset.entries.map { it.displayName } + words("特效", "效果", "预设", "沉浸式效果", "effects", "fx", "preset"),
            ),
            SettingsSearchEntry(
                "切换方式",
                at(KireiSettingsPage.INTERFACE, home, "视觉效果"),
                ImmersiveCardSwitch.entries.map { it.displayName } + words("切换动画", "卡片切换", "滑动", "过渡", "洗牌", "卡片动画", "transition", "shuffle"),
            ),
            SettingsSearchEntry(
                "高级选项",
                at(KireiSettingsPage.INTERFACE, home, "视觉效果", scope = SettingsSearchScope.IMMERSIVE_ADVANCED),
                words("细项", "自定义效果", "高级", "advanced"),
            ),
        )
    }

    /** 首页 › 高级选项: every detail of the immersive effects. */
    private fun effectEntries(): List<SettingsSearchEntry> {
        val advanced = SettingsSearchScope.IMMERSIVE_ADVANCED
        val backdrop = at(KireiSettingsPage.INTERFACE, home, "高级选项", "背景", scope = advanced)
        val card = at(KireiSettingsPage.INTERFACE, home, "高级选项", "卡片", scope = advanced)
        val ambient = at(KireiSettingsPage.INTERFACE, home, "高级选项", "氛围", scope = advanced)
        return listOf(
            SettingsSearchEntry(
                "背景样式",
                backdrop,
                ImmersiveBackdropStyle.entries.map { it.displayName } +
                    words("背景", "壁纸", "虚化", "模糊背景", "封面背景", "放大封面", "极光", "流光", "纯黑", "backdrop", "background", "wallpaper", "aurora", "blur", "cover"),
            ),
            SettingsSearchEntry("背景强度", backdrop, words("强度", "浓淡", "遮罩", "背景亮度", "intensity")),
            SettingsSearchEntry("封面缓慢推拉", backdrop, words("推拉", "缩放", "平移", "肯伯恩斯", "kenburns", "ken burns", "zoom", "cover")),
            SettingsSearchEntry("胶片颗粒", backdrop, words("颗粒", "噪点", "胶片", "质感", "grain", "film", "noise")),
            SettingsSearchEntry("暗角", backdrop, words("晕影", "四角变暗", "vignette")),
            SettingsSearchEntry("3D 深度", card, words("立体", "深度", "透视", "倾斜", "3d", "depth", "tilt", "perspective")),
            SettingsSearchEntry("中心卡光晕", card, words("光晕", "发光", "辉光", "glow")),
            SettingsSearchEntry("边缘光", card, words("描边", "轮廓光", "边框", "rim", "rim light")),
            SettingsSearchEntry("高光扫过", card, words("高光", "扫光", "闪光", "反光", "sheen", "shine")),
            SettingsSearchEntry("动态取色", card, words("取色", "颜色", "封面颜色", "跟随封面", "自适应颜色", "dynamic color", "tint", "cover color")),
            SettingsSearchEntry(
                "氛围粒子",
                ambient,
                ImmersiveAmbient.entries.filter { it != ImmersiveAmbient.NONE }.map { it.displayName } + words("粒子", "下雪", "雪花", "飘落", "particles", "snow", "sakura", "dust"),
            ),
            SettingsSearchEntry("陀螺仪视差", ambient, words("陀螺仪", "视差", "重力感应", "体感", "倾斜", "gyro", "gyroscope", "parallax")),
            SettingsSearchEntry("恢复默认效果", at(KireiSettingsPage.INTERFACE, home, "高级选项", scope = advanced), words("重置", "恢复默认", "默认效果", "reset", "default")),
        )
    }

    private fun libraryEntries(): List<SettingsSearchEntry> {
        val updates = at(KireiSettingsPage.LIBRARY, library, scanRoots, "自动更新")
        return listOf(
            SettingsSearchEntry(
                "游戏库顺序",
                at(KireiSettingsPage.LIBRARY_HUB, library, "游戏卡片"),
                words("排序", "顺序", "最近游玩", "添加时间", "名称", "自定义排序", "手动排序", "sort", "order"),
            ),
            SettingsSearchEntry(
                "添加扫描目录",
                at(KireiSettingsPage.LIBRARY, library, scanRoots, "已添加的目录"),
                words("添加目录", "添加文件夹", "导入游戏", "添加游戏", "add folder", "import"),
            ),
            SettingsSearchEntry("目录自动更新", updates, words("自动扫描", "刷新", "自动添加", "启动时扫描", "auto scan", "refresh")),
            SettingsSearchEntry("已移除游戏更新目录不会再添加", updates, words("隐藏", "不再添加", "屏蔽", "hidden")),
            SettingsSearchEntry("已移除的游戏", updates, words("恢复", "移除", "回收站", "已删除", "找回", "restore", "removed")),
            SettingsSearchEntry(
                "使用内置文件浏览器",
                at(KireiSettingsPage.LIBRARY, library, scanRoots, "文件选择"),
                words("文件浏览器", "文件选择器", "系统文件选择器", "file picker", "file browser"),
            ),
        )
    }

    private fun coverEntries(): List<SettingsSearchEntry> {
        val sources = at(KireiSettingsPage.COVERS, covers, "来源与匹配")
        val matching = at(KireiSettingsPage.COVERS, covers, "自动匹配")
        val adult = at(KireiSettingsPage.COVERS, covers, "成人内容")
        val accounts = at(KireiSettingsPage.COVER_ACCOUNTS, settingsPageTitle(KireiSettingsPage.COVER_ACCOUNTS))
        return listOf(
            SettingsSearchEntry("默认来源", sources, CoverSourceMode.active.map { it.label } + words("来源", "数据源", "资料来源", "bgm", "source")),
            SettingsSearchEntry("Bangumi 镜像地址", sources, words("镜像", "代理", "地址", "网络", "api", "bgm", "mirror", "proxy")),
            SettingsSearchEntry("同时查找多个来源", matching, words("多来源", "混合匹配", "自动匹配", "hybrid")),
            SettingsSearchEntry("仅 Wi-Fi 自动匹配", matching, words("流量", "移动数据", "省流量", "无线", "网络", "wlan")),
            SettingsSearchEntry(COVER_STRICT_MATCH_TITLE, matching, words("严格匹配", "标题匹配", "精确匹配", "自动设置封面", "strict", "cover match")),
            SettingsSearchEntry("清除无结果记录", matching, words("清除", "无结果", "重新匹配", "重试", "缓存", "clear", "retry")),
            SettingsSearchEntry("模糊成人内容封面", adult, words("成人", "r18", "nsfw", "18禁", "遮挡", "隐私", "blur", "cover")),
            SettingsSearchEntry("搜索结果包含成人作品", adult, words("成人", "r18", "nsfw", "18禁", "adult")),
            SettingsSearchEntry("待确认封面", at(KireiSettingsPage.COVERS, covers), words("待确认", "待选择", "匹配失败", "选择封面", "挑选封面", "review", "pending", "cover")),
            SettingsSearchEntry("已设置封面", at(KireiSettingsPage.COVERS, covers), words("已设置", "更换封面", "重新选择封面", "修改封面", "change cover")),
        ) +
            CoverAccountSources.map { source ->
                val token = if (source == CoverSourceMode.BANGUMI) words("令牌", "token", "access token") else words("密码")
                SettingsSearchEntry(source.label, accounts, words("账号", "登录", "退出", "account", "login") + token)
            }
    }

    private fun engineEntries(): List<SettingsSearchEntry> {
        val krkrDefaults = at(KireiSettingsPage.KRKR, engines, krkr, "全局默认")
        val onsDisplay = at(KireiSettingsPage.ONS, engines, ons, "显示")
        val onsCompat = at(KireiSettingsPage.ONS, engines, ons, "兼容")
        val artemisDisplay = at(KireiSettingsPage.ARTEMIS, engines, artemis, "显示")
        val tyranoDisplay = at(KireiSettingsPage.TYRANO, engines, tyrano, "显示")
        val orientationWords = words("方向", "横屏", "左横屏", "右横屏", "旋转", "orientation", "landscape")
        val keepAwakeWords = words("常亮", "息屏", "熄屏", "休眠", "keep awake", "screen on")
        val cutoutWords = words("刘海", "挖孔", "摄像头", "异形屏", "cutout", "notch")
        val saveWords = words("存档", "存档目录", "存档位置", "保存", "数据目录", "save", "savedata", "scoped")
        return listOf(
            SettingsSearchEntry("启动 KRKR 原生引擎", at(KireiSettingsPage.KRKR, engines, krkr, "启动"), words("启动", "运行", "打开游戏", "游戏文件", "xp3", "launch", "run")),
            SettingsSearchEntry("引擎版本", krkrDefaults, KrkrEngineVersions.filter { it != "auto" } + words("版本", "engine version")),
            SettingsSearchEntry("兼容模式", krkrDefaults, words("兼容", "花屏", "黑屏", "闪退", "compat", "compatibility")),
            SettingsSearchEntry("跳过开头信息弹窗", krkrDefaults, words("弹窗", "跳过", "提示框", "对话框", "dialog", "popup", "skip")),
            SettingsSearchEntry("游戏屏幕方向", krkrDefaults, orientationWords),
            SettingsSearchEntry("游戏屏幕方向", onsDisplay, orientationWords),
            SettingsSearchEntry("游戏屏幕方向", artemisDisplay, orientationWords),
            SettingsSearchEntry("游戏屏幕方向", tyranoDisplay, orientationWords),
            SettingsSearchEntry("保持屏幕常亮", onsDisplay, keepAwakeWords),
            SettingsSearchEntry("保持屏幕常亮", tyranoDisplay, keepAwakeWords),
            SettingsSearchEntry("忽略刘海", tyranoDisplay, cutoutWords),
            SettingsSearchEntry("拉伸全屏", onsDisplay, words("全屏", "拉伸", "铺满", "画面比例", "stretch", "fullscreen")),
            SettingsSearchEntry("忽略刘海", onsDisplay, words("刘海", "挖孔", "摄像头", "异形屏", "cutout", "notch")),
            SettingsSearchEntry("画面锐化", onsDisplay, words("锐化", "清晰", "模糊", "sharp", "sharpness")),
            SettingsSearchEntry("锐化强度", onsDisplay, words("锐化", "清晰度", "sharpness", "strength")),
            SettingsSearchEntry("文本编码", onsCompat, words("编码", "乱码", "gbk", "utf8", "utf-8", "sjis", "shift-jis", "encoding")),
            SettingsSearchEntry("禁用视频", onsCompat, words("视频", "过场", "黑屏", "卡住", "video", "movie", "disable")),
            SettingsSearchEntry("独立存档目录", at(KireiSettingsPage.ONS, engines, ons, "存档"), saveWords),
            SettingsSearchEntry("独立存档目录", at(KireiSettingsPage.ARTEMIS, engines, artemis, "存档"), saveWords),
            SettingsSearchEntry("独立存档目录", at(KireiSettingsPage.TYRANO, engines, tyrano, "存档"), saveWords),
        ) + winlatorEntries()
    }

    private fun winlatorEntries(): List<SettingsSearchEntry> {
        val container = at(KireiSettingsPage.WINLATOR, engines, winlator, "容器")
        val graphics = at(KireiSettingsPage.WINLATOR, engines, winlator, "图形")
        val locale = at(KireiSettingsPage.WINLATOR, engines, winlator, "语言与时区")
        val advanced = at(KireiSettingsPage.WINLATOR, engines, winlator, "高级")
        return listOf(
            SettingsSearchEntry("打开 Winlator", at(KireiSettingsPage.WINLATOR, engines, winlator), words("启动", "安装", "未安装", "版本", "open", "install")),
            SettingsSearchEntry("创建容器教程", at(KireiSettingsPage.WINLATOR, engines, winlator), words("教程", "引导", "首次", "新手", "容器", "创建", "无法启动", "guide", "tutorial", "container")),
            SettingsSearchEntry("容器名称", container, words("容器", "名字", "container", "name")),
            SettingsSearchEntry("容器 ID", container, words("容器", "编号", "id", "container")),
            SettingsSearchEntry("图形驱动", graphics, words("驱动", "turnip", "vortek", "zink", "virgl", "gladio", "vulkan", "opengl", "gpu", "黑屏", "driver")),
            SettingsSearchEntry("图形加速", graphics, words("dxvk", "wined3d", "directx", "d3d", "显卡", "转换")),
            SettingsSearchEntry("分辨率", graphics, words("画面", "屏幕", "大小", "resolution", "screen", "size")),
            SettingsSearchEntry("语言环境", locale, words("日文", "日语", "乱码", "中文", "lc_all", "locale", "language")),
            SettingsSearchEntry("时区", locale, words("时间", "东京", "tz", "timezone")),
            SettingsSearchEntry("Box64 预设", advanced, words("box64", "性能", "稳定", "兼容", "cpu", "preset")),
            SettingsSearchEntry("环境变量", advanced, words("变量", "wine", "winedlloverrides", "env", "environment")),
            SettingsSearchEntry("写入容器配置", advanced, words("保存", "持久", "确认", "弹窗", "save", "persist")),
        )
    }

    private fun systemEntries(): List<SettingsSearchEntry> =
        listOf(
            SettingsSearchEntry(
                StorageAccessPolicy.nativeAccessTitle(),
                at(KireiSettingsPage.PERMISSIONS, permissions, "KRKR 原生引擎"),
                words("所有文件访问", "存储权限", "文件权限", "授权", "manage storage", "all files access", "permission"),
                anchor = NATIVE_ACCESS_ANCHOR,
            ),
            SettingsSearchEntry("检查访问权限", at(KireiSettingsPage.PERMISSIONS, permissions), words("检查权限", "权限报告", "无法读取", "读写", "check permission")),
            SettingsSearchEntry("记录诊断日志", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "记录"), words("日志", "调试", "详细日志", "debug", "log", "verbose")),
            SettingsSearchEntry("导出日志", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "分享"), words("分享日志", "保存日志", "发送日志", "反馈", "export", "share")),
            SettingsSearchEntry("复制日志", at(KireiSettingsPage.DIAGNOSTICS, diagnostics, "分享"), words("复制", "剪贴板", "copy", "clipboard")),
        )

    private fun aboutEntries(): List<SettingsSearchEntry> {
        val community = at(KireiSettingsPage.ABOUT, about, "社区与许可")
        return listOf(
            SettingsSearchEntry("检查更新", at(KireiSettingsPage.ABOUT, about, "版本"), words("更新", "升级", "新版本", "版本", "下载", "update", "upgrade", "version")),
            SettingsSearchEntry("GitHub", community, words("源代码", "开源", "仓库", "代码", "source", "repo")),
            SettingsSearchEntry("第三方声明", community, words("许可证", "开源许可", "协议", "版权", "license", "licenses", "notices")),
        )
    }
}
