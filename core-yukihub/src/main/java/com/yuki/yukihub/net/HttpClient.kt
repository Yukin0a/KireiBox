package com.yuki.yukihub.net

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HTTP 客户端公共工厂。
 *
 * 统一管理 OkHttpClient / Retrofit 实例的创建与配置，
 * 为项目中所有网络请求（WebDAV、VNDB、Bangumi、月幕 Gal、AI Review）提供一致的底层设施。
 *
 * - `object` + `@JvmStatic` 保留 Java 调用方原有的 `HttpClient.xxx()` 静态调用形式。
 * - `@Throws(IOException::class)` 保留字节码签名上的 checked exception 声明。
 */
object HttpClient {
    private const val USER_AGENT = "KireiBox (https://github.com/Yukin0a/KireiBox)"

    /** Raw response shape used by the original YukiHub metadata clients. */
    data class ResponseResult(
        @JvmField val code: Int,
        @JvmField val body: String,
    ) {
        fun isSuccessful(): Boolean = code in 200..299
    }

    private val rateLimits = java.util.concurrent.ConcurrentHashMap<String, Long>()

    class HttpFailure(val status: Int, message: String) : IOException(message)

    @JvmStatic fun checkRateLimit(host: String) {
        val remaining = (rateLimits[host] ?: 0L) - System.currentTimeMillis()
        if (remaining > 0) throw HttpFailure(429, "请求受限，请在 ${(remaining + 999) / 1000} 秒后重试")
    }

    @JvmStatic fun recordRateLimit(
        host: String,
        header: String?,
    ) {
        val now = System.currentTimeMillis()
        val until =
            header?.toLongOrNull()?.coerceIn(1, 86400)?.let { now + it * 1000 }
                ?: parseHttpDate(header).takeIf { it > now } ?: (now + 60_000)
        rateLimits.merge(host, until, ::maxOf)
    }

    private val HTTP_DATE_FORMATS =
        arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEEE, dd-MMM-yy HH:mm:ss z",
            "EEE MMM dd HH:mm:ss yyyy",
        )

    /**
     * Creates a builder for **metadata API** calls. The connect timeout is deliberately short:
     * a blocked or black-holed host (e.g. Bangumi on some networks) must fail fast so it cannot
     * stall a search, while still leaving room for a normal TLS handshake.
     */
    @JvmStatic
    fun okHttpClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)

    /** 创建带 User-Agent 的 OkHttpClient Builder。 */
    @JvmStatic
    fun defaultBuilder(): OkHttpClient.Builder =
        okHttpClientBuilder()
            .addInterceptor { chain ->
                MetadataRequests.track(chain.call()) {
                    val original = chain.request()
                    checkRateLimit(original.url.host)
                    val request =
                        original.newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build()
                    chain.proceed(request).also { response ->
                        if (response.code == 429) recordRateLimit(original.url.host, response.header("Retry-After"))
                    }
                }
            }

    /**
     * 基于 OkHttpClient 创建 Retrofit 实例。
     *
     * @param baseUrl 基础 URL，必须以 / 结尾
     * @param client  已配置好的 OkHttpClient
     */
    @JvmStatic
    fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .build()

    /** Execute a single request and return its body, preserving YukiHub's Java API. */
    @JvmStatic
    @Throws(IOException::class)
    fun executeForString(call: Call<ResponseBody>): String {
        val result = executeForStringWithCode(call)
        if (!result.isSuccessful()) {
            throw IOException("HTTP ${result.code}" + if (result.body.isBlank()) "" else ": ${result.body}")
        }
        return result.body
    }

    /** Execute a request without discarding non-2xx status/body information. */
    @JvmStatic
    @Throws(IOException::class)
    fun executeForStringWithCode(call: Call<ResponseBody>): ResponseResult {
        val response = call.execute()
        return try {
            val body =
                if (response.isSuccessful) {
                    response.body()?.string().orEmpty()
                } else {
                    response.errorBody()?.string().orEmpty()
                }
            ResponseResult(response.code(), body)
        } finally {
            closeQuietly(response)
        }
    }

    /**
     * 带重试的同步执行，返回 ResponseBody 字符串。
     * 对 429 / 5xx 自动重试，指数退避。
     *
     * @param callFactory 每次重试创建新 Call 的工厂
     * @param maxRetries  最大重试次数（不含首次）
     * @param baseDelayMs 首次重试延迟（ms），后续指数增长
     */
    @JvmStatic
    @Throws(IOException::class)
    fun executeWithRetry(
        callFactory: CallFactory,
        maxRetries: Int,
        baseDelayMs: Long,
    ): String {
        var lastError: IOException? = null
        val attempts = maxRetries.coerceIn(0, 1)
        for (attempt in 0..attempts) {
            if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            val response =
                try {
                    callFactory.create().execute()
                } catch (error: IOException) {
                    if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
                    if (error is HttpFailure) throw error
                    lastError = error
                    if (attempt == attempts) throw error
                    sleep(baseDelayMs)
                    continue
                }
            try {
                if (response.isSuccessful) return response.body()?.string().orEmpty()
                val code = response.code()
                if (code == 429) {
                    val seconds = response.headers()["Retry-After"]?.toLongOrNull()?.coerceAtLeast(1) ?: 60
                    throw HttpFailure(429, "请求受限，请在 $seconds 秒后重试")
                }
                if (code < 500 || attempt == attempts) throw HttpFailure(code, "搜索失败（HTTP $code）")
                lastError = IOException("服务暂时不可用（HTTP $code）")
            } finally {
                closeQuietly(response)
            }
            sleep(baseDelayMs)
        }
        throw lastError ?: IOException("请求失败")
    }

    /**
     * 解析 HTTP 日期头（如 Last-Modified、Date）。
     * 支持三种 RFC 7231 / RFC 850 / asctime 格式。
     *
     * @param dateStr 日期字符串
     * @return 毫秒时间戳，解析失败返回 0
     */
    @JvmStatic
    fun parseHttpDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0
        for (fmt in HTTP_DATE_FORMATS) {
            try {
                return SimpleDateFormat(fmt, Locale.US).parse(dateStr)?.time ?: 0
            } catch (_: Exception) {
            }
        }
        return 0
    }

    /** Call 工厂接口，用于重试场景下每次创建新的 Call。 */
    fun interface CallFactory {
        fun create(): Call<ResponseBody>
    }

    private fun closeQuietly(response: Response<out ResponseBody>?) {
        if (response == null) return
        try {
            val body = response.body()
            body?.close()
            val errBody = response.errorBody()
            if (errBody != null && errBody !== body) errBody.close()
        } catch (_: Exception) {
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(maxOf(0L, ms))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.util.concurrent.CancellationException("请求已取消")
        }
    }
}
