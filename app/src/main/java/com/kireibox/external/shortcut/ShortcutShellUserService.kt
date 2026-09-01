package com.kireibox.external.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.app.PendingIntent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import androidx.annotation.Keep
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs inside Shizuku with the shell UID. It exposes no arbitrary command execution. */
@Keep
class ShortcutShellUserService @Keep constructor(context: Context) : IShortcutShellService.Stub() {
    private val shellContext: Context = context.createPackageContext(
        SHELL_PACKAGE,
        Context.CONTEXT_IGNORE_SECURITY,
    )
    private val launcherApps: LauncherApps = requireNotNull(
        shellContext.getSystemService(LauncherApps::class.java),
    ) { "LauncherApps service unavailable" }

    override fun canReadPinnedShortcuts(userId: Int): Boolean = asShell {
        runCatching {
            launcherApps.getShortcuts(pinnedShortcutQuery(), currentUser(userId))
        }.isSuccess
    }

    override fun scan(userId: Int): MutableList<Bundle> = asShell {
        val user = currentUser(userId)
        val query = pinnedShortcutQuery()
        val shortcuts = launcherApps.getShortcuts(query, user).orEmpty()
            .filter { it.isEnabled }
        val gameHubRoutes = if (shortcuts.any { it.`package` == ExternalShortcutDirectLaunch.GAME_HUB_PACKAGE }) {
            runCatching { readGameHubDirectLaunches(userId) }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        shortcuts.asSequence()
            .map { shortcut ->
                Bundle().apply {
                    putInt(KEY_USER_ID, userId)
                    putString(KEY_PACKAGE, shortcut.`package`)
                    putString(KEY_APP_NAME, applicationLabel(shortcut.`package`))
                    putString(KEY_ID, shortcut.id)
                    putString(KEY_TITLE, shortcut.shortLabel?.toString().orEmpty())
                    putString(KEY_ACTIVITY, shortcut.activity?.className)
                    // An any-launcher query only marks isPinned for pins owned by this caller.
                    // Reaching this result set is the reliable proof that another launcher pins it.
                    putBoolean(KEY_PINNED, true)
                    putBoolean(KEY_DYNAMIC, shortcut.isDynamic)
                    gameHubRoutes[shortcut.id]?.encode()?.let {
                        putString(KEY_DIRECT_LAUNCH_TARGET, it)
                    }
                }
            }
            .toMutableList()
    }

    override fun exists(userId: Int, packageName: String, shortcutId: String): Boolean = asShell {
        queryOne(userId, packageName, shortcutId) != null
    }

    override fun launch(userId: Int, packageName: String, shortcutId: String): Boolean = asShell {
        if (queryOne(userId, packageName, shortcutId) == null) return@asShell false
        launcherApps.startShortcut(
            packageName,
            shortcutId,
            null,
            null,
            currentUser(userId),
        )
        true
    }

    override fun launchIntent(
        userId: Int,
        packageName: String,
        shortcutId: String,
    ): PendingIntent? = asShell {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@asShell null
        if (queryOne(userId, packageName, shortcutId) == null) return@asShell null
        launcherApps.getShortcutIntent(
            packageName,
            shortcutId,
            null,
            currentUser(userId),
        )
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun queryOne(userId: Int, packageName: String, shortcutId: String) =
        launcherApps.getShortcuts(
            pinnedShortcutQuery().apply {
                setPackage(packageName)
                setShortcutIds(listOf(shortcutId))
            },
            currentUser(userId),
        )?.firstOrNull { it.isEnabled && it.id == shortcutId }

    /**
     * Shell owns the signature ACCESS_SHORTCUTS permission on supported Android builds. That
     * permission intentionally grants visibility into shortcuts pinned by another launcher, even
     * though shell is not the selected HOME app and hasShortcutHostPermission() returns false.
     */
    private fun pinnedShortcutQuery() = LauncherApps.ShortcutQuery().apply {
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER)
    }

    private fun currentUser(requestedUserId: Int): UserHandle {
        val current = UserHandle.getUserHandleForUid(Process.myUid())
        check(requestedUserId == current.hashCode()) { "Only the current user is supported" }
        return current
    }

    private fun applicationLabel(packageName: String): String = runCatching {
        val info = shellContext.packageManager.getApplicationInfo(packageName, 0)
        shellContext.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /** Fixed command and fixed package: this service never accepts an arbitrary shell command. */
    private fun readGameHubDirectLaunches(userId: Int): Map<String, ExternalShortcutDirectLaunch> {
        val process = ProcessBuilder(
            "/system/bin/dumpsys",
            "shortcut",
            "--user",
            userId.toString(),
            "-p",
            "^com\\.xiaoji\\.egggame$",
        ).redirectErrorStream(true).start()
        val reader = Executors.newSingleThreadExecutor()
        return try {
            val output = reader.submit<String> {
                process.inputStream.bufferedReader().use { input ->
                    val result = StringBuilder()
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(result.length + count <= MAX_DUMP_CHARS) { "Shortcut dump is too large" }
                        result.append(buffer, 0, count)
                    }
                    result.toString()
                }
            }.get(DUMP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            check(process.waitFor(DUMP_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) {
                "Unable to inspect GameHub shortcuts"
            }
            GameHubShortcutDumpParser.parse(output)
        } finally {
            process.destroy()
            reader.shutdownNow()
        }
    }

    private inline fun <T> asShell(block: () -> T): T {
        val token = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val KEY_USER_ID = "user_id"
        const val KEY_PACKAGE = "package"
        const val KEY_APP_NAME = "app_name"
        const val KEY_ID = "shortcut_id"
        const val KEY_TITLE = "title"
        const val KEY_ACTIVITY = "activity"
        const val KEY_PINNED = "pinned"
        const val KEY_DYNAMIC = "dynamic"
        const val KEY_DIRECT_LAUNCH_TARGET = "direct_launch_target"
        private const val MAX_DUMP_CHARS = 512 * 1024
        private const val DUMP_TIMEOUT_SECONDS = 5L
    }
}
