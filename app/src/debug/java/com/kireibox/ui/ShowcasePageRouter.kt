package com.kireibox.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.kireibox.designsystem.KireiColors

/**
 * Page ids of the "real page on fixture data" showcases that UiShowcaseActivity opens with
 * `--es page <id>` (see ShowcasePageRouter). Ids are matched case-insensitively.
 */
internal object ShowcasePages {
    const val LIBRARY = "library"
    const val LIBRARY_EDIT = "library-edit"
    const val LIBRARY_EMPTY = "library-empty"
    const val COMPONENTS_HOME = "components-home"
    const val DASHBOARD_EDITOR = "dashboard-editor"
    const val TASK_CENTER = "task-center"
    const val ACCOUNTS = "accounts"

    /** Optional `--ei game <index>` for `sheet-actions`. */
    const val EXTRA_GAME = "game"

    val ids: List<String> =
        listOf(LIBRARY, LIBRARY_EDIT, LIBRARY_EMPTY, "pickers") + ShowcasePicker.entries.map { it.id } +
            listOf(COMPONENTS_HOME, DASHBOARD_EDITOR, TASK_CENTER, ACCOUNTS) + ShowcaseSheet.entries.map { it.id }

    fun handles(page: String?): Boolean = page?.trim()?.lowercase() in ids
}

@Composable
internal fun ShowcasePageRouter(
    activity: ComponentActivity,
    intent: Intent,
    page: String,
    colors: KireiColors,
) {
    val id = page.trim().lowercase()
    val picker = ShowcasePicker.fromId(id)
    val sheet = ShowcaseSheet.fromId(id)
    when {
        id == ShowcasePages.LIBRARY -> ShowcaseLibraryPage(activity, colors, ShowcaseLibraryVariant.BROWSE)
        id == ShowcasePages.LIBRARY_EDIT -> ShowcaseLibraryPage(activity, colors, ShowcaseLibraryVariant.EDIT)
        id == ShowcasePages.LIBRARY_EMPTY -> ShowcaseLibraryPage(activity, colors, ShowcaseLibraryVariant.EMPTY)
        id == ShowcasePages.TASK_CENTER -> ShowcaseLibraryPage(activity, colors, ShowcaseLibraryVariant.TASKS)
        id == ShowcasePages.COMPONENTS_HOME -> ShowcaseHomePage(activity, colors, editor = false)
        id == ShowcasePages.DASHBOARD_EDITOR -> ShowcaseHomePage(activity, colors, editor = true)
        id == ShowcasePages.ACCOUNTS -> ShowcaseAccountsPage(activity, colors)
        picker != null -> ShowcasePickerPage(activity, colors, picker)
        sheet != null ->
            ShowcaseSheetPage(
                activity,
                colors,
                sheet,
                gameIndex = intent.getIntExtra(ShowcasePages.EXTRA_GAME, -1).takeIf { it >= 0 },
            )
    }
}
