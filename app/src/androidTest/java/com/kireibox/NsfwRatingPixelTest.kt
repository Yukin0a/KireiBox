package com.kireibox

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kireibox.cover.NsfwCoverBackfill
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.metadata.YukiMetadataController
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.YukiSettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NsfwRatingPixelTest {
    @Test fun cachedImageRatingsRepairBothMissedAndFalseFlagsWhileManualChoicesSurvive() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = GameRepository(context)
        val settings = YukiSettingsStore(context)
        val metadata = YukiMetadataController(context)
        val prefs = context.getSharedPreferences(YukiSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
        val fixtures = mutableListOf<Game>()
        fun fixture(score: Double, automatic: Boolean?, blurred: Boolean = true, imageSource: String = "vndb"): Game {
            val game = Game(title = "NSFW regression fixture", rootUri = "test://nsfw-${System.nanoTime()}",
                nsfw = blurred, coverSourceType = 1, vndbBindingMode = "AUTO")
            repo.insert(game)
            fixtures += game
            metadata.saveMetadata(game.id, CoverSourceMode.NEXTMOE,
                VnMetadata(coverUrl = "https://example.invalid/cover-${game.id}.jpg", coverSexual = score, externalRefs = "vndb:v17",
                    covers = mutableListOf(com.yuki.yukihub.metadata.model.MetadataCover("https://example.invalid/cover-${game.id}.jpg", source = imageSource, sexual = score.toString()))))
            when (automatic) {
                true -> settings.markAutomaticCoverRating(game.id)
                false -> settings.markNsfwDetected(game.id)
                // Pre-r2 automatic flags had no provenance key.
                null -> prefs.edit().putBoolean(YukiSettingsStore.nsfwDetectionKey(game.id), true).commit()
            }
            return game
        }
        try {
            val untrusted = fixture(1.0, true, imageSource = "ai_redraw")
            val bangumi = fixture(1.0, true, imageSource = "bangumi")
            val unknown = fixture(2.0, true, imageSource = "")
            val safe = fixture(0.0, true)
            val missed = fixture(1.13, true, false)
            val suggestive = fixture(1.0, true)
            val legacy = fixture(1.13, null)
            val explicit = fixture(2.0, true)
            val manual = fixture(0.0, false)
            val manualClear = fixture(2.0, false, false)
            // A local/default replacement can retain old metadata but must stay clear.
            val replacement = fixture(2.0, true, false).also { it.vndbBindingMode="MANUAL"; repo.update(it) }
            val default = fixture(2.0, true, false).also { it.vndbBindingMode="DISABLED"; repo.update(it) }
            NsfwCoverBackfill(context).run()
            assertFalse("AI redraw must not inherit VNDB trust", repo.findById(untrusted.id)!!.nsfw)
            assertFalse(repo.findById(bangumi.id)!!.nsfw)
            assertFalse(repo.findById(unknown.id)!!.nsfw)
            assertFalse("safe cover must be repaired", repo.findById(safe.id)!!.nsfw)
            assertTrue("censored nudity must be restored after r3", repo.findById(missed.id)!!.nsfw)
            assertTrue(repo.findById(suggestive.id)!!.nsfw)
            assertTrue(repo.findById(legacy.id)!!.nsfw)
            assertTrue(repo.findById(explicit.id)!!.nsfw)
            assertTrue(repo.findById(manual.id)!!.nsfw)
            assertFalse(repo.findById(manualClear.id)!!.nsfw)
            assertFalse(repo.findById(replacement.id)!!.nsfw)
            assertFalse(repo.findById(default.id)!!.nsfw)
            assertTrue(settings.followsAutomaticCoverRating(legacy.id))
            val updatedAt = repo.findById(suggestive.id)!!.updatedAt
            NsfwCoverBackfill(context).run()
            assertEquals(updatedAt, repo.findById(suggestive.id)!!.updatedAt)
        } finally {
            fixtures.forEach { game ->
                repo.delete(game.id)
                prefs.edit().remove(YukiSettingsStore.nsfwDetectionKey(game.id))
                    .remove("nsfw_auto_cover_${game.id}").remove("visible_metadata_source_${game.id}").commit()
            }
        }
    }
}
