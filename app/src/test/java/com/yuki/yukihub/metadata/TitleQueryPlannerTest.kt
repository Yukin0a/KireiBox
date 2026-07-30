package com.yuki.yukihub.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleQueryPlannerTest {
    @Test fun networkQueriesAreBoundedAndPreferCleanEngineFreeTitles() {
        assertEquals("ATRI", TitleQueryPlanner.networkQueries("【KRKR】ATRI").first())
        assertEquals("as9nine", TitleQueryPlanner.networkQueries("as9nine krkr").first())
        assertTrue(TitleQueryPlanner.networkQueries("anything").size <= 3)
    }

    @Test fun networkQueriesFallBackToLongestChineseRun() {
        val queries = TitleQueryPlanner.networkQueries("9nine-1-九次九日九重天")
        assertTrue(queries.contains("九次九日九重天"))
        assertTrue(queries.size <= 3)
    }
    @Test
    fun stripsKrkrAndKrOnlyAtSearchBoundaries() {
        listOf(
            "【KRKR】天使嚣嚣",
            "KRKR天使嚣嚣",
            "KR天使嚣嚣",
            "天使嚣嚣-KRKR",
            "天使嚣嚣 kr"
        ).forEach { raw ->
            assertTrue("$raw -> ${TitleQueryPlanner.variants(raw)}", TitleQueryPlanner.variants(raw).contains("天使嚣嚣"))
        }
    }

    @Test
    fun doesNotStripKrInsideRealWordsOrNumberedNames() {
        assertTrue(TitleQueryPlanner.variants("KARAKARA").any { it.equals("KARAKARA", true) })
        assertTrue(TitleQueryPlanner.variants("KR-17").any { it.contains("KR-17", true) })
    }

    @Test
    fun createsAsNineQueriesFromAttachedLatinEngineSuffix() {
        listOf("as9ninekrkr", "as9nineKR").forEach { raw ->
            val variants = TitleQueryPlanner.variants(raw)
            assertTrue("$raw -> $variants", variants.any { it.equals("as9nine", true) })
            assertTrue("$raw -> $variants", variants.any { it.equals("as:9-nine-", true) })
            assertTrue("$raw -> $variants", variants.any { it.equals("Asnine", true) })
        }
    }

    @Test
    fun stripsRepeatedEngineDecorations() {
        assertTrue(TitleQueryPlanner.variants("【PC】【KRKR】【KR】天使嚣嚣").contains("天使嚣嚣"))
    }

    @Test
    fun removesEnginePrefixFromChineseTitle() {
        val variants = TitleQueryPlanner.variants("\u3010KR\u3011\u9b54\u5973\u7684\u591c\u5bb4")
        assertTrue(variants.contains("\u9b54\u5973\u7684\u591c\u5bb4"))
    }

    @Test
    fun createsChineseAndLatinQueriesForNumberedGame() {
        val variants = TitleQueryPlanner.variants("9nine-1 \u4e5d\u6b21\u4e5d\u65e5\u4e5d\u91cd\u5929")
        assertTrue(variants.any { it.contains("\u4e5d\u6b21\u4e5d\u65e5\u4e5d\u91cd\u5929") })
        assertTrue(variants.any { it.replace("-", " ").contains("9nine 1", ignoreCase = true) })
    }

    @Test
    fun removesTranslationAndBackupSuffixes() {
        val variants = TitleQueryPlanner.variants(
            "\u7075\u611f\u6ee1\u6ea2\u7684\u751c\u871c\u521b\u60f3 AI\u7ffb\u8bd1\u5907\u4efd"
        )
        assertTrue(variants.first().contains("\u7075\u611f\u6ee1\u6ea2\u7684\u751c\u871c\u521b\u60f3"))
        assertTrue(variants.none { it.contains("\u5907\u4efd") || it.contains("AI", ignoreCase = true) })
    }

    @Test
    fun handlesMismatchedAndCombinedEngineBrackets() {
        assertTrue(TitleQueryPlanner.variants("[KRKR】A146").contains("A146"))
        assertTrue(TitleQueryPlanner.variants("【kr+pc】少女领域").contains("少女领域"))
        assertTrue(TitleQueryPlanner.variants("IPC+KR】DRACU-RIOT!").any { it.contains("DRACU", true) })
    }

    @Test
    fun createsEpisodeAndChineseVariantsWithoutSpaces() {
        val variants = TitleQueryPlanner.variants("9-nine-1九次九日九重色")
        assertTrue(variants.contains("9-nine- Episode 1"))
        assertTrue(variants.contains("九次九日九重色"))
    }

    @Test
    fun splitsDualTitleFolder() {
        val variants = TitleQueryPlanner.variants(
            "【KR】金色Loveriche-Golden Time-/金辉恋曲四重奏-Golden Time"
        )
        assertTrue(variants.any { it.contains("金色Loveriche", true) })
        assertTrue(variants.any { it.contains("金辉恋曲四重奏") })
    }

    @Test
    fun createsUsefulQueriesForReportedLibraryNames() {
        val cases = mapOf(
            "9-nine-1九次九日九重色" to listOf("9-nine- Episode 1", "九次九日九重色"),
            "9-nine-3春色春恋春熙风" to listOf("9-nine- Episode 3", "春色春恋春熙风"),
            "9-nine-4雪色雪花雪余痕" to listOf("9-nine- Episode 4", "雪色雪花雪余痕"),
            "【kr+pc】少女领域" to listOf("少女领域"),
            "【KR】ATRI-My Dear Moments" to listOf("ATRI-My Dear Moments"),
            "【KR】寄宿之恋" to listOf("寄宿之恋"),
            "【KR】糖调！" to listOf("糖调"),
            "【KR】纯白交响曲FHD+SANA" to listOf("纯白交响曲", "SANA"),
            "IPC+KR】DRACU-RIOT!" to listOf("DRACU-RIOT"),
            "永不枯萎的世界与终结之花" to listOf("永不枯萎的世界与终结之花"),
            "灵感满溢的甜蜜创想" to listOf("灵感满溢的甜蜜创想"),
            "缘之空" to listOf("缘之空")
        )
        cases.forEach { (folder, expectedFragments) ->
            val variants = TitleQueryPlanner.variants(folder)
            expectedFragments.forEach { fragment ->
                assertTrue(
                    "$folder should yield $fragment but was $variants",
                    variants.any { it.contains(fragment, ignoreCase = true) }
                )
            }
        }
    }
}
