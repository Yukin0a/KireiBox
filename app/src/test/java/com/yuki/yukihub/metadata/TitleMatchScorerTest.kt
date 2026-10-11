package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.match.TitleMatchScorer
import com.yuki.yukihub.metadata.match.TitleQueryPlanner
import com.yuki.yukihub.metadata.model.TitleEvidence
import com.yuki.yukihub.metadata.model.TitleEvidenceSource
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.metadata.model.VndbSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchScorerTest {
    @Test
    fun normalizeRemovesEditionNoiseAndPunctuation() {
        assertEquals("千恋万花", TitleMatchScorer.normalize("【汉化版】千恋＊万花"))
    }

    @Test
    fun exactAlternativeTitleWins() {
        val target = VnMetadata(chineseTitle = "千恋万花", originalTitle = "千恋＊万花", romanTitle = "Senren Banka")
        val wrong = VnMetadata(chineseTitle = "千恋", romanTitle = "Other")
        val match = TitleMatchScorer.best("Senren Banka", listOf(wrong, target))
        assertEquals(target, match?.metadata)
        assertEquals(100.0, match?.score ?: 0.0, 0.001)
    }

    @Test
    fun aliasMatchesChineseQueryWhenPrimaryTitlesDiffer() {
        // NextMoe publishes the Chinese name only as an alternate title; the folder name must
        // still match it.
        val candidate =
            VnMetadata(
                id = "nm1",
                originalTitle = "この素晴らしい世界に祝福を！",
                romanTitle = "Kono Subarashii Sekai ni Shukufuku wo!",
                titleAliases = mutableListOf("为美好的世界献上祝福！"),
                coverUrl = "https://example.invalid/c.jpg"
            )
        val ranked =
            TitleMatchScorer.ranked(
                TitleQueryPlanner.variants("为美好的世界献上祝福！"),
                listOf(candidate)
            )
        assertEquals("nm1", ranked.first().metadata.id)
        assertEquals(100.0, ranked.first().score, 0.001)
        assertTrue(TitleMatchScorer.isAutomaticMatch(ranked))
    }

    @Test
    fun lowConfidenceCandidateIsRejected() {
        val score = TitleMatchScorer.score("Summer Pockets", "Clannad")
        assertFalse(TitleMatchScorer.isAutomaticMatch(score))
    }

    @Test
    fun closeSpellingCanPassThreshold() {
        val score = TitleMatchScorer.score("Aokana Four Rhythms", "Aokana Four Rhythm")
        assertTrue(TitleMatchScorer.isAutomaticMatch(score))
    }

    @Test
    fun chineseTitleInsideDecoratedFolderIsHighConfidence() {
        val candidates =
            listOf(
                VnMetadata(id = "v1", chineseTitle = "\u9b54\u5973\u7684\u591c\u5bb4"),
                VnMetadata(id = "v2", chineseTitle = "\u9b54\u604b\u65e5\u8bb0")
            )
        val ranked =
            TitleMatchScorer.ranked(
                TitleQueryPlanner.variants("\u3010KR\u3011\u9b54\u5973\u7684\u591c\u5bb4"),
                candidates
            )
        assertEquals("v1", ranked.first().metadata.id)
        assertTrue(TitleMatchScorer.isAutomaticMatch(ranked))
    }

    @Test
    fun episodeConflictDoesNotBeatMatchingEpisode() {
        val candidates =
            listOf(
                VnMetadata(id = "v2", romanTitle = "9-nine- Episode 2"),
                VnMetadata(id = "v1", romanTitle = "9-nine- Episode 1")
            )
        val ranked =
            TitleMatchScorer.ranked(
                TitleQueryPlanner.variants("9nine-1 \u4e5d\u6b21\u4e5d\u65e5\u4e5d\u91cd\u5929"),
                candidates
            )
        assertEquals("v1", ranked.first().metadata.id)
    }

    @Test
    fun decoratedKrTitleProducesCleanChineseQuery() {
        assertTrue(TitleQueryPlanner.variants("【KR】魔女的夜宴").contains("魔女的夜宴"))
    }

    @Test
    fun mixedNineTitleProducesEpisodeAndChineseQueries() {
        val variants = TitleQueryPlanner.variants("9nine-1 九次九日九重天")
        assertTrue(variants.any { it.contains("9nine", ignoreCase = true) })
        assertTrue(variants.contains("九次九日九重天"))
    }

    @Test
    fun artemisChineseTitleRemainsExact() {
        val candidate = VnMetadata(id = "v-art", chineseTitle = "灵感满溢的甜蜜创想")
        val ranked = TitleMatchScorer.ranked(TitleQueryPlanner.variants("灵感满溢的甜蜜创想"), listOf(candidate))
        assertEquals(100.0, ranked.first().score, 0.001)
    }

    @Test
    fun shortRomanQueryMatchesLongerOfficialTitleAsLeadingToken() {
        // "ATRI -My Dear Moments-" must outrank fuzzy VNDB neighbours like "1a(t)rium" or
        // "A Trick of the Mind": the query is the official title's leading whole token.
        assertEquals(94.0, TitleMatchScorer.score("ATRI", "ATRI -My Dear Moments-"), 0.001)
        assertTrue(TitleMatchScorer.isAutomaticMatch(TitleMatchScorer.score("ATRI", "ATRI -My Dear Moments-")))
        assertFalse(TitleMatchScorer.isAutomaticMatch(TitleMatchScorer.score("ATRI", "A Trick of the Mind")))
        assertFalse(TitleMatchScorer.isAutomaticMatch(TitleMatchScorer.score("ATRI", "1a(t)rium")))
    }

    @Test
    fun shortCjkQueryMatchesLongerOfficialTitleAsLeadingToken() {
        // The Han common-substring rule needs 4+ chars; a 3-char Chinese title still has to bind.
        assertEquals(94.0, TitleMatchScorer.score("亚托莉", "亚托莉 -我挚爱的时光-"), 0.001)
    }

    @Test
    fun searchRankAloneCannotAutoBind() {
        val metadata =
            VnMetadata(
                id = "v27449",
                chineseTitle = "常轨脱离Creative",
                romanTitle = "Hamidashi Creative"
            )
        val queries = TitleQueryPlanner.variants("灵感满溢的甜蜜创想")
        val hits =
            listOf(
                VndbSearchHit(metadata, "灵感满溢的甜蜜创想", 1),
                VndbSearchHit(metadata, "灵感满溢", 1)
            )
        val ranked = TitleMatchScorer.rankedWithEvidence(queries, hits)
        assertFalse(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun reportedTenshiFolderAutoBindsV40520() {
        val correct =
            VnMetadata(
                id = "v40520",
                chineseTitle = "天使☆嚣嚣 RE-BOOT!",
                originalTitle = "天使☆騒々 RE-BOOT!",
                romanTitle = "Tenshi Souzou RE-BOOT!",
                coverUrl = "https://t.vndb.org/cv/66/88666.jpg",
                titleAliases = mutableListOf("天使☆嚣嚣 RE-BOOT!", "Angelic Chaos RE-BOOT!")
            )
        val wrong =
            VnMetadata(
                id = "v1",
                chineseTitle = "天使的日曜日",
                coverUrl = "https://example.invalid/wrong.jpg"
            )
        val evidence = TitleQueryPlanner.evidence("【KRKR】天使嚣嚣", TitleEvidenceSource.FOLDER)
        val ranked =
            TitleMatchScorer.rankedFromEvidence(
                evidence,
                listOf(VndbSearchHit(correct, "天使嚣嚣", 1), VndbSearchHit(wrong, "天使嚣嚣", 2))
            )
        assertEquals("v40520", ranked.first().metadata.id)
        assertTrue(ranked.first().score >= 96.0)
        assertTrue(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun reportedAsNineFolderAutoBindsV52407() {
        val correct =
            VnMetadata(
                id = "v52407",
                romanTitle = "as:9-nine- ARTEISIA",
                originalTitle = "as:9-nine- アズナイン",
                chineseTitle = "9-nine- 阿尔特西亚",
                coverUrl = "https://t.vndb.org/cv/91/122491.jpg"
            )
        val otherEpisodes =
            listOf(
                VnMetadata(id = "v19829", romanTitle = "9-nine- Episode 1"),
                VnMetadata(id = "v21668", romanTitle = "9-nine- Episode 2"),
                VnMetadata(id = "v23740", romanTitle = "9-nine- Episode 3"),
                VnMetadata(id = "v26523", romanTitle = "9-nine- Episode 4")
            )
        val evidence = TitleQueryPlanner.evidence("as9ninekrkr", TitleEvidenceSource.FOLDER)
        val hits =
            (listOf(correct) + otherEpisodes).mapIndexed { index, metadata ->
                VndbSearchHit(metadata, "as9nine", index + 1)
            }
        val ranked = TitleMatchScorer.rankedFromEvidence(evidence, hits)
        assertEquals("v52407", ranked.first().metadata.id)
        assertTrue(ranked.first().score >= 96.0)
        assertTrue(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun weakXp3FilenameCannotAutoBindByItself() {
        val metadata = VnMetadata(id = "v1", chineseTitle = "天使嚣嚣", coverUrl = "https://example.invalid/a.jpg")
        val ranked =
            TitleMatchScorer.rankedFromEvidence(
                listOf(TitleEvidence("天使嚣嚣", TitleEvidenceSource.XP3_FILE)),
                listOf(VndbSearchHit(metadata, "天使嚣嚣", 1))
            )
        assertFalse(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun twoIndependentStrongSourcesCanAutoBind() {
        val metadata = VnMetadata(id = "v1", chineseTitle = "天使嚣嚣", coverUrl = "https://example.invalid/a.jpg")
        val ranked =
            TitleMatchScorer.rankedFromEvidence(
                listOf(
                    TitleEvidence("天使嚣嚣", TitleEvidenceSource.FOLDER),
                    TitleEvidence("天使嚣嚣", TitleEvidenceSource.CONFIG)
                ),
                listOf(VndbSearchHit(metadata, "天使嚣嚣", 1))
            )
        assertTrue(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun chapterConflictStillPenalizesSearchEvidence() {
        val one = VnMetadata(id = "v1", romanTitle = "9-nine- Episode 1")
        val three = VnMetadata(id = "v3", romanTitle = "9-nine- Episode 3")
        val queries = TitleQueryPlanner.variants("9-nine-3春色春恋春熙风")
        val ranked =
            TitleMatchScorer.rankedWithEvidence(
                queries,
                listOf(VndbSearchHit(one, "9-nine-3", 1), VndbSearchHit(three, "9-nine-3", 2))
            )
        assertEquals("v3", ranked.first().metadata.id)
    }

    @Test
    fun sharedLatinBrandDoesNotScoreEverySequelAlike() {
        // The Han common-substring rule must ignore the shared ASCII brand "9nine": the correct
        // episode matches its subtitle (96), the wrong episode must not sit near the bind range.
        val query = "9 nine 天色天歌天之音 2部"
        assertEquals(96.0, TitleMatchScorer.score(query, "9-nine-2 天色天歌天之音"), 0.001)
        assertTrue(
            "a different 9-nine episode scored ${TitleMatchScorer.score(query, "9-nine-1 九次九日九重色")}",
            TitleMatchScorer.score(query, "9-nine-1 九次九日九重色") < 60.0,
        )
    }

    @Test
    fun episodeNumberReadsTheRealSequelDigitNotTheFranchiseNine() {
        val ep1 = VnMetadata(id = "v1", romanTitle = "9-nine- Episode 1")
        val ep2 = VnMetadata(id = "v2", romanTitle = "9-nine- Episode 2")
        val ranked = TitleMatchScorer.ranked(listOf("9-nine-2"), listOf(ep1, ep2))
        assertEquals("v2", ranked.first().metadata.id)
    }

    @Test
    fun partMarkerSelectsTheCorrectNineEpisode() {
        val ep1 = VnMetadata(id = "v1", romanTitle = "9-nine-1 九次九日九重色", chineseTitle = "9-nine-1 九次九日九重色")
        val ep2 = VnMetadata(id = "v2", romanTitle = "9-nine-2 天色天歌天之音", chineseTitle = "9-nine-2 天色天歌天之音")
        val ranked = TitleMatchScorer.ranked(TitleQueryPlanner.variants("9 nine 天色天歌天之音 2部"), listOf(ep1, ep2))
        assertEquals("v2", ranked.first().metadata.id)
        assertTrue(ranked.first().score >= 96.0)
        assertTrue("a sequel conflict must not lead", ranked.first().score - ranked.last().score >= 5.0)
    }

    @Test
    fun episodeMarkersCoverChineseAndOrdinalForms() {
        val ep2 = VnMetadata(id = "v2", romanTitle = "Game Episode 2")
        val ep1 = VnMetadata(id = "v1", romanTitle = "Game Episode 1")
        // 第二部 / 第2話 / 2nd all resolve to episode 2 and beat episode 1.
        listOf("Game 第二部", "Game 第2話", "Game 2nd").forEach { query ->
            val ranked = TitleMatchScorer.ranked(listOf(query), listOf(ep1, ep2))
            assertEquals(query, "v2", ranked.first().metadata.id)
        }
    }

    @Test
    fun fanVariantSubtitleStillPicksTheRightNineEpisodeFromTheSeries() {
        // Real data: the catalog spells 9-nine-2's Chinese subtitle 天籁音 while the folder says
        // 天之音. The subtitle is unsearchable, but once the series is queried the episode number
        // and the shared 天色天歌天 characters must still select episode 2 and auto-bind.
        val ep1 = VnMetadata(id = "v19829", romanTitle = "9-nine- Episode 1", chineseTitle = "9-nine-九次九日九重色")
        val ep2 = VnMetadata(id = "v21668", romanTitle = "9-nine- Episode 2", chineseTitle = "9-nine-天色天歌天籁音")
        val ranked = TitleMatchScorer.ranked(TitleQueryPlanner.variants("9 nine 天色天歌天之音 2部"), listOf(ep1, ep2))
        assertEquals("v21668", ranked.first().metadata.id)
        assertTrue(TitleMatchScorer.isAutomaticMatch(ranked))
        assertTrue("the wrong episode must be far below", ranked.first().score - ranked.last().score >= 20.0)
    }
}
