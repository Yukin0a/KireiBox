package com.yuki.yukihub.metadata
import com.yuki.yukihub.metadata.client.AccountCatalogClient
import com.yuki.yukihub.metadata.model.VnMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCatalogClientTest {
    @Test fun nextmoeKeepsLargeIdsAndCoverProvenanceThroughPersistence() {
        val parsed =
            AccountCatalogClient.parseNextmoe(
                JSONObject(
                    """{
            "id":"18446744073709551610", "display_name":"ATRI", "latin":null,
            "localized":{"zh-Hans":{"value":"亚托莉"}},
            "refs":[{"source":"vndb","external_id":"v27448"}],
            "intros":[{"lang":"zh-Hans","value":"简介"}],
            "covers":[
                {"url":"https://example.com/landscape.jpg","width":1600,"height":900,"source":"vndb","sexual":"safe"},
                {"url":"https://example.com/portrait.jpg","width":600,"height":900,"source":"bangumi","sexual":"suggestive"}
            ]
        }"""
                )
            )
        assertEquals("18446744073709551610", parsed.id)
        assertEquals("ATRI", parsed.romanTitle)
        assertEquals("亚托莉", parsed.chineseTitle)
        assertEquals("简介", parsed.description)
        assertEquals("vndb:v27448", parsed.externalRefs)
        assertEquals(2, parsed.covers.size)
        assertEquals("https://example.com/portrait.jpg", parsed.coverUrl)
        val restored = VnMetadata.fromJson(parsed.toJson().toString())!!
        assertEquals(parsed.covers, restored.covers)
        assertEquals(parsed.externalRefs, restored.externalRefs)
        assertEquals(1.0, restored.coverSexual, 0.0)
    }

    @Test fun nextmoeReadsTitlesCompaniesRatingsTagsAndAllRefs() {
        // Mirrors the include=titles,refs,intros,covers,companies,ratings,tags list lane.
        val parsed =
            AccountCatalogClient.parseNextmoe(
                JSONObject(
                    """{
            "id":"207379",
            "display_name":"この素晴らしい世界に祝福を！",
            "latin":"Kono Subarashii Sekai ni Shukufuku wo!",
            "olang":"ja",
            "release_date":"2021-08-27",
            "localized":{"zh-Hans":{"value":"为美好的世界献上祝福！","is_machine":false}},
            "titles":[
                {"lang":"ja","title":"この素晴らしい世界に祝福を！","latin":"Kono Subarashii Sekai ni Shukufuku wo!","title_kind":"official","is_machine":false},
                {"lang":"zh-Hans","title":"为美好的世界献上祝福！","latin":null,"title_kind":"official","is_machine":false},
                {"lang":"en","title":"God's Blessing on this Wonderful World!","latin":null,"title_kind":"alias","is_machine":false}
            ],
            "refs":[
                {"source":"vndb","external_id":"v19658"},
                {"source":"bangumi","external_id":"302835"},
                {"source":"dlsite","external_id":"RJ01012345"}
            ],
            "companies":[
                {"id":"900","display_name":"発売元","company_kind":"publisher","attribution_role":"publisher"},
                {"id":"901","display_name":"開発ブランド","company_kind":"game_brand","attribution_role":"developer"}
            ],
            "ratings":[
                {"source":"vndb","score":7.87,"vote_count":375,"rank":null},
                {"source":"bangumi","score":6.9,"vote_count":1065,"rank":4184}
            ],
            "tags":[
                {"id":"1","display_name":"奇幻","source":"bangumi","is_sexual":false},
                {"id":null,"display_name":"搞笑","source":"vndb","is_sexual":false}
            ],
            "covers":[
                {"id":"1","vote_count":3,"portrait_pinned":true,"cover_kind":"main","url":"https://example.com/portrait.jpg","width":600,"height":900,"sexual":"safe","source":"vndb"}
            ],
            "cover":{"url":"https://example.com/base.jpg","width":560,"height":420,"sexual":"safe","source":"dlsite"}
        }"""
                )
            )
        assertEquals("为美好的世界献上祝福！", parsed.chineseTitle)
        assertEquals("この素晴らしい世界に祝福を！", parsed.originalTitle)
        assertEquals("Kono Subarashii Sekai ni Shukufuku wo!", parsed.romanTitle)
        assertTrue(parsed.titleAliases.contains("God's Blessing on this Wonderful World!"))
        assertEquals("開発ブランド", parsed.developer)
        assertEquals("评分：6.9/10（1065人） · Bangumi", parsed.ratingText)
        assertEquals("奇幻  搞笑", parsed.tagsText)
        assertEquals("vndb:v19658,bangumi:302835,dlsite:RJ01012345", parsed.externalRefs)
        assertEquals("https://example.com/portrait.jpg", parsed.coverUrl)
        assertEquals(2, parsed.covers.size)
    }

    @Test fun hikarinagiReadsDetailTitleNotSearchSubtitleYear() {
        val parsed =
            AccountCatalogClient.parseHikarinagi(
                JSONObject(
                    """{
            "id":123,"origin_title":"ATRI", "trans_title":"亚托莉", "en_title":null,
            "trans_intro":"简介", "release_date":"2020-06-19T00:00:00Z",
            "covers":[{"url":"https://example.com/cover.jpg","width":600,"height":900,"sexual":1}]
        }"""
                )
            )
        assertEquals("亚托莉", parsed.chineseTitle)
        assertEquals("ATRI", parsed.romanTitle)
        assertEquals("2020-06-19", parsed.released)
        assertEquals(1.0, parsed.coverSexual, 0.0)
    }

    @Test fun hikarinagiRelativeCoverUrlIsResolvedBeforeTheHttpsFilter() {
        // The official reference documents covers[].url as `path/to/asset.jpg`. Without
        // resolution MetadataCover would drop it and the candidate would show no artwork.
        val parsed =
            AccountCatalogClient.parseHikarinagi(
                JSONObject(
                    """{
            "id":7,"origin_title":"X",
            "covers":[{"url":"upload/cover.jpg","width":600,"height":900,"sexual":0}]
        }"""
                )
            )
        assertEquals(1, parsed.covers.size)
        assertEquals("https://imagesp.yurari.moe/upload/cover.jpg", parsed.coverUrl)
        assertEquals(0.0, parsed.coverSexual, 0.0)
    }

    @Test fun hikarinagiProtocolRelativeCoverUrlBecomesHttps() {
        val parsed =
            AccountCatalogClient.parseHikarinagi(
                JSONObject(
                    """{
            "id":8,"origin_title":"X","covers":[{"url":"//cdn.example.com/c.jpg","sexual":"suggestive"}]
        }"""
                )
            )
        assertEquals("https://cdn.example.com/c.jpg", parsed.coverUrl)
        assertEquals(1.0, parsed.coverSexual, 0.0)
    }

    @Test fun hikarinagiTakesFirstCoverBecauseTheApiSortsByVotes() {
        val parsed =
            AccountCatalogClient.parseHikarinagi(
                JSONObject(
                    """{
            "id":9,"origin_title":"X","covers":[
                {"url":"upload/best.jpg","sexual":0},
                {"url":"upload/second.jpg","sexual":0}
            ]
        }"""
                )
            )
        assertEquals("https://imagesp.yurari.moe/upload/best.jpg", parsed.coverUrl)
    }

    @Test fun nextmoeFallsBackToIdentityCoreCoverWhenCoversBlockIsAbsent() {
        // Search hits carry no covers block; only the identity-core `cover` may be present.
        val parsed =
            AccountCatalogClient.parseNextmoe(
                JSONObject(
                    """{
            "id":"42","display_name":"ATRI",
            "cover":{"url":"https://example.com/base.jpg","width":560,"height":420,"sexual":"explicit"}
        }"""
                )
            )
        assertEquals("https://example.com/base.jpg", parsed.coverUrl)
        assertEquals(2.0, parsed.coverSexual, 0.0)
    }

    @Test fun nextmoeMergesBothTitleLanesKeepingSearchHitsFirst() {
        // `works?q=` surfaces some works the `search` lane misses, so both must be probed.
        val merged =
            AccountCatalogClient.mergeNextmoeIds(
                fromSearch = listOf("12", "99"),
                fromWorks = listOf("99", "231766", "947"),
            )
        assertEquals(listOf("12", "99", "231766", "947"), merged)
    }

    @Test fun nextmoeMergeDropsNonNumericAndBlankIds() {
        val merged =
            AccountCatalogClient.mergeNextmoeIds(
                fromSearch = listOf("12", "", "abc", "null"),
                fromWorks = listOf("947"),
            )
        assertEquals(listOf("12", "947"), merged)
    }
}
