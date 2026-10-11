package com.kireibox.app.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.kireibox.app.LauncherActivity
import com.kireibox.ui.AddGamePick
import com.kireibox.ui.BuiltinFileBrowserRequest
import com.kireibox.ui.KireiPrompt
import com.yuki.yukihub.engine.EngineStorageProbe
import com.yuki.yukihub.settings.YukiSettingsStore

internal fun LauncherActivity.chooseStorage(pick: AddGamePick? = null) {
    val useBuiltin =
        YukiSettingsStore(this)
            .snapshot().useBuiltinFileChooser
    if (!useBuiltin) {
        openSystemPicker(pick)
        return
    }
    if (hasNativeFileAccess()) {
        openBuiltinBrowser(pick)
    } else {
        pendingBrowserOpen = true
        pendingBrowserPick = pick
        showBuiltinFileAccessDialog(pick)
    }
}

internal fun LauncherActivity.openBuiltinBrowser(pick: AddGamePick? = null) {
    val internal = Environment.getExternalStorageDirectory()
    val appDirectory = getExternalFilesDir(null) ?: filesDir
    fileBrowserRequest.value =
        BuiltinFileBrowserRequest(
            startPath = internal.absolutePath,
            appPath = appDirectory.absolutePath,
            pick = pick
        )
}

internal fun LauncherActivity.openSystemPicker(pick: AddGamePick? = null) {
    if (pick == null) {
        folderPicker.launch(null)
    } else {
        pendingAddPick = pick
        addGameFolderPicker.launch(null)
    }
}

internal fun LauncherActivity.showBuiltinFileAccessDialog(pick: AddGamePick? = null) {
    if (isFinishing || isDestroyed) return
    showPrompt(
        KireiPrompt(
            title = "允许 KireiBox 浏览文件",
            message =
                buildString {
                    append("Android 11 及以上需要“所有文件访问权限”才能使用内置浏览器。授权后会自动继续刚才的操作；KireiBox 本版本只浏览和选择，不会复制、移动、重命名或删除文件。")
                    if (EngineStorageProbe.isHuaweiFamily()) {
                        append("\n\n华为/HarmonyOS 路径：设置 → 应用 → KireiBox → 权限管理 → 存储 → 允许；HarmonyOS 3 及以上还需开启“所有文件访问”。")
                    }
                },
            confirmLabel = "前往授权",
            dismissLabel = "使用系统 SAF",
            onConfirm = {
                val manageApp =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                runCatching { startActivity(manageApp) }
                    .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    .recoverCatching {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    }
            },
            onDismiss = {
                pendingBrowserOpen = false
                pendingBrowserPick = null
                openSystemPicker(pick)
            }
        )
    )
}

/** Permission guide text; Huawei/HarmonyOS gets its own path (权限管理 → 存储). */
internal fun LauncherActivity.storagePermissionGuide(android11Plus: Boolean): String =
    if (EngineStorageProbe.isHuaweiFamily()) {
        buildString {
            append("华为/HarmonyOS 路径：设置 → 应用 → KireiBox → 权限管理 → 存储 → 允许。")
            if (android11Plus) append("HarmonyOS 3 及以上还需开启“所有文件访问”。")
            append("已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。")
        }
    } else if (android11Plus) {
        "Android 11 及以上需要“所有文件访问权限”：设置 → 应用 → KireiBox → 权限 → 所有文件访问 → 允许。授权后返回会自动继续；仍无法写入请把游戏目录移到内部存储常规位置后重试。"
    } else {
        "Android 10 及以下请授予 KireiBox“存储”权限：设置 → 应用 → KireiBox → 权限 → 存储 → 允许。已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。"
    }

/**
 * Android 11+ requires MANAGE_EXTERNAL_STORAGE (all-files access) because
 * scoped storage is mandatory. Android 6-10 use the legacy
 * WRITE_EXTERNAL_STORAGE runtime permission: with targetSdk 29 and
 * requestLegacyExternalStorage it grants full physical access, which is
 * what the KRKR native engine needs to write <game>/savedata.
 */
internal fun LauncherActivity.hasNativeFileAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

/** Requests the permission model matching this Android version; onResume continues the pending action. */
internal fun LauncherActivity.requestNativeFileAccess() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        showNativeFileAccessDialog()
    } else {
        krkrStoragePermission.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
        )
    }
}

internal fun LauncherActivity.showNativeFileAccessDialog(hint: String? = null) {
    if (isFinishing || isDestroyed) return
    val android11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val permissionName = if (android11Plus) "所有文件访问" else "存储"
    showPrompt(
        KireiPrompt(
            title = "允许 KRKR 读取游戏",
            message =
                buildString {
                    append("目录授权用于扫描和管理；KRKR 原生内核还需要“")
                    append(permissionName)
                    append("”权限才能直接读取 XP3，并在游戏目录的 savedata 中保存进度。KireiBox 不会修改 XP3。")
                    if (!hint.isNullOrBlank()) {
                        append("\n\n上次失败原因：")
                        append(hint)
                    }
                    append("\n\n")
                    append(storagePermissionGuide(android11Plus))
                },
            confirmLabel = "前往授权",
            dismissLabel = "稍后",
            onConfirm = {
                val details =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                if (android11Plus) {
                    val manageApp =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    runCatching { startActivity(manageApp) }
                        .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        .recoverCatching { startActivity(details) }
                        .onFailure { showMessage("无法打开设置", "请在系统设置中手动允许 KireiBox 的“所有文件访问”后再试。") }
                } else {
                    runCatching { startActivity(details) }
                        .onFailure { showMessage("无法打开设置", "请在系统设置中手动允许 KireiBox 的“存储”权限后再试。") }
                }
            }
        )
    )
}

internal fun LauncherActivity.checkPermissions() {
    val roots = YukiSettingsStore(this).scanRoots()
    val persisted = contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
    val details =
        buildString {
            append("KRKR 原生文件访问：")
            append(if (hasNativeFileAccess()) "正常" else "需要授权")
            append('\n')
            if (roots.isEmpty()) append("尚未添加扫描目录。")
            roots.forEachIndexed { index, root ->
                val grant = persisted[root]
                val doc = runCatching { DocumentFile.fromTreeUri(this@checkPermissions, Uri.parse(root)) }.getOrNull()
                append("目录 ${index + 1}：")
                append(if (grant?.isReadPermission == true && doc?.canRead() == true) "可读取" else "需要重新授权")
                append(if (grant?.isWritePermission == true && doc?.canWrite() == true) " / 可写入" else " / 只读")
                append('\n')
            }
            append("\nSAF 负责扫描、封面与目录管理；KRKR 原生内核使用物理文件路径。\n")
            append(storagePermissionGuide(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R))
        }
    showMessage("权限检查", details)
}

internal fun LauncherActivity.repairPermission(root: String?) {
    pendingPermissionRoot = root
    permissionRepairPicker.launch(root?.let(Uri::parse))
}
