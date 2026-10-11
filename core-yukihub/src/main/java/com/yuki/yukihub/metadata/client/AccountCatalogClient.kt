package com.yuki.yukihub.metadata.client
import com.yuki.yukihub.auth.CoverAccounts
import com.yuki.yukihub.auth.CoverRateLimitPolicy
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.MetadataCover
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.net.HttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Hikarinagi returns cover urls that may be relative (the official reference shows
 * `path/to/asset.jpg`) rather than absolute. They must be resolved against the image host
 * before reaching [MetadataCover], whose https-only filter would otherwise discard the entry.
 */
object HikarinagiImagePolicy {
    private const val IMAGE_BASE = "https://imagesp.yurari.moe/"

    @JvmStatic
    fun absolute(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value == "null") return ""
        if (value.startsWith("//")) return "https:$value"
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        return IMAGE_BASE + value.trimStart('/')
    }
}

/** Catalog reads only. User credentials never travel to image hosts. */
class AccountCatalogClient(private val accounts: CoverAccounts) {
    private val client = HttpClient.defaultBuilder().followRedirects(false).build()

    fun searchNextmoe(
        keyword: String,
        limit: Int,
        refs: String = "",
    ): List<VnMetadata> {
        val source = CoverSourceMode.NEXTMOE
        val base = "https://api.nextmoe.dev/v2/catalog/"
        if (refs.isNotBlank()) {
            // Batch lane: resolve known external ids straight to hydrated works.
            val resolved = get(source, base + "works", mapOf("refs" to refs, "include" to NEXTMOE_INCLUDE))
            return objects(resolved.optJSONArray("items")).map(::parseNextmoe)
        }
        val ids = nextmoeSearchIds(source, base, keyword, limit)
        if (ids.isEmpty()) return emptyList()
        return hydrateNextmoe(source, base, ids, ::parseNextmoe)
    }

    /**
     * NextMoe exposes two title lanes with *different* coverage: `/catalog/search` and the plain
     * `/catalog/works?q=` list. Some works only surface on one of them (e.g. サクラノ詩 is absent
     * from `search` but present in `works`), so both are probed and their ids merged.
     *
     * A failing lane is skipped so the other still contributes; cancellation always propagates,
     * and if every lane failed the first error is rethrown rather than silently returning empty.
     */
    private fun nextmoeSearchIds(
        source: CoverSourceMode,
        base: String,
        keyword: String,
        limit: Int,
    ): List<String> {
        var firstError: Throwable? = null

        fun lane(block: () -> List<String>): List<String> =
            try {
                block()
            } catch (cancelled: java.util.concurrent.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (firstError == null) firstError = error
                emptyList()
            }
        val fromSearch =
            lane {
                objects(
                    get(
                        source,
                        base + "search",
                        mapOf(
                            "object" to "work",
                            "q" to keyword,
                            "limit" to limit.toString(),
                        ),
                    ).optJSONArray("items"),
                ).map { it.optString("id") }
            }
        val fromWorks =
            lane {
                objects(
                    get(
                        source,
                        base + "works",
                        mapOf(
                            "q" to keyword,
                            "limit" to limit.toString(),
                        ),
                    ).optJSONArray("items"),
                ).map { it.optString("id") }
            }
        val ids = mergeNextmoeIds(fromSearch, fromWorks)
        if (ids.isEmpty()) firstError?.let { throw it }
        return ids
    }

    /**
     * NextMoe trims every response to its identity core, so search hits carry **no** cover block:
     * returning them as-is yields a candidate list with no artwork at all. One extra `ids=` call
     * (up to 100 ids) hydrates `covers`/`refs`/`intros` for the whole page at once.
     *
     * Hydration failure degrades silently — a missing thumbnail is cosmetic and must not fail
     * the search that already succeeded.
     */
    private fun hydrateNextmoe(
        source: CoverSourceMode,
        base: String,
        ids: List<String>,
        parse: (JSONObject) -> VnMetadata,
    ): List<VnMetadata> {
        val hydrated =
            runCatching {
                objects(
                    get(
                        source,
                        base + "works",
                        mapOf(
                            "ids" to ids.joinToString(","),
                            "include" to NEXTMOE_INCLUDE,
                            "limit" to "100",
                        ),
                    ).optJSONArray("items"),
                )
            }.getOrDefault(emptyList())
        val byId = hydrated.associateBy { it.optString("id") }
        return ids.mapNotNull { id -> byId[id]?.let(parse) }
    }

    fun searchHikarinagi(
        keyword: String,
        limit: Int,
    ): List<VnMetadata> {
        val root =
            get(
                CoverSourceMode.HIKARINAGI,
                "https://api.hikarinagi.org/v3/search",
                mapOf("q" to keyword, "types" to "galgame", "page" to "1", "page_size" to limit.toString()),
            )
        val data = root.optJSONObject("data") ?: root
        return objects(data.optJSONArray("items")).filter { it.optString("type") == "galgame" }.mapNotNull { row ->
            val id = row.optString("id")
            require(id.matches(Regex("[0-9]+"))) { "资料 ID 无效" }
            val response = get(CoverSourceMode.HIKARINAGI, "https://api.hikarinagi.org/v3/galgames/$id", emptyMap())
            val detail = response.optJSONObject("data") ?: response
            if (!accounts.includesAdult() && detail.optBoolean("nsfw", true)) return@mapNotNull null
            parseHikarinagi(detail)
        }
    }

    private fun get(
        source: CoverSourceMode,
        endpoint: String,
        parameters: Map<String, String>,
    ): JSONObject {
        // A merged catalog id answers 404 ENTITY_MERGED + current_id. Replaying with the surviving
        // id is a repair, not a retry, so it does not consume the retry budget below.
        var merged = false
        var target = endpoint
        var refreshed = false
        var retried = false
        while (true) {
            val url =
                target.toHttpUrl().newBuilder().apply {
                    parameters.forEach { (name, value) -> addQueryParameter(name, value) }
                    if (source == CoverSourceMode.NEXTMOE) addQueryParameter("nsfw", accounts.includesAdult().toString())
                }.build()
            val remaining = (cooldown[source] ?: 0) - System.currentTimeMillis()
            if (remaining > 0) throw IOException("请求受限，请在 ${(remaining + 999) / 1000} 秒后重试")
            val token = accounts.accessToken(source)
            val response =
                try {
                    client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute()
                } catch (error: IOException) {
                    if (error is HttpClient.HttpFailure) throw error
                    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                    if (retried) throw error
                    retried = true
                    Thread.sleep(1000)
                    continue
                }
            response.use {
                val body = it.body.string()
                if (it.isSuccessful) return JSONObject(body)
                if (it.code == 404 && source == CoverSourceMode.NEXTMOE && !merged) {
                    val current = runCatching { JSONObject(body).optString("current_id") }.getOrNull()
                    if (!current.isNullOrBlank() && current.matches(Regex("[0-9]+"))) {
                        merged = true
                        target = endpoint.substringBeforeLast('/') + "/" + current
                        continue
                    }
                    throw IOException("该作品已在目录中合并，请重新搜索")
                }
                if (it.code == 401 && !refreshed) {
                    accounts.accessToken(source, forceRefresh = true, rejectedToken = token)
                    refreshed = true
                } else if (it.code == 401) {
                    accounts.markExpired(source)
                    throw IOException("${source.label} 授权已失效（可能已被撤销），请重新授权")
                } else if (it.code == 429) {
                    val now = System.currentTimeMillis()
                    val seconds = it.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 86400)
                    if (CoverRateLimitPolicy.isDailyQuota(body)) {
                        // Daily quota: the budget only resets at UTC midnight, so backing off and
                        // retrying would just burn the next attempt. Park the source until then.
                        cooldown[source] = now + CoverRateLimitPolicy.quotaCooldownMillis(now)
                        throw IOException("${source.label} 今日调用额度已用尽，请明天再试")
                    }
                    cooldown[source] = now + (seconds ?: 60) * 1000
                    throw IOException("请求受限，请在 ${seconds ?: 60} 秒后重试")
                } else if (it.code >= 500 && !retried) {
                    retried = true
                } else {
                    throw IOException(if (it.code == 403) "账号缺少资料读取权限，请重新授权" else "资料搜索失败（HTTP ${it.code}）")
                }
            }
            if (retried) Thread.sleep(1000)
        }
    }

    companion object {
        private val cooldown = ConcurrentHashMap<CoverSourceMode, Long>()

        /**
         * Every block a **list lane** accepts (`/v2/catalog/schemas/work` → `list_include`).
         * `titles` is the important one: it carries the localized names and aliases the cover
         * ranker needs, and without it NextMoe's Chinese titles never arrive. `companies`,
         * `ratings` and `tags` fill the card's developer/score/tag rows. A wrong token is
         * `400 UNKNOWN_INCLUDE`, so this must stay within the documented list vocabulary.
         */
        private const val NEXTMOE_INCLUDE = "titles,refs,intros,covers,companies,ratings,tags"

        /** Source-native rating scales, in display preference order. */
        private val NEXTMOE_RATING_SOURCES = listOf("bangumi", "vndb", "erogamescape", "dlsite", "howlongtobeat")
        private val NEXTMOE_RATING_SCALES =
            mapOf(
                "bangumi" to 10.0,
                "vndb" to 10.0,
                "dlsite" to 5.0,
                "erogamescape" to 100.0,
                "howlongtobeat" to 10.0,
            )
        private const val NEXTMOE_TAG_LIMIT = 8

        private val NUMERIC_ID = Regex("[0-9]+")

        /**
         * Merges the two NextMoe title lanes: `search` hits first, then `works?q=` hits, keeping
         * numeric ids only and preserving discovery order while de-duplicating.
         */
        @JvmStatic
        fun mergeNextmoeIds(
            fromSearch: List<String>,
            fromWorks: List<String>,
        ): List<String> = (fromSearch + fromWorks).filter { it.matches(NUMERIC_ID) }.distinct()

        internal fun objects(array: JSONArray?): List<JSONObject> =
            if (array == null) {
                emptyList()
            } else {
                (0 until array.length()).mapNotNull(array::optJSONObject)
            }

        fun parseHikarinagi(row: JSONObject): VnMetadata =
            VnMetadata().apply {
                fun text(key: String) = row.optString(key).takeUnless { it == "null" }.orEmpty()
                id = text("id")
                originalTitle = text("origin_title")
                romanTitle = text("en_title").ifBlank { originalTitle }
                chineseTitle = text("trans_title").ifBlank { originalTitle }
                description = text("trans_intro").ifBlank { text("origin_intro") }
                developer = text("developer")
                released = text("release_date").take(10)
                // covers[] arrives sorted by vote count, so the first usable entry is the best cover.
                // Absolute-ize before building MetadataCover: a relative url would be dropped outright
                // by its https:// filter and the candidate would show no artwork at all.
                covers +=
                    objects(row.optJSONArray("covers")).mapNotNull { cover ->
                        val url = HikarinagiImagePolicy.absolute(cover.optString("url"))
                        if (url.isEmpty()) {
                            null
                        } else {
                            MetadataCover(
                                url, cover.optInt("width"), cover.optInt("height"),
                                "Hikarinagi", cover.optString("sexual", "unknown"),
                            )
                        }
                    }
                covers.firstOrNull()?.let { cover ->
                    coverUrl = cover.url
                    fullCoverUrl = cover.url
                    coverSexual = sexualScore(cover.sexual)
                }
            }

        /**
         * Maps one NextMoe `work` row to a candidate.
         *
         * `titles[]` (include=titles) is the richest title source: it carries the Chinese name,
         * the original-language name and every alias, so the local ranker can match a folder name
         * in any of them. Identity-core `display_name`/`latin`/`localized` stay the fallback for
         * lanes that did not ask for `titles`. `companies`/`ratings`/`tags` fill the card rows.
         */
        fun parseNextmoe(row: JSONObject): VnMetadata {
            val displayName = row.optString("display_name")
            val identityLatin = row.optString("latin").takeUnless { it == "null" }.orEmpty()
            val localized = row.optJSONObject("localized")
            val titles = objects(row.optJSONArray("titles"))

            fun localizedValue(key: String): String = localized?.optJSONObject(key)?.optString("value").takeUnless { it == "null" }.orEmpty()

            // Prefer a human title over a machine translation when both share a language.
            fun titleFor(vararg langPrefixes: String): String {
                val matches =
                    titles.filter { title ->
                        title.optString("title").isNotBlank() &&
                            langPrefixes.any { title.optString("lang").startsWith(it, ignoreCase = true) }
                    }
                return (matches.firstOrNull { !it.optBoolean("is_machine", false) } ?: matches.firstOrNull())
                    ?.optString("title").orEmpty()
            }

            val original = titleFor("ja").ifBlank { displayName }
            val chinese =
                sequenceOf(
                    titleFor("zh-Hans"),
                    titleFor("zh-Hant"),
                    titleFor("zh"),
                    localizedValue("zh-Hans"),
                    localizedValue("zh-Hant"),
                    localizedValue("zh"),
                ).firstOrNull { it.isNotBlank() }.orEmpty().ifBlank { original }
            val roman =
                titles.firstOrNull { it.optString("latin").isNotBlank() && it.optString("latin") != "null" }
                    ?.optString("latin")?.takeUnless { it == "null" }?.ifBlank { identityLatin }
                    ?: identityLatin
            val chosen = setOf(original, chinese, roman)

            return VnMetadata().apply {
                id = row.optString("id")
                originalTitle = original
                chineseTitle = chinese
                romanTitle = roman.ifBlank { original }
                released = row.optString("release_date").takeUnless { it == "null" }.orEmpty()

                // Keep every upstream anchor, not just vndb/bangumi: the refs block is the
                // cross-source identity bridge the hybrid search relies on.
                externalRefs =
                    objects(row.optJSONArray("refs")).mapNotNull {
                        val source = it.optString("source")
                        val external = it.optString("external_id")
                        if (source.isNotBlank() && external.isNotBlank()) "$source:$external" else null
                    }.distinct().joinToString(",")

                // Every other known title becomes an alias the ranker may match against.
                val aliasSet = LinkedHashSet<String>()
                titles.forEach { title ->
                    title.optString("title").takeIf { it.isNotBlank() }?.let(aliasSet::add)
                    title.optString("latin").takeIf { it.isNotBlank() && it != "null" }?.let(aliasSet::add)
                }
                localized?.let { loc ->
                    loc.keys().forEach { key ->
                        loc.optJSONObject(key)?.optString("value")
                            ?.takeIf { it.isNotBlank() }?.let(aliasSet::add)
                    }
                }
                (aliasSet + displayName + identityLatin)
                    .filter { it.isNotBlank() && it != "null" && it !in chosen }
                    .forEach(titleAliases::add)

                val intros = objects(row.optJSONArray("intros"))
                description =
                    (intros.firstOrNull { it.optString("lang").startsWith("zh") } ?: intros.firstOrNull())
                        ?.let { it.optString("value") }.orEmpty()

                val companies = objects(row.optJSONArray("companies"))
                developer =
                    listOf("developer", "brand", "publisher", "circle").firstNotNullOfOrNull { role ->
                        companies.firstOrNull { it.optString("attribution_role") == role }
                            ?.optString("display_name")
                    }.orEmpty().ifBlank { companies.firstOrNull()?.optString("display_name").orEmpty() }

                val ratings = objects(row.optJSONArray("ratings"))
                val rating =
                    NEXTMOE_RATING_SOURCES.firstNotNullOfOrNull { source ->
                        ratings.firstOrNull { it.optString("source") == source }
                    } ?: ratings.firstOrNull()
                rating?.let { rowRating ->
                    val score = rowRating.optDouble("score", 0.0)
                    if (score > 0) {
                        val source = rowRating.optString("source")
                        val scale = NEXTMOE_RATING_SCALES[source] ?: 10.0
                        val votes = rowRating.optInt("vote_count", 0)
                        val label = source.replaceFirstChar { it.uppercase() }
                        ratingText = "评分：${formatScore(score)}/${formatScore(scale)}" +
                            (if (votes > 0) "（${votes}人）" else "") + " · $label"
                    }
                }

                tagsText =
                    objects(row.optJSONArray("tags"))
                        .mapNotNull { it.optString("display_name").takeIf(String::isNotBlank) }
                        .distinct()
                        .take(NEXTMOE_TAG_LIMIT)
                        .joinToString("  ")

                // covers[] needs an explicit include; the elected `cover`/`banner` slots are the
                // fallback and add the landscape variant the portrait-first sort would otherwise miss.
                val images =
                    objects(row.optJSONArray("covers")) +
                        listOfNotNull(row.optJSONObject("cover"), row.optJSONObject("banner"))
                covers +=
                    images.mapNotNull(MetadataCover::fromJson).distinctBy { it.url }.sortedWith(
                        compareByDescending<MetadataCover> { it.height > it.width && it.width > 0 }
                            .thenByDescending { it.width.toLong() * it.height },
                    )
                covers.firstOrNull()?.let { cover ->
                    coverUrl = cover.url
                    fullCoverUrl = cover.url
                    coverSexual = sexualScore(cover.sexual)
                }
            }
        }

        /** Prints `10.0` as `10` while keeping `6.8` as `6.8`. */
        private fun formatScore(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

        fun sexualScore(value: String): Double =
            when (value.lowercase()) {
                "explicit", "2" -> 2.0
                "suggestive", "1" -> 1.0
                else -> value.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 0.0
            }
    }
}
