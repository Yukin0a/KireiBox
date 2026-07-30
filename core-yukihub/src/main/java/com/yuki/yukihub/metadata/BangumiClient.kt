package com.yuki.yukihub.metadata

import com.yuki.yukihub.net.ApiService
import com.yuki.yukihub.net.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

object BangumiClient {
    private const val SEARCH_ENDPOINT_BGM = "https://api.bgm.tv/v0/search/subjects"
    private const val SEARCH_ENDPOINT_MIRROR = "https://api.bangumi.one/v0/search/subjects"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1500L

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var bgmService: ApiService? = null

    @Volatile
    private var mirrorService: ApiService? = null

    @JvmStatic
    @Throws(Exception::class)
    @JvmOverloads
    fun searchCandidates(keyword: String?, token: String?, limit: Int, useMirror: Boolean = false): List<VnMetadata> {
        val out = ArrayList<VnMetadata>()
        if (keyword == null || keyword.trim().isEmpty()) return out
        if (token == null || token.trim().isEmpty()) throw IllegalArgumentException("Bangumi token required")

        val body = JSONObject()
        body.put("keyword", MetadataUtils.cleanTitle(keyword))
        body.put("sort", "match")
        val filter = JSONObject()
        filter.put("type", JSONArray().put(4))
        body.put("filter", filter)

        val endpoint = if (useMirror) SEARCH_ENDPOINT_MIRROR else SEARCH_ENDPOINT_BGM
        val url = "$endpoint?limit=${limit.coerceIn(1, 10)}&offset=0"
        val auth = "Bearer ${token.trim()}"

        val requestBody = body.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON_TYPE)

        val service = if (useMirror) getMirrorService() else getBgmService()
        val text = HttpClient.executeWithRetry(
            { service.postWithAuth(url, requestBody, auth) },
            MAX_RETRIES, RETRY_DELAY_MS
        )

        val root = JSONObject(text)
        root.optJSONArray("data")?.let { data ->
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                out.add(parseSubject(item))
            }
        }
        return out
    }

    @JvmStatic
    @Throws(Exception::class)
    @JvmOverloads
    fun searchFirst(keyword: String?, token: String?, useMirror: Boolean = false): VnMetadata? {
        val list = searchCandidates(keyword, token, 1, useMirror)
        return list.firstOrNull()
    }

    private fun getBgmService(): ApiService {
        if (bgmService == null) {
            synchronized(BangumiClient) {
                if (bgmService == null) {
                    val client = HttpClient.defaultBuilder()
                        .addInterceptor { chain ->
                            chain.proceed(chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .build())
                        }
                        .build()
                    val retrofit = HttpClient.retrofit("https://api.bgm.tv/", client)
                    bgmService = retrofit.create(ApiService::class.java)
                }
            }
        }
        return bgmService!!
    }

    private fun getMirrorService(): ApiService {
        if (mirrorService == null) {
            synchronized(BangumiClient) {
                if (mirrorService == null) {
                    val client = HttpClient.defaultBuilder()
                        .addInterceptor { chain ->
                            chain.proceed(chain.request().newBuilder()
                                .header("Accept", "application/json")
                                .build())
                        }
                        .build()
                    val retrofit = HttpClient.retrofit("https://api.bangumi.one/", client)
                    mirrorService = retrofit.create(ApiService::class.java)
                }
            }
        }
        return mirrorService!!
    }

    private fun parseSubject(o: JSONObject): VnMetadata {
        val m = VnMetadata()
        val id = o.optInt("id", 0)
        m.id = if (id > 0) id.toString() else o.optString("id", "")
        m.romanTitle = o.optString("name", "")
        m.chineseTitle = MetadataUtils.firstNonEmpty(o.optString("name_cn", ""), m.romanTitle)
        m.originalTitle = m.romanTitle
        m.description = stripSummary(o.optString("summary", ""))
        m.released = o.optString("date", "")

        o.optJSONObject("images")?.let { images ->
            m.coverUrl = MetadataUtils.firstNonEmpty(
                images.optString("large", ""),
                MetadataUtils.firstNonEmpty(images.optString("common", ""), images.optString("grid", ""))
            )
            if (m.coverUrl.startsWith("//")) m.coverUrl = "https:${m.coverUrl}"
        }

        o.optJSONObject("rating")?.let { rating ->
            val score = rating.optDouble("score", 0.0)
            val total = rating.optInt("total", 0)
            if (score > 0) {
                m.ratingText = if (total > 0) {
                    String.format(Locale.US, "评分：%.1f/10（%d人）", score, total)
                } else {
                    String.format(Locale.US, "评分：%.1f/10", score)
                }
            }
        }

        o.optJSONArray("tags")?.let { tags ->
            val names = ArrayList<String>()
            for (i in 0 until tags.length()) {
                if (names.size >= 5) break
                val tag = tags.optJSONObject(i) ?: continue
                val name = tag.optString("name", "")
                if (name.isNotEmpty()) names.add(name)
            }
            m.tagsText = MetadataUtils.join(names, "  ")
        }

        m.lengthText = "游玩时长：-"
        return m
    }

    private fun stripSummary(s: String?): String {
        return s?.replace("\\r", "")?.trim() ?: ""
    }
}
