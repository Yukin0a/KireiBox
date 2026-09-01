package com.kireibox.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kireibox.external.shortcut.ExternalShortcutDirectLaunch
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.storage.StorageRootIdentity
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Presentation-only formatter. Raw persisted URIs remain untouched. */
object GameLocationLabel {
    fun forGame(context: Context, game: Game): String {
        if (game.engine == EngineType.ANDROID_APP) {
            ExternalShortcutIdentity.fromGame(game)?.let {
                return "应用快捷方式 / ${it.packageName} / ${it.shortcutId}"
            }
            return listOfNotNull(
                game.emulatorPackage?.trim()?.takeIf(String::isNotEmpty),
                game.launchTarget?.trim()?.takeIf(String::isNotEmpty)
            ).joinToString(" / ").ifBlank { "Android 应用" }
        }
        val root = format(context, game.rootUri)
        val entry = game.entryKey.trim().takeIf(String::isNotEmpty)
        return listOfNotNull(root.takeIf(String::isNotEmpty), entry?.let(::formatSegments))
            .joinToString(" / ")
            .ifBlank { "游戏目录" }
    }

    fun launchSubtitle(game: Game): String = when {
        ExternalShortcutDirectLaunch.fromGame(game) != null -> "直接启动应用快捷方式"
        ExternalShortcutIdentity.isExternalShortcut(game) -> "通过 Shizuku 启动"
        game.engine == EngineType.KIRIKIRI -> "使用 KRKR 启动"
        game.engine == EngineType.ANDROID_APP -> "直接启动 Android 应用"
        else -> "使用当前引擎启动"
    }

    fun xp3LaunchSubtitle(game: Game): String = game.launchTarget
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.replace('\\', '/')
        ?.substringAfterLast('/')
        ?.let(::decode)
        ?.takeIf(String::isNotBlank)
        ?: "自动选择合适的 XP3"

    fun format(context: Context, raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "未设置目录"
        if (value.startsWith("file://", ignoreCase = true)) {
            return formatStorageIdentity(StorageRootIdentity.key(value))
        }
        if (!value.startsWith("content://", ignoreCase = true)) {
            return Uri.decode(value).replace('\\', '/').let(::formatSegments)
        }
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return Uri.decode(value)
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if (!documentId.isNullOrBlank()) return formatDocumentId(Uri.decode(documentId))
        val name = runCatching {
            DocumentFile.fromTreeUri(context, uri)?.name
                ?: DocumentFile.fromSingleUri(context, uri)?.name
        }.getOrNull()
        return name?.takeIf(String::isNotBlank) ?: Uri.decode(value)
    }

    fun formatDocumentId(documentId: String): String {
        val decoded = decode(documentId).trim().replace('\\', '/')
        val volume = decoded.substringBefore(':', missingDelimiterValue = "")
        val relative = decoded.substringAfter(':', missingDelimiterValue = decoded).trim('/')
        val root = when {
            volume.equals("primary", ignoreCase = true) -> "内部存储"
            volume.equals("home", ignoreCase = true) -> "内部存储 / Documents"
            volume.isNotEmpty() -> "存储卡 $volume"
            else -> ""
        }
        return listOfNotNull(root.takeIf(String::isNotEmpty), relative.takeIf(String::isNotEmpty)?.let(::formatSegments))
            .joinToString(" / ")
            .ifBlank { formatSegments(decoded) }
    }

    private fun formatStorageIdentity(identity: String): String {
        val value = identity.trim()
        return when {
            value.startsWith("primary:/") -> listOf(
                "内部存储",
                value.removePrefix("primary:/").takeIf(String::isNotBlank)?.let(::formatSegments),
            ).filterNotNull().joinToString(" / ")
            value.startsWith("volume:") -> {
                val volumeAndPath = value.removePrefix("volume:")
                val volume = volumeAndPath.substringBefore(":/")
                val relative = volumeAndPath.substringAfter(":/", "")
                listOf(
                    "存储卡 $volume",
                    relative.takeIf(String::isNotBlank)?.let(::formatSegments),
                ).filterNotNull().joinToString(" / ")
            }
            value.startsWith("file:") -> formatSegments(value.removePrefix("file:"))
            else -> formatSegments(value)
        }
    }

    private fun formatSegments(value: String): String = value
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(" / ")

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(value)
}
