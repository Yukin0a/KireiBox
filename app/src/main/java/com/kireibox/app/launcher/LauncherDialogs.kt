package com.kireibox.app.launcher

import com.kireibox.app.LauncherActivity
import com.kireibox.ui.KireiPrompt

internal fun LauncherActivity.showMessage(
    title: String,
    message: String
) {
    if (isFinishing || isDestroyed) return
    showPrompt(KireiPrompt(title = title, message = message, confirmLabel = "好"))
}

internal fun LauncherActivity.showPrompt(prompt: KireiPrompt) {
    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        launcherPrompt.value = prompt
    } else {
        runOnUiThread { launcherPrompt.value = prompt }
    }
}
