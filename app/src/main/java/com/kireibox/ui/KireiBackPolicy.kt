package com.kireibox.ui

enum class KireiBackDestination {
    SETTINGS_PARENT,
    HOME,
    EXIT,
}

enum class KireiLibraryBackAction {
    CLOSE_TASK_CENTER,
    CLOSE_FILTER_MENU,
    HIDE_IME,
    CLOSE_SEARCH,
    EXIT_EDIT_MODE,
    PAGE,
}

object KireiBackPolicy {
    fun resolveLibraryBack(
        taskCenterOpen: Boolean = false,
        filterMenuExpanded: Boolean,
        imeVisible: Boolean,
        searchActive: Boolean,
        editMode: Boolean,
    ): KireiLibraryBackAction =
        when {
            taskCenterOpen -> KireiLibraryBackAction.CLOSE_TASK_CENTER
            filterMenuExpanded -> KireiLibraryBackAction.CLOSE_FILTER_MENU
            imeVisible -> KireiLibraryBackAction.HIDE_IME
            searchActive -> KireiLibraryBackAction.CLOSE_SEARCH
            editMode -> KireiLibraryBackAction.EXIT_EDIT_MODE
            else -> KireiLibraryBackAction.PAGE
        }

    fun settingsParent(page: KireiSettingsPage): KireiSettingsPage? =
        when (page) {
            KireiSettingsPage.ROOT -> null
            KireiSettingsPage.APPEARANCE,
            KireiSettingsPage.LIBRARY_HUB,
            KireiSettingsPage.COVERS,
            KireiSettingsPage.ENGINES,
            KireiSettingsPage.INTERFACE,
            KireiSettingsPage.PERMISSIONS,
            KireiSettingsPage.DIAGNOSTICS,
            KireiSettingsPage.ABOUT -> KireiSettingsPage.ROOT
            KireiSettingsPage.LIBRARY -> KireiSettingsPage.LIBRARY_HUB
            KireiSettingsPage.COVER_ACCOUNTS -> KireiSettingsPage.COVERS
            KireiSettingsPage.PLAYTIME_SYNC -> KireiSettingsPage.COVER_ACCOUNTS
            KireiSettingsPage.KRKR,
            KireiSettingsPage.ONS,
            KireiSettingsPage.ARTEMIS,
            KireiSettingsPage.TYRANO,
            KireiSettingsPage.WINLATOR -> KireiSettingsPage.ENGINES
        }

    /**
     * Where back from a settings page goes. A page opened as a shortcut ([shortcutOrigin]: a search
     * result, the root's 来源账号 row, the cover picker) returns straight to the root, which still
     * shows a search's results; otherwise its parent.
     */
    fun settingsBackTarget(
        page: KireiSettingsPage,
        shortcutOrigin: KireiSettingsPage?,
    ): KireiSettingsPage = if (shortcutOrigin == page) KireiSettingsPage.ROOT else settingsParent(page) ?: KireiSettingsPage.ROOT

    fun settingsDepth(page: KireiSettingsPage): Int =
        when (page) {
            KireiSettingsPage.ROOT -> 0
            KireiSettingsPage.LIBRARY,
            KireiSettingsPage.COVER_ACCOUNTS,
            KireiSettingsPage.KRKR,
            KireiSettingsPage.ONS,
            KireiSettingsPage.ARTEMIS,
            KireiSettingsPage.TYRANO,
            KireiSettingsPage.WINLATOR -> 2
            KireiSettingsPage.PLAYTIME_SYNC -> 3
            else -> 1
        }

    fun resolveRoot(
        tab: KireiTab,
        settingsPage: KireiSettingsPage
    ): KireiBackDestination =
        when {
            tab == KireiTab.SETTINGS && settingsPage != KireiSettingsPage.ROOT -> KireiBackDestination.SETTINGS_PARENT
            tab != KireiTab.HOME -> KireiBackDestination.HOME
            else -> KireiBackDestination.EXIT
        }

    fun closeKrkrEditor(state: KireiUiState): KireiUiState {
        val game = state.krkrEditorGame ?: return state
        return state.copy(selectedGame = game, krkrEditorGame = null)
    }

    fun closeWinlatorEditor(state: KireiUiState): KireiUiState {
        val game = state.winlatorEditorGame ?: return state
        return state.copy(selectedGame = game, winlatorEditorGame = null)
    }
}
