package com.kireibox.cover

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.metadata.VnMetadata
import com.yuki.yukihub.metadata.YukiMetadataService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
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

    fun confirm(
        gameId: Long,
        source: CoverSourceMode,
        candidate: VnMetadata,
        bindingMode: String = "MANUAL",
        matchScore: Double = 0.0
    ): Result<String> = runCatching {
        require(gameId > 0) { "游戏不存在" }
        require(candidate.coverUrl.isNotBlank()) { "候选没有封面" }
        val directory = File(app.filesDir, "covers/yukihub/game_$gameId").apply { mkdirs() }
        val temp = File(directory, ".${UUID.randomUUID()}.part")
        try {
            val request = Request.Builder().url(candidate.coverUrl).header("User-Agent", "KireiBox/0.3").build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body ?: error("封面响应为空")
                FileOutputStream(temp).use { output ->
                    val input = body.byteStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        total += count
                        check(total <= MAX_BYTES) { "封面文件过大" }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "封面无法解码" }
            val final = File(directory, "cover-${UUID.randomUUID()}.jpg")
            check(temp.renameTo(final)) { "封面原子提交失败" }
            val uri = Uri.fromFile(final).toString()
            val game = repository.findById(gameId) ?: error("游戏不存在")
            val previous = game.copy()
            game.coverUri = uri
            game.coverPersistUri = uri
            game.coverSourceType = 1
            game.vndbBindingMode = bindingMode
            game.vndbMatchScore = matchScore.coerceIn(0.0, 100.0)
            game.updatedAt = System.currentTimeMillis()
            try {
                check(repository.update(game) == 1) { "封面记录提交失败" }
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
        val game = repository.findById(gameId) ?: error("游戏不存在")
        val previous = game.coverUri
        game.coverUri = null
        game.coverPersistUri = null
        game.coverSourceType = 0
        game.vndbBindingMode = "DISABLED"
        game.vndbMatchScore = 0.0
        game.updatedAt = System.currentTimeMillis()
        check(repository.update(game) == 1) { "默认封面设置失败" }
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

    companion object { private const val MAX_BYTES = 20L * 1024L * 1024L }
}
