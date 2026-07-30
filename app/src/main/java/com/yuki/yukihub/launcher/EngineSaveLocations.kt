package com.yuki.yukihub.launcher

import android.content.Context
import android.util.Log
import com.yuki.yukihub.launcherbridge.LauncherOnsGameSettingsBridge
import com.yuki.yukihub.model.EngineType
import java.io.File
import java.util.LinkedHashMap

/** Aggregates the save directories actually used by the built-in engines. */
internal object EngineSaveLocations {
    private const val TAG = "EmulatorLauncher"
    private const val PREFS_NAME = "yukihub_prefs"

    data class Location(
        @JvmField val directory: File?,
        @JvmField val description: String,
        @JvmField val available: Boolean,
    ) {
        companion object {
            fun unavailable(description: String) = Location(null, description, false)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun resolve(
        context: Context?,
        engine: EngineType?,
        rootUri: String?,
        launchTarget: String?,
        gameId: Long = 0L,
    ): Location {
        if (context == null) return Location.unavailable("应用上下文不可用")
        if (engine == null) return Location.unavailable("游戏引擎信息不可用")
        return try {
            when (engine) {
                EngineType.KIRIKIRI -> {
                    val location = KrkrLauncher.resolveSaveLocation(
                        context,
                        rootUri,
                        launchTarget,
                    )
                    Location(location.directory, location.description, location.available)
                }

                EngineType.ARTEMIS -> {
                    val rootPath = ScriptEngineLaunchers.stripFileScheme(
                        ArtemisLauncher.resolveGamePath(rootUri, launchTarget),
                    )
                    val scoped = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean("artemis_scoped_save_dir", true)
                    val location = ArtemisLauncher.resolveSaveLocation(context, rootPath, scoped)
                    Location(location.directory, location.description, location.available)
                }

                EngineType.ONS -> {
                    val requestedRoot = ScriptEngineLaunchers.stripFileScheme(
                        ScriptEngineLaunchers.uriToFilePath(rootUri),
                    )
                    val rootPath = ScriptEngineLaunchers.resolveOnsGameDirectory(requestedRoot)
                    val scoped = LauncherOnsGameSettingsBridge.load(context, gameId).scopedSaveDir
                    val location = ScriptEngineLaunchers.resolveOnsSaveLocation(context, rootPath, scoped)
                    Location(location.directory, location.description, location.available)
                }

                EngineType.TYRANO -> {
                    val gameDirectory = ScriptEngineLaunchers.stripFileScheme(
                        ScriptEngineLaunchers.resolveTyranoGameDirectory(rootUri, launchTarget),
                    )
                    val scoped = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean("tyrano_scoped_save_dir", true)
                    val location = ScriptEngineLaunchers.resolveTyranoSaveLocation(
                        context,
                        gameDirectory,
                        scoped,
                    )
                    Location(location.directory, location.description, location.available)
                }

                else -> Location.unavailable("该内置引擎未提供可管理的存档目录")
            }
        } catch (error: Throwable) {
            logWarn("resolve actual save location failed engine=$engine root=$rootUri", error)
            Location.unavailable("无法解析实际存档目录")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun resolveDirectories(
        context: Context?,
        engine: EngineType?,
        rootUri: String?,
        launchTarget: String?,
        gameId: Long = 0L,
    ): List<File> {
        return uniqueDirectories(
            listOf(resolve(context, engine, rootUri, launchTarget, gameId).directory),
        )
    }

    @JvmStatic
    fun uniqueDirectories(candidates: Iterable<File?>): List<File> {
        val output = LinkedHashMap<String, File>()
        candidates.forEach { directory ->
            if (directory == null) return@forEach
            val normalized = runCatching { directory.canonicalFile }.getOrElse { directory.absoluteFile }
            output[normalized.path] = normalized
        }
        return output.values.toList()
    }

    private fun logWarn(message: String, error: Throwable) {
        runCatching { Log.w(TAG, message, error) }
    }
}
