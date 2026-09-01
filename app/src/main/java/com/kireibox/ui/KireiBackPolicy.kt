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
    ): KireiLibraryBackAction = when {
        taskCenterOpen -> KireiLibraryBackAction.CLOSE_TASK_CENTER
        filterMenuExpanded -> KireiLibraryBackAction.CLOSE_FILTER_MENU
        imeVisible -> KireiLibraryBackAction.HIDE_IME
        searchActive -> KireiLibraryBackAction.CLOSE_SEARCH
        editMode -> KireiLibraryBackAction.EXIT_EDIT_MODE
        else -> KireiLibraryBackAction.PAGE
    }

    fun settingsParent(page: KireiSettingsPage): KireiSettingsPage? = when (page) {
        KireiSettingsPage.ROOT -> null
        KireiSettingsPage.LIBRARY_HUB,
        KireiSettingsPage.COVERS,
        KireiSettingsPage.KRKR,
        KireiSettingsPage.INTERFACE,
        KireiSettingsPage.PERMISSIONS,
        KireiSettingsPage.DIAGNOSTICS,
        KireiSettingsPage.ABOUT -> KireiSettingsPage.ROOT
        KireiSettingsPage.LIBRARY -> KireiSettingsPage.LIBRARY_HUB
    }

    fun settingsDepth(page: KireiSettingsPage): Int = when (page) {
        KireiSettingsPage.ROOT -> 0
        KireiSettingsPage.LIBRARY -> 2
        else -> 1
    }

    fun resolveRoot(tab: KireiTab, settingsPage: KireiSettingsPage): KireiBackDestination = when {
        tab == KireiTab.SETTINGS && settingsPage != KireiSettingsPage.ROOT -> KireiBackDestination.SETTINGS_PARENT
        tab != KireiTab.HOME -> KireiBackDestination.HOME
        else -> KireiBackDestination.EXIT
    }

    fun closeKrkrEditor(state: KireiUiState): KireiUiState {
        val game = state.krkrEditorGame ?: return state
        return state.copy(selectedGame = game, krkrEditorGame = null)
    }

    fun shouldCloseLibrarySearch(
        editMode: Boolean,
        searchFocused: Boolean,
        query: String,
        revealPhase: SearchRevealPhase,
    ): Boolean = editMode || searchFocused || query.isNotBlank() || revealPhase != SearchRevealPhase.HIDDEN
}
