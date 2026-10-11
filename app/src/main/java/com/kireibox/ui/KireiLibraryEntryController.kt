package com.kireibox.ui

import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.viewModelScope
import com.kireibox.external.shortcut.ExternalShortcutDescriptor
import com.kireibox.external.shortcut.ExternalShortcutDirectLaunch
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.kireibox.external.shortcut.ExternalShortcutMergePolicy
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.kireibox.krkr.KrkrGameSettings
import com.yuki.yukihub.launch.KrkrLaunchTargetScanner
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal class KireiLibraryEntryController(host: KireiViewModel) : KireiViewModelController(host) {
    fun openXp3Picker(game: Game) = openLaunchFilePicker(game, LaunchFileKind.XP3)

    fun openExePicker(game: Game) = openLaunchFilePicker(game, LaunchFileKind.EXE)

    private fun openLaunchFilePicker(
        game: Game,
        kind: LaunchFileKind,
    ) {
        mutableState.value =
            mutableState.value.copy(
                selectedGame = null,
                xp3Picker = Xp3PickerUi(game = game, kind = kind),
            )
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    KrkrLaunchTargetScanner.list(hostApplication, game.rootUri, maxDepth = 4, extension = kind.extension)
                }
            withContext(Dispatchers.Main) {
                val current = mutableState.value.xp3Picker
                if (current?.game?.id != game.id) return@withContext
                mutableState.value =
                    mutableState.value.copy(
                        xp3Picker =
                            current.copy(
                                candidates = result.getOrDefault(emptyList()),
                                loading = false,
                                error = result.exceptionOrNull()?.message,
                            ),
                    )
            }
        }
    }

    fun dismissXp3Picker() {
        mutableState.value = mutableState.value.copy(xp3Picker = null)
    }

    fun selectXp3LaunchTarget(relativePath: String) {
        val picker = mutableState.value.xp3Picker ?: return
        val target = relativePath.replace('\\', '/').trimStart('/')
        if (target !in picker.candidates) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.findById(picker.game.id)
            val result =
                runCatching {
                    check(current != null) { "游戏记录不存在" }
                    check(current.engine == picker.kind.engine) { "这个游戏不能选择这种启动文件" }
                    val updated =
                        current.copy(
                            launchTarget = target,
                            updatedAt = System.currentTimeMillis(),
                        )
                    check(repository.update(updated) == 1) { "保存启动文件失败" }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            xp3Picker = null,
                            notice = "已选择启动文件：$target",
                        )
                    } else {
                        mutableState.value.copy(
                            xp3Picker =
                                mutableState.value.xp3Picker?.copy(
                                    loading = false,
                                    error = result.exceptionOrNull()?.message ?: "保存启动文件失败",
                                ),
                        )
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun openAndroidAppPicker() {
        mutableState.value = mutableState.value.copy(androidAppPicker = AndroidAppPickerUi())
        viewModelScope.launch(Dispatchers.IO) {
            val app = hostApplication
            val result =
                runCatching {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                        .asSequence()
                        .filter { info ->
                            val activity = info.activityInfo
                            activity != null &&
                                activity.enabled &&
                                activity.applicationInfo?.enabled == true &&
                                activity.packageName != app.packageName
                        }
                        .map { info ->
                            val activity = requireNotNull(info.activityInfo)
                            AndroidAppEntry(
                                packageName = activity.packageName,
                                activityName = activity.name,
                                label =
                                    info.loadLabel(app.packageManager).toString()
                                        .trim().ifEmpty { activity.packageName },
                            )
                        }
                        .distinctBy(AndroidAppEntry::componentKey)
                        .sortedWith(
                            compareBy<AndroidAppEntry> { it.label.lowercase(Locale.ROOT) }
                                .thenBy { it.packageName },
                        )
                        .toList()
                }
            withContext(Dispatchers.Main) {
                if (mutableState.value.androidAppPicker == null) return@withContext
                mutableState.value =
                    mutableState.value.copy(
                        androidAppPicker =
                            AndroidAppPickerUi(
                                entries = result.getOrDefault(emptyList()),
                                loading = false,
                                error = result.exceptionOrNull()?.message,
                            ),
                    )
            }
        }
    }

    fun dismissAndroidAppPicker() {
        mutableState.value = mutableState.value.copy(androidAppPicker = null)
    }

    fun selectAndroidApp(entry: AndroidAppEntry) {
        mutableState.value = mutableState.value.copy(androidAppPicker = null, addGame = null)
        addAndroidApp(entry.packageName, entry.activityName, entry.label)
    }

    fun openExternalShortcutPicker() {
        val status = shortcutGateway.status()
        mutableState.value = mutableState.value.copy(shortcutServiceStatus = status)
        if (status == ShortcutServiceStatus.PERMISSION_REQUIRED) {
            mutableState.value = mutableState.value.copy(externalShortcutPicker = ExternalShortcutPickerUi())
            val result = shortcutGateway.requestPermission()
            if (result.isFailure) {
                mutableState.value =
                    mutableState.value.copy(
                        externalShortcutPicker =
                            ExternalShortcutPickerUi(
                                loading = false,
                                error = result.exceptionOrNull()?.message ?: "无法请求 Shizuku 权限",
                            ),
                    )
            }
            return
        }
        if (status != ShortcutServiceStatus.READY) {
            mutableState.value =
                mutableState.value.copy(
                    externalShortcutPicker =
                        ExternalShortcutPickerUi(
                            loading = false,
                            error = shortcutStatusMessage(status),
                        ),
                )
            return
        }
        mutableState.value = mutableState.value.copy(externalShortcutPicker = ExternalShortcutPickerUi())
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                shortcutGateway.scan().map { shortcuts ->
                    shortcuts.map { shortcut ->
                        val existing = synchronizeImportedExternalShortcut(shortcut)
                        ExternalShortcutPickerEntry(
                            shortcut,
                            imported = ExternalShortcutMergePolicy.isImported(existing),
                        )
                    }
                }
            withContext(Dispatchers.Main) {
                if (mutableState.value.externalShortcutPicker == null) return@withContext
                mutableState.value =
                    mutableState.value.copy(
                        externalShortcutPicker =
                            ExternalShortcutPickerUi(
                                entries = result.getOrDefault(emptyList()),
                                loading = false,
                                error = result.exceptionOrNull()?.message,
                            ),
                        shortcutServiceStatus =
                            if (
                                result.exceptionOrNull()?.message?.contains("shell 读取桌面快捷方式") == true
                            ) {
                                ShortcutServiceStatus.SYSTEM_PERMISSION_MISSING
                            } else {
                                shortcutGateway.status()
                            },
                    )
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun onShizukuPermissionResult(granted: Boolean) {
        mutableState.value = mutableState.value.copy(shortcutServiceStatus = shortcutGateway.status())
        if (granted && mutableState.value.externalShortcutPicker != null) {
            openExternalShortcutPicker()
        } else if (!granted) {
            mutableState.value =
                mutableState.value.copy(
                    externalShortcutPicker =
                        mutableState.value.externalShortcutPicker?.copy(
                            loading = false,
                            error = "未授权 KireiBox 使用 Shizuku。",
                        ),
                )
        }
    }

    fun refreshShortcutServiceStatus() {
        mutableState.value = mutableState.value.copy(shortcutServiceStatus = shortcutGateway.status())
    }

    fun dismissExternalShortcutPicker() {
        shortcutGateway.release()
        mutableState.value = mutableState.value.copy(externalShortcutPicker = null)
    }

    fun toggleExternalShortcutSelection(stableKey: String) {
        val picker = mutableState.value.externalShortcutPicker ?: return
        val selected = ExternalShortcutPickerPolicy.toggle(picker.entries, picker.selectedKeys, stableKey)
        mutableState.value = mutableState.value.copy(externalShortcutPicker = picker.copy(selectedKeys = selected))
    }

    fun toggleAllExternalShortcuts() {
        val picker = mutableState.value.externalShortcutPicker ?: return
        val selected = ExternalShortcutPickerPolicy.toggleAll(picker.entries, picker.selectedKeys)
        mutableState.value = mutableState.value.copy(externalShortcutPicker = picker.copy(selectedKeys = selected))
    }

    fun importSelectedExternalShortcuts() {
        val picker = mutableState.value.externalShortcutPicker ?: return
        val selected = picker.entries.filter { !it.imported && it.stableKey in picker.selectedKeys }
        if (selected.isEmpty()) return
        mutableState.value = mutableState.value.copy(externalShortcutPicker = picker.copy(loading = true, error = null))
        viewModelScope.launch(Dispatchers.IO) {
            val result =
                runCatching {
                    selected.count { entry ->
                        val shortcut = entry.shortcut
                        val rootUri =
                            ExternalShortcutIdentity.rootUri(
                                shortcut.userId,
                                shortcut.packageName,
                                shortcut.shortcutId,
                            )
                        val existing = repository.findByIdentity(rootUri, "")
                        if (existing != null) {
                            if (!existing.hidden) return@count false
                            existing.hidden = false
                            existing.updatedAt = System.currentTimeMillis()
                            check(repository.update(existing) == 1) { "无法恢复已移出的快捷方式卡片" }
                            return@count true
                        }
                        val game =
                            Game(
                                title = shortcut.title,
                                originalTitle = shortcut.title,
                                engine = EngineType.ANDROID_APP,
                                rootUri =
                                    ExternalShortcutIdentity.rootUri(
                                        shortcut.userId,
                                        shortcut.packageName,
                                        shortcut.shortcutId,
                                    ),
                                entryKey = "",
                                emulatorPackage = shortcut.packageName,
                                launchTarget = shortcut.directLaunchTarget ?: shortcut.activityName,
                            )
                        repository.insertIfNotExists(game) > 0L
                    }
                }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        val count = result.getOrDefault(0)
                        mutableState.value.copy(
                            externalShortcutPicker = null,
                            addGame = null,
                            notice = if (count > 0) "已导入 $count 个应用快捷方式" else "所选快捷方式已在游戏库中",
                        )
                    } else {
                        mutableState.value.copy(
                            externalShortcutPicker =
                                mutableState.value.externalShortcutPicker?.copy(
                                    loading = false,
                                    error = result.exceptionOrNull()?.message ?: "导入应用快捷方式失败",
                                ),
                        )
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    private fun synchronizeImportedExternalShortcut(shortcut: ExternalShortcutDescriptor): Game? {
        val rootUri = ExternalShortcutIdentity.rootUri(shortcut.userId, shortcut.packageName, shortcut.shortcutId)
        val legacyRootUri = ExternalShortcutIdentity.legacyVivoRootUri(shortcut.packageName, shortcut.shortcutId)
        val existing =
            repository.findByIdentity(rootUri, "")
                ?: repository.findByIdentity(legacyRootUri, "")?.also { legacy ->
                    legacy.rootUri = rootUri
                    legacy.updatedAt = System.currentTimeMillis()
                    repository.update(legacy)
                }
                ?: return null
        val userRenamed = ExternalShortcutMergePolicy.isUserRenamed(existing.title, existing.originalTitle)
        val launchTarget =
            shortcut.directLaunchTarget
                ?: existing.launchTarget?.takeIf { ExternalShortcutDirectLaunch.parse(it) != null }
                ?: shortcut.activityName
        val changed =
            existing.originalTitle != shortcut.title ||
                existing.emulatorPackage != shortcut.packageName ||
                existing.launchTarget != launchTarget ||
                (!userRenamed && existing.title != shortcut.title)
        if (changed) {
            if (!userRenamed) existing.title = shortcut.title
            existing.originalTitle = shortcut.title
            existing.emulatorPackage = shortcut.packageName
            existing.launchTarget = launchTarget
            existing.updatedAt = System.currentTimeMillis()
            repository.update(existing)
        }
        return existing
    }

    private fun shortcutStatusMessage(status: ShortcutServiceStatus): String =
        when (status) {
            ShortcutServiceStatus.NOT_INSTALLED -> "未安装 Shizuku。"
            ShortcutServiceStatus.NOT_RUNNING -> "Shizuku 未运行，请先完成无线调试配对。"
            ShortcutServiceStatus.PERMISSION_REQUIRED -> "需要授权 KireiBox 使用 Shizuku。"
            ShortcutServiceStatus.PERMISSION_DENIED -> "Shizuku 权限已被拒绝，请在授权应用中重新允许。"
            ShortcutServiceStatus.UNSUPPORTED_VERSION -> "需要 Shizuku 13.1.5 或更新版本。"
            ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> "只支持无线调试启动的 Shizuku，不支持 Root/Sui 模式。"
            ShortcutServiceStatus.SYSTEM_PERMISSION_MISSING -> "当前系统未向 shell 开放快捷方式权限。"
            ShortcutServiceStatus.BINDING -> "正在连接 Shizuku。"
            ShortcutServiceStatus.CONNECTION_LOST -> "Shizuku 连接已失效。"
            ShortcutServiceStatus.READY -> "Shizuku 快捷方式服务已连接。"
        }

    fun addAndroidApp(
        packageName: String,
        activity: String?,
        label: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = Game(title = label, originalTitle = label, engine = EngineType.ANDROID_APP)
            game.rootUri = "android-app://$packageName"
            game.entryKey = activity.orEmpty()
            game.launchTarget = activity.orEmpty()
            game.emulatorPackage = packageName
            repository.insertIfNotExists(game)
            withContext(Dispatchers.Main) { mutableState.value = mutableState.value.copy(notice = "已添加 Android 应用：$label") }
            reloadInternal()
        }
    }

    fun restoreRemovedGame(game: Game) {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = game.copy(hidden = false, updatedAt = System.currentTimeMillis())
            val result = runCatching { check(repository.update(restored) == 1) { "恢复失败" } }
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(
                            removedGames = mutableState.value.removedGames.filterNot { it.id == game.id },
                            notice = "已恢复 ${game.title ?: "游戏"}",
                        )
                    } else {
                        mutableState.value.withNotice(result.exceptionOrNull()?.message ?: "恢复失败", NoticeTone.ERROR)
                    }
            }
            if (result.isSuccess) reloadInternal()
        }
    }

    fun openKrkrEditor(game: Game) {
        mutableState.value =
            mutableState.value.copy(
                selectedGame = null,
                krkrEditorGame = game,
                krkrSettings = krkrStore.load(game)
            )
    }

    fun closeKrkrEditor() {
        mutableState.value = KireiBackPolicy.closeKrkrEditor(mutableState.value)
    }

    fun saveKrkrSettings(
        game: Game,
        settings: KrkrGameSettings
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = krkrStore.save(game, settings)
            withContext(Dispatchers.Main) {
                mutableState.value =
                    if (result.isSuccess) {
                        mutableState.value.copy(krkrSettings = settings, krkrEditorGame = null, notice = "KRKR 设置已保存")
                    } else {
                        mutableState.value.copy(krkrSettings = settings).withNotice(result.exceptionOrNull()?.message ?: "保存失败", NoticeTone.ERROR)
                    }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cover accounts / cover settings
// ---------------------------------------------------------------------------------------------
