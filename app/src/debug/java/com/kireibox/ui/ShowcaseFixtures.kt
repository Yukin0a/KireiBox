package com.kireibox.ui

import android.content.Context
import com.kireibox.cover.CoverQueueStatus
import com.kireibox.cover.CoverQueueUiState
import com.kireibox.cover.CoverTaskStatus
import com.kireibox.cover.CoverTaskUi
import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import com.yuki.yukihub.data.GameSearchIndex
import com.yuki.yukihub.data.PlayTrendPoint
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import java.io.File
import java.io.RandomAccessFile

/**
 * Fixture data for the page showcases (ShowcasePageRouter): a mixed-engine library, cover tasks in every
 * state, picker candidates and a throw-away directory tree for the built-in file browser. Nothing here
 * touches the real library or settings.
 */
internal object ShowcaseFixtures {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val FIRST_ID = 81_000L

    /** One fixture row: [cover] is 1-5 (bundled fixture covers) or null for the engine default cover. */
    private class Row(
        val title: String,
        val engine: EngineType,
        val cover: Int?,
        val favorite: Boolean = false,
        val nsfw: Boolean = false,
        val lastPlayedAgoMs: Long? = null,
        val playMinutes: Long = 0L,
        val original: String? = null,
    )

    private val rows =
        listOf(
            Row("9nine新章", EngineType.KIRIKIRI, 1, favorite = true, lastPlayedAgoMs = 2 * HOUR, playMinutes = 750),
            Row("樱色彼方", EngineType.ONS, 2, favorite = true, lastPlayedAgoMs = DAY, playMinutes = 190),
            Row("Summer Pockets REFLECTION BLUE", EngineType.ARTEMIS, 4, favorite = true, lastPlayedAgoMs = 3 * DAY, playMinutes = 2400),
            Row("月影回廊", EngineType.TYRANO, 3, lastPlayedAgoMs = 5 * DAY, playMinutes = 45),
            Row("紫苑之庭", EngineType.ARTEMIS, 4),
            Row("晴空轨迹", EngineType.KIRIKIRI, 5, lastPlayedAgoMs = 9 * DAY, playMinutes = 2460),
            Row("魔女的夜宴", EngineType.ONS, null, lastPlayedAgoMs = 14 * DAY, playMinutes = 620),
            Row("Fate/Grand Order", EngineType.ANDROID_APP, null, lastPlayedAgoMs = 20 * HOUR, playMinutes = 5400),
            Row("夜之终章", EngineType.KIRIKIRI, 3, nsfw = true, lastPlayedAgoMs = 30 * DAY, playMinutes = 300),
            Row(
                "在这个世界的角落里发现了你和你的故事 完全版 -Another Story- ～Extra Chapter～",
                EngineType.TYRANO,
                2,
                lastPlayedAgoMs = 40 * DAY,
                playMinutes = 90,
            ),
            Row("未识别的游戏", EngineType.UNKNOWN, null),
            Row("恋と選挙とチョコレート", EngineType.ONS, 5, lastPlayedAgoMs = 60 * DAY, playMinutes = 1260),
            Row("白色相簿", EngineType.KIRIKIRI, 1),
            Row("千恋＊万花", EngineType.KIRIKIRI, 2, lastPlayedAgoMs = 4 * HOUR, playMinutes = 3120),
        )

    fun libraryGames(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ): List<Game> =
        rows.mapIndexed { index, row ->
            val coverUri =
                row.cover?.let { n ->
                    val id = context.resources.getIdentifier("fixture_cover_$n", "drawable", context.packageName)
                    "android.resource://${context.packageName}/$id"
                }
            Game(
                id = FIRST_ID + index,
                title = row.title,
                originalTitle = row.original,
                engine = row.engine,
                rootUri = "debug-fixture://library/$index",
                coverUri = coverUri,
                coverSourceType = if (coverUri != null) 1 else 0,
                favorite = row.favorite,
                nsfw = row.nsfw,
                totalPlayTime = row.playMinutes * MINUTE,
                lastPlayedAt = row.lastPlayedAgoMs?.let { now - it } ?: 0L,
                playStatus = if (row.playMinutes > 0) "playing" else "unplayed",
                launchTarget = if (row.engine == EngineType.KIRIKIRI) "data.xp3" else null,
            )
        }

    fun searchIndex(games: List<Game>): Map<Long, String> = games.associate { it.id to GameSearchIndex.build(it, emptyList()) }

    /** Fourteen days of play history for the trend widget. */
    fun playTrend(now: Long = System.currentTimeMillis()): List<PlayTrendPoint> {
        val minutes = listOf(35L, 0L, 80L, 120L, 0L, 45L, 150L, 60L, 0L, 95L, 30L, 180L, 70L, 110L)
        val today = now - now % DAY
        return minutes.mapIndexed { i, m -> PlayTrendPoint(today - (minutes.size - 1 - i) * DAY, m * MINUTE, if (m > 0) 1 + (i % 3) else 0) }
    }

    /** Cover tasks covering every group of the task center: needs attention, active and finished. */
    fun coverQueue(): CoverQueueUiState {
        fun task(
            offset: Int,
            title: String,
            source: CoverSourceMode,
            status: CoverTaskStatus,
            bytes: Long = 0L,
            total: Long? = null,
            candidates: Int = 0,
            error: String? = null,
        ) = CoverTaskUi(FIRST_ID + offset, title, source, status, bytes, total, candidates, null, error)
        return CoverQueueUiState(
            listOf(
                task(5, "晴空轨迹", CoverSourceMode.BANGUMI, CoverTaskStatus.NEEDS_REVIEW, candidates = 4),
                task(6, "魔女的夜宴", CoverSourceMode.HIKARINAGI, CoverTaskStatus.FAILED, error = "网络连接超时"),
                task(1, "樱色彼方", CoverSourceMode.BANGUMI, CoverTaskStatus.DOWNLOADING, bytes = 482_000, total = 1_240_000, candidates = 3),
                task(3, "月影回廊", CoverSourceMode.VNDB, CoverTaskStatus.SEARCHING),
                task(4, "紫苑之庭", CoverSourceMode.NEXTMOE, CoverTaskStatus.QUEUED),
                task(0, "9nine新章", CoverSourceMode.BANGUMI, CoverTaskStatus.COMPLETED),
                task(9, "在这个世界的角落里发现了你和你的故事 完全版", CoverSourceMode.BANGUMI, CoverTaskStatus.COMPLETED),
                task(11, "恋と選挙とチョコレート", CoverSourceMode.VNDB, CoverTaskStatus.CANCELLED),
            ),
            CoverQueueStatus.RUNNING,
        )
    }

    fun xp3Picker(game: Game): Xp3PickerUi =
        Xp3PickerUi(
            game = game,
            candidates =
                listOf(
                    "data.xp3",
                    "patch.xp3",
                    "patch2.xp3",
                    "bgimage.xp3",
                    "evimage.xp3",
                    "fgimage.xp3",
                    "voice/voice.xp3",
                    "bgm/bgm.xp3",
                    "extra/special/omake.xp3",
                ),
            loading = false,
        )

    fun androidAppPicker(): AndroidAppPickerUi =
        AndroidAppPickerUi(
            entries =
                listOf(
                    "Fate/Grand Order" to "com.bilibili.fatego",
                    "原神" to "com.miHoYo.Yuanshen",
                    "崩坏：星穹铁道" to "com.miHoYo.hkrpg",
                    "明日方舟" to "com.hypergryph.arknights",
                    "碧蓝档案" to "com.nexon.bluearchive",
                    "Kirikiroid2" to "org.github.krkrz.kirikiroid2",
                    "ONScripter-Plus" to "org.onscripter.plus",
                    "GameHub Lite" to "com.xiaoji.gamehub.lite",
                    "Winlator" to "com.winlator",
                    "RetroArch" to "com.retroarch.aarch64",
                    "一个名字特别特别长的安卓应用程序用来检查标题省略的表现" to "com.example.very.long.package.name.for.layout.testing",
                    "Joiplay" to "cyou.joiplay.joiplay",
                ).map { (label, pkg) -> AndroidAppEntry(pkg, "$pkg.MainActivity", label) },
            loading = false,
        )

    fun shortcutPicker(): ExternalShortcutPickerUi {
        fun entry(
            title: String,
            app: String,
            pkg: String,
            imported: Boolean = false,
        ) = ExternalShortcutPickerEntry(
            ExternalShortcutDescriptor(0, pkg, app, "game-${title.hashCode()}", title, "$pkg.DeepLinkActivity", pinned = true, dynamic = false),
            imported,
        )
        val entries =
            listOf(
                entry("千恋＊万花", "GameHub", "com.xiaoji.gamehub"),
                entry("星光咖啡馆与死神之蝶", "GameHub", "com.xiaoji.gamehub", imported = true),
                entry("Summer Pockets", "GameHub", "com.xiaoji.gamehub"),
                entry("樱之诗", "盖世小鸡", "com.xiaoji.egggame"),
                entry("紫苑之庭 完整版", "盖世小鸡", "com.xiaoji.egggame", imported = true),
                entry("在这个世界的角落里发现了你和你的故事 完全版", "GameHub", "com.xiaoji.gamehub"),
            )
        return ExternalShortcutPickerUi(entries, selectedKeys = setOf(entries[0].stableKey, entries[2].stableKey), loading = false)
    }

    /** Builds (or refreshes) the throw-away tree the built-in file browser lists; returns its two roots. */
    fun fileBrowserRoots(context: Context): Pair<File, File> {
        val base = File(context.cacheDir, "showcase-browser")
        val internal = File(base, "storage")
        val app = File(base, "app")

        fun dir(
            parent: File,
            name: String,
            vararg children: String,
        ): File =
            File(parent, name).also { d ->
                d.mkdirs()
                children.forEach { File(d, it).apply { if (!exists()) writeText(it) } }
            }

        fun sized(
            parent: File,
            name: String,
            bytes: Long,
        ) {
            val f = File(parent, name)
            if (!f.exists() || f.length() != bytes) RandomAccessFile(f, "rw").use { it.setLength(bytes) }
        }
        listOf("Android", "DCIM", "Documents", "Download", "Music", "Pictures").forEach { dir(internal, it, "a.bin", "b.bin") }
        val games = dir(internal, "Games")
        dir(games, "9nine新章", "startup.tjs", "data.xp3", "patch.xp3")
        dir(games, "樱色彼方", "0.txt", "nscript.dat", "arc.nsa")
        dir(games, "Summer Pockets REFLECTION BLUE", "root.pfs", "root.pfs.000", "scenario.pfs")
        dir(games, "月影回廊", "index.html", "data")
        dir(File(internal, "Download"), "patches", "fix1.xp3")
        sized(internal, "data.xp3", 3_400_000L)
        sized(internal, "patch.xp3", 1_150_000L)
        sized(internal, "startup.tjs", 4_200L)
        sized(internal, "index.html", 12_800L)
        sized(internal, "0.txt", 96_000L)
        sized(internal, "readme.txt", 1_024L)
        sized(internal, "archive.zip", 52_000_000L)
        dir(app, "files", "settings.json")
        dir(app, "cache")
        return internal to app
    }
}
