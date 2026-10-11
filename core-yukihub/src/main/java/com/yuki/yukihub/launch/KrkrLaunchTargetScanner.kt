package com.yuki.yukihub.launch

import android.content.Context
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import java.util.Locale

/**
 * Lists explicit KRKR archives, and for .xp3 also the exes that carry an XP3 archive
 * (KRKR 汉化 / single-exe builds, see [KrkrBoundExe]).
 *
 * The bounded walk works for both SAF trees and physical directories and keeps
 * the stored launch target relative to the game root.
 */
object KrkrLaunchTargetScanner {
    private const val DEFAULT_MAX_DEPTH = 1
    private const val MAX_VISITED_NODES = 10_000
    private const val MAX_RESULTS = 512

    @JvmStatic
    @JvmOverloads
    fun list(
        context: Context,
        rootUri: String?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        extension: String = ".xp3",
    ): List<String> {
        val root =
            rootUri?.takeIf(String::isNotBlank)?.let { StorageNodes.resolve(context, it) }
                ?: return emptyList()
        val boundExe = if (extension == ".xp3") { node: StorageNode -> KrkrBoundExe.isBound(context, node) } else null
        return list(root, maxDepth, extension, boundExe)
    }

    @JvmStatic
    @JvmOverloads
    fun list(
        root: StorageNode?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        extension: String = ".xp3",
        boundExe: ((StorageNode) -> Boolean)? = null,
    ): List<String> {
        if (root?.isDirectory != true) return emptyList()
        val output = ArrayList<String>()
        var visited = 0

        fun walk(
            directory: StorageNode,
            prefix: String,
            depth: Int,
        ) {
            if (depth > maxDepth || output.size >= MAX_RESULTS || visited >= MAX_VISITED_NODES) return
            val children =
                runCatching { directory.children() }.getOrDefault(emptyList())
                    .sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            for (child in children) {
                if (++visited > MAX_VISITED_NODES || output.size >= MAX_RESULTS) return
                val name = child.name.trim()
                if (name.isEmpty() || name.startsWith('.')) continue
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    if (name.equals("savedata", ignoreCase = true)) continue
                    walk(child, relative, depth + 1)
                } else if (isLaunchFile(child, name, extension) || isBoundExe(child, name, boundExe)) {
                    output += relative
                }
            }
        }

        walk(root, "", 1)
        return output.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    private fun isBoundExe(
        node: StorageNode,
        name: String,
        boundExe: ((StorageNode) -> Boolean)?,
    ): Boolean = boundExe != null && KrkrBoundExe.isCandidateName(name) && node.isFile && boundExe(node)

    private fun isLaunchFile(
        node: StorageNode,
        name: String,
        extension: String,
    ): Boolean = node.isFile && name.lowercase(Locale.ROOT).endsWith(extension)
}
