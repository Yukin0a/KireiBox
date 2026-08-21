package com.yuki.yukihub.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.apps.LauncherActivity
import com.yuki.yukihub.launcherbridge.LauncherOnsGameSettingsBridge
import com.yuki.yukihub.ons.OnsSettings
import com.yuki.yukihub.tyrano.TyranoActivity
import com.yuri.onscripter.ONScripter
import java.io.File
import java.util.Locale

/** 内置 Tyrano 与 ONS 引擎的路径解析、存档定位及 Intent 契约。 */
internal object ScriptEngineLaunchers {
    private const val TAG = "EmulatorLauncher"
    private const val PREFS_NAME = "yukihub_prefs"
    private val onsBootNames = setOf(
        "0.txt", "00.txt", "nscr_sec.dat", "nscript.dat", "onscript.nt2", "onscript.nt3",
        "arc.nsa", "arc.sar",
    )

    @JvmStatic
    fun buildTyranoIntent(context: Context, gamePath: String?, launchTarget: String?): Intent {
        val resolvedPath = resolveTyranoGameDirectory(gamePath, launchTarget)
        val path = stripFileScheme(resolvedPath)
        val scoped = isTyranoScopedSaveEnabled(context)
        val save = resolveTyranoSaveLocation(context, path, scoped)
        check(save.available && save.directory != null) { save.description }
        ensureWritable(save.directory, "Tyrano")
        logInfo(
            "internal Tyrano root=$gamePath target=$launchTarget resolved=$resolvedPath " +
                "scopedSave=$scoped save=${save.directory.absolutePath}",
        )
        return Intent(context, TyranoActivity::class.java).apply {
            if (!resolvedPath.isNullOrEmpty()) {
                putExtra("path", path)
                putExtra("gamePath", path)
                putExtra("projectRoot", path)
                putExtra("gamedir", path)
            }
            putExtra("rootUri", gamePath)
            putExtra("launchTarget", launchTarget)
            putExtra("type", "Tyrano")
            putExtra("launchMode", "internal.tyrano")
            putExtra("orientation", 6)
            putExtra("scopedSaveDir", scoped)
            putExtra("scopedSaveRoot", save.directory.absolutePath)
            addFlags(engineIntentFlags())
            appendThemeColors(this, context)
        }
    }

    @JvmStatic
    fun resolveTyranoGameDirectory(rootUri: String?, launchTarget: String?): String? {
        val rootPath = uriToFilePath(rootUri)
        if (rootPath.isNullOrEmpty()) return rootUri
        val target = launchTarget?.trim().orEmpty()
        if (target.isEmpty() || target == "[游戏目录]" || target.equals("DIR", ignoreCase = true)) {
            return rootPath
        }
        val targetFile = if (target.startsWith('/') || target.startsWith("file://")) {
            File(requireNotNull(stripFileScheme(target)))
        } else {
            File(rootPath, target)
        }
        return if (targetFile.isFile) targetFile.parent else targetFile.absolutePath
    }

    @JvmStatic
    @JvmOverloads
    fun buildOnsIntent(
        context: Context,
        gamePath: String?,
        launchTarget: String?,
        gameId: Long = 0L,
    ): Intent {
        val requestedRoot = stripFileScheme(uriToFilePath(gamePath))
        val root = resolveOnsGameDirectory(requestedRoot)
        if (root.isNullOrEmpty()) {
            val message = "未在所选目录、上一级或直属子目录中找到 ONS 启动文件"
            logError("internal ONS rejected: $message root=$requestedRoot")
            throw IllegalStateException(message)
        }
        validateOnsArchiveLayout(root)?.let { error ->
            logError("internal ONS rejected: $error root=$root")
            throw IllegalStateException(error)
        }
        val settings = LauncherOnsGameSettingsBridge.load(context, gameId)
        val save = resolveOnsSaveLocation(context, root, settings.scopedSaveDir)
        check(save.available && save.directory != null) { save.description }
        ensureWritable(save.directory, "ONS")
        logInfo(
            "internal ONS root=$gamePath target=$launchTarget requested=$requestedRoot resolved=$root " +
                "scopedSave=${settings.scopedSaveDir} save=${save.directory.absolutePath}",
        )
        return Intent(context, ONScripter::class.java).apply {
            putStringArrayListExtra(OnsSettings.EXTRA_GAME_ARGS, settings.buildArgs(context, root, save.directory))
            putExtra(OnsSettings.EXTRA_IGNORE_CUTOUT, settings.ignoreCutout)
            putExtra("path", root)
            putExtra("gamePath", root)
            putExtra("rootUri", gamePath)
            putExtra("launchTarget", launchTarget)
            putExtra("launchMode", "internal.ons")
            addFlags(engineIntentFlags())
        }
    }

    @JvmStatic
    fun resolveOnsGameDirectory(requestedRootPath: String?): String? {
        if (requestedRootPath.isNullOrBlank()) return null
        return try {
            val root = File(requestedRootPath)
            if (!root.isDirectory) return null
            findOnsBootEntry(root)?.let { entry ->
                logInfo("internal ONS accepted root=${root.absolutePath} entry=$entry")
                return root.absolutePath
            }
            root.parentFile?.let { parent ->
                findOnsBootEntry(parent)?.let { entry ->
                    logInfo("internal ONS auto-resolved parent ${root.absolutePath} -> ${parent.absolutePath} entry=$entry")
                    return parent.absolutePath
                }
            }
            root.listFiles()?.forEach { child ->
                findOnsBootEntry(child)?.let { entry ->
                    logInfo("internal ONS auto-resolved child ${root.absolutePath} -> ${child.absolutePath} entry=$entry")
                    return child.absolutePath
                }
            }
            logWarn("internal ONS has no boot entry root=${root.absolutePath} files=${describeDirectory(root, 40)}")
            null
        } catch (error: Throwable) {
            logWarn("internal ONS root resolve failed: $requestedRootPath", error)
            null
        }
    }

    @JvmStatic
    fun resolveOnsSaveDirectory(context: Context, rootPath: String?, scoped: Boolean): File? =
        resolveOnsSaveLocation(context, rootPath, scoped).directory

    @JvmStatic
    fun resolveTyranoSaveDirectory(context: Context?, gameDirectory: String?, scoped: Boolean): File? =
        resolveTyranoSaveLocation(context, gameDirectory, scoped).directory

    private fun validateOnsArchiveLayout(rootPath: String): String? {
        val root = File(rootPath)
        val base = File(root, "arc.nsa")
        val extra = File(root, "arc1.nsa")
        return if (base.isFile && base.length() > 0 && extra.isFile && extra.length() == 0L) {
            "游戏资源不完整：arc1.nsa 为 0 字节。请重新解压或重新导入完整游戏目录。"
        } else null
    }

    private fun findOnsBootEntry(directory: File?): String? = directory
        ?.takeIf(File::isDirectory)
        ?.listFiles()
        ?.firstOrNull { it?.isFile == true && it.name?.lowercase(Locale.ROOT) in onsBootNames }
        ?.name

    private fun describeDirectory(directory: File?, maxEntries: Int): String {
        directory ?: return "<null>"
        val files = directory.listFiles() ?: return "<unavailable>"
        val limit = maxEntries.coerceAtLeast(1)
        val entries = files.take(limit).joinToString(", ") { file ->
            if (file == null) "<null>" else file.name + if (file.isDirectory) "/" else ""
        }
        return "[$entries${if (files.size > limit) ", … total=${files.size}" else ""}]"
    }

    @JvmStatic
    fun resolveOnsSaveLocation(context: Context, rootPath: String?, scoped: Boolean): SaveLocation {
        val directory = if (scoped) {
            OnsSettings.resolveScopedSaveDirectory(context, rootPath)
        } else {
            OnsSettings.resolveGameSaveDirectory(rootPath)
        }
        return if (directory == null) {
            SaveLocation.unavailable(
                if (scoped) "ONS 应用独立存储目录不可用" else "ONS 游戏目录不可用或不是可写的本地目录",
            )
        } else {
            SaveLocation.available(directory, if (scoped) "ONS 应用独立存档目录" else "ONS 游戏内存档目录")
        }
    }

    @JvmStatic
    fun resolveTyranoSaveLocation(
        context: Context?,
        gameDirectory: String?,
        scoped: Boolean,
    ): SaveLocation {
        if (gameDirectory.isNullOrBlank() || gameDirectory.startsWith("content://")) {
            return SaveLocation.unavailable("无法解析 Tyrano 实际游戏目录")
        }
        return try {
            val root = File(stripFileScheme(gameDirectory).orEmpty()).canonicalFile
            if (!root.isDirectory) return SaveLocation.unavailable("Tyrano 游戏目录不可用")
            if (scoped) {
                val external = context?.getExternalFilesDir(null)
                    ?: return SaveLocation.unavailable("Tyrano 应用独立存储目录不可用")
                SaveLocation.available(
                    File(File(File(external, "save"), "tyrano"), safeSaveName(root.absolutePath)),
                    "Tyrano 应用独立存档目录",
                )
            } else {
                val directory = File(root, "savedata").canonicalFile
                if (!directory.path.startsWith(root.path + File.separator)) {
                    SaveLocation.unavailable("Tyrano 游戏内存档目录无效")
                } else {
                    SaveLocation.available(directory, "Tyrano 游戏内存档目录")
                }
            }
        } catch (_: Throwable) {
            SaveLocation.unavailable("无法解析 Tyrano 实际游戏目录")
        }
    }

    private fun isTyranoScopedSaveEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("tyrano_scoped_save_dir", true)

    private fun ensureWritable(directory: File, engine: String) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建 $engine 存档目录：${directory.absolutePath}")
        }
        if (!directory.isDirectory || !directory.canWrite()) {
            throw IllegalStateException("$engine 存档目录不可写：${directory.absolutePath}")
        }
    }

    private fun appendThemeColors(intent: Intent, context: Context) {
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
            Log.w(TAG, "appendThemeColors failed", error)
        }
    }

    private fun engineIntentFlags(): Int = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(message: String, error: Throwable? = null) {
        runCatching { if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error) }
    }

    private fun logError(message: String) {
        runCatching { Log.e(TAG, message) }
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

    data class SaveLocation(
        @JvmField val directory: File?,
        @JvmField val description: String,
        @JvmField val available: Boolean,
    ) {
        companion object {
            fun available(directory: File, description: String) = SaveLocation(directory, description, true)
            fun unavailable(description: String) = SaveLocation(null, description, false)
        }
    }

    @JvmStatic
    fun stripFileScheme(path: String?): String? = path?.removePrefix("file://")

    @JvmStatic
    fun uriToFilePath(uriText: String?): String? {
        if (uriText.isNullOrBlank() || uriText.startsWith('/')) return uriText
        return try {
            val uri = Uri.parse(uriText)
            if (uri.scheme.equals("file", ignoreCase = true)) return uri.path
            if (uri.scheme.equals("content", ignoreCase = true)) {
                var documentId: String? = null
                if (uri.path?.contains("/document/") == true) {
                    documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                }
                if (documentId.isNullOrEmpty()) {
                    documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                }
                if (documentId.isNullOrEmpty()) {
                    documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                }
                if (documentId != null) {
                    val colon = documentId.indexOf(':')
                    val volume = if (colon >= 0) documentId.substring(0, colon) else documentId
                    val relative = if (colon >= 0) documentId.substring(colon + 1) else ""
                    val candidate = if (isInternalVolume(volume)) {
                        val base = internalStorageBase()
                        if (relative.isEmpty()) base else "$base/$relative"
                    } else if (volume.isNotEmpty()) {
                        if (relative.isEmpty()) "/storage/$volume" else "/storage/$volume/$relative"
                    } else {
                        null
                    }
                    if (candidate != null) {
                        if (File(candidate).exists()) return candidate
                        val base = internalStorageBase()
                        val fallback = if (relative.isEmpty()) base else "$base/$relative"
                        if (fallback != candidate && File(fallback).exists()) return fallback
                        return candidate
                    }
                }
            }
            uriText
        } catch (_: Throwable) {
            uriText
        }
    }

    private fun internalStorageBase(): String =
        runCatching { Environment.getExternalStorageDirectory().absolutePath }
            .getOrDefault("/storage/emulated/0")

    /**
     * Internal-storage volume aliases found in SAF document IDs. Besides the
     * standard "primary", some OEM/HarmonyOS providers report "internal",
     * "emulated" or the user id "0" for the same internal storage.
     */
    private fun isInternalVolume(volume: String?): Boolean {
        if (volume.isNullOrEmpty()) return false
        return when (volume.lowercase(Locale.ROOT)) {
            "primary", "internal", "emulated", "0" -> true
            else -> false
        }
    }
}
