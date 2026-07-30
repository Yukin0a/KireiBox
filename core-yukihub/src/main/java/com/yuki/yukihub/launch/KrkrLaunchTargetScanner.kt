package com.yuki.yukihub.launch

import android.content.Context
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import java.util.Locale

/**
 * Lists explicit KRKR archives without opening them.
 *
 * The bounded walk works for both SAF trees and physical directories and keeps
 * the stored launch target relative to the game root.
 */
object KrkrLaunchTargetScanner {
    private const val DEFAULT_MAX_DEPTH = 4
    private const val MAX_VISITED_NODES = 10_000
    private const val MAX_RESULTS = 512

    @JvmStatic
    @JvmOverloads
    fun list(
        context: Context,
        rootUri: String?,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
    ): List<String> {
        val root = rootUri?.takeIf(String::isNotBlank)?.let { StorageNodes.resolve(context, it) }
            ?: return emptyList()
        return list(root, maxDepth)
    }

    @JvmStatic
    @JvmOverloads
    fun list(root: StorageNode?, maxDepth: Int = DEFAULT_MAX_DEPTH): List<String> {
        if (root?.isDirectory != true) return emptyList()
        val output = ArrayList<String>()
        var visited = 0

        fun walk(directory: StorageNode, prefix: String, depth: Int) {
            if (depth > maxDepth || output.size >= MAX_RESULTS || visited >= MAX_VISITED_NODES) return
            val children = runCatching { directory.children() }.getOrDefault(emptyList())
                .sortedWith(compareBy<StorageNode> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            for (child in children) {
                if (++visited > MAX_VISITED_NODES || output.size >= MAX_RESULTS) return
                val name = child.name.trim()
                if (name.isEmpty() || name.startsWith('.')) continue
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    if (name.equals("savedata", ignoreCase = true)) continue
                    walk(child, relative, depth + 1)
                } else if (
                    child.isFile &&
                    name.lowercase(Locale.ROOT).endsWith(".xp3")
                ) {
                    output += relative
                }
            }
        }

        walk(root, "", 1)
        return output.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
}
