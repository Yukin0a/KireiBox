package com.yuki.yukihub.metadata

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
        val candidates = listOf(
            VnMetadata(id = "v1", chineseTitle = "\u9b54\u5973\u7684\u591c\u5bb4"),
            VnMetadata(id = "v2", chineseTitle = "\u9b54\u604b\u65e5\u8bb0")
        )
        val ranked = TitleMatchScorer.ranked(
            TitleQueryPlanner.variants("\u3010KR\u3011\u9b54\u5973\u7684\u591c\u5bb4"),
            candidates
        )
        assertEquals("v1", ranked.first().metadata.id)
        assertTrue(TitleMatchScorer.isAutomaticMatch(ranked))
    }

    @Test
    fun episodeConflictDoesNotBeatMatchingEpisode() {
        val candidates = listOf(
            VnMetadata(id = "v2", romanTitle = "9-nine- Episode 2"),
            VnMetadata(id = "v1", romanTitle = "9-nine- Episode 1")
        )
        val ranked = TitleMatchScorer.ranked(
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
    fun searchRankAloneCannotAutoBind() {
        val metadata = VnMetadata(
            id = "v27449",
            chineseTitle = "常轨脱离Creative",
            romanTitle = "Hamidashi Creative"
        )
        val queries = TitleQueryPlanner.variants("灵感满溢的甜蜜创想")
        val hits = listOf(
            VndbSearchHit(metadata, "灵感满溢的甜蜜创想", 1),
            VndbSearchHit(metadata, "灵感满溢", 1)
        )
        val ranked = TitleMatchScorer.rankedWithEvidence(queries, hits)
        assertFalse(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun reportedTenshiFolderAutoBindsV40520() {
        val correct = VnMetadata(
            id = "v40520",
            chineseTitle = "天使☆嚣嚣 RE-BOOT!",
            originalTitle = "天使☆騒々 RE-BOOT!",
            romanTitle = "Tenshi Souzou RE-BOOT!",
            coverUrl = "https://t.vndb.org/cv/66/88666.jpg",
            titleAliases = mutableListOf("天使☆嚣嚣 RE-BOOT!", "Angelic Chaos RE-BOOT!")
        )
        val wrong = VnMetadata(
            id = "v1",
            chineseTitle = "天使的日曜日",
            coverUrl = "https://example.invalid/wrong.jpg"
        )
        val evidence = TitleQueryPlanner.evidence("【KRKR】天使嚣嚣", TitleEvidenceSource.FOLDER)
        val ranked = TitleMatchScorer.rankedFromEvidence(
            evidence,
            listOf(VndbSearchHit(correct, "天使嚣嚣", 1), VndbSearchHit(wrong, "天使嚣嚣", 2))
        )
        assertEquals("v40520", ranked.first().metadata.id)
        assertTrue(ranked.first().score >= 96.0)
        assertTrue(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun reportedAsNineFolderAutoBindsV52407() {
        val correct = VnMetadata(
            id = "v52407",
            romanTitle = "as:9-nine- ARTEISIA",
            originalTitle = "as:9-nine- アズナイン",
            chineseTitle = "9-nine- 阿尔特西亚",
            coverUrl = "https://t.vndb.org/cv/91/122491.jpg"
        )
        val otherEpisodes = listOf(
            VnMetadata(id = "v19829", romanTitle = "9-nine- Episode 1"),
            VnMetadata(id = "v21668", romanTitle = "9-nine- Episode 2"),
            VnMetadata(id = "v23740", romanTitle = "9-nine- Episode 3"),
            VnMetadata(id = "v26523", romanTitle = "9-nine- Episode 4")
        )
        val evidence = TitleQueryPlanner.evidence("as9ninekrkr", TitleEvidenceSource.FOLDER)
        val hits = (listOf(correct) + otherEpisodes).mapIndexed { index, metadata ->
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
        val ranked = TitleMatchScorer.rankedFromEvidence(
            listOf(TitleEvidence("天使嚣嚣", TitleEvidenceSource.XP3_FILE)),
            listOf(VndbSearchHit(metadata, "天使嚣嚣", 1))
        )
        assertFalse(TitleMatchScorer.shouldAutoBind(ranked))
    }

    @Test
    fun twoIndependentStrongSourcesCanAutoBind() {
        val metadata = VnMetadata(id = "v1", chineseTitle = "天使嚣嚣", coverUrl = "https://example.invalid/a.jpg")
        val ranked = TitleMatchScorer.rankedFromEvidence(
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
        val ranked = TitleMatchScorer.rankedWithEvidence(
            queries,
            listOf(VndbSearchHit(one, "9-nine-3", 1), VndbSearchHit(three, "9-nine-3", 2))
        )
        assertEquals("v3", ranked.first().metadata.id)
    }
}
