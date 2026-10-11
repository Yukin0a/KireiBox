package com.kireibox.external.shortcut

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalShortcutPolicyTest {
    @Test fun statusPolicySeparatesShellRootAndPermissionStates() {
        fun status(
            granted: Boolean,
            uid: Int = 2000,
            rationale: Boolean = false
        ) = ShortcutServiceStatusPolicy.resolve(true, true, true, granted, rationale, uid, false, false)
        assertEquals(ShortcutServiceStatus.READY, status(true))
        assertEquals(ShortcutServiceStatus.UNSUPPORTED_IDENTITY, status(true, uid = 0))
        assertEquals(ShortcutServiceStatus.PERMISSION_REQUIRED, status(false))
        assertEquals(ShortcutServiceStatus.PERMISSION_DENIED, status(false, rationale = true))
        assertEquals(
            ShortcutServiceStatus.NOT_RUNNING,
            ShortcutServiceStatusPolicy.resolve(true, false, false, false, false, -1, false, false),
        )
    }

    @Test fun filtersOwnPackageDynamicOnlySourcesAndDuplicates() {
        val accepted = descriptor("com.game", "id", pinned = true)
        assertEquals(
            listOf(accepted),
            ExternalShortcutPolicy.descriptors(
                listOf(
                    accepted,
                    accepted.copy(title = "duplicate"),
                    descriptor("com.kireibox", "own", pinned = true),
                    descriptor("com.features", "dynamic-only", pinned = false, dynamic = true),
                    descriptor("com.none", "none", pinned = false, dynamic = false),
                ),
                "com.kireibox",
            ),
        )
    }

    @Test fun identityRoundTripsUtf8AndParsesLegacyVivo() {
        val root = ExternalShortcutIdentity.rootUri(0, "com.game", "徒然喜欢你_chs")
        assertEquals(ExternalShortcutIdentity.Parsed(0, "com.game", "徒然喜欢你_chs"), ExternalShortcutIdentity.parse(root))
        assertTrue(ExternalShortcutIdentity.parse(ExternalShortcutIdentity.legacyVivoRootUri("com.game", "old"))?.legacy == true)
        assertNull(ExternalShortcutIdentity.parse("android-app://com.game"))
        assertTrue(ExternalShortcutIdentity.isExternalShortcut(Game(engine = EngineType.ANDROID_APP, rootUri = root)))
        assertFalse(ExternalShortcutIdentity.isExternalShortcut(Game(engine = EngineType.KIRIKIRI, rootUri = root)))
    }

    @Test fun protectsUserRename() {
        assertTrue(ExternalShortcutMergePolicy.isUserRenamed("我的名字", "来源名字"))
        assertFalse(ExternalShortcutMergePolicy.isUserRenamed("来源名字", "来源名字"))
    }

    @Test fun directLaunchRecipeBuildsOnlyTheFixedGameHubIntent() {
        val recipe = ExternalShortcutDirectLaunch.create("local_2FeqxFmUQBiqMenoyWYaeA")!!
        val spec = recipe.intentSpec()
        assertEquals("com.xiaoji.egggame", spec.packageName)
        assertEquals("com.xiaoji.egggame.DeepLinkActivity", spec.activityName)
        assertEquals("local_game_launch", spec.extras["app_nav_target"])
        assertEquals("local_2FeqxFmUQBiqMenoyWYaeA", spec.extras["app_nav_game_id"])
        assertNull(ExternalShortcutDirectLaunch.create("not-local"))
    }

    @Test fun importedDirectRecipeNoLongerRequiresShizukuToLaunch() {
        val root = ExternalShortcutIdentity.rootUri(0, "com.xiaoji.egggame", "totsulover_chs")
        val oldCard = Game(engine = EngineType.ANDROID_APP, rootUri = root)
        val directCard =
            oldCard.copy(
                launchTarget = ExternalShortcutDirectLaunch.create("local_2FeqxFmUQBiqMenoyWYaeA")!!.encode(),
            )
        assertTrue(ExternalShortcutLaunchPolicy.requiresShizuku(oldCard))
        assertFalse(ExternalShortcutLaunchPolicy.requiresShizuku(directCard))
    }

    private fun descriptor(
        packageName: String,
        id: String,
        pinned: Boolean,
        dynamic: Boolean = false
    ) = ExternalShortcutDescriptor(0, packageName, packageName, id, id, null, pinned, dynamic)
}
