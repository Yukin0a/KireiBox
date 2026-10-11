package com.yuki.yukihub.auth

import android.content.Context
import com.kireibox.core.yukihub.BuildConfig
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.net.HttpClient
import com.yuki.yukihub.net.MetadataRequests
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class CoverAccountStatus(
    val connected: Boolean = false,
    val name: String = "",
    val needsReauth: Boolean = false,
    /** The account's own id/handle on the source, shown once signed in. */
    val id: String = "",
    /** The account's avatar URL on the source, shown once signed in (empty = fall back to the source tile). */
    val avatarUrl: String = "",
)

/**
 * NextMoe publishes two independent limit windows: a per-minute rate and a per-day quota.
 * `RATE_LIMITED` may be retried after `Retry-After`; `QUOTA_EXCEEDED` must not, because the
 * budget only resets at UTC midnight — backing off would just burn the next try too.
 */
object CoverRateLimitPolicy {
    private const val DAY_MILLIS = 86_400_000L

    /** Milliseconds from [nowMillis] until the next UTC midnight. */
    @JvmStatic
    fun quotaCooldownMillis(nowMillis: Long): Long = DAY_MILLIS - (nowMillis % DAY_MILLIS)

    /** True when the 429 body names the daily-quota code rather than the minute-window one. */
    @JvmStatic
    fun isDailyQuota(body: String?): Boolean = body?.contains("QUOTA_EXCEEDED") == true
}

object CoverOAuthConfig {
    fun clientId(source: CoverSourceMode): String =
        when (source) {
            CoverSourceMode.NEXTMOE -> BuildConfig.NEXTMOE_CLIENT_ID
            CoverSourceMode.HIKARINAGI -> BuildConfig.HIKARINAGI_CLIENT_ID
            else -> ""
        }.trim()

    fun authorizeUrl(source: CoverSourceMode) =
        when (source) {
            CoverSourceMode.NEXTMOE -> "https://account.nextmoe.com/api/v1/oauth/authorize"
            CoverSourceMode.HIKARINAGI -> "https://id.hikarinagi.org/oidc/auth"
            else -> error("来源不支持授权登录")
        }

    fun tokenUrl(source: CoverSourceMode) =
        when (source) {
            CoverSourceMode.NEXTMOE -> "https://account.nextmoe.com/api/v1/oauth/token"
            CoverSourceMode.HIKARINAGI -> "https://id.hikarinagi.org/oidc/token"
            else -> error("来源不支持授权登录")
        }

    /**
     * Where the display name comes from when the id_token has none. Hikarinagi is deliberately
     * absent: its open-API access token is audience-bound, and its userinfo endpoint answers 401 to it.
     */
    fun userinfoUrl(source: CoverSourceMode): String? = if (source == CoverSourceMode.NEXTMOE) "https://account.nextmoe.com/api/v1/oauth/userinfo" else null
}

/** One credential authority per process. No token is exposed through UI state or exports. */
class CoverAccounts private constructor(context: Context) {
    private val secure = SecureCoverTokens(context)
    private val settings = context.getSharedPreferences("yukihub_prefs", Context.MODE_PRIVATE)
    private val client = HttpClient.defaultBuilder().followRedirects(false).build()
    private val locks = CoverSourceMode.active.associateWith { Any() }
    private val generations = CoverSourceMode.active.associateWith { AtomicLong() }
    private val access = ConcurrentHashMap<CoverSourceMode, Pair<String, Long>>()
    private val mutableStatus = MutableStateFlow<Map<CoverSourceMode, CoverAccountStatus>>(emptyMap())
    val status = mutableStatus.asStateFlow()

    init {
        refreshStatus()
    }

    fun generation(source: CoverSourceMode) = generations.getValue(source).get()

    /**
     * Whether adult works are searched. Defaults to **on**: most galgames are adult-only and every
     * source hides them while this is off, so the default-off behaviour made the cover picker look
     * broken (e.g. Riddle Joker returned nothing). NSFW covers are still blurred by default.
     */
    fun includesAdult() = settings.getBoolean("cover_include_adult", true)

    fun setIncludesAdult(value: Boolean) {
        settings.edit().putBoolean("cover_include_adult", value).apply()
    }

    fun canSearch(source: CoverSourceMode): Boolean =
        !source.requiresLogin ||
            (status.value[source]?.let { it.connected && !it.needsReauth } == true)

    @Synchronized private fun publish(
        source: CoverSourceMode,
        value: CoverAccountStatus,
    ) {
        mutableStatus.value = mutableStatus.value + (source to value)
    }

    private fun refreshStatus() {
        CoverSourceMode.active.filter { it != CoverSourceMode.VNDB }.forEach { source ->
            try {
                val record = secure.read(source.key)?.let(::JSONObject)
                publish(
                    source,
                    CoverAccountStatus(
                        connected = record != null,
                        name = record?.optString("name").orEmpty(),
                        needsReauth = record?.optBoolean("reauth") == true,
                        id = record?.optString("id").orEmpty(),
                        avatarUrl = record?.optString("avatar").orEmpty(),
                    ),
                )
            } catch (_: Exception) {
                publish(source, CoverAccountStatus(needsReauth = true))
            }
        }
    }

    fun connectBangumi(token: String) {
        val epoch = generation(CoverSourceMode.BANGUMI)
        require(token.isNotBlank()) { "请填写 Bangumi 个人 Token" }
        val request =
            Request.Builder().url("https://api.bgm.tv/v0/me")
                .header("Authorization", "Bearer ${token.trim()}").build()
        val me =
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw IOException("Token 无效或已过期，请重新生成")
                if (!response.isSuccessful) throw IOException("账号验证失败（HTTP ${response.code}）")
                JSONObject(response.body.string())
            }
        synchronized(locks.getValue(CoverSourceMode.BANGUMI)) {
            MetadataRequests.checkActive()
            checkGeneration(CoverSourceMode.BANGUMI, epoch)
            val name = me.optString("nickname").ifBlank { me.optString("username", "Bangumi 用户") }
            val id = me.optLong("id", 0L).takeIf { it > 0L }?.toString().orEmpty()
            val avatar =
                me.optJSONObject("avatar")?.let { avatar ->
                    avatar.optString("large").ifBlank { avatar.optString("medium") }.ifBlank { avatar.optString("small") }
                }.orEmpty()
            secure.write(
                "bangumi",
                JSONObject().put("token", token.trim()).put("name", name).put("id", id).put("avatar", avatar).toString(),
            )
            access.remove(CoverSourceMode.BANGUMI)
            settings.edit().remove("bangumi_token").apply()
            publish(CoverSourceMode.BANGUMI, CoverAccountStatus(true, name, id = id, avatarUrl = avatar))
        }
    }

    /** Encrypt legacy storage offline; revalidation is explicit and never delays the library. */
    fun migrateBangumi(): Unit =
        synchronized(locks.getValue(CoverSourceMode.BANGUMI)) {
            val legacy = settings.getString("bangumi_token", "").orEmpty()
            if (legacy.isBlank()) return@synchronized
            if (secure.read("bangumi") == null) {
                secure.write("bangumi", JSONObject().put("token", legacy).put("name", "待验证的 Bangumi 账号").put("reauth", true).toString())
                settings.edit().remove("bangumi_token").commit()
                refreshStatus()
            } else {
                settings.edit().remove("bangumi_token").commit()
            }
        }

    // Identity (profile/id/avatar) travels with a fresh login; a refresh passes blanks. Keeping it
    // as parameters mirrors the OAuth response shape and callers, so the count is intentional.
    @Suppress("LongParameterList")
    fun saveOAuth(
        source: CoverSourceMode,
        token: String,
        refresh: String,
        expiresIn: Long,
        epoch: Long,
        profile: String = "",
        accountId: String = "",
        avatarUrl: String = "",
    ) {
        require(source.requiresLogin && token.isNotBlank() && refresh.isNotBlank()) { "授权响应缺少令牌，请重新登录" }
        synchronized(locks.getValue(source)) {
            checkGeneration(source, epoch)
            // A token refresh preserves the identity/avatar already stored; only a fresh login passes them.
            val existing = runCatching { secure.read(source.key)?.let(::JSONObject) }.getOrNull()
            val current = status.value[source]
            val stored =
                existing?.let { CoverProfile(it.optString("name"), it.optString("id"), it.optString("avatar")) }
                    ?: CoverProfile(current?.name.orEmpty(), current?.id.orEmpty(), current?.avatarUrl.orEmpty())
            val identity = CoverProfileClaims.merge(stored, CoverProfile(profile, accountId, avatarUrl))
            val name = identity.name.ifBlank { "${source.label} 已授权" }
            val id = identity.accountId
            val avatar = identity.avatarUrl
            secure.write(
                source.key,
                JSONObject().put("refresh", refresh).put("name", name).put("id", id).put("avatar", avatar).toString(),
            )
            access[source] = token to (System.currentTimeMillis() + expiresIn.coerceAtLeast(1) * 1000)
            publish(source, CoverAccountStatus(true, name, id = id, avatarUrl = avatar))
        }
    }

    fun accessToken(
        source: CoverSourceMode,
        forceRefresh: Boolean = false,
        rejectedToken: String? = null,
    ): String =
        synchronized(locks.getValue(source)) {
            val epoch = generation(source)
            val record =
                try {
                    secure.read(source.key)?.let(::JSONObject)
                } catch (_: Exception) {
                    throw IOException("账号安全存储不可用，请重新连接")
                }
            if (source == CoverSourceMode.BANGUMI) {
                if (record?.optBoolean("reauth") == true) throw IOException("Bangumi 授权已失效，请重新连接或断开后匿名搜索")
                return@synchronized record?.optString("token").orEmpty()
            }
            if (record == null || record.optBoolean("reauth")) throw IOException("请登录自己的 ${source.label} 账号")
            access[source]?.takeIf {
                (!forceRefresh || (rejectedToken != null && it.first != rejectedToken)) && it.second > System.currentTimeMillis() + 60_000
            }?.let { return@synchronized it.first }
            val id = CoverOAuthConfig.clientId(source)
            check(id.isNotBlank()) { "此测试包尚未配置 ${source.label} 客户端，暂不能登录" }
            val body =
                FormBody.Builder().add("grant_type", "refresh_token").add("client_id", id)
                    .add("refresh_token", record.getString("refresh")).build()
            client.newCall(Request.Builder().url(CoverOAuthConfig.tokenUrl(source)).post(body).build()).execute().use { response ->
                val json = runCatching { JSONObject(response.body.string()) }.getOrDefault(JSONObject())
                checkGeneration(source, epoch)
                // 401 can mean either an expired token or a user who revoked the grant in their
                // account centre (Hikarinagi documents both as refresh failures). Both are handled
                // the same way: drop the credential and ask for a fresh authorization — never retry.
                if (response.code == 401 || json.optString("error") == "invalid_grant") {
                    markExpired(source)
                    throw IOException("${source.label} 授权已失效（可能已被撤销），请重新授权")
                }
                if (!response.isSuccessful) throw IOException("暂时无法续期（HTTP ${response.code}），请稍后重试")
                val token = json.optString("access_token")
                val refresh =
                    json.optString("refresh_token").ifBlank {
                        if (source == CoverSourceMode.HIKARINAGI) record.getString("refresh") else ""
                    }
                saveOAuth(source, token, refresh, json.optLong("expires_in", 900), epoch)
                token
            }
        }

    /** True when the account is signed in but still shows a stand-in name that its userinfo endpoint can replace. */
    fun needsProfile(source: CoverSourceMode): Boolean {
        val current = status.value[source] ?: return false
        return CoverOAuthConfig.userinfoUrl(source) != null &&
            current.connected && !current.needsReauth &&
            CoverProfileClaims.isPlaceholder(source, current.name, current.id)
    }

    /** Fetches the display name and avatar from the userinfo endpoint. Blocking: call off the main thread. */
    fun refreshProfile(source: CoverSourceMode) {
        val url = CoverOAuthConfig.userinfoUrl(source) ?: return
        val epoch = generation(source)
        val token = accessToken(source)
        val profile =
            client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("账号资料获取失败（HTTP ${response.code}）")
                CoverProfileClaims.fromJson(source, JSONObject(response.body.string()))
            }
        if (profile.name.isNotBlank()) storeProfile(source, profile, epoch)
    }

    private fun storeProfile(
        source: CoverSourceMode,
        profile: CoverProfile,
        epoch: Long,
    ) = synchronized(locks.getValue(source)) {
        checkGeneration(source, epoch)
        val record = secure.read(source.key)?.let(::JSONObject) ?: return@synchronized
        val id = profile.accountId.ifBlank { record.optString("id") }
        val avatar = profile.avatarUrl.ifBlank { record.optString("avatar") }
        secure.write(source.key, record.put("name", profile.name).put("id", id).put("avatar", avatar).toString())
        publish(source, CoverAccountStatus(true, profile.name, record.optBoolean("reauth"), id, avatar))
    }

    fun markExpired(source: CoverSourceMode) =
        synchronized(locks.getValue(source)) {
            access.remove(source)
            secure.read(source.key)?.let { raw -> secure.write(source.key, JSONObject(raw).put("reauth", true).toString()) }
            publish(source, (status.value[source] ?: CoverAccountStatus()).copy(needsReauth = true))
        }

    fun disconnect(source: CoverSourceMode) {
        generations.getValue(source).incrementAndGet()
        MetadataRequests.cancel(source)
        synchronized(locks.getValue(source)) {
            secure.remove(source.key)
            access.remove(source)
            if (source == CoverSourceMode.BANGUMI) settings.edit().remove("bangumi_token").apply()
            publish(source, CoverAccountStatus())
        }
    }

    private fun checkGeneration(
        source: CoverSourceMode,
        epoch: Long,
    ) {
        if (generation(source) != epoch) throw CancellationException("账号操作已取消")
    }

    companion object {
        @Volatile private var instance: CoverAccounts? = null

        fun get(context: Context): CoverAccounts =
            instance ?: synchronized(this) {
                instance ?: CoverAccounts(context.applicationContext).also { instance = it }
            }
    }
}
