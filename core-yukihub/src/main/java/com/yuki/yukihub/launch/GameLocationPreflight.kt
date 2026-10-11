package com.yuki.yukihub.launch

import android.content.Context
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageNode
import com.yuki.yukihub.storage.StorageNodes
import java.text.Normalizer
import java.util.Locale

/** Validates a launch entry and repairs an old scan-root card only when unambiguous. */
object GameLocationPreflight {
    data class Result(val game: Game?, val repaired: Boolean, val message: String)

    fun verify(
        context: Context,
        repository: GameRepository,
        source: Game,
    ): Result {
        if (source.engine == EngineType.ANDROID_APP) return Result(source, false, "android package")
        val rootUri =
            source.rootUri?.takeIf(String::isNotBlank)
                ?: return Result(null, false, "游戏目录为空")
        val root =
            StorageNodes.resolve(context, rootUri)
                ?: return Result(null, false, "无法读取游戏目录，请重新授权")
        val target = source.launchTarget?.trim().orEmpty()
        if (target.isBlank() || target.startsWith("[")) return Result(source, false, "directory entry")
        if (source.engine == EngineType.KIRIKIRI && Xp3EntrySelector.isAssetTarget(target)) {
            return repairAssetXp3Target(repository, source, root)
        }
        if (resolve(root, target) != null) return Result(source, false, "verified")

        val candidates = ArrayList<StorageNode>()
        collectDirectoriesContaining(root, target, 0, 3, candidates)
        val distinct = candidates.distinctBy { it.uri.toString() }
        val selected =
            when {
                distinct.size == 1 -> distinct.single()
                distinct.isEmpty() -> null
                else -> distinct.filter { titleCompatible(source.title, it.name) }.singleOrNull()
            } ?: return Result(
                null,
                false,
                if (distinct.isEmpty()) "找不到启动文件 $target" else "找到多个 $target，请重新选择游戏目录",
            )

        val repaired =
            source.copy(
                rootUri = selected.uri.toString(),
                entryKey = if (source.entryKey.isBlank()) "" else GameRepository.normalizeEntryKey(target),
                launchTarget = target,
                updatedAt = System.currentTimeMillis(),
            )
        repository.update(repaired)
        return Result(repository.findById(source.id) ?: repaired, true, "已修复游戏目录：${selected.name.orEmpty()}")
    }

    private fun repairAssetXp3Target(
        repository: GameRepository,
        source: Game,
        root: StorageNode,
    ): Result {
        val children = runCatching { root.children() }.getOrDefault(emptyList())
        val entries =
            children.asSequence()
                .filter { it.isFile && it.name.endsWith(".xp3", ignoreCase = true) }
                .map { Xp3EntrySelector.Entry(it.name, it.size) }
                .toList()
        val selected = Xp3EntrySelector.select(entries, root.name, source.title)
        val newTarget = selected ?: "[游戏目录]"
        val repaired =
            source.copy(
                launchTarget = newTarget,
                updatedAt = System.currentTimeMillis(),
            )
        repository.update(repaired)
        return Result(
            repository.findById(source.id) ?: repaired,
            true,
            if (selected != null) "已修正 XP3 启动文件：$selected" else "已改为目录模式启动",
        )
    }

    private fun collectDirectoriesContaining(
        directory: StorageNode,
        target: String,
        depth: Int,
        maxDepth: Int,
        output: MutableList<StorageNode>,
    ) {
        if (resolve(directory, target) != null) output += directory
        if (depth >= maxDepth) return
        val children = runCatching { directory.children() }.getOrDefault(emptyList())
        for (child in children) {
            if (!child.isDirectory || child.name.startsWith('.')) continue
            collectDirectoriesContaining(child, target, depth + 1, maxDepth, output)
        }
    }

    private fun resolve(
        root: StorageNode,
        relativePath: String,
    ): StorageNode? {
        var current: StorageNode? = root
        for (segment in relativePath.replace('\\', '/').split('/').filter(String::isNotBlank)) {
            current = runCatching { current?.findChild(segment) }.getOrNull() ?: return null
        }
        return current?.takeIf { it.isFile }
    }

    private fun titleCompatible(
        title: String?,
        directoryName: String?,
    ): Boolean {
        val a = normalize(title)
        val b = normalize(directoryName)
        return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    private fun normalize(value: String?): String =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")
}
