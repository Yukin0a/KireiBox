package com.kireibox.app.launcher

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.kireibox.app.LauncherActivity
import com.kireibox.external.shortcut.ExternalShortcutLaunchPolicy
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.kireibox.shortcut.DesktopGameShortcutFactory
import com.kireibox.ui.LauncherMemoryCoordinator
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.launcherbridge.ExternalPlaySessionTimer
import com.yuki.yukihub.launcherbridge.LauncherGameLaunchBridge
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.util.DevLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LauncherActivity.launchGame(game: Game) {
    if (ExternalShortcutLaunchPolicy.requiresShizuku(game)) {
        when (shortcutGateway.status()) {
            ShortcutServiceStatus.READY -> Unit
            ShortcutServiceStatus.PERMISSION_REQUIRED -> {
                pendingShizukuLaunch = game
                shortcutGateway.requestPermission().onFailure {
                    pendingShizukuLaunch = null
                    showMessage("无法授权 Shizuku", it.message ?: "Shizuku 权限请求失败。")
                }
                return
            }
            else -> {
                val message =
                    shortcutGateway.requestPermission().exceptionOrNull()?.message
                        ?: "Shizuku 快捷方式服务不可用。"
                showMessage("无法启动快捷方式", message)
                return
            }
        }
    }
    if (game.engine == EngineType.KIRIKIRI && !hasNativeFileAccess()) {
        DevLogger.event("krkr launch gate: native file access missing -> prompt")
        pendingNativeLaunch = game
        requestNativeFileAccess()
        return
    }
    launchPreparedGame(game)
}

internal fun LauncherActivity.consumeDesktopGameIntent(source: Intent?) {
    if (source?.action != DesktopGameShortcutFactory.ACTION_LAUNCH_GAME) return
    val gameId = source.getLongExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID, -1L)
    source.action = Intent.ACTION_MAIN
    source.removeExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID)
    if (gameId <= 0L) {
        showMessage("快捷方式已失效", "桌面快捷方式没有有效的游戏 ID。")
        return
    }
    lifecycleScope.launch(Dispatchers.IO) {
        val game = GameRepository(applicationContext).findById(gameId)
        withContext(Dispatchers.Main) {
            when {
                game == null -> showMessage("快捷方式已失效", "游戏已不在 KireiBox 游戏库中。")
                game.hidden -> showMessage("快捷方式已失效", "该游戏已从 KireiBox 游戏库隐藏。")
                else -> launchGame(game)
            }
        }
    }
}

internal fun LauncherActivity.pinGameShortcut(game: Game) {
    val result = DesktopGameShortcutFactory.requestPin(this, game)
    if (result.isFailure) {
        showMessage("无法添加到桌面", result.exceptionOrNull()?.message ?: "桌面快捷方式创建失败。")
    } else if (result.getOrNull() == DesktopGameShortcutFactory.PinOutcome.UPDATED) {
        viewModel.showNotice("已更新桌面快捷方式")
    } else {
        viewModel.showNotice("请在系统弹窗中确认添加到桌面")
    }
}

internal fun LauncherActivity.launchPreparedGame(game: Game) {
    LauncherGameLaunchBridge.launchAsync(
        this,
        game,
        object : LauncherGameLaunchBridge.LaunchCallback {
            override fun onResult(result: LauncherGameLaunchBridge.LaunchResult) {
                if (result.success) {
                    val externalHandoff =
                        result.handoff == LauncherGameLaunchBridge.LaunchHandoff.EXTERNAL_APP ||
                            result.handoff == LauncherGameLaunchBridge.LaunchHandoff.SHORTCUT
                    runningSessionId = result.sessionId
                    engineWasOpened = result.handoff == LauncherGameLaunchBridge.LaunchHandoff.INTERNAL_ENGINE
                    runningSessionJournaled = engineWasOpened
                    externalPlayActive = externalHandoff
                    externalPlayTimer =
                        if (externalHandoff) {
                            ExternalPlaySessionTimer(applicationContext).also { it.start() }
                        } else {
                            externalPlayTimer?.cleanup()
                            null
                        }
                    launcherHandoffActive = result.handoff != LauncherGameLaunchBridge.LaunchHandoff.NONE
                    viewModel.suspendForEngineHandoff()
                    LauncherMemoryCoordinator.onEngineHandoff()
                    viewModel.reload()
                } else if (!hasNativeFileAccess() && isPermissionLaunchFailure(result.message)) {
                    DevLogger.event("launch permission failure routed to grant dialog: ${result.message}")
                    pendingNativeLaunch = game
                    showNativeFileAccessDialog(hint = result.message)
                } else {
                    showMessage("无法启动", result.message)
                }
            }
        }
    )
}

internal fun LauncherActivity.launchKrkrNative() {
    if (!hasNativeFileAccess()) {
        pendingNativeShell = true
        requestNativeFileAccess()
        return
    }
    openKrkrNativeShell()
}

internal fun LauncherActivity.openKrkrNativeShell() {
    val result =
        runCatching {
            startActivity(
                EmulatorLauncher.buildInternalKrkrIntent(
                    this,
                    null,
                    null,
                    true,
                    "auto",
                    false,
                ),
            )
        }
    if (result.isFailure) {
        showMessage("无法启动", result.exceptionOrNull()?.message ?: "KRKR 原生引擎启动失败")
    }
}

internal fun LauncherActivity.isPermissionLaunchFailure(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    return message.contains("EACCES", ignoreCase = true) ||
        message.contains("Permission denied", ignoreCase = true) ||
        message.contains("所有文件访问", ignoreCase = true) ||
        message.contains("无法创建 savedata", ignoreCase = true) ||
        message.contains("savedata 无法写入", ignoreCase = true) ||
        message.contains("只读目录", ignoreCase = true) ||
        message.contains("写入检查失败", ignoreCase = true)
}
