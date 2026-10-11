package com.kireibox.app.launcher

import com.kireibox.app.LauncherActivity
import java.io.File

internal fun LauncherActivity.installUpdate(apkPath: String) {
    val updateRoot = runCatching { File(cacheDir, "updates").canonicalFile }.getOrNull()
    val apk = runCatching { File(apkPath).canonicalFile }.getOrNull()
    if (updateRoot == null || apk == null || !apk.isFile || apk.parentFile != updateRoot) {
        showMessage("无法安装更新", "更新文件位置无效，请重新下载。")
        return
    }
    if (!updateInstaller.canRequestPackageInstalls()) {
        pendingUpdateApkPath = apk.absolutePath
        runCatching { startActivity(updateInstaller.unknownSourcesSettingsIntent()) }
            .onFailure {
                pendingUpdateApkPath = null
                showMessage("无法打开授权页面", it.message ?: "请在系统设置中允许 KireiBox 安装未知应用。")
            }
        return
    }
    pendingUpdateApkPath = null
    runCatching { updateInstallLauncher.launch(updateInstaller.installIntent(apk)) }
        .onFailure { showMessage("无法启动安装器", it.message ?: "系统安装器不可用。") }
}
