package com.kireibox.ui

import com.yuki.yukihub.settings.HomePresentationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * r4 · S2 settings search: normalization, the ranking contract (exact title > title prefix > title
 * contains > keyword > subtitle > path), the Chinese / English / full-width queries people type
 * against the real index, where a result leads, and the scroll math of the jump to a row.
 */
class SettingsSearchTest {
    private val entries = SettingsSearchIndex.entries

    private fun titles(query: String): List<String> = SettingsSearch.search(entries, query).map { it.title }

    @Test
    fun normalizationFoldsWidthCaseSpacesAndJoiners() {
        assertEquals("krkr 引擎", SettingsSearch.normalize("  ＫＲＫＲ　引擎 "))
        assertEquals("abc 123", SettingsSearch.normalize("ＡＢＣ　　１２３"))
        assertEquals("wifi", SettingsSearch.compact("Wi-Fi"))
        assertEquals("外观显示", SettingsSearch.compact("外观 › 显示"))
        assertEquals("3d深度", SettingsSearch.compact("3D 深度"))
        assertEquals(listOf("封面", "来源"), SettingsSearch.words(" 封面　来源 "))
        assertTrue("blank queries match nothing", SettingsSearch.words(" 　 ").isEmpty())
        assertTrue(SettingsSearch.search(entries, "   ").isEmpty())
    }

    @Test
    fun rankingIsExactTitleThenPrefixThenContainsThenKeywordThenSubtitleThenPath() {
        val here = SettingsSearchLocation(KireiSettingsPage.APPEARANCE, "外观 › 显示")
        val exact = SettingsSearchEntry("主题", here)
        val prefix = SettingsSearchEntry("主题色", here)
        val contains = SettingsSearchEntry("深色主题", here)
        val keywordExact = SettingsSearchEntry("外观", here, keywords = listOf("主题"))
        val keyword = SettingsSearchEntry("颜色", here, keywords = listOf("主题模式"))
        val subtitle = SettingsSearchEntry("页面", here, anchor = null, subtitle = "切换主题")
        val path = SettingsSearchEntry("开关", SettingsSearchLocation(KireiSettingsPage.APPEARANCE, "外观 › 主题"))
        val miss = SettingsSearchEntry("无关", here)
        val shuffled = listOf(path, miss, subtitle, keyword, keywordExact, contains, prefix, exact)
        assertEquals(listOf(exact, prefix, contains, keywordExact, keyword, subtitle, path), SettingsSearch.search(shuffled, "主题"))
        assertEquals(SettingsSearch.Tier.EXACT_TITLE, SettingsSearch.tier(exact, "主题"))
        assertEquals(SettingsSearch.Tier.BREADCRUMB, SettingsSearch.tier(path, "主题"))
        assertNull(SettingsSearch.tier(miss, "主题"))
    }

    @Test
    fun equalTitleTiersPreferTheShorterTitleOtherwiseIndexOrder() {
        // 「更新」 is inside both titles: 检查更新 (4) is closer to the query than 目录自动更新 (6).
        val update = titles("更新")
        assertTrue(update.indexOf("检查更新") < update.indexOf("目录自动更新"))
        // Same length → index order: page entries precede effects, so 封面来源账号 comes before the
        // 封面缓慢推拉 effect.
        val covers = titles("封面")
        assertEquals(listOf("封面与元数据", "封面来源账号"), covers.take(2))
        // Among keyword matches, settings that live on the 封面与元数据 page come before effects
        // that merely mention covers (背景样式 · 封面虚化).
        assertTrue(covers.indexOf(COVER_STRICT_MATCH_TITLE) < covers.indexOf("背景样式"))
    }

    @Test
    fun chineseQueriesFindTheSettingPeopleMean() {
        assertEquals("深色 → 主题", "主题", titles("深色").first())
        assertEquals("主题", titles("夜间模式").first())
        assertEquals("主题", titles("黑暗模式").first())
        assertEquals("颜色 → 强调色", "强调色", titles("颜色").first())
        assertEquals("配色 → 强调色", "强调色", titles("配色").first())
        assertEquals("强调色", titles("樱粉").first())
        val blur = titles("模糊")
        assertTrue("模糊 → 减少透明度: $blur", "减少透明度" in blur)
        assertEquals("a title match outranks the 减少透明度 keyword", "模糊成人内容封面", blur.first())
        assertTrue(blur.indexOf("模糊成人内容封面") < blur.indexOf("减少透明度"))
        assertEquals("减少透明度", titles("毛玻璃").first())
        assertEquals("动画效果", titles("动效").first())
        assertEquals("屏幕方向", titles("旋转").first())
        assertEquals("屏幕方向", titles("横屏").first())
        assertEquals("氛围粒子", titles("雪花").first())
        assertEquals("陀螺仪视差", titles("视差").first())
        assertEquals("首页", titles("首页与沉浸式").first())
        assertEquals(QUICK_SETUP_TITLE, titles("引导").first())
        assertEquals("日志与诊断", titles("日志").first())
        assertEquals("日志与诊断", titles("崩溃").first())
        assertEquals(StorageAccessPolicy.nativeAccessTitle(), titles("所有文件访问").first())
        assertEquals("a longer phrase still finds the keyword inside it", "主题", titles("怎么开夜间模式").first())
    }

    @Test
    fun englishAbbreviationsAndFullWidthQueriesWork() {
        val krkr = titles("KRKR")
        assertEquals("KRKR 引擎设置", krkr.first())
        assertTrue("启动 KRKR 原生引擎" in krkr)
        assertEquals("full width folds to the same search", krkr, titles("ＫＲＫＲ"))
        assertEquals(krkr, titles("krkr"))
        assertEquals("KRKR 引擎设置", titles("吉里吉里").first())
        assertEquals("KRKR 引擎设置", titles("Kirikiri").first())
        assertEquals("仅 Wi-Fi 自动匹配", titles("wifi").first())
        assertEquals("主题", titles("dark").first())
        assertEquals("减少透明度", titles("blur").first())
        assertEquals("Bangumi", titles("bangumi").first())
        assertEquals("Bangumi 镜像地址", titles("bangumi").getOrNull(1))
        assertEquals("检查更新", titles("update").first())
        assertEquals("3D 深度", titles("3d").first())
        // English keywords never match inside unrelated words: 「login」 is no 「log」.
        assertFalse("日志与诊断" in titles("login"))
        assertEquals(settingsPageTitle(KireiSettingsPage.COVER_ACCOUNTS), titles("login").first())
    }

    @Test
    fun englishCoverAndBlurFindWhatTheirChineseWordsFind() {
        // 「cover」 used to find 2 rows while 「封面」 found 18: every setting named after covers answers to it.
        val cover = titles("cover")
        entries.filter { "封面" in it.title }.forEach { entry -> assertTrue("cover finds ${entry.title}", entry.title in cover) }
        assertTrue("背景样式" in cover)
        // 「blur」: the glass, the cover-blur backdrop and the adult-cover blur.
        val blur = titles("blur")
        assertTrue(listOf("减少透明度", "背景样式", "模糊成人内容封面").all { it in blur })
        assertTrue(titles("模糊").containsAll(listOf("减少透明度", "模糊成人内容封面")))
    }

    @Test
    fun everyWordMustMatchAndUnknownWordsFindNothing() {
        assertTrue(titles("不存在的词").isEmpty())
        assertTrue(titles("xyzzy").isEmpty())
        val both = SettingsSearch.search(entries, "封面 来源")
        assertTrue(both.isNotEmpty())
        both.forEach { entry -> assertTrue("${entry.title} matches both words", SettingsSearch.match(entry, listOf("封面", "来源")) != null) }
        assertFalse("主题 matches neither", both.any { it.title == "主题" })
        assertTrue("one word missing → no result", titles("主题 不存在").isEmpty())
    }

    @Test
    fun everySuggestionFindsSomething() {
        SettingsSearchIndex.suggestions.forEach { word -> assertTrue("$word finds a setting", titles(word).isNotEmpty()) }
    }

    @Test
    fun resultsLeadToTheirRowAndImmersiveOnlyOnesToTheHomeStyleInComponentsMode() {
        val accent = entries.single { it.title == "强调色" }
        assertEquals(SettingsSearchTarget(KireiSettingsPage.APPEARANCE, "强调色"), settingsSearchTarget(accent, HomePresentationMode.COMPONENTS))
        val grain = entries.single { it.title == "胶片颗粒" }
        assertEquals(SettingsSearchTarget(KireiSettingsPage.INTERFACE, "胶片颗粒", expandAdvanced = true), settingsSearchTarget(grain, HomePresentationMode.IMMERSIVE))
        // Without the immersive home the row does not exist: lead to the switch that brings it back.
        val homeStyle = SettingsSearchTarget(KireiSettingsPage.INTERFACE, SettingsSearchIndex.HOME_STYLE_ANCHOR)
        assertEquals(homeStyle, settingsSearchTarget(grain, HomePresentationMode.COMPONENTS))
        val playTime = entries.single { it.title == "显示游玩时长" }
        assertEquals(SettingsSearchTarget(KireiSettingsPage.INTERFACE, "显示游玩时长"), settingsSearchTarget(playTime, HomePresentationMode.IMMERSIVE))
        assertEquals(homeStyle, settingsSearchTarget(playTime, HomePresentationMode.COMPONENTS))
        // A page result opens the page itself (no row to find).
        val covers = entries.single { it.anchor == null && it.page == KireiSettingsPage.COVERS }
        assertNull(settingsSearchTarget(covers, HomePresentationMode.IMMERSIVE).anchor)
    }

    @Test
    fun resultSubtitlesSayWhereASettingLives() {
        val reduce = entries.single { it.title == "减少透明度" }
        assertEquals("外观 › 显示", settingsSearchSubtitle(reduce, HomePresentationMode.IMMERSIVE))
        val grain = entries.single { it.title == "胶片颗粒" }
        assertEquals("首页 › 高级选项 › 背景", settingsSearchSubtitle(grain, HomePresentationMode.IMMERSIVE))
        assertEquals("首页 › 高级选项 › 背景 · 需使用沉浸式首页", settingsSearchSubtitle(grain, HomePresentationMode.COMPONENTS))
        val appearance = entries.single { it.anchor == null && it.page == KireiSettingsPage.APPEARANCE }
        assertEquals("a page shows the group it sits in", "设置 › 个性化", settingsSearchSubtitle(appearance, HomePresentationMode.IMMERSIVE))
    }

    @Test
    fun aFoundRowScrollsOnlyWhenItIsOutOfViewAndLandsInTheUpperThird() {
        val band = SettingsRowSpan(top = 200f, bottom = 2000f)
        assertNull("already visible: highlight in place", SettingsRevealMath.scrollTarget(SettingsRowSpan(400f, 560f), band, scroll = 0, maxScroll = 3000))
        // Below the dock: its top lands 30% down the room left in the band.
        val below = SettingsRevealMath.scrollTarget(SettingsRowSpan(2400f, 2560f), band, scroll = 100, maxScroll = 3000)
        val room = band.height - 160f
        assertEquals((100 + 2400 - (200 + room * .3f)).toInt(), below)
        // Under the top bar → scroll back, never past the top.
        assertEquals(0, SettingsRevealMath.scrollTarget(SettingsRowSpan(-300f, -140f), band, scroll = 200, maxScroll = 3000))
        // Never past the end of the page.
        assertEquals(800, SettingsRevealMath.scrollTarget(SettingsRowSpan(5000f, 5160f), band, scroll = 0, maxScroll = 800))
        // Taller than the band: its top goes right under the bar.
        assertEquals(1000 + 2200 - 200, SettingsRevealMath.scrollTarget(SettingsRowSpan(2200f, 4400f), band, scroll = 1000, maxScroll = 9000))
        // Clamped to where the page already is → nothing to do.
        assertNull(SettingsRevealMath.scrollTarget(SettingsRowSpan(2400f, 2560f), band, scroll = 800, maxScroll = 800))
    }

    @Test
    fun aSearchTargetIsOneShotAndAStaleDropNeverClearsANewerOne() {
        val reveal = SettingsRevealState()
        val first = SettingsSearchTarget(KireiSettingsPage.APPEARANCE, "主题")
        val second = SettingsSearchTarget(KireiSettingsPage.KRKR, "兼容模式")
        reveal.requestSearchTarget(first)
        reveal.requestSearchTarget(second)
        reveal.consumeSearchTarget(first)
        assertEquals("a stale timeout for the first target keeps the second", second, reveal.searchTarget)
        reveal.consumeSearchTarget()
        assertNull(reveal.searchTarget)
    }

    @Test
    fun theHighlightPulsesForAboutOnePointTwoSeconds() {
        val pulse = SettingsAnchorMotion.PULSE_IN_MS + SettingsAnchorMotion.PULSE_HOLD_MS + SettingsAnchorMotion.PULSE_OUT_MS
        assertEquals(1_200L, pulse)
        assertEquals("reduced motion: a static tint for as long", 1_200L, SettingsAnchorMotion.STATIC_HOLD_MS)
        assertTrue("a stale target outlives the arrival and the settle frames", SettingsAnchorMotion.STALE_TARGET_MS > SettingsAnchorMotion.ARRIVAL_DELAY_MS + 1_000L)
    }
}
