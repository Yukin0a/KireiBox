package com.yuki.yukihub.metadata.client
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.metadata.policy.MetadataUtils
import com.yuki.yukihub.net.ApiService
import com.yuki.yukihub.net.HttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.TimeUnit

object BangumiClient {
    /** Official API. Unreachable on some networks (mainland China blocks it). */
    const val OFFICIAL_BASE = "https://api.bgm.tv/"

    /**
     * Community mirror used when the official host is unreachable. These mirrors rotate domains as
     * each one gets blocked, so this is only a default — callers pass the base per search and users
     * can override it in settings.
     */
    const val MIRROR_BASE = "https://api.bangumi.vip/"

    /**
     * Bangumi is the anonymous fallback for mainland networks, so a blocked official host must not
     * eat the hybrid window with retries: one connect attempt (2s) then straight to the mirror.
     * The mirror is the real retry; a second request to the same dead host only adds latency.
     */
    private const val MAX_RETRIES = 0
    private const val RETRY_DELAY_MS = 1500L

    /**
     * Short connect timeout so a blocked host (official `api.bgm.tv` on a mainland network) fails
     * fast and the mirror is reached inside the hybrid per-source window, instead of eating the
     * whole budget on one dead host.
     */
    private const val CONNECT_TIMEOUT_SECONDS = 2L

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var bgmService: ApiService? = null

    /** Normalises a user-supplied base into `https://host/` form. */
    @JvmStatic
    fun normalizeBase(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return OFFICIAL_BASE
        return if (value.endsWith("/")) value else "$value/"
    }

    @JvmStatic
    @Throws(Exception::class)
    @JvmOverloads
    fun searchCandidates(
        keyword: String?,
        token: String?,
        limit: Int,
        apiBase: String = OFFICIAL_BASE,
        includeAdult: Boolean = false,
    ): List<VnMetadata> {
        val cleaned = MetadataUtils.cleanTitle(keyword)
        if (cleaned.isBlank()) return emptyList()
        val bounded = limit.coerceIn(1, 10)

        // The newer `/v0/search/subjects` engine tokenises CJK poorly: common Chinese/Japanese
        // titles are simply absent from its result set (e.g. 千恋万花 never returns 千恋＊万花 and
        // Riddle Joker never returns RIDDLE JOKER), so a keyword search there looks "empty".
        // The legacy `/search/subject/{keyword}` endpoint matches those titles correctly, so it
        // is the primary path. The v0 call remains as a fallback for mirrors that only expose the
        // newer API.
        return try {
            searchLegacy(cleaned, bounded, apiBase)
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (missing: HttpClient.HttpFailure) {
            // A 4xx (other than an auth failure) means this host has no legacy endpoint; a
            // network error / timeout is rethrown so the caller can walk to the next base instead
            // of paying for two dead requests on the same host.
            if (missing.status in 400..499 && missing.status != 401) {
                searchV0(cleaned, token, bounded, apiBase, includeAdult)
            } else {
                throw missing
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    @JvmOverloads
    fun searchFirst(
        keyword: String?,
        token: String?,
        apiBase: String = OFFICIAL_BASE,
    ): VnMetadata? {
        val list = searchCandidates(keyword, token, 1, apiBase)
        return list.firstOrNull()
    }

    /**
     * Legacy keyword search: `GET {base}search/subject/{keyword}?type=4&responseGroup=large`.
     * Anonymous and not backed by the v0 token. Returns only `游戏` (type 4) subjects.
     */
    private fun searchLegacy(
        keyword: String,
        limit: Int,
        apiBase: String,
    ): List<VnMetadata> {
        val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
        val endpoint = normalizeBase(apiBase) + "search/subject/$encoded"
        val url = "$endpoint?type=4&responseGroup=large&max_results=$limit"
        val text = HttpClient.executeWithRetry({ getBgmService().get(url) }, MAX_RETRIES, RETRY_DELAY_MS)
        val root = JSONObject(text)
        val out = ArrayList<VnMetadata>()
        val data = root.optJSONArray("list") ?: return out
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            if (item.optInt("type", 4) != 4) continue
            out.add(parseSubject(item))
        }
        return out
    }

    /** Newer v0 search. Kept only as a fallback for hosts without the legacy endpoint. */
    private fun searchV0(
        keyword: String,
        token: String?,
        limit: Int,
        apiBase: String,
        includeAdult: Boolean,
    ): List<VnMetadata> {
        val body = JSONObject()
        body.put("keyword", keyword)
        body.put("sort", "match")
        val filter = JSONObject()
        filter.put("type", JSONArray().put(4))
        if (!includeAdult) filter.put("nsfw", false)
        body.put("filter", filter)

        val endpoint = normalizeBase(apiBase) + "v0/search/subjects"
        val url = "$endpoint?limit=${limit.coerceIn(1, 10)}&offset=0"
        val auth = token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

        val requestBody = body.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON_TYPE)

        val service = getBgmService()
        val text =
            HttpClient.executeWithRetry(
                { if (auth == null) service.post(url, requestBody) else service.postWithAuth(url, requestBody, auth) },
                MAX_RETRIES,
                RETRY_DELAY_MS,
            )

        val out = ArrayList<VnMetadata>()
        val root = JSONObject(text)
        root.optJSONArray("data")?.let { data ->
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                out.add(parseSubject(item))
            }
        }
        return out
    }

    private fun getBgmService(): ApiService {
        if (bgmService == null) {
            synchronized(BangumiClient) {
                if (bgmService == null) {
                    val client =
                        HttpClient.defaultBuilder()
                            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .addInterceptor { chain ->
                                chain.proceed(
                                    chain.request().newBuilder()
                                        .header("Accept", "application/json")
                                        .build(),
                                )
                            }
                            .build()
                    val retrofit = HttpClient.retrofit("https://api.bgm.tv/", client)
                    bgmService = retrofit.create(ApiService::class.java)
                }
            }
        }
        return bgmService!!
    }

    private fun parseSubject(o: JSONObject): VnMetadata {
        val m = VnMetadata()
        val id = o.optInt("id", 0)
        m.id = if (id > 0) id.toString() else o.optString("id", "")
        m.romanTitle = o.optString("name", "")
        m.chineseTitle = MetadataUtils.firstNonEmpty(o.optString("name_cn", ""), m.romanTitle)
        m.originalTitle = m.romanTitle
        m.description = stripSummary(o.optString("summary", ""))
        // Legacy search uses `air_date`; v0 uses `date`.
        m.released = MetadataUtils.firstNonEmpty(o.optString("air_date", ""), o.optString("date", ""))

        o.optJSONObject("images")?.let { images ->
            m.coverUrl =
                MetadataUtils.firstNonEmpty(
                    images.optString("large", ""),
                    MetadataUtils.firstNonEmpty(images.optString("common", ""), images.optString("grid", "")),
                )
            if (m.coverUrl.startsWith("//")) {
                m.coverUrl = "https:${m.coverUrl}"
            } else if (m.coverUrl.startsWith("http://")) {
                // Bangumi mirrors return cleartext image URLs; the app blocks cleartext traffic.
                m.coverUrl = "https://${m.coverUrl.removePrefix("http://")}"
            }
        }

        o.optJSONObject("rating")?.let { rating ->
            val score = rating.optDouble("score", 0.0)
            val total = rating.optInt("total", 0)
            if (score > 0) {
                m.ratingText =
                    if (total > 0) {
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
