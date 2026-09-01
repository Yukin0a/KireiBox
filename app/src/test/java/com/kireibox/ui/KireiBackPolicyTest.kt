package com.kireibox.ui

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val expectedParents = mapOf(
            KireiSettingsPage.ROOT to null,
            KireiSettingsPage.LIBRARY_HUB to KireiSettingsPage.ROOT,
            KireiSettingsPage.LIBRARY to KireiSettingsPage.LIBRARY_HUB,
            KireiSettingsPage.COVERS to KireiSettingsPage.ROOT,
            KireiSettingsPage.KRKR to KireiSettingsPage.ROOT,
            KireiSettingsPage.INTERFACE to KireiSettingsPage.ROOT,
            KireiSettingsPage.PERMISSIONS to KireiSettingsPage.ROOT,
            KireiSettingsPage.DIAGNOSTICS to KireiSettingsPage.ROOT,
            KireiSettingsPage.ABOUT to KireiSettingsPage.ROOT,
        )

        assertEquals(expectedParents, KireiSettingsPage.entries.associateWith(KireiBackPolicy::settingsParent))
    }

    @Test
    fun settingsDepthMakesLibraryLeavesOneLevelDeeperThanTheirHub() {
        assertEquals(0, KireiBackPolicy.settingsDepth(KireiSettingsPage.ROOT))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.LIBRARY_HUB))
        assertEquals(2, KireiBackPolicy.settingsDepth(KireiSettingsPage.LIBRARY))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.COVERS))
        assertEquals(1, KireiBackPolicy.settingsDepth(KireiSettingsPage.DIAGNOSTICS))
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
    fun expandedLibrarySearchConsumesBackBeforeLeavingLibrary() {
        assertTrue(
            KireiBackPolicy.shouldCloseLibrarySearch(
                editMode = false,
                searchFocused = false,
                query = "",
                revealPhase = SearchRevealPhase.SHOWN,
            )
        )
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
