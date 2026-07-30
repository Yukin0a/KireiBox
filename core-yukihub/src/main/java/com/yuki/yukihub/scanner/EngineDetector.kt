package com.yuki.yukihub.scanner

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import java.util.Locale

/** Feature detector for KireiBox's four supported visual-novel engines. */
object EngineDetector {
    private const val TAG = "EngineDetector"

    class Result {
        @JvmField var engine: EngineType = EngineType.UNKNOWN
        @JvmField var confidence: Int = 0
        @JvmField var launchTarget: String = ""
        @JvmField var xp3Candidates: MutableList<String> = ArrayList()
    }

    @JvmStatic
    fun detect(dir: DocumentFile?): Result = detect(dir, 2)

    @JvmStatic
    fun detect(dir: DocumentFile?, featureDepth: Int): Result = detect(dir, featureDepth, null)

    @JvmStatic
    fun detect(dir: DocumentFile?, featureDepth: Int, rootFiles: Array<DocumentFile>?): Result {
        if (dir == null) return Result()
        return detect(
            StorageNodes.fromDocument(dir),
            featureDepth,
            rootFiles?.map { StorageNodes.fromDocument(it) }
        )
    }

    @JvmStatic
    fun detect(dir: StorageNode?, featureDepth: Int = 2, rootFiles: List<StorageNode>? = null): Result {
        val result = Result()
        if (dir == null) return result
        val features = FeatureState()
        collectFeatures(dir, "", "", 1, featureDepth.coerceIn(1, 4), features, rootFiles)
        if (features.empty) return result

        features.xp3Files.sortWith(String.CASE_INSENSITIVE_ORDER)
        features.gameNamedXp3Files.sortWith(String.CASE_INSENSITIVE_ORDER)
        val firstXp3 = when {
            features.gameNamedXp3Files.size == 1 -> features.gameNamedXp3Files.single()
            features.dataXp3 != null -> features.dataXp3
            features.xp3Files.isNotEmpty() -> features.xp3Files.first()
            else -> null
        }

        val tyranoRuntime = features.hasTyranoDir || features.hasDataDir ||
            "tyrano.css" in features.names || "tyrano.base.js" in features.names ||
            "tyrano/tyrano.css" in features.relativeNames ||
            "tyrano/tyrano.base.js" in features.relativeNames
        val electronWrapper = features.hasResourcesDir &&
            (features.hasAppAsar || features.hasPackageJson || features.hasElectronPak)
        val artemisRuntime = (features.hasSystemIni && features.hasFirstIet) ||
            features.hasRootPfs || features.hasAnyPfsFile

        when {
            features.hasIndex && tyranoRuntime -> score(result, EngineType.TYRANO, 96, "[游戏目录]")
            features.hasAppAsar && electronWrapper -> score(result, EngineType.TYRANO, 82, "[游戏目录]")
            features.hasIndex && !electronWrapper -> score(result, EngineType.TYRANO, 70, "[游戏目录]")
            artemisRuntime -> score(
                result,
                EngineType.ARTEMIS,
                if ((features.hasSystemIni && features.hasFirstIet) || features.hasRootPfs) 95 else 90,
                "[游戏目录]"
            )
            firstXp3 != null || features.hasStartupTjs || features.hasConfigTjs -> {
                score(result, EngineType.KIRIKIRI, if (firstXp3 != null) 95 else 80, firstXp3 ?: "[游戏目录]")
                if (features.gameNamedXp3Files.size > 1 ||
                    (features.gameNamedXp3Files.isEmpty() && features.dataXp3 == null && features.xp3Files.size > 1)
                ) {
                    result.xp3Candidates.addAll(features.xp3Files)
                }
            }
            features.hasOnsScript || features.hasOnsArchive -> score(
                result,
                EngineType.ONS,
                if (features.hasOnsScript) 90 else 70,
                "[游戏目录]"
            )
        }
        return result
    }

    private class FeatureState {
        var empty = true
        val names = HashSet<String>()
        val relativeNames = HashSet<String>()
        val xp3Files = ArrayList<String>()
        val gameNamedXp3Files = ArrayList<String>()
        var dataXp3: String? = null
        var hasIndex = false
        var hasTyranoDir = false
        var hasDataDir = false
        var hasResourcesDir = false
        var hasStartupTjs = false
        var hasConfigTjs = false
        var hasSystemIni = false
        var hasFirstIet = false
        var hasRootPfs = false
        var hasAnyPfsFile = false
        var hasOnsScript = false
        var hasOnsArchive = false
        var hasAppAsar = false
        var hasPackageJson = false
        var hasElectronPak = false
    }

    private fun collectFeatures(
        dir: StorageNode?,
        lowerPrefix: String,
        originalPrefix: String,
        level: Int,
        maxLevel: Int,
        state: FeatureState,
        knownFiles: List<StorageNode>?
    ) {
        val files = try {
            if (dir?.isDirectory != true) return
            knownFiles ?: dir.children()
        } catch (error: Throwable) {
            Log.w(TAG, "feature scan failed uri=${safeUri(dir)}", error)
            return
        }
        for (file in files) {
            val original = safeName(file)
            val lower = original.lowercase(Locale.ROOT)
            if (lower.isEmpty()) continue
            val relative = if (lowerPrefix.isEmpty()) lower else "$lowerPrefix/$lower"
            val originalRelative = if (originalPrefix.isEmpty()) original else "$originalPrefix/$original"
            state.empty = false
            state.names += lower
            state.relativeNames += relative

            if (file.isDirectory) {
                if (lower == "tyrano") state.hasTyranoDir = true
                if (lower == "data") state.hasDataDir = true
                if (lower == "resources") state.hasResourcesDir = true
                if (level < maxLevel && lower in FEATURE_DIRECTORIES) {
                    collectFeatures(file, relative, originalRelative, level + 1, maxLevel, state, null)
                }
                continue
            }
            if (!file.isFile) continue

            if (lower == "index.html" || lower == "index.htm") state.hasIndex = true
            if (lower == "startup.tjs") state.hasStartupTjs = true
            if (lower == "config.tjs") state.hasConfigTjs = true
            if (lower == "system.ini") state.hasSystemIni = true
            if (relative == "system/first.iet" || relative.endsWith("/system/first.iet")) state.hasFirstIet = true
            if (lower == "root.pfs") state.hasRootPfs = true
            if (lower.endsWith(".pfs")) state.hasAnyPfsFile = true
            if (lower in ONS_SCRIPTS) state.hasOnsScript = true
            if (lower.endsWith(".nsa") || lower.endsWith(".sar") || lower.endsWith(".ns2")) state.hasOnsArchive = true
            if (lower == "app.asar" || relative.endsWith("/app.asar")) state.hasAppAsar = true
            if (lower == "package.json" || relative.endsWith("/package.json")) state.hasPackageJson = true
            if (lower.startsWith("chrome_") && lower.endsWith(".pak")) state.hasElectronPak = true
            if (lower.endsWith(".xp3")) {
                state.xp3Files += originalRelative
                if (lower.contains("游戏")) state.gameNamedXp3Files += originalRelative
                if (lower == "data.xp3" && state.dataXp3 == null) state.dataXp3 = originalRelative
            }
        }
    }

    private fun safeName(file: StorageNode?): String = runCatching { file?.name.orEmpty() }.getOrDefault("")
    private fun safeUri(file: StorageNode?): String = runCatching { file?.uri?.toString().orEmpty() }.getOrDefault("unknown")

    private fun score(result: Result, engine: EngineType, confidence: Int, launchTarget: String) {
        if (confidence <= result.confidence) return
        result.engine = engine
        result.confidence = confidence
        result.launchTarget = launchTarget
    }

    private val FEATURE_DIRECTORIES = setOf("resources", "app", "tyrano", "data", "scenario", "system")
    private val ONS_SCRIPTS = setOf("0.txt", "00.txt", "nscr_sec.dat", "nscript.dat", "onscript.nt2", "onscript.nt3")
}
