package com.kireibox.external.winlator

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.yuki.yukihub.launcher.ScriptEngineLaunchers
import com.yuki.yukihub.model.Game
import org.json.JSONObject
import java.io.File
import java.util.Locale

enum class WinlatorAvailability { READY, NOT_INSTALLED, UNSUPPORTED }

/** What the installed Winlator can do for us; [packageName] is the real one, a repackaged build may differ. */
data class WinlatorStatus(
    val availability: WinlatorAvailability,
    val packageName: String? = null,
    val versionName: String? = null,
)

sealed interface WinlatorLaunchResult {
    data class Started(val packageName: String) : WinlatorLaunchResult

    data class Failed(val message: String) : WinlatorLaunchResult
}

/** Starts a Windows game through the exported `ExternalLaunchActivity` of the user's own Winlator-CN. */
object WinlatorLauncher {
    private const val DIRECTORY_TARGET_MARKER = "["

    /**
     * Finds the package that exports the launch activity: by its `winlator://launch` link first (works
     * for any repackaged build), then by the default package name.
     */
    fun status(context: Context): WinlatorStatus {
        val pm = context.packageManager
        val viaLink = exportedLaunchPackage(pm)
        val packageName = viaLink ?: WinlatorContract.DEFAULT_PACKAGE.takeIf { launchActivity(pm, it)?.exported == true }
        if (packageName != null) return WinlatorStatus(WinlatorAvailability.READY, packageName, packageInfo(pm, packageName)?.versionName)
        val installed = packageInfo(pm, WinlatorContract.DEFAULT_PACKAGE)
        return if (installed != null) {
            WinlatorStatus(WinlatorAvailability.UNSUPPORTED, WinlatorContract.DEFAULT_PACKAGE, installed.versionName)
        } else {
            WinlatorStatus(WinlatorAvailability.NOT_INSTALLED)
        }
    }

    /** The launcher activity of Winlator, so the guide and settings can open it to create a container. */
    fun openAppIntent(
        context: Context,
        packageName: String,
    ): Intent? = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launch(
        context: Context,
        game: Game,
        settings: WinlatorSettings,
        sessionId: Long,
    ): WinlatorLaunchResult {
        val app = context.applicationContext
        return try {
            val packageName = readyPackage(app)
            val dir = directoryOf(game)
            val exe = resolveExe(dir, game.launchTarget) ?: blocked("找不到启动文件 ${game.launchTarget.orEmpty().ifBlank { ".exe" }}")
            app.startActivity(buildIntent(packageName, dir.absolutePath, exe, "kireibox-$sessionId", settings))
            WinlatorLaunchResult.Started(packageName)
        } catch (stop: LaunchBlocked) {
            WinlatorLaunchResult.Failed(stop.message.orEmpty())
        } catch (_: ActivityNotFoundException) {
            WinlatorLaunchResult.Failed(statusMessage(WinlatorAvailability.UNSUPPORTED))
        } catch (_: SecurityException) {
            WinlatorLaunchResult.Failed("系统拒绝了对 Winlator 的跳转，请检查它是否被停用或限制。")
        }
    }

    /** Ends a launch early with a message the user can act on. */
    private class LaunchBlocked(message: String) : Exception(message)

    private fun blocked(message: String): Nothing = throw LaunchBlocked(message)

    private fun readyPackage(app: Context): String {
        val status = status(app)
        return status.packageName?.takeIf { status.availability == WinlatorAvailability.READY } ?: blocked(statusMessage(status.availability))
    }

    fun statusMessage(availability: WinlatorAvailability): String =
        when (availability) {
            WinlatorAvailability.READY -> ""
            WinlatorAvailability.NOT_INSTALLED -> "未安装 Winlator，请在「Winlator 引擎设置」中下载"
            WinlatorAvailability.UNSUPPORTED -> "Winlator 版本过旧，不支持外置启动，请更新（需含外置启动功能的 winlator-cn）"
        }

    internal fun buildIntent(
        packageName: String,
        dirPath: String,
        exe: String,
        launchId: String,
        settings: WinlatorSettings,
    ): Intent =
        Intent().apply {
            component = ComponentName(packageName, WinlatorContract.ACTIVITY_NAME)
            putExtra(WinlatorContract.EXTRA_DIR_PATH, dirPath)
            putExtra(WinlatorContract.EXTRA_EXE_PATH, exe)
            // Winlator only skips its own confirmation when the user turned that off there; false never forces it.
            putExtra(WinlatorContract.EXTRA_CONFIRM, false)
            putExtra(WinlatorContract.EXTRA_LAUNCH_ID, launchId)
            WinlatorContract.extras(settings).forEach { (key, value) ->
                when (value) {
                    is Int -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                    is Map<*, *> -> putExtra(key, JSONObject(value).toString())
                    else -> putExtra(key, value.toString())
                }
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun directoryOf(game: Game): File {
        val path = ScriptEngineLaunchers.uriToFilePath(game.rootUri)?.takeIf { it.startsWith("/") } ?: blocked("无法读取游戏目录的真实路径，请用内置文件浏览器重新添加这个游戏。")
        return File(path).takeIf { it.isDirectory } ?: blocked("无法读取游戏目录，可能需要「所有文件访问」权限，或游戏已被移动。")
    }

    /** The exe relative to the mounted folder: the stored target, or the only .exe the folder has. */
    internal fun resolveExe(
        dir: File,
        target: String?,
    ): String? {
        val stored = target?.trim().orEmpty().replace('\\', '/')
        return when {
            stored.isEmpty() || stored.startsWith(DIRECTORY_TARGET_MARKER) -> onlyExe(dir)
            stored.startsWith("/") -> relativeInside(dir, stored)?.takeIf { File(dir, it).isFile }
            else -> stored.takeIf { File(dir, it).isFile }
        }
    }

    private fun relativeInside(
        dir: File,
        absolute: String,
    ): String? {
        val base = dir.canonicalPath
        return File(absolute).canonicalPath.takeIf { it.startsWith("$base/") }?.substring(base.length + 1)
    }

    private fun onlyExe(dir: File): String? =
        dir.listFiles { file -> file.isFile && file.name.lowercase(Locale.ROOT).endsWith(".exe") }
            ?.singleOrNull()
            ?.name

    private fun exportedLaunchPackage(pm: PackageManager): String? {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("${WinlatorContract.DEEPLINK_SCHEME}://${WinlatorContract.DEEPLINK_HOST}")).addCategory(Intent.CATEGORY_BROWSABLE)
        val matches =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(link, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(link, 0)
                }
            }.getOrDefault(emptyList())
        return matches.firstOrNull { it.activityInfo?.name == WinlatorContract.ACTIVITY_NAME && it.activityInfo.exported }?.activityInfo?.packageName
    }

    private fun launchActivity(
        pm: PackageManager,
        packageName: String,
    ): ActivityInfo? =
        runCatching {
            val component = ComponentName(packageName, WinlatorContract.ACTIVITY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, 0)
            }
        }.getOrNull()

    private fun packageInfo(
        pm: PackageManager,
        packageName: String,
    ): PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
}
