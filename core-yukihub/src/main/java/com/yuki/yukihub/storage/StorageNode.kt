package com.yuki.yukihub.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * One storage contract for SAF documents and directly accessible files.
 *
 * Scanner and engine detection consume this type so file:// support does not
 * create a second, behaviorally different scanning implementation.
 */
interface StorageNode {
    val uri: Uri
    val name: String
    val isDirectory: Boolean
    val isFile: Boolean
    val size: Long
    val lastModified: Long

    fun children(): List<StorageNode>

    fun findChild(name: String): StorageNode?

    fun createFile(
        context: Context,
        mimeType: String,
        name: String,
    ): StorageNode?

    fun openInputStream(context: Context): InputStream?

    fun openOutputStream(context: Context): OutputStream?
}

object StorageNodes {
    @JvmStatic
    fun resolve(
        context: Context,
        uri: Uri,
    ): StorageNode? =
        when (uri.scheme?.lowercase(Locale.ROOT)) {
            null, "", "file" -> {
                val path = if (uri.scheme.equals("file", true)) uri.path else uri.toString()
                path?.takeIf(String::isNotBlank)?.let(::File)?.let(::FileStorageNode)
            }
            "content" -> {
                val document =
                    DocumentFile.fromTreeUri(context, uri)
                        ?: DocumentFile.fromSingleUri(context, uri)
                document?.let(::DocumentStorageNode)
            }
            else -> null
        }

    @JvmStatic
    fun resolve(
        context: Context,
        raw: String,
    ): StorageNode? = resolve(context, Uri.parse(raw))

    @JvmStatic
    fun fromDocument(document: DocumentFile): StorageNode = DocumentStorageNode(document)

    @JvmStatic
    fun fromFile(file: File): StorageNode = FileStorageNode(file)
}

private class FileStorageNode(private val file: File) : StorageNode {
    override val uri: Uri get() = Uri.fromFile(file)
    override val name: String get() = file.name.ifBlank { file.absolutePath }
    override val isDirectory: Boolean get() = file.isDirectory
    override val isFile: Boolean get() = file.isFile
    override val size: Long get() = file.length().coerceAtLeast(0L)
    override val lastModified: Long get() = file.lastModified().coerceAtLeast(0L)

    override fun children(): List<StorageNode> = file.listFiles()?.map(::FileStorageNode).orEmpty()

    override fun findChild(name: String): StorageNode? = File(file, name).takeIf { it.exists() }?.let(::FileStorageNode)

    override fun createFile(
        context: Context,
        mimeType: String,
        name: String,
    ): StorageNode? =
        runCatching {
            val child = File(file, name)
            if (child.exists() || child.createNewFile()) FileStorageNode(child) else null
        }.getOrNull()

    override fun openInputStream(context: Context): InputStream? = runCatching { if (file.isFile) file.inputStream() else null }.getOrNull()

    override fun openOutputStream(context: Context): OutputStream? = runCatching { if (file.isFile) file.outputStream() else null }.getOrNull()
}

private class DocumentStorageNode(private val document: DocumentFile) : StorageNode {
    override val uri: Uri get() = document.uri
    override val name: String get() = document.name.orEmpty().ifBlank { "未命名" }
    override val isDirectory: Boolean get() = runCatching { document.isDirectory }.getOrDefault(false)
    override val isFile: Boolean get() = runCatching { document.isFile }.getOrDefault(false)
    override val size: Long get() = runCatching { document.length() }.getOrDefault(0L).coerceAtLeast(0L)
    override val lastModified: Long get() = runCatching { document.lastModified() }.getOrDefault(0L).coerceAtLeast(0L)

    override fun children(): List<StorageNode> = runCatching { document.listFiles().map(::DocumentStorageNode) }.getOrDefault(emptyList())

    override fun findChild(name: String): StorageNode? = runCatching { document.findFile(name)?.let(::DocumentStorageNode) }.getOrNull()

    override fun createFile(
        context: Context,
        mimeType: String,
        name: String,
    ): StorageNode? = runCatching { document.createFile(mimeType, name)?.let(::DocumentStorageNode) }.getOrNull()

    override fun openInputStream(context: Context): InputStream? = runCatching { context.contentResolver.openInputStream(document.uri) }.getOrNull()

    override fun openOutputStream(context: Context): OutputStream? = runCatching { context.contentResolver.openOutputStream(document.uri, "wt") }.getOrNull()
}
