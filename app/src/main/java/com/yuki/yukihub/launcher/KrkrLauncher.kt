package com.yuki.yukihub.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.system.Os
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.akira.tyranoemu.remote.Kirikiroid126
import com.akira.tyranoemu.remote.Kirikiroid134
import com.akira.tyranoemu.remote.Kirikiroid139
import com.apps.LauncherActivity
import com.yuki.yukihub.launch.Xp3EntrySelector
import com.yuki.yukihub.launcherbridge.LauncherKrkrBridge
import org.tvp.kirikiri2.KR2Activity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

/** KRKR entry resolution, engine routing and game-local savedata preparation. */
internal object KrkrLauncher {
    private const val TAG = "EmulatorLauncher"
    private const val PREFS_NAME = "yukihub_prefs"
    private const val SAVE_MIGRATION_PREFIX = "kr_game_save_migrated_v1."

    data class SaveLocation(
        @JvmField val directory: File?,
        @JvmField val description: String,
        @JvmField val available: Boolean,
    )

    data class SavePreparation(
        @JvmField val available: Boolean,
        @JvmField val description: String,
        @JvmField val migratedFiles: Int = 0,
        @JvmField val conflicts: Int = 0,
    )

    @JvmStatic
    @JvmOverloads
    fun buildIntent(
        context: Context,
        gamePath: String?,
        launchTarget: String?,
        originMode: Boolean = false,
        engineVersion: String? = "auto",
        safFileFallback: Boolean = false,
    ): Intent {
        val resolvedPath = if (originMode) null else resolvePath(context, gamePath, launchTarget)
        val rawRootPath = ScriptEngineLaunchers.stripFileScheme(
            ScriptEngineLaunchers.uriToFilePath(gamePath),
        )
        val path = ScriptEngineLaunchers.stripFileScheme(resolvedPath)
        val rootPath = rootForPath(rawRootPath, path)
        val compatMode = LauncherKrkrBridge.isCompatMode(context)
        val savePreparation = if (originMode) null else {
            prepareGameSaveDirectory(context, gamePath, launchTarget)
        }
        if (!originMode && savePreparation?.available != true) {
            throw IllegalStateException(savePreparation?.description ?: "无法准备游戏目录 savedata")
        }
        if (savePreparation != null) {
            logInfo(
                "KRKR game-local save ready root=$rootPath path=$path " +
                    "migrated=${savePreparation.migratedFiles} conflicts=${savePreparation.conflicts}",
            )
        }
        if (safFileFallback) {
            logWarn("KRKR SAF write fallback is retired; game-local savedata remains authoritative")
        }

        val resolvedVersion = if (originMode) "auto" else normalizeEngineVersion(engineVersion)
        val use126 = !originMode && resolvedVersion == "1.2.6"
        val use134 = !originMode && resolvedVersion == "1.3.4"
        val activityClass = when {
            originMode -> KR2Activity::class.java
            use126 -> Kirikiroid126::class.java
            use134 -> Kirikiroid134::class.java
            else -> Kirikiroid139::class.java
        }
        logInfo(
            "internal KRKR originMode=$originMode engineVersion=$resolvedVersion use126=$use126 " +
                "use134=$use134 root=$gamePath target=$launchTarget resolved=$resolvedPath " +
                "rootPath=$rootPath gameLocalSave=${!originMode}",
        )
        return Intent(context, activityClass).apply {
            if (!path.isNullOrEmpty()) {
                putExtra("path", path)
                putExtra("gamePath", path)
            }
            if (!rootPath.isNullOrEmpty()) {
                putExtra("projectRoot", rootPath)
                putExtra("gamedir", rootPath)
            }
            putExtra("rootUri", gamePath)
            putExtra("launchTarget", launchTarget)
            putExtra("launchId", "${System.currentTimeMillis()}-${kotlin.math.abs((gamePath ?: path ?: "").hashCode())}")
            putExtra("launchStartedElapsed", SystemClock.elapsedRealtime())
            putExtra("krStartupDialogPolicy", LauncherKrkrBridge.getStartupDialogPolicy(context))
            putExtra("originMode", originMode)
            putExtra("krEngineVersion", when { use126 -> "1.2.6"; use134 -> "1.3.4"; else -> "1.3.9" })
            putExtra("orientation", 6)
            putExtra("launchMode", if (originMode) "internal.krkr.origin" else "internal.krkr")
            putExtra("terminateKrProcessOnDestroy", false)
            putExtra("safFileFallback", false)
            putExtra("compatMode", compatMode)
            if (compatMode) putExtra("maps", true)
            addFlags(engineIntentFlags())
            appendThemeColors(this, context)
        }
    }

    @JvmStatic
    fun resolveSaveLocation(
        context: Context?,
        rootUri: String?,
        launchTarget: String?,
    ): SaveLocation {
        if (context == null) return SaveLocation(null, "应用上下文不可用", false)
        return try {
            val resolved = resolvePath(context, rootUri, launchTarget)
            val rawRoot = ScriptEngineLaunchers.stripFileScheme(
                ScriptEngineLaunchers.uriToFilePath(rootUri),
            )
            val root = rootForPath(rawRoot, ScriptEngineLaunchers.stripFileScheme(resolved))
            if (root.isNullOrBlank() || root.startsWith("content://")) {
                return SaveLocation(null, "无法解析 KRKR 实际游戏目录", false)
            }
            SaveLocation(File(root, "savedata"), "KRKR 游戏目录存档", true)
        } catch (error: Throwable) {
            logWarn("resolve KRKR save location failed root=$rootUri", error)
            SaveLocation(null, "无法解析实际存档目录", false)
        }
    }

    @JvmStatic
    fun resolvePath(context: Context?, rootUri: String?, launchTarget: String?): String? {
        val rootPath = ScriptEngineLaunchers.stripFileScheme(
            ScriptEngineLaunchers.uriToFilePath(rootUri),
        )
        if (rootPath.isNullOrEmpty()) return rootUri
        val target = launchTarget?.trim().orEmpty()
        if (target.isEmpty() || target == "[游戏目录]" || target.equals("DIR", ignoreCase = true)) {
            return rootPath
        }
        if (target.equals("XP3_FIRST", ignoreCase = true)) {
            val selected = selectXp3Entry(rootPath)
            if (selected != null) return selected
            // Directory accessible but no reliable entry (e.g. only asset packs):
            // launch in directory mode so the engine resolves startup.tjs itself.
            if (File(rootPath).isDirectory) return rootPath
            return findPreferredEntryFromTree(context, rootUri, rootPath) ?: rootPath
        }
        if (target.startsWith('/')) {
            val file = File(target)
            return if (file.isFile) file.absolutePath else target
        }
        val targetFile = File(rootPath, target)
        if (targetFile.isFile || targetFile.isDirectory) return targetFile.absolutePath
        findTargetFromTree(context, rootUri, rootPath, target)?.let { return it }
        return if (
            target.endsWith(".xp3") || target.endsWith(".tjs") ||
            target.endsWith(".exe") || target.endsWith(".dll")
        ) targetFile.absolutePath else rootPath
    }

    @JvmStatic
    fun rootForPath(rawRootPath: String?, launchPath: String?): String? = try {
        rawRootPath?.takeIf(String::isNotBlank)?.let { rawPath ->
            val raw = File(rawPath)
            if (raw.isDirectory) return raw.absolutePath
            raw.parentFile?.let { return it.absolutePath }
        }
        launchPath?.takeIf(String::isNotBlank)?.let { path ->
            val launch = File(path)
            if (launch.isDirectory) return launch.absolutePath
            launch.parentFile?.let { return it.absolutePath }
        }
        rawRootPath
    } catch (_: Throwable) {
        rawRootPath
    }

    @JvmStatic
    fun normalizeEngineVersion(engineVersion: String?): String {
        return when (engineVersion?.trim()?.lowercase(Locale.ROOT) ?: "auto") {
            "134", "1.3.4", "kr134", "kirikiroid134" -> "1.3.4"
            "126", "1.2.6", "kr126", "kirikiroid126" -> "1.2.6"
            "139", "1.3.9", "kr139", "kirikiroid139" -> "1.3.9"
            else -> "auto"
        }
    }

    private fun selectXp3Entry(rootPath: String): String? {
        val root = File(rootPath)
        if (!root.isDirectory) return null
        val files = root.listFiles() ?: return null
        val entries = files.asSequence()
            .filter { it.isFile && it.name.endsWith(".xp3", ignoreCase = true) }
            .map { Xp3EntrySelector.Entry(it.name, it.length()) }
            .toList()
        if (entries.isEmpty()) return null
        val picked = Xp3EntrySelector.select(entries, root.name, null) ?: return null
        return File(root, picked).absolutePath
    }

    private fun findPreferredEntryFromTree(context: Context?, rootUri: String?, rootPath: String): String? {
        arrayOf("data.xp3", "startup.tjs", "patch.xp3").forEach { name ->
            findTargetFromTree(context, rootUri, rootPath, name)?.let { return it }
        }
        return try {
            val directory = if (context == null || rootUri == null) null else {
                DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            }
            directory?.takeIf(DocumentFile::isDirectory)?.listFiles()?.firstOrNull { file ->
                file?.isFile == true && file.name?.lowercase(Locale.ROOT)?.endsWith(".xp3") == true
            }?.name?.let { name -> if (rootPath.endsWith('/')) rootPath + name else "$rootPath/$name" }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findTargetFromTree(
        context: Context?,
        rootUri: String?,
        rootPath: String,
        target: String?,
    ): String? {
        if (context == null || rootUri == null || target.isNullOrBlank()) return null
        return try {
            var current = DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
            if (current?.isDirectory != true) return null
            target.split('/').forEach { part ->
                if (part.isEmpty() || part == ".") return@forEach
                current = current?.findFile(part) ?: return null
            }
            if (current?.exists() == true && current?.isFile == true) {
                val cleanTarget = target.trimStart('/')
                if (rootPath.endsWith('/')) rootPath + cleanTarget else "$rootPath/$cleanTarget"
            } else null
        } catch (_: Throwable) {
            null
        }
    }


    @JvmStatic
    fun prepareGameSaveDirectory(
        context: Context?,
        rootUri: String?,
        launchTarget: String?,
    ): SavePreparation {
        if (context == null) return SavePreparation(false, "应用上下文不可用")
        val location = resolveSaveLocation(context, rootUri, launchTarget)
        val saveDirectory = location.directory
            ?: return SavePreparation(false, location.description)
        val gameRoot = saveDirectory.parentFile
            ?: return SavePreparation(false, "无法解析 KRKR 游戏根目录")
        return try {
            if (!gameRoot.isDirectory) {
                return SavePreparation(
                    false,
                    "KRKR 游戏目录不存在或无法直接读取。SAF 可用于扫描，但原生内核需要可访问的物理目录。",
                )
            }
            val canonicalRoot = gameRoot.canonicalFile
            val canonicalSave = saveDirectory.canonicalFile
            if (canonicalSave.parentFile != canonicalRoot) {
                return SavePreparation(false, "savedata 路径不在游戏目录内，已停止启动")
            }
            if (isSymlink(saveDirectory)) {
                return SavePreparation(false, "savedata 不能是符号链接，请改为游戏目录内的普通文件夹")
            }
            if (saveDirectory.exists() && !saveDirectory.isDirectory) {
                return SavePreparation(false, "游戏目录中的 savedata 是文件而不是文件夹")
            }
            if (!saveDirectory.exists() && !saveDirectory.mkdirs()) {
                return SavePreparation(false, gameLocalWriteHelp("无法创建 savedata"))
            }
            val probeError = probeWritable(saveDirectory)
            if (probeError != null) {
                return SavePreparation(false, gameLocalWriteHelp(probeError))
            }
            val migration = migrateLegacySaves(context, gameRoot, saveDirectory)
            SavePreparation(
                available = true,
                description = if (migration.conflicts > 0) {
                    "游戏目录 savedata 可写；旧存档有 ${migration.conflicts} 个同名文件未覆盖"
                } else {
                    "游戏目录 savedata 可写"
                },
                migratedFiles = migration.copied,
                conflicts = migration.conflicts,
            )
        } catch (error: Throwable) {
            logWarn("prepare KRKR game-local savedata failed save=$saveDirectory", error)
            SavePreparation(false, gameLocalWriteHelp(error.message ?: "写入检查失败"))
        }
    }

    internal data class MigrationResult(val copied: Int = 0, val conflicts: Int = 0) {
        operator fun plus(other: MigrationResult) =
            MigrationResult(copied + other.copied, conflicts + other.conflicts)
    }

    private fun migrateLegacySaves(
        context: Context,
        gameRoot: File,
        saveDirectory: File,
    ): MigrationResult {
        val saveName = safeSaveName(gameRoot.absolutePath)
        val marker = SAVE_MIGRATION_PREFIX + Integer.toHexString(gameRoot.canonicalPath.hashCode())
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(marker, false)) return MigrationResult()
        val sources = buildList {
            context.filesDir?.let { internal ->
                add(File(File(File(internal, "krkr_mirror"), saveName), "savedata"))
                add(File(File(internal, "save"), saveName))
            }
            context.getExternalFilesDir(null)?.let { external ->
                add(File(File(external, "save"), saveName))
            }
        }
        var result = MigrationResult()
        for (source in sources) {
            if (sameCanonicalDirectory(source, saveDirectory)) continue
            val migrated = copyMissingFilesRecursively(source, saveDirectory)
            result += migrated
            if (migrated.copied > 0 || migrated.conflicts > 0) {
                logInfo(
                    "migrated KRKR legacy saves copied=${migrated.copied} " +
                        "conflicts=${migrated.conflicts} from=$source to=$saveDirectory",
                )
            }
        }
        prefs.edit().putBoolean(marker, true).apply()
        return result
    }

    internal fun copyMissingFilesRecursively(
        fromDirectory: File?,
        toDirectory: File?,
    ): MigrationResult {
        if (fromDirectory?.isDirectory != true || toDirectory == null) return MigrationResult()
        if (!toDirectory.isDirectory && !toDirectory.mkdirs()) return MigrationResult()
        var result = MigrationResult()
        for (child in fromDirectory.listFiles().orEmpty()) {
            if (isSymlink(child)) continue
            val target = File(toDirectory, child.name)
            result += when {
                child.isDirectory && target.exists() && !target.isDirectory ->
                    MigrationResult(conflicts = 1)
                child.isDirectory ->
                    copyMissingFilesRecursively(child, target)
                child.isFile && target.exists() ->
                    MigrationResult(conflicts = 1)
                child.isFile && copyFile(child, target) ->
                    MigrationResult(copied = 1)
                else -> MigrationResult()
            }
        }
        return result
    }

    private fun copyFile(source: File, destination: File): Boolean = try {
        destination.parentFile?.let { parent ->
            if (!parent.isDirectory && !parent.mkdirs()) return false
        }
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output -> input.copyTo(output, 64 * 1024) }
        }
        destination.setLastModified(source.lastModified())
        true
    } catch (error: Throwable) {
        logWarn("copy file failed $source -> $destination", error)
        false
    }

    private fun probeWritable(directory: File): String? {
        val suffix = "${android.os.Process.myPid()}-${System.nanoTime()}"
        val pending = File(directory, ".kireibox-write-test-$suffix.tmp")
        val committed = File(directory, ".kireibox-write-test-$suffix.ok")
        return try {
            FileOutputStream(pending, false).use { output ->
                output.write(byteArrayOf(0x4b, 0x42))
                output.flush()
                output.fd.sync()
            }
            if (!pending.renameTo(committed)) return "savedata 无法完成原子重命名"
            if (!committed.delete()) return "savedata 写入测试文件无法删除"
            null
        } catch (error: Throwable) {
            error.message ?: "savedata 无法写入"
        } finally {
            runCatching { pending.delete() }
            runCatching { committed.delete() }
        }
    }

    private fun sameCanonicalDirectory(first: File, second: File): Boolean =
        runCatching { first.canonicalFile == second.canonicalFile }.getOrDefault(false)

    private fun gameLocalWriteHelp(reason: String): String =
        "$reason。请授予“所有文件访问权限”；若游戏位于 SD 卡、Android/data 或只读目录，请移动到内部存储后重试。"

    private fun isSymlink(file: File): Boolean = try {
        Os.readlink(file.absolutePath)
        true
    } catch (_: Throwable) {
        false
    }

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

    private fun appendThemeColors(intent: Intent, context: Context) {
        try {
            val primary = KireiEngineTheme.primary(context)
            intent.putExtra("primaryColor", primary)
            intent.putExtra("darkMode", LauncherActivity.isLauncherDarkMode(context))
            intent.putExtra("themeColorPrimary", primary)
            intent.putExtra("themeColorBackground", KireiEngineTheme.background(context))
            intent.putExtra("themeColorOnPrimary", KireiEngineTheme.onPrimary(context))
            intent.putExtra("themeColorCard", KireiEngineTheme.card(context))
            intent.putExtra("themeColorText", KireiEngineTheme.text(context))
            intent.putExtra("themeColorTextMuted", KireiEngineTheme.textMuted(context))
        } catch (error: Throwable) {
            logWarn("appendThemeColors failed", error)
        }
    }

    private fun engineIntentFlags(): Int = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(message: String, error: Throwable? = null) {
        runCatching { if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error) }
    }
}
