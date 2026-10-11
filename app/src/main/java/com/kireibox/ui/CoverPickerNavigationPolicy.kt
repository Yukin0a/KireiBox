package com.kireibox.ui

enum class CoverPickerOrigin { GAME_MENU, TASK_CENTER }

object CoverPickerNavigationPolicy {
    fun shouldRestoreTaskCenter(origin: CoverPickerOrigin): Boolean = origin == CoverPickerOrigin.TASK_CENTER
}
