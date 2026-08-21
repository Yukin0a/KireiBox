package com.kireibox.ui

enum class CoverSearchIntent {
    MANUAL,
    BACKGROUND,
}

enum class CoverPickerOrigin { GAME_MENU, TASK_CENTER }

object CoverPickerNavigationPolicy {
    fun shouldRestoreTaskCenter(origin: CoverPickerOrigin): Boolean = origin == CoverPickerOrigin.TASK_CENTER

    fun retryOrigin(origin: CoverPickerOrigin): CoverPickerOrigin = origin
}

object CoverSelectionPolicy {
    fun shouldAutoApply(
        intent: CoverSearchIntent,
        automaticMatch: Boolean,
        candidateCount: Int,
    ): Boolean = intent == CoverSearchIntent.BACKGROUND && automaticMatch && candidateCount > 0
}
