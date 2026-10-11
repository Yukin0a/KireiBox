package com.yuki.yukihub.sync

import com.yuki.yukihub.auth.CoverAccounts
import com.yuki.yukihub.auth.CoverRateLimitPolicy
import com.yuki.yukihub.metadata.client.AccountCatalogClient
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.metadata.model.VnMetadata
import com.yuki.yukihub.net.HttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** NextMoe answered 429. [dailyQuota] means the per-day budget: nothing may be sent until [untilMillis]. */
class NextmoeLimitException(
    val dailyQuota: Boolean,
    val untilMillis: Long,
) : IOException(if (dailyQuota) "NextMoe 今日调用额度已用尽" else "NextMoe 请求过于频繁")

/** The signed-in account can no longer be used (refresh failed or was revoked); it is already marked for re-login. */
class NextmoeAuthException(message: String) : IOException(message)

/** One item of a `207` batch write, in request order. */
data class PlaytimeWriteResult(
    val workId: String,
    val ok: Boolean,
    val code: String = "",
    val currentId: String = "",
)

/** The NextMoe calls playtime sync makes; [NextmoeUserApi] in the app, a fake in tests. */
interface NextmoePlaytimeRemote {
    /** Remote minutes per work for up to 100 works; works never reported are absent. */
    fun playtimes(workIds: List<String>): Map<String, Int>

    /** Every work the user has time on (any device, any app), paged through to the end. */
    fun allPlaytimes(): Map<String, Int>

    /** Writes absolute minutes for up to 100 works. */
    fun write(items: List<Pair<String, Int>>): List<PlaytimeWriteResult>

    /** Removes this app's row on [workId] (other apps' rows stay). A row that never existed is fine. */
    fun delete(workId: String)

    /** Catalog works for up to 100 `source:external_id` refs, adult works included. */
    fun resolveRefs(refs: List<String>): List<VnMetadata>
}

/**
 * The user-data endpoints playtime sync needs (`/v2/me/playtimes`, plus the catalog `refs=` lane).
 * Only the user's own access token is sent, to api.nextmoe.dev only. A 401 refreshes once; a second
 * 401 marks the account for re-login. 429 is reported, never retried here.
 */
class NextmoeUserApi(
    private val accounts: CoverAccounts,
    private val base: String = "https://api.nextmoe.dev/v2/",
) : NextmoePlaytimeRemote {
    private val client = HttpClient.defaultBuilder().followRedirects(false).build()

    override fun playtimes(workIds: List<String>): Map<String, Int> {
        if (workIds.isEmpty()) return emptyMap()
        val json = send("me/playtimes", mapOf("work_ids" to workIds.joinToString(","), "nsfw" to "true"))
        return objects(json.optJSONArray("items"))
            .filter { it.optString("work_id").isNotBlank() }
            .associate { it.optString("work_id") to it.optInt("minutes") }
    }

    override fun allPlaytimes(): Map<String, Int> {
        val all = mutableMapOf<String, Int>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val query = mapOf("limit" to "100", "nsfw" to "true") + listOfNotNull(cursor?.let { "cursor" to it })
            val json = send("me/playtimes", query)
            objects(json.optJSONArray("items")).filter { it.optString("work_id").isNotBlank() }.forEach { all[it.optString("work_id")] = it.optInt("minutes") }
            // next_cursor is absent on the last page.
            cursor = json.optString("next_cursor").ifBlank { null } ?: return all
        }
        return all
    }

    override fun write(items: List<Pair<String, Int>>): List<PlaytimeWriteResult> {
        if (items.isEmpty()) return emptyList()
        val body =
            JSONObject().put(
                "items",
                JSONArray(items.map { (workId, minutes) -> JSONObject().put("work_id", workId).put("minutes", minutes) }),
            )
        val answers = objects(send("me/playtimes", emptyMap(), body.toString().toRequestBody(JSON)).optJSONArray("items"))
        return items.mapIndexed { index, (workId, _) -> parseWrite(workId, answers.getOrNull(index)) }
    }

    override fun delete(workId: String) {
        send("me/playtimes/$workId", emptyMap(), method = "DELETE", allowNotFound = true)
    }

    override fun resolveRefs(refs: List<String>): List<VnMetadata> {
        if (refs.isEmpty()) return emptyList()
        val json = send("catalog/works", mapOf("refs" to refs.joinToString(","), "include" to "titles,refs", "nsfw" to "true"))
        return objects(json.optJSONArray("items")).map { AccountCatalogClient.parseNextmoe(it) }
    }

    private fun send(
        path: String,
        query: Map<String, String>,
        body: RequestBody? = null,
        method: String = if (body == null) "GET" else "POST",
        allowNotFound: Boolean = false,
    ): JSONObject {
        val url = (base + path).toHttpUrl().newBuilder().apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        var token = accounts.accessToken(SOURCE)
        repeat(2) { attempt ->
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").method(method, body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful || (allowNotFound && response.code == 404) -> return text.ifBlank { "{}" }.let(::JSONObject)
                    response.code == 401 && attempt == 0 -> token = accounts.accessToken(SOURCE, forceRefresh = true, rejectedToken = token)
                    else -> throw failure(response.code, text, response.header("Retry-After"))
                }
            }
        }
        throw IOException("NextMoe 授权刷新后仍被拒绝")
    }

    /** A refused request: a second 401 also marks the account for re-login. */
    private fun failure(
        code: Int,
        body: String,
        retryAfter: String?,
    ): IOException =
        when (code) {
            401 -> {
                accounts.markExpired(SOURCE)
                NextmoeAuthException("NextMoe 授权已失效，请重新登录")
            }
            429 -> limit(body, retryAfter)
            else -> IOException("NextMoe 请求失败（HTTP $code）")
        }

    companion object {
        private val SOURCE = CoverSourceMode.NEXTMOE
        private val JSON = "application/json".toMediaType()
        private const val MAX_PAGES = 50

        fun limit(
            body: String?,
            retryAfter: String?,
            now: Long = System.currentTimeMillis(),
        ): NextmoeLimitException =
            if (CoverRateLimitPolicy.isDailyQuota(body)) {
                NextmoeLimitException(true, now + CoverRateLimitPolicy.quotaCooldownMillis(now))
            } else {
                NextmoeLimitException(false, now + (retryAfter?.toLongOrNull()?.coerceIn(1, 3600) ?: 60) * 1000)
            }

        /** A `207` item is either the written playtime (`status` 200) or a problem object. */
        fun parseWrite(
            workId: String,
            item: JSONObject?,
        ): PlaytimeWriteResult {
            if (item == null) return PlaytimeWriteResult(workId, false, "MISSING")
            val status = item.optInt("status", if (item.optString("object") == "playtime") 200 else 0)
            return if (status in 200..299) {
                PlaytimeWriteResult(workId, true)
            } else {
                PlaytimeWriteResult(workId, false, item.optString("code").ifBlank { "HTTP_$status" }, item.optString("current_id"))
            }
        }

        private fun objects(array: JSONArray?): List<JSONObject> = if (array == null) emptyList() else (0 until array.length()).mapNotNull(array::optJSONObject)
    }
}
