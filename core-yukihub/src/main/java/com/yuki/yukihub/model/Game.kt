package com.yuki.yukihub.model

/**
 * 游戏库核心数据模型，对应 SQLite 表 `games` 的一行。
 *
 * - `@JvmField var` 暴露公有字段，保留 Java 调用方原有的直接字段访问语法
 *   （`game.title = ...`、`g.id`、`g.engine`），实现零破坏迁移。
 * - `@JvmOverloads` 让 Java 调用方继续使用 `new Game()` 无参构造；
 *   `init` 块复刻原 Java 构造器逻辑：`engine` 默认 `UNKNOWN`，`createdAt`/`updatedAt` 默认当前时间。
 * - `data class` 自动生成 equals/hashCode/toString/copy，
 *   便于快照比较、日志输出与单字段更新（`game.copy(playStatus = "completed")`）。
 */
data class Game @JvmOverloads constructor(
    @JvmField var id: Long = 0L,
    @JvmField var title: String? = null,
    @JvmField var originalTitle: String? = null,
    @JvmField var engine: EngineType? = null,
    @JvmField var rootUri: String? = null,
    /** Empty for scanner-managed directory cards; normalized relative entry for explicit launch-file cards. */
    @JvmField var entryKey: String = "",
    @JvmField var coverUri: String? = null,
    @JvmField var coverPersistUri: String? = null,
    @JvmField var coverSourceType: Int = 0, // 0=none 1=uri 2=embedded/base64
    @JvmField var vndbBindingMode: String = "NONE", // NONE / AUTO / MANUAL / REVIEW / RETRY / DISABLED
    @JvmField var vndbMatchScore: Double = 0.0,
    @JvmField var emulatorPackage: String? = null,
    @JvmField var launchTarget: String? = null, // AUTO, DIR, startup.tjs, data.xp3, patch.xp3, XP3_FIRST
    @JvmField var description: String? = null,
    @JvmField var tags: String? = null,
    @JvmField var playStatus: String = "unplayed", // unplayed / playing / completed
    @JvmField var totalPlayTime: Long = 0L,
    @JvmField var lastPlayedAt: Long = 0L,
    @JvmField var playtimeResetAt: Long = 0L,
    @JvmField var createdAt: Long = 0L,
    @JvmField var updatedAt: Long = 0L,
    @JvmField var hidden: Boolean = false,
    @JvmField var favorite: Boolean = false,
    /** Whether persisted covers for this game are sensitive and should be blurred in-app. */
    @JvmField var nsfw: Boolean = false,
    /** Stable user-defined position; favorites and regular games keep separate visual groups. */
    @JvmField var manualOrder: Long = 0L,
) {
    init {
        if (engine == null) engine = EngineType.UNKNOWN
        if (createdAt == 0L) createdAt = System.currentTimeMillis()
        if (updatedAt == 0L) updatedAt = createdAt
    }
}
