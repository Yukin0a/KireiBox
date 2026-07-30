package com.yuki.yukihub.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.yuki.yukihub.model.EngineType
import java.io.File

/** Thin launch facade for KireiBox's four embedded engines and Android package cards. */
class EmulatorLauncher {
    class ActualSaveLocation internal constructor(
        @JvmField val directory: File?,
        @JvmField val description: String,
        @JvmField val available: Boolean,
    )

    companion object {
        @JvmStatic
        fun launchAndroidApp(context: Context?, packageName: String?, activityName: String?): Boolean {
            if (context == null || packageName.isNullOrBlank()) return false
            return runCatching {
                val explicit = activityName?.trim()?.takeIf(String::isNotEmpty)?.let { activity ->
                    val resolved = if (activity.startsWith('.')) packageName + activity else activity
                    Intent(Intent.ACTION_MAIN).apply {
                        component = ComponentName(packageName, resolved)
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                }
                val intent = explicit ?: context.packageManager.getLaunchIntentForPackage(packageName)
                    ?: return false
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        @JvmStatic
        fun buildInternalTyranoIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
        ): Intent = ScriptEngineLaunchers.buildTyranoIntent(requireNotNull(context), gamePath, launchTarget)

        @JvmStatic
        fun resolveInternalTyranoGameDirectory(rootUri: String?, launchTarget: String?): String? =
            ScriptEngineLaunchers.resolveTyranoGameDirectory(rootUri, launchTarget)

        @JvmStatic
        fun buildInternalOnsIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
        ): Intent = ScriptEngineLaunchers.buildOnsIntent(requireNotNull(context), gamePath, launchTarget)

        @JvmStatic
        fun buildInternalOnsIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
            gameId: Long,
        ): Intent = ScriptEngineLaunchers.buildOnsIntent(requireNotNull(context), gamePath, launchTarget, gameId)

        @JvmStatic
        fun buildInternalArtemisIntent(
            context: Context?,
            packageName: String?,
            gamePath: String?,
            launchTarget: String?,
        ): Intent = ArtemisLauncher.buildIntent(requireNotNull(context), packageName, gamePath, launchTarget)

        @JvmStatic
        fun buildInternalArtemisIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
        ): Intent = ArtemisLauncher.buildIntent(requireNotNull(context), "internal.artemis", gamePath, launchTarget)

        @JvmStatic
        fun buildInternalKrkrIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
        ): Intent = KrkrLauncher.buildIntent(requireNotNull(context), gamePath, launchTarget, false, "auto", false)

        @JvmStatic
        fun buildInternalKrkrIntent(
            context: Context?,
            gamePath: String?,
            launchTarget: String?,
            originMode: Boolean,
            engineVersion: String?,
            safFileFallback: Boolean,
        ): Intent = KrkrLauncher.buildIntent(
            requireNotNull(context), gamePath, launchTarget, originMode, engineVersion, safFileFallback,
        )

        @JvmStatic
        fun resolveActualSaveLocation(
            context: Context?,
            engine: EngineType?,
            rootUri: String?,
            launchTarget: String?,
        ): ActualSaveLocation = EngineSaveLocations.resolve(context, engine, rootUri, launchTarget).toPublicLocation()

        @JvmStatic
        fun resolveActualSaveLocation(
            context: Context?,
            engine: EngineType?,
            rootUri: String?,
            launchTarget: String?,
            gameId: Long,
        ): ActualSaveLocation = EngineSaveLocations.resolve(context, engine, rootUri, launchTarget, gameId).toPublicLocation()

        @JvmStatic
        fun resolveActualSaveDirectories(
            context: Context?,
            engine: EngineType?,
            rootUri: String?,
            launchTarget: String?,
        ): List<File> = EngineSaveLocations.resolveDirectories(context, engine, rootUri, launchTarget)

        @JvmStatic
        fun resolveActualSaveDirectories(
            context: Context?,
            engine: EngineType?,
            rootUri: String?,
            launchTarget: String?,
            gameId: Long,
        ): List<File> = EngineSaveLocations.resolveDirectories(context, engine, rootUri, launchTarget, gameId)

        private fun EngineSaveLocations.Location.toPublicLocation() =
            ActualSaveLocation(directory, description, available)
    }
}
