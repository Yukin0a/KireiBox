package com.kireibox.app.launcher

import android.content.Intent
import android.net.Uri
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.AddGamePick

/** Hands a finished folder pick of the 添加游戏 flow to the view model. */
internal fun LauncherActivity.routeAddGamePick(
    pick: AddGamePick,
    uri: Uri
) {
    when (pick.kind) {
        AddGamePick.Kind.BATCH_FOLDER -> viewModel.addBatchFolder(uri)
        AddGamePick.Kind.SINGLE_FOLDER -> viewModel.addSingleFolder(uri, pick.engine)
    }
}

internal fun LauncherActivity.persist(uri: Uri): Boolean {
    return runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        true
    }.recoverCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)
}
