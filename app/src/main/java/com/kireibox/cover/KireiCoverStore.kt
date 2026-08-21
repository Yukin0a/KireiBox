package com.kireibox.cover

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.metadata.VnMetadata
import com.yuki.yukihub.metadata.YukiMetadataService
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Verified, rollback-safe YukiHub cover storage shared by automatic and manual matching. */
class KireiCoverStore(context: Context) {
    private val app = context.applicationContext
    private val repository = GameRepository(app)
    private val metadata = YukiMetadataService(app)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val downloader = CoverDownloader(client)

    suspend fun confirm(
        gameId: Long,
        source: CoverSourceMode,
        candidate: VnMetadata,
        bindingMode: String = "MANUAL",
        matchScore: Double = 0.0,
        onProgress: (CoverDownloadProgress) -> Unit = {},
    ): Result<String> = runCatching {
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
            val final = File(directory, "cover-${UUID.randomUUID()}.jpg")
            check(temp.renameTo(final)) { "Cover atomic commit failed" }
            val uri = Uri.fromFile(final).toString()
            val game = repository.findById(gameId) ?: error("Game does not exist")
            val previous = game.copy()
            game.coverUri = uri
            game.coverPersistUri = uri
            game.coverSourceType = 1
            game.vndbBindingMode = bindingMode
            game.vndbMatchScore = matchScore.coerceIn(0.0, 100.0)
            game.updatedAt = System.currentTimeMillis()
            try {
                check(repository.update(game) == 1) { "Cover record commit failed" }
                metadata.save(gameId, source, candidate)
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
    }

    fun useDefault(gameId: Long): Result<Unit> = runCatching {
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
    }

    private fun cleanup(directory: File, keep: File, previousUri: String?) {
        directory.listFiles()?.filter { it.isFile && it != keep && !it.name.endsWith(".part") }
            ?.forEach { old ->
                if (previousUri.isNullOrBlank() || Uri.parse(previousUri).path == old.path) old.delete()
            }
    }
}
