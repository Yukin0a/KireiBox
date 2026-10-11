package com.yuki.yukihub.metadata.client
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.metadata.model.VndbSearchHit
import com.yuki.yukihub.metadata.policy.MetadataUtils
import com.yuki.yukihub.net.ApiService
import com.yuki.yukihub.net.HttpClient
import com.yuki.yukihub.util.AppExecutors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * VNDB kana API 客户端（Kotlin object 单例）。
 *
 * - 替代原 Java 静态方法类，使用 `object VndbClient` 单例。
 * - `@JvmStatic` 保留 4 个公共方法的 Java 静态调用形式，调用方无需修改。
 * - `@Throws(Exception::class)` 保留 `throws Exception` 在字节码签名上。
 * - JSON 解析层使用 `?.let` / `?:` / `when` / 字符串模板替代原 `if (xxx != null)` +
 *   字符串拼接模式，移除 VnMetadata String 字段已非空后的死 `== null` 检查。
 * - OkHttp 5.x idiom：`toMediaType()` / `toRequestBody()` 替代已废弃的
 *   `MediaType.parse()` / `RequestBody.create()`。
 *
 * 锁对象说明：原 Java `static synchronized` / `synchronized(VndbClient.class)` 锁
 * `VndbClient.class`；Kotlin 版 `@Synchronized` / `synchronized(VndbClient)` 锁单例
 * `VndbClient.INSTANCE`。锁对象不同，但本类内部互斥语义一致（throttle 与 getService
 * 各自独立，无跨方法共享锁需求）。当前无外部代码锁 `VndbClient.class`，无实际影响。
 */
object VndbClient {
    private const val ENDPOINT = "https://api.vndb.org/kana/vn"
    private const val FIELDS =
        "title,alttitle,titles.lang,titles.title,titles.latin,titles.official,titles.main,olang," +
            "released,image.url,image.thumbnail,image.sexual,image.violence,description,rating," +
            "length,length_minutes,length_votes,developers.name,developers.original,tags.name," +
            "tags.rating,tags.spoiler,screenshots.url,screenshots.thumbnail,screenshots.sexual," +
            "screenshots.violence"
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1500L
    private const val MIN_REQUEST_INTERVAL_MS = 1100L

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    // 仅在 @Synchronized throttle() 内读写，happens-before 由 monitor 保证，无需 @Volatile。
    private var lastRequestTime = 0L

    @Volatile
    private var apiService: ApiService? = null

    interface Callback {
        fun onSuccess(data: VnMetadata?)

        fun onError(error: Exception)
    }

    interface CandidatesCallback {
        fun onSuccess(data: List<VnMetadata>)

        fun onError(error: Exception)
    }

    @JvmStatic
    fun searchAsync(
        title: String?,
        callback: Callback,
    ) {
        AppExecutors.runOnIo {
            try {
                callback.onSuccess(search(title))
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun search(title: String?): VnMetadata? = searchCandidates(title, 1).firstOrNull()

    @JvmStatic
    fun searchCandidatesAsync(
        title: String?,
        limit: Int,
        callback: CandidatesCallback,
    ) {
        AppExecutors.runOnIo {
            try {
                callback.onSuccess(searchCandidates(title, limit))
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    @JvmOverloads
    fun searchCandidates(
        title: String?,
        limit: Int,
        includeAdult: Boolean = true,
    ): List<VnMetadata> {
        // MetadataUtils.cleanTitle 显式容忍 null（返回 ""），与原 Java 行为一致：
        // 传入 null 时返回空列表而非 NPE。
        val q = MetadataUtils.cleanTitle(title)
        if (q.isEmpty()) return ArrayList()

        val body = JSONObject()
        val nameFilter = JSONArray().put("search").put("=").put(q)
        body.put(
            "filters",
            if (includeAdult) {
                nameFilter
            } else {
                JSONArray().put("and").put(nameFilter)
                    .put(JSONArray().put("release").put("=").put(JSONArray().put("minage").put("<").put(18)))
            },
        )
        body.put("fields", FIELDS)
        body.put("sort", "searchrank")
        body.put("results", limit.coerceIn(1, 10))

        throttle()

        val url = ENDPOINT
        val requestBody = body.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON_TYPE)

        val text =
            HttpClient.executeWithRetry(
                { getService().post(url, requestBody) },
                MAX_RETRIES,
                RETRY_DELAY_MS,
            )

        val root = JSONObject(text)
        val out = ArrayList<VnMetadata>()
        root.optJSONArray("results")?.let { results ->
            for (i in 0 until results.length()) out.add(parse(results.getJSONObject(i)))
        }
        return out
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getById(id: String?): VnMetadata? {
        val safeId = id?.trim().orEmpty()
        if (!safeId.matches(Regex("""v\d+"""))) return null
        val body = JSONObject()
        body.put("filters", JSONArray().put("id").put("=").put(safeId))
        body.put("fields", FIELDS)
        body.put("results", 1)
        throttle()
        val requestBody = body.toString().toByteArray(StandardCharsets.UTF_8).toRequestBody(JSON_TYPE)
        val text =
            HttpClient.executeWithRetry(
                { getService().post(ENDPOINT, requestBody) },
                MAX_RETRIES,
                RETRY_DELAY_MS,
            )
        return JSONObject(text).optJSONArray("results")
            ?.optJSONObject(0)
            ?.let(::parse)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun searchHits(
        title: String?,
        limit: Int,
    ): List<VndbSearchHit> =
        searchCandidates(title, limit).mapIndexed { index, metadata ->
            VndbSearchHit(metadata, title.orEmpty(), index + 1)
        }

    @Synchronized
    @Throws(InterruptedException::class)
    private fun throttle() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            Thread.sleep(MIN_REQUEST_INTERVAL_MS - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    private fun getService(): ApiService {
        if (apiService == null) {
            synchronized(VndbClient) {
                if (apiService == null) {
                    val client =
                        HttpClient.defaultBuilder()
                            .addInterceptor { chain ->
                                chain.proceed(
                                    chain.request().newBuilder()
                                        .header("Content-Type", "application/json")
                                        .header("Accept", "application/json")
                                        .build(),
                                )
                            }
                            .build()
                    val retrofit = HttpClient.retrofit("https://api.vndb.org/", client)
                    apiService = retrofit.create(ApiService::class.java)
                }
            }
        }
        return apiService!!
    }

    private fun parse(o: JSONObject): VnMetadata {
        val m = VnMetadata()
        m.id = o.optString("id", "")
        m.romanTitle = o.optString("title", "")
        m.originalTitle = o.optString("alttitle", "")
        m.description = stripVndbMarkup(o.optString("description", ""))
        m.released = o.optString("released", "")
        val rating = o.optInt("rating", 0)
        if (rating > 0) {
            m.ratingText = String.format(Locale.US, "评分：%.1f/10", rating / 10.0f)
        }
        m.lengthMinutes = o.optInt("length_minutes", 0)
        m.lengthVotes = o.optInt("length_votes", 0)
        m.lengthText = formatLengthText(o.optInt("length", 0), m.lengthMinutes, m.lengthVotes)

        // VnMetadata.chineseTitle / originalTitle 均为非空 String（默认 ""），无 null 分支。
        o.optJSONArray("titles")?.let { titles ->
            for (i in 0 until titles.length()) {
                val t = titles.optJSONObject(i) ?: continue
                val lang = t.optString("lang", "")
                val titleStr = t.optString("title", "")
                val main = t.optBoolean("main", false)
                if (titleStr.isNotBlank() && titleStr !in m.titleAliases) {
                    m.titleAliases.add(titleStr)
                }
                if (lang == "zh-Hans" || lang == "zh-Hant" || lang == "zh") {
                    if (m.chineseTitle.isEmpty()) m.chineseTitle = titleStr
                }
                if (main && m.originalTitle.isEmpty()) m.originalTitle = titleStr
            }
        }
        if (m.chineseTitle.isEmpty()) {
            m.chineseTitle = MetadataUtils.firstNonEmpty(m.originalTitle, m.romanTitle)
        }
        if (m.originalTitle.isEmpty()) m.originalTitle = m.romanTitle

        o.optJSONObject("image")?.let { image ->
            m.thumbnailUrl = image.optString("thumbnail", "")
            m.fullCoverUrl = image.optString("url", "")
            m.coverUrl = MetadataUtils.firstNonEmpty(m.fullCoverUrl, m.thumbnailUrl)
            m.coverSexual = image.optDouble("sexual", 0.0)
            m.coverViolence = image.optDouble("violence", 0.0)
        }

        o.optJSONArray("developers")?.let { devs ->
            val names = ArrayList<String>()
            for (i in 0 until devs.length()) {
                if (names.size >= 3) break
                val d = devs.optJSONObject(i) ?: continue
                val name = MetadataUtils.firstNonEmpty(d.optString("original", ""), d.optString("name", ""))
                if (name.isNotEmpty()) names.add(name)
            }
            m.developer = MetadataUtils.join(names, " / ")
        }

        o.optJSONArray("tags")?.let { tags ->
            val scored = ArrayList<TagScore>()
            for (i in 0 until tags.length()) {
                val t = tags.optJSONObject(i) ?: continue
                if (t.optInt("spoiler", 0) > 0) continue
                val name = t.optString("name", "")
                if (name.isNotEmpty()) scored.add(TagScore(name, t.optDouble("rating", 0.0)))
            }
            scored.sortByDescending { it.score }
            val top = scored.take(5).map { it.name }
            m.tagsText = MetadataUtils.join(top, "  ")
        }

        o.optJSONArray("screenshots")?.let { shots ->
            for (i in 0 until shots.length()) {
                if (m.screenshotUrls.size >= 2) break
                val s = shots.optJSONObject(i) ?: continue
                val url = MetadataUtils.firstNonEmpty(s.optString("thumbnail", ""), s.optString("url", ""))
                if (url.isNotEmpty()) m.screenshotUrls.add(url)
            }
        }
        return m
    }

    private fun stripVndbMarkup(s: String?): String {
        if (s == null) return ""
        return s.replace(Regex("\\[url=[^\\]]+]([^\\[]+)\\[/url]"), "$1")
            .replace(Regex("\\[[^\\]]+]"), "")
            .replace("\\r", "")
            .trim()
    }

    private fun formatLengthText(
        length: Int,
        minutes: Int,
        votes: Int,
    ): String {
        val label =
            when (length) {
                1 -> "很短"
                2 -> "短"
                3 -> "中等"
                4 -> "长"
                5 -> "很长"
                else -> ""
            }
        val hoursText =
            if (minutes > 0) {
                String.format(Locale.US, "%.1f小时", minutes / 60.0f)
            } else {
                ""
            }
        val voteText = if (votes > 0) "，${votes}人统计" else ""
        return when {
            label.isNotEmpty() && hoursText.isNotEmpty() ->
                "游玩时长：$label（${hoursText}$voteText）"
            hoursText.isNotEmpty() -> "游玩时长：${hoursText}$voteText"
            label.isNotEmpty() -> "游玩时长：$label"
            else -> ""
        }
    }

    private data class TagScore(val name: String, val score: Double)
}
