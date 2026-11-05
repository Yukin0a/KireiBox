package com.kireibox.cover

import android.content.Context
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.data.MetadataRepository
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.policy.NsfwCoverPolicy
import com.yuki.yukihub.settings.YukiSettingsStore

/** Repairs old automatic blur decisions from cached image ratings without networking. */
internal class NsfwCoverBackfill(context: Context) {
    private val context = context.applicationContext
    private val games = GameRepository(context.applicationContext)
    private val metadata = MetadataRepository(context.applicationContext)
    private val settings = YukiSettingsStore(context.applicationContext)
    private val controller = com.yuki.yukihub.metadata.YukiMetadataController(context.applicationContext)

    fun run(): Int {
        var changed = 0
        games.loadAllIncludingHidden().forEach { game ->
            if (game.id <= 0L) return@forEach
            val hasDecision = settings.hasCoverRatingDecision(game.id)
            // Old versions stored automatic and manual flags identically. Only AUTO bindings
            // without provenance are migrated; known manual choices always win.
            val legacyAutomatic = !hasDecision && game.nsfw && game.vndbBindingMode == "AUTO"
            val firstDecision = !game.nsfw && !settings.wasNsfwDetected(game.id) && game.vndbBindingMode == "AUTO"
            // Recheck AUTO covers cleared by the overly permissive r3 policy as well.
            val repairAutomatic = settings.followsAutomaticCoverRating(game.id) &&
                (game.nsfw || game.vndbBindingMode == "AUTO")
            // A clear automatic flag may describe a local/default replacement with stale metadata.
            if (!legacyAutomatic && !firstDecision && !repairAutomatic) return@forEach
            val source =
                when (controller.visibleSource(game.id)?.key) {
                    CoverSourceMode.NEXTMOE.key -> CoverSourceMode.NEXTMOE
                    CoverSourceMode.VNDB.key -> CoverSourceMode.VNDB
                    CoverSourceMode.HIKARINAGI.key -> CoverSourceMode.HIKARINAGI
                    else -> return@forEach
                }
            val cached = metadata.get(game.id, source) ?: return@forEach
            val shouldBlur = NsfwCoverPolicy.shouldAutoMark(source, cached)
            if (game.nsfw != shouldBlur) {
                val updated = game.copy(nsfw = shouldBlur, updatedAt = System.currentTimeMillis())
                if (games.update(updated) != 1) return@forEach
                changed++
            }
            if (!hasDecision) settings.markAutomaticCoverRating(game.id)
        }
        return changed
    }
}
