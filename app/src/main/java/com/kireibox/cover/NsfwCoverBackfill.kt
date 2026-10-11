package com.kireibox.cover

import android.content.Context
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.MetadataRepository
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.policy.NsfwCoverPolicy
import com.yuki.yukihub.settings.YukiSettingsStore

/** Applies the first NSFW decision to metadata cached by pre-v21 installs without networking. */
internal class NsfwCoverBackfill(context: Context) {
    private val games = GameRepository(context.applicationContext)
    private val metadata = MetadataRepository(context.applicationContext)
    private val settings = YukiSettingsStore(context.applicationContext)

    fun run(): Int {
        var changed = 0
        games.loadAllIncludingHidden().forEach { game ->
            if (game.id <= 0L || game.nsfw || settings.wasNsfwDetected(game.id)) return@forEach
            val source =
                when (metadata.getMostRecentlyUpdatedSource(game.id)) {
                    CoverSourceMode.VNDB.key -> CoverSourceMode.VNDB
                    CoverSourceMode.HIKARINAGI.key -> CoverSourceMode.HIKARINAGI
                    else -> return@forEach
                }
            val cached = metadata.get(game.id, source) ?: return@forEach
            if (!NsfwCoverPolicy.shouldAutoMark(
                    source = source,
                    coverSexual = cached.coverSexual,
                    alreadyNsfw = game.nsfw,
                    detectionHandled = settings.wasNsfwDetected(game.id),
                )
            ) {
                return@forEach
            }
            val updated = game.copy(nsfw = true, updatedAt = System.currentTimeMillis())
            if (games.update(updated) == 1) {
                settings.markNsfwDetected(game.id)
                changed += 1
            }
        }
        return changed
    }
}
