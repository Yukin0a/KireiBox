package com.kireibox.ui

import com.kireibox.external.shortcut.ExternalShortcutDirectLaunch
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class GameLocationLabelTest {
    @Test fun hidesOpaqueDirectLaunchRecipeFromPresentation() {
        val root = ExternalShortcutIdentity.rootUri(0, "com.xiaoji.egggame", "totsulover_chs")
        val direct = Game(
            engine = EngineType.ANDROID_APP,
            rootUri = root,
            launchTarget = ExternalShortcutDirectLaunch.create("local_2FeqxFmUQBiqMenoyWYaeA")!!.encode(),
        )
        assertEquals("直接启动应用快捷方式", GameLocationLabel.launchSubtitle(direct))
        assertEquals(
            "通过 Shizuku 启动",
            GameLocationLabel.launchSubtitle(direct.copy(launchTarget = "com.example.MainActivity")),
        )
    }

    @Test fun usesShortEngineLaunchCopy() {
        assertEquals(
            "使用 KRKR 启动",
            GameLocationLabel.launchSubtitle(Game(engine = EngineType.KIRIKIRI)),
        )
        assertEquals(
            "使用当前引擎启动",
            GameLocationLabel.launchSubtitle(Game(engine = EngineType.ONS)),
        )
    }

    @Test fun xp3SubtitleUsesReadableNameOrAutomaticFallback() {
        assertEquals(
            "data.xp3",
            GameLocationLabel.xp3LaunchSubtitle(
                Game(engine = EngineType.KIRIKIRI, launchTarget = "content://games/folder/data.xp3"),
            ),
        )
        assertEquals(
            "自动选择合适的 XP3",
            GameLocationLabel.xp3LaunchSubtitle(Game(engine = EngineType.KIRIKIRI)),
        )
    }

    @Test fun formatsPrimaryStorage() {
        assertEquals(
            "内部存储 / Download / gal / ATRI",
            GameLocationLabel.formatDocumentId("primary:Download/gal/ATRI")
        )
    }

    @Test fun formatsSdCardStorage() {
        assertEquals(
            "存储卡 1234-ABCD / Games / ATRI",
            GameLocationLabel.formatDocumentId("1234-ABCD:Games/ATRI")
        )
    }

    @Test fun formatsDocumentsHomeRoot() {
        assertEquals(
            "内部存储 / Documents / VisualNovels",
            GameLocationLabel.formatDocumentId("home:VisualNovels")
        )
    }
}
