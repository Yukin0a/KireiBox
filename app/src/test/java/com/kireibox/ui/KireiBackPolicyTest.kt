package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KireiBackPolicyTest {
    @Test
    fun taskCenterClosesBeforeAnyLibraryTransientUi() {
        assertEquals(
            KireiLibraryBackAction.CLOSE_TASK_CENTER,
            KireiBackPolicy.resolveLibraryBack(
                taskCenterOpen = true,
                filterMenuExpanded = true,
                imeVisible = true,
                searchActive = true,
                editMode = true,
            ),
        )
    }

    @Test
    fun settingsSubPageReturnsToItsParent() {
        assertEquals(
            KireiBackDestination.SETTINGS_PARENT,
            KireiBackPolicy.resolveRoot(KireiTab.SETTINGS, KireiSettingsPage.KRKR),
        )
    }

    @Test
    fun settingsPagesHaveAStableSingleFocusParentHierarchy() {
        val expectedParents =
            mapOf(
                KireiSettingsPage.ROOT to null,
                KireiSettingsPage.APPEARANCE to KireiSettingsPage.ROOT,
                KireiSettingsPage.LIBRARY_HUB to KireiSettingsPage.ROOT,
                KireiSettingsPage.LIBRARY to KireiSettingsPage.LIBRARY_HUB,
                KireiSettingsPage.COVERS to KireiSettingsPage.ROOT,
                // P3: 来源账号 is a sub-page of 封面与元数据, so back returns to the cover page (a root
                // shortcut opens it with the root as its back target instead, see below).
                KireiSettingsPage.COVER_ACCOUNTS to KireiSettingsPage.COVERS,
                // 同步游玩时长 hangs off the NextMoe card; the overview opens it as a root shortcut.
                KireiSettingsPage.PLAYTIME_SYNC to KireiSettingsPage.COVER_ACCOUNTS,
                KireiSettingsPage.ENGINES to KireiSettingsPage.ROOT,
                KireiSettingsPage.KRKR to KireiSettingsPage.ENGINES,
                KireiSettingsPage.ONS to KireiSettingsPage.ENGINES,
                KireiSettingsPage.ARTEMIS to KireiSettingsPage.ENGINES,
                KireiSettingsPage.TYRANO to KireiSettingsPage.ENGINES,
                KireiSettingsPage.WINLATOR to KireiSettingsPage.ENGINES,
                KireiSettingsPage.INTERFACE to KireiSettingsPage.ROOT,
                KireiSettingsPage.PERMISSIONS to KireiSettingsPage.ROOT,
                KireiSettingsPage.DIAGNOSTICS to KireiSettingsPage.ROOT,
                KireiSettingsPage.ABOUT to KireiSettingsPage.ROOT,
            )

        assertEquals(expectedParents, KireiSettingsPage.entries.associateWith(KireiBackPolicy::settingsParent))
    }

    @Test
    fun aShortcutPageReturnsStraightToTheRoot() {
        // r4 · S2: 添加扫描目录 / Bangumi results open second-level pages the user never walked through;
        // back must not land on 游戏库 / 封面与元数据 first. r5: the root's 来源账号 row and the cover
        // picker's login action open 来源账号 the same way.
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.LIBRARY, KireiSettingsPage.LIBRARY))
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.COVER_ACCOUNTS, KireiSettingsPage.COVER_ACCOUNTS))
        // Pages reached normally (or below the result's page) keep their parent.
        assertEquals(KireiSettingsPage.LIBRARY_HUB, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.LIBRARY, null))
        assertEquals(KireiSettingsPage.COVERS, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.COVER_ACCOUNTS, KireiSettingsPage.COVERS))
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsBackTarget(KireiSettingsPage.ROOT, null))
    }

    @Test
    fun settingsDepthMakesLibraryLeavesOneLevelDeeperThanTheirHub() {
        assertEquals(0, KireiBackPolicy.settingsDepth(KireiSettingsPage.ROOT))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.APPEARANCE))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.LIBRARY_HUB))
        assertEquals(2, KireiBackPolicy.settingsDepth(KireiSettingsPage.LIBRARY))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.COVERS))
        assertEquals("来源账号 slides in one level deeper than 封面与元数据", 2, KireiBackPolicy.settingsDepth(KireiSettingsPage.COVER_ACCOUNTS))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.DIAGNOSTICS))
    }

    @Test
    fun appearancePageIsATopLevelSettingsPageThatReturnsToRoot() {
        assertEquals(
            KireiBackDestination.SETTINGS_PARENT,
            KireiBackPolicy.resolveRoot(KireiTab.SETTINGS, KireiSettingsPage.APPEARANCE),
        )
        assertEquals(KireiSettingsPage.ROOT, KireiBackPolicy.settingsParent(KireiSettingsPage.APPEARANCE))
    }

    @Test
    fun gamesTabReturnsToHome() {
        assertEquals(
            KireiBackDestination.HOME,
            KireiBackPolicy.resolveRoot(KireiTab.GAMES, KireiSettingsPage.ROOT),
        )
    }

    @Test
    fun homeReturnsExitToSystem() {
        assertEquals(
            KireiBackDestination.EXIT,
            KireiBackPolicy.resolveRoot(KireiTab.HOME, KireiSettingsPage.ROOT),
        )
    }

    @Test
    fun closingKrkrEditorRestoresItsGameActionSheet() {
        val game = Game(id = 41, title = "ATRI")
        val state = KireiUiState(krkrEditorGame = game)

        val closed = KireiBackPolicy.closeKrkrEditor(state)

        assertEquals(game, closed.selectedGame)
        assertNull(closed.krkrEditorGame)
    }

    @Test
    fun libraryFilterMenuConsumesBackBeforeImeAndSearch() {
        assertEquals(
            KireiLibraryBackAction.CLOSE_FILTER_MENU,
            KireiBackPolicy.resolveLibraryBack(
                filterMenuExpanded = true,
                imeVisible = true,
                searchActive = true,
                editMode = true,
            ),
        )
    }

    @Test
    fun libraryImeConsumesBackBeforeSearch() {
        assertEquals(
            KireiLibraryBackAction.HIDE_IME,
            KireiBackPolicy.resolveLibraryBack(
                filterMenuExpanded = false,
                imeVisible = true,
                searchActive = true,
                editMode = true,
            ),
        )
    }

    @Test
    fun librarySearchConsumesBackAfterImeIsHidden() {
        assertEquals(
            KireiLibraryBackAction.CLOSE_SEARCH,
            KireiBackPolicy.resolveLibraryBack(
                filterMenuExpanded = false,
                imeVisible = false,
                searchActive = true,
                editMode = true,
            ),
        )
    }

    @Test
    fun libraryPageHandlesBackWhenNoTransientUiIsOpen() {
        assertEquals(
            KireiLibraryBackAction.PAGE,
            KireiBackPolicy.resolveLibraryBack(
                filterMenuExpanded = false,
                imeVisible = false,
                searchActive = false,
                editMode = false,
            ),
        )
    }
}
