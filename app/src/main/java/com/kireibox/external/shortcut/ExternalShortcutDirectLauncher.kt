package com.kireibox.external.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Foreground direct handoff for a recipe that was validated and captured during import. */
object ExternalShortcutDirectLauncher {
    fun launch(
        context: Context,
        identity: ExternalShortcutIdentity.Parsed,
        encodedTarget: String?,
    ): Result<Unit>? {
        val recipe = ExternalShortcutDirectLaunch.parse(encodedTarget) ?: return null
        return runCatching {
            check(identity.packageName == ExternalShortcutDirectLaunch.GAME_HUB_PACKAGE) {
                "快捷方式直启信息与应用不匹配，请重新扫描。"
            }
            val spec = recipe.intentSpec()
            val component = ComponentName(spec.packageName, spec.activityName)
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getActivityInfo(component, 0)
                }
            check(info.applicationInfo.enabled && info.enabled && info.exported && info.permission.isNullOrBlank()) {
                "盖世直启入口不可用，请重新扫描或更新盖世。"
            }
            context.startActivity(
                Intent(spec.action).apply {
                    this.component = component
                    flags = spec.flags
                    spec.extras.forEach { (key, value) -> putExtra(key, value) }
                }
            )
        }.recoverCatching { error ->
            if (error is PackageManager.NameNotFoundException) {
                throw IllegalStateException("未找到可用的盖世直启入口，请确认已安装原版盖世 6.1.2 或重新扫描。", error)
            }
            throw error
        }
    }
}
