package com.kireibox.cover

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.metadata.NsfwCoverPolicy
import com.yuki.yukihub.metadata.VnMetadata
import com.yuki.yukihub.metadata.YukiMetadataService
import com.yuki.yukihub.settings.YukiSettingsStore
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Verified, rollback-safe YukiHub cover storage shared by automatic and manual matching. */
class KireiCoverStore(context: Context) {
    private val app = context.applicationContext
    private val repository = GameRepository(app)
    private val metadata = YukiMetadataService(app)
    private val settings = YukiSettingsStore(app)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val downloader = CoverDownloader(client)
    private val gameLocks = ConcurrentHashMap<Long, Mutex>()

    suspend fun confirm(
        gameId: Long,
        source: CoverSourceMode,
        candidate: VnMetadata,
        bindingMode: String = "MANUAL",
        matchScore: Double = 0.0,
        onProgress: (CoverDownloadProgress) -> Unit = {},
    ): Result<String> = gameLock(gameId).withLock { runCatching {
        require(gameId > 0) { "Game does not exist" }
        require(candidate.coverUrl.isNotBlank()) { "Cover candidate has no image" }
        val directory = File(app.filesDir, "covers/yukihub/game_$gameId").apply { mkdirs() }
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach(File::delete)
        val temp = File(directory, ".${UUID.randomUUID()}.part")
        try {
            downloader.download(candidate.coverUrl, temp, onProgress).getOrThrow()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cover cannot be decoded" }
            val game = repository.findById(gameId) ?: error("Game does not exist")
            if (bindingMode.equals("AUTO", ignoreCase = true) && !AutomaticCoverCommitPolicy.canCommit(game)) {
                return@runCatching game.coverUri.orEmpty()
            }
            val final = File(directory, "cover-${UUID.randomUUID()}.jpg")
            check(temp.renameTo(final)) { "Cover atomic commit failed" }
            val uri = Uri.fromFile(final).toString()
            val previous = game.copy()
            game.coverUri = uri
            game.coverPersistUri = uri
            game.coverSourceType = 1
            game.vndbBindingMode = bindingMode
            game.vndbMatchScore = matchScore.coerceIn(0.0, 100.0)
            val autoMarkedNsfw = NsfwCoverPolicy.shouldAutoMark(
                source = source,
                coverSexual = candidate.coverSexual,
                alreadyNsfw = game.nsfw,
                detectionHandled = settings.wasNsfwDetected(gameId),
            )
            if (autoMarkedNsfw) game.nsfw = true
            game.updatedAt = System.currentTimeMillis()
            try {
                check(repository.update(game) == 1) { "Cover record commit failed" }
                metadata.save(gameId, source, candidate)
            } catch (error: Throwable) {
                repository.update(previous)
                final.delete()
                throw error
            }
            if (autoMarkedNsfw) settings.markNsfwDetected(gameId)
            cleanup(directory, final, previous.coverUri)
            uri
        } finally {
            temp.delete()
        }
    } }

    suspend fun useDefault(gameId: Long): Result<Unit> = gameLock(gameId).withLock { runCatching {
        val game = repository.findById(gameId) ?: error("Game does not exist")
        val previous = game.coverUri
        game.coverUri = null
        game.coverPersistUri = null
        game.coverSourceType = 0
        game.vndbBindingMode = "DISABLED"
        game.vndbMatchScore = 0.0
        game.updatedAt = System.currentTimeMillis()
        check(repository.update(game) == 1) { "Default cover setting failed" }
        val managedRoot = File(app.filesDir, "covers/yukihub").canonicalFile
        val oldFile = previous?.let { Uri.parse(it).path }?.let(::File)
        if (oldFile != null && oldFile.exists() && oldFile.canonicalFile.path.startsWith(managedRoot.path)) {
            oldFile.delete()
        }
    } }

    suspend fun importLocal(gameId: Long, sourceUri: Uri): Result<String> =
        gameLock(gameId).withLock { runCatching {
            require(gameId > 0) { "Game does not exist" }
            require(sourceUri.scheme.equals("content", ignoreCase = true) ||
                sourceUri.scheme.equals("file", ignoreCase = true)) { "Unsupported image URI" }
            val directory = File(app.filesDir, "covers/yukihub/game_$gameId").apply { mkdirs() }
            directory.listFiles()?.filter { it.isFile && it.name.endsWith(".part") }?.forEach(File::delete)
            val temp = File(directory, ".${UUID.randomUUID()}.part")
            try {
                val input = app.contentResolver.openInputStream(sourceUri) ?: error("Unable to read selected image")
                var copied = 0L
                input.use { source ->
                    FileOutputStream(temp).use { target ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            copied += read
                            check(copied <= LocalCoverImportPolicy.MAX_BYTES) { "Image exceeds 20 MB" }
                            target.write(buffer, 0, read)
                        }
                        target.fd.sync()
                    }
                }
                check(LocalCoverImportPolicy.acceptsByteCount(copied)) { "Selected image is empty" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.path, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a usable image" }
                check(LocalCoverImportPolicy.acceptsDimensions(bounds.outWidth, bounds.outHeight)) { "Image resolution too large" }
                val extension = LocalCoverImportPolicy.extensionFor(app.contentResolver.getType(sourceUri))
                val final = File(directory, "cover-${UUID.randomUUID()}.$extension")
                check(temp.renameTo(final)) { "Cover file save failed" }
                val uri = Uri.fromFile(final).toString()
                val game = repository.findById(gameId) ?: run {
                    final.delete()
                    error("Game does not exist")
                }
                val previous = game.copy()
                game.coverUri = uri
                game.coverPersistUri = uri
                game.coverSourceType = 1
                game.vndbBindingMode = "MANUAL"
                game.vndbMatchScore = 0.0
                game.updatedAt = System.currentTimeMillis()
                try {
                    check(repository.update(game) == 1) { "Cover record save failed" }
                } catch (error: Throwable) {
                    repository.update(previous)
                    final.delete()
                    throw error
                }
                cleanup(directory, final, previous.coverUri)
                uri
            } finally {
                temp.delete()
            }
        } }

    private fun gameLock(gameId: Long): Mutex = gameLocks.getOrPut(gameId) { Mutex() }

    private fun cleanup(directory: File, keep: File, previousUri: String?) {
        directory.listFiles()?.filter { it.isFile && it != keep && !it.name.endsWith(".part") }
            ?.forEach { old ->
                if (previousUri.isNullOrBlank() || Uri.parse(previousUri).path == old.path) old.delete()
            }
    }
}
