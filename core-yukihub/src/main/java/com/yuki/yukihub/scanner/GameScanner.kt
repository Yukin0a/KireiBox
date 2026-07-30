package com.yuki.yukihub.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.model.SupportedEngines
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import java.util.Locale

/** Bounded SAF scanner that emits only KireiBox's four directory-based engines. */
object GameScanner {
    private const val TAG = "GameScanner"
    const val SCAN_ALL_LEVELS = -1
    const val SCAN_UNTIL_GAME_MATCH = -2

    @JvmStatic
    fun scan(context: Context, rootUri: Uri): List<ScanResult> = scan(context, rootUri, 2)

    @JvmStatic
    fun scan(context: Context, rootUri: Uri, maxDepth: Int): List<ScanResult> =
        scan(context, rootUri, ScanRequest.defaults(maxDepth)).results

    @JvmStatic
    fun scan(context: Context, rootUri: Uri, request: ScanRequest): ScanReport {
        val report = ScanReport()
        val root = runCatching { StorageNodes.resolve(context, rootUri) }.getOrElse { error ->
            Log.w(TAG, "resolve storage node failed uri=$rootUri", error)
            report.setStopReason(ScanReport.StopReason.INVALID_ROOT)
            report.addError("无法访问扫描目录：$rootUri")
            return report
        }
        if (root == null || !runCatching { root.isDirectory }.getOrDefault(false)) {
            report.setStopReason(ScanReport.StopReason.INVALID_ROOT)
            report.addError("扫描目标不是可读目录：$rootUri")
            return report
        }

        val requestedDepth = request.maxDepth
        val unbounded = requestedDepth == SCAN_ALL_LEVELS || requestedDepth == SCAN_UNTIL_GAME_MATCH
        val maxDepth = if (unbounded) Int.MAX_VALUE else requestedDepth.coerceIn(1, 4)
        val traverseMatchedGames = shouldTraverseMatchedGames(requestedDepth)
        scanDirectory(root, 0, maxDepth, traverseMatchedGames, report, HashSet(), request)
        return report
    }

    internal fun shouldTraverseMatchedGames(requestedDepth: Int): Boolean =
        requestedDepth == SCAN_ALL_LEVELS

    private fun scanDirectory(
        directory: StorageNode,
        depth: Int,
        maxDepth: Int,
        traverseMatchedGames: Boolean,
        report: ScanReport,
        seenUris: MutableSet<String>,
        request: ScanRequest
    ) {
        if (report.shouldStop(request)) return
        val name = safeName(directory)
        val uri = safeUri(directory)
        if (request.shouldSkipDirectory(name, uri)) {
            report.addSkippedDirectory()
            return
        }
        if (!report.tryVisit(request, uri)) return

        val files = try {
            directory.children()
        } catch (error: Throwable) {
            Log.w(TAG, "listFiles failed uri=$uri", error)
            report.addError("无法读取目录：$uri")
            return
        }
        val detected = runCatching { EngineDetector.detect(directory, 2, files) }.getOrElse { error ->
            Log.w(TAG, "engine detection failed uri=$uri", error)
            report.addError("识别游戏目录失败：$uri")
            EngineDetector.Result()
        }
        val matched = detected.confidence > 0 && SupportedEngines.contains(detected.engine)
        if (matched) {
            val identity = GameRepository.normalizeRootUriKey(uri)
            if (identity.isEmpty() || seenUris.add(identity)) {
                report.addResult(
                    ScanResult(
                        title = name,
                        uri = uri,
                        engine = detected.engine,
                        confidence = detected.confidence,
                        launchTarget = detected.launchTarget,
                        coverUri = findCover(files)?.uri?.toString().orEmpty(),
                        xp3Candidates = detected.xp3Candidates
                    )
                )
            }
        }
        if (depth >= maxDepth || (matched && !traverseMatchedGames)) return

        for (child in files) {
            if (report.shouldStop(request)) return
            if (!child.isDirectory) continue
            val childName = safeName(child).lowercase(Locale.ROOT)
            if (childName in INTERNAL_ENGINE_DIRECTORIES) continue
            scanDirectory(child, depth + 1, maxDepth, traverseMatchedGames, report, seenUris, request)
        }
    }

    private fun findCover(files: List<StorageNode>): StorageNode? = files
        .asSequence()
        .filter { it.isFile }
        .filter { isImage(safeName(it)) }
        .maxByOrNull { coverScore(safeName(it)) }

    private fun isImage(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".bmp")
    }

    private fun coverScore(name: String): Int {
        val lower = name.lowercase(Locale.ROOT)
        return when {
            lower == "cover.jpg" || lower == "cover.png" || lower == "cover.webp" -> 100
            lower == "folder.jpg" || lower == "folder.png" || lower == "folder.webp" -> 95
            "cover" in lower || "folder" in lower || "封面" in lower -> 80
            "poster" in lower || "package" in lower || "main" in lower -> 60
            else -> 10
        }
    }

    private fun safeName(file: StorageNode): String = runCatching { file.name }
        .getOrDefault("").ifBlank { "未命名游戏" }
    private fun safeUri(file: StorageNode): String = runCatching { file.uri.toString() }.getOrDefault("unknown")

    private val INTERNAL_ENGINE_DIRECTORIES = setOf(
        "data", "system", "tyrano", "scenario", "resources", "app", "savedata", "save",
        "bgimage", "fgimage", "image", "sound", "bgm", "voice", "video", "movie", "font"
    )
}
