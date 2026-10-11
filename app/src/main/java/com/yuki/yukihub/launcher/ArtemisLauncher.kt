package com.yuki.yukihub.launcher

import android.content.Context
import android.content.Intent
import android.os.FileObserver
import android.system.Os
import android.util.Log
import com.akira.tyranoemu.remote.ArtemisActivityV1
import com.akira.tyranoemu.remote.ArtemisActivityV2
import com.akira.tyranoemu.remote.ArtemisActivityV3
import com.kireibox.app.LauncherActivity
import com.yuki.yukihub.launcherbridge.LauncherEngineOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/** Artemis 引擎的作用域镜像、存档同步和 Activity 路由。 */
internal object ArtemisLauncher {
    private const val TAG = "EmulatorLauncher"
    private const val PREFS_NAME = "yukihub_prefs"
    private const val ENGINE_PREF_PREFIX = "artemis_engine."
    private val observerLock = Any()
    private val saveObservers = mutableMapOf<String, FileObserver>()

    data class SaveLocation(
        @JvmField val directory: File?,
        @JvmField val description: String,
        @JvmField val available: Boolean,
    )

    @JvmStatic
    fun buildIntent(
        context: Context,
        packageName: String?,
        gamePath: String?,
        launchTarget: String?,
    ): Intent {
        val resolvedPath = resolveGamePath(gamePath, launchTarget)
        val rootPath = ScriptEngineLaunchers.stripFileScheme(resolvedPath)
        var launchPath = rootPath
        val scoped = isScopedSaveEnabled(context)
        var saveName = safeSaveName(rootPath)
        if (scoped) {
            val save = resolveSaveLocation(context, rootPath, true)
            check(save.available && save.directory != null) { save.description }
            saveName = save.directory.name
            launchPath = prepareScopedMirror(context, rootPath, saveName)
                ?: throw IllegalStateException("无法创建 Artemis 应用独立存档目录")
            logInfo("internal Artemis scoped mirror root=$rootPath -> $launchPath")
        }

        val requestedPackage = packageName?.trim().orEmpty()
        val autoFallback = requestedPackage.equals("internal.artemis", ignoreCase = true)
        val effectivePackage =
            if (autoFallback) {
                preferredPackage(context, requestedPackage, rootPath)
            } else {
                requestedPackage
            }
        val activityClass = chooseActivity(effectivePackage)
        logInfo(
            "ARTEMIS_SCOPED_V2 pkg=$requestedPackage effectivePkg=$effectivePackage " +
                "activity=${activityClass.simpleName} root=$gamePath target=$launchTarget " +
                "resolved=$resolvedPath path=$launchPath scoped=$scoped saveName=$saveName",
        )
        return Intent(context, activityClass).apply {
            if (!launchPath.isNullOrEmpty()) {
                putExtra("path", launchPath)
                putExtra("gamePath", launchPath)
            }
            putExtra("rootUri", gamePath)
            putExtra("launchTarget", launchTarget)
            putExtra("launchMode", "internal.artemis")
            putExtra("orientation", LauncherEngineOptions.orientation(context))
            putExtra("scopedSaveDir", scoped)
            putExtra("scopedSaveName", saveName)
            putExtra("artemisAutoFallback", autoFallback)
            putExtra("artemisFallbackStage", fallbackStage(effectivePackage))
            addFlags(engineIntentFlags())
            appendThemeColors(this, context)
        }
    }

    @JvmStatic
    fun resolveGamePath(
        rootUri: String?,
        launchTarget: String?
    ): String? = ScriptEngineLaunchers.uriToFilePath(rootUri)?.takeUnless(String::isEmpty) ?: rootUri

    @JvmStatic
    fun resolveSaveLocation(
        context: Context?,
        rootPath: String?,
        scoped: Boolean
    ): SaveLocation {
        if (!scoped) {
            return SaveLocation(
                null,
                "Artemis 已使用游戏原始目录，无法安全识别存档文件",
                false,
            )
        }
        if (rootPath.isNullOrBlank() || rootPath.startsWith("content://")) {
            return SaveLocation(null, "无法解析游戏本地目录", false)
        }
        val external =
            context?.getExternalFilesDir(null)
                ?: return SaveLocation(null, "应用独立存储目录不可用", false)
        return SaveLocation(
            File(File(external, "save"), safeSaveName(rootPath)),
            "Artemis 独立存档目录",
            true,
        )
    }

    private fun prepareScopedMirror(
        context: Context,
        rootPath: String?,
        saveName: String?
    ): String? {
        if (rootPath.isNullOrBlank()) return null
        return try {
            val sourceRoot = File(rootPath)
            if (!sourceRoot.isDirectory) return null
            val internal = context.filesDir ?: return null
            val external = context.getExternalFilesDir(null) ?: return null
            val name = saveName?.takeIf(String::isNotBlank) ?: safeSaveName(rootPath)
            val mirrorRoot = File(File(internal, "artemis_mirror"), name)
            val saveRoot = File(File(external, "save"), name)
            if (!ensureDirectory(mirrorRoot) || !ensureDirectory(saveRoot)) return null

            val imported = copyRegularFiles(saveRoot, mirrorRoot, onlyNewer = false)
            if (imported > 0) {
                logInfo("Artemis imported external saves count=$imported from=$saveRoot to=$mirrorRoot")
            }
            val children = sourceRoot.listFiles()
            var linkCount = 0
            var skippedSaveCount = 0
            children?.forEach { child ->
                val childName = child?.name
                if (childName.isNullOrEmpty()) return@forEach
                val link = File(mirrorRoot, childName)
                if (!isResourceName(childName)) {
                    if (isSymlink(link)) deleteRecursively(link)
                    skippedSaveCount++
                } else if (ensureSymlink(link, child)) {
                    linkCount++
                }
            }
            if (!children.isNullOrEmpty() && linkCount == 0) return null
            startSaveObserver(mirrorRoot, saveRoot)
            logInfo(
                "Artemis scoped mirror ready source=$rootPath mirror=${mirrorRoot.absolutePath} " +
                    "save=${saveRoot.absolutePath} links=$linkCount skippedSave=$skippedSaveCount",
            )
            mirrorRoot.absolutePath
        } catch (error: Throwable) {
            logWarn("prepare Artemis scoped mirror failed root=$rootPath", error)
            null
        }
    }

    private fun startSaveObserver(
        mirrorRoot: File,
        saveRoot: File
    ) {
        try {
            val mirrorPath = mirrorRoot.absolutePath
            val savePath = saveRoot.absolutePath
            val previous: FileObserver?
            synchronized(observerLock) {
                previous = saveObservers.remove(mirrorPath)
                saveObservers.values.forEach { runCatching { it.stopWatching() } }
                saveObservers.clear()
            }
            previous?.stopWatching()
            val observer =
                object : FileObserver(
                    mirrorPath,
                    CLOSE_WRITE or MOVED_TO or CREATE,
                ) {
                    override fun onEvent(
                        event: Int,
                        path: String?
                    ) {
                        try {
                            copyRegularFiles(File(mirrorPath), File(savePath), onlyNewer = true)
                        } catch (error: Throwable) {
                            logWarn("Artemis realtime save export failed", error)
                        }
                    }
                }
            observer.startWatching()
            synchronized(observerLock) { saveObservers[mirrorPath] = observer }
            logInfo("Artemis save observer started mirror=$mirrorPath save=$savePath")
        } catch (error: Throwable) {
            logWarn("Artemis save observer start failed mirror=$mirrorRoot save=$saveRoot", error)
        }
    }

    private fun copyRegularFiles(
        fromDirectory: File?,
        toDirectory: File?,
        onlyNewer: Boolean
    ): Int {
        if (fromDirectory?.isDirectory != true || toDirectory == null) return 0
        if (!ensureDirectory(toDirectory)) return 0
        return fromDirectory.listFiles()?.count { source ->
            if (source?.isFile != true || isSymlink(source)) return@count false
            val destination = File(toDirectory, source.name)
            if (onlyNewer && destination.exists() &&
                destination.lastModified() >= source.lastModified() &&
                destination.length() == source.length()
            ) {
                return@count false
            }
            copyFile(source, destination)
        } ?: 0
    }

    private fun copyFile(
        source: File,
        destination: File
    ): Boolean =
        try {
            destination.parentFile?.let { if (!ensureDirectory(it)) return false }
            FileInputStream(source).use { input ->
                FileOutputStream(destination, false).use { output -> input.copyTo(output, 64 * 1024) }
            }
            destination.setLastModified(source.lastModified())
            true
        } catch (error: Throwable) {
            logWarn("copy file failed $source -> $destination", error)
            false
        }

    @JvmStatic
    fun isResourceName(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase(Locale.ROOT) ?: return false
        return normalized == "system" || normalized == "movie" ||
            normalized == "artemisengine.exe" || normalized == "system.ini" ||
            normalized.startsWith("root.pfs") || normalized.endsWith(".pfs") ||
            normalized.endsWith(".xp3") || normalized.endsWith(".arc") ||
            normalized.endsWith(".pak") || normalized.endsWith(".dat.arc")
    }

    @JvmStatic
    fun fallbackStage(packageName: String?): Int {
        val normalized = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "compat.v2" in normalized || "compatible_v2" in normalized || normalized.endsWith(".2") -> 2
            "compat" in normalized -> 1
            else -> 0
        }
    }

    private fun preferredPackage(
        context: Context,
        requestedPackage: String,
        rootPath: String?
    ): String {
        if (rootPath.isNullOrBlank()) return requestedPackage
        val saved =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ENGINE_PREF_PREFIX + Integer.toHexString(rootPath.hashCode()), null)
        return saved?.takeIf(String::isNotBlank) ?: requestedPackage
    }

    private fun chooseActivity(packageName: String?): Class<out android.app.Activity> =
        when (fallbackStage(packageName)) {
            2 -> ArtemisActivityV3::class.java
            1 -> ArtemisActivityV2::class.java
            else -> ArtemisActivityV1::class.java
        }

    private fun isScopedSaveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("artemis_scoped_save_dir", true)

    private fun safeSaveName(rootPath: String?): String {
        if (rootPath.isNullOrBlank()) return "default"
        return try {
            var name = File(rootPath).name
            if (name.isBlank()) name = kotlin.math.abs(rootPath.hashCode()).toString()
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "default" }
        } catch (_: Throwable) {
            "default"
        }
    }

    private fun ensureDirectory(directory: File): Boolean = directory.isDirectory || (!directory.exists() && directory.mkdirs())

    private fun ensureSymlink(
        link: File,
        target: File
    ): Boolean {
        return try {
            if (link.exists()) {
                if (isSymlinkTo(link, target)) return true
                deleteRecursively(link)
            }
            Os.symlink(target.absolutePath, link.absolutePath)
            isSymlinkTo(link, target)
        } catch (error: Throwable) {
            logWarn("symlink failed $link -> $target", error)
            false
        }
    }

    private fun isSymlinkTo(
        link: File,
        target: File
    ): Boolean =
        try {
            Os.readlink(link.absolutePath) == target.absolutePath
        } catch (_: Throwable) {
            false
        }

    private fun isSymlink(file: File): Boolean =
        try {
            Os.readlink(file.absolutePath)
            true
        } catch (_: Throwable) {
            false
        }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) return
        if (file.isDirectory && !isSymlink(file)) file.listFiles()?.forEach(::deleteRecursively)
        if (!file.delete()) logWarn("delete failed ${file.absolutePath}")
    }

    private fun appendThemeColors(
        intent: Intent,
        context: Context
    ) {
        try {
            val primary = KireiEngineTheme.primary(context)
            intent.putExtra("primaryColor", primary)
            intent.putExtra("darkMode", LauncherActivity.isLauncherDarkMode(context))
            intent.putExtra("themeColorPrimary", primary)
            intent.putExtra("themeColorOnPrimary", KireiEngineTheme.onPrimary(context))
            intent.putExtra("themeColorCard", KireiEngineTheme.card(context))
            intent.putExtra("themeColorText", KireiEngineTheme.text(context))
            intent.putExtra("themeColorTextMuted", KireiEngineTheme.textMuted(context))
        } catch (error: Throwable) {
            logWarn("appendThemeColors failed", error)
        }
    }

    private fun engineIntentFlags(): Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(
        message: String,
        error: Throwable? = null
    ) {
        runCatching { if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error) }
    }
}
