package com.kireibox.external.shortcut

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameHubShortcutDumpParserTest {
    @Test fun parsesTheValidatedGameHubLocalGameRoute() {
        val parsed = GameHubShortcutDumpParser.parse(ACTUAL_DUMP)

        val recipe = parsed["totsulover_chs"]
        assertEquals("local_2FeqxFmUQBiqMenoyWYaeA", recipe?.gameId)
        assertEquals(recipe, ExternalShortcutDirectLaunch.parse(recipe?.encode()))
        assertEquals(
            ExternalShortcutDirectLaunch.GAME_HUB_ACTIVITY,
            recipe?.intentSpec()?.activityName,
        )
        assertEquals(
            ExternalShortcutDirectLaunch.FLAG_ACTIVITY_NEW_TASK or
                ExternalShortcutDirectLaunch.FLAG_ACTIVITY_CLEAR_TOP,
            recipe?.intentSpec()?.flags,
        )
    }

    @Test fun rejectsUnrelatedOrTamperedIntentData() {
        assertTrue(GameHubShortcutDumpParser.parse(ACTUAL_DUMP.replace("local_game_launch", "open_store")).isEmpty())
        assertTrue(GameHubShortcutDumpParser.parse(ACTUAL_DUMP.replace(".DeepLinkActivity", ".MainActivity")).isEmpty())
        assertTrue(GameHubShortcutDumpParser.parse(ACTUAL_DUMP.replace("android.intent.action.VIEW", "bad.action")).isEmpty())
        assertTrue(GameHubShortcutDumpParser.parse(ACTUAL_DUMP.replace("local_2FeqxFmUQBiqMenoyWYaeA", "../../bad")).isEmpty())
        assertNull(ExternalShortcutDirectLaunch.parse("kireibox-direct://gamehub/v1/not-hex"))
    }

    @Test fun hiddenCardsAreAvailableForReimport() {
        val visible = com.yuki.yukihub.model.Game(hidden = false)
        val hidden = visible.copy(hidden = true)
        assertTrue(ExternalShortcutMergePolicy.isImported(visible))
        assertFalse(ExternalShortcutMergePolicy.isImported(hidden))
        assertFalse(ExternalShortcutMergePolicy.isImported(null))
    }

    companion object {
        private val ACTUAL_DUMP =
            """
            ShortcutInfo {id=totsulover_chs, flags=0x8a [PinIc-fStr]
              packageName=com.xiaoji.egggame
              activity=ComponentInfo{com.xiaoji.egggame/com.xiaoji.egggame.MainActivity}
              shortLabel=totsulover_chs, resId=0[null]
              intents=[Intent { act=android.intent.action.VIEW flg=0x14000000 cmp=com.xiaoji.egggame/.DeepLinkActivity }/PersistableBundle[{app_nav_target=local_game_launch, app_nav_game_id=local_2FeqxFmUQBiqMenoyWYaeA}]]
              iconRes=0[null]}
            """.trimIndent()
    }
}
