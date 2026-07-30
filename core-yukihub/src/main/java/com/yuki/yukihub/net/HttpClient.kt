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

    private const val USER_AGENT = "KireiBox/0.5.0-alpha.1"

    /** Raw response shape used by the original YukiHub metadata clients. */
    data class ResponseResult(
        @JvmField val code: Int,
        @JvmField val body: String
    ) {
        fun isSuccessful(): Boolean = code in 200..299
    }

    private val HTTP_DATE_FORMATS = arrayOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEEE, dd-MMM-yy HH:mm:ss z",
        "EEE MMM dd HH:mm:ss yyyy"
    )

    /**
     * 创建一个通用的 OkHttpClient Builder，预设了合理的超时和 User-Agent。
     * 调用方可在此基础上追加 Interceptor（如 Auth）或修改超时。
     */
    @JvmStatic
    fun okHttpClientBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(true)
            .followSslRedirects(true)

    /** 创建带 User-Agent 的 OkHttpClient Builder。 */
    @JvmStatic
    fun defaultBuilder(): OkHttpClient.Builder =
        okHttpClientBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }

    /**
     * 基于 OkHttpClient 创建 Retrofit 实例。
     *
     * @param baseUrl 基础 URL，必须以 / 结尾
     * @param client  已配置好的 OkHttpClient
     */
    @JvmStatic
    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
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
            val body = if (response.isSuccessful) {
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
    fun executeWithRetry(callFactory: CallFactory, maxRetries: Int, baseDelayMs: Long): String {
        var lastError: IOException? = null
        for (attempt in 0..maxRetries) {
            var response: Response<ResponseBody>? = null
            try {
                response = callFactory.create().execute()
                if (response.isSuccessful) {
                    return response.body()?.string() ?: ""
                }
                val code = response.code()
                val err = response.errorBody()?.string() ?: ""
                val msg = "HTTP $code" + if (err.isEmpty()) "" else ": $err"

                if ((code == 429 || code >= 500) && attempt < maxRetries) {
                    lastError = IOException(msg)
                    closeQuietly(response)
                    response = null
                    sleep(baseDelayMs * (attempt + 1))
                    continue
                }
                throw IOException(msg)
            } catch (e: IOException) {
                if (attempt < maxRetries) {
                    lastError = e
                    closeQuietly(response)
                    response = null
                    sleep(baseDelayMs * (attempt + 1))
                    continue
                }
                throw e
            } finally {
                closeQuietly(response)
            }
        }
        throw lastError ?: IOException("unreachable")
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
            } catch (_: Exception) { }
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
        } catch (_: Exception) { }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(maxOf(0L, ms))
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
