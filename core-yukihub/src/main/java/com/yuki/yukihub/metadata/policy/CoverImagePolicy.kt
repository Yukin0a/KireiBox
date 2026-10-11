package com.yuki.yukihub.metadata.policy

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Per-source HTTP headers and URL rewrites for cover **images** (not catalog APIs).
 *
 * Several hosts refuse anonymous or header-less image requests:
 * - VNDB serves a warning page unless the `vndb_img=1` cookie and a browser UA are present.
 * - Bangumi's `lain.bgm.tv` is effectively unreachable from mainland China and needs a mirror.
 *
 * Metadata API requests keep their own identity (see [com.yuki.yukihub.net.HttpClient]); this
 * policy applies only to downloading and previewing cover bytes.
 */
object CoverImagePolicy {
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    /** Bangumi site images are proxied verbatim: the full original URL is appended unencoded. */
    private const val BANGUMI_IMAGE_PROXY = "https://imagesp.yurari.moe/bangumi/"

    @JvmStatic
    fun referer(source: CoverSourceKey): String? =
        when (source) {
            CoverSourceKey.BANGUMI -> "https://bgm.tv/"
            CoverSourceKey.VNDB -> "https://vndb.org/"
            else -> null
        }

    @JvmStatic
    fun cookie(source: CoverSourceKey): String? =
        when (source) {
            CoverSourceKey.VNDB -> "vndb_img=1; vndb_samesite=1"
            else -> null
        }

    /**
     * Rewrites a Bangumi site image onto the mirror. Only `bgm.tv` and its subdomains qualify —
     * `lain.bangumi.pro` hosts its own working CDN, and every other source is reachable directly,
     * so proxying them would only add a hop. Already-proxied urls pass through untouched.
     */
    @JvmStatic
    fun proxyUrl(url: String): String {
        val value = url.trim()
        if (value.isEmpty() || value.startsWith(BANGUMI_IMAGE_PROXY)) return value
        if (!value.startsWith("http://") && !value.startsWith("https://")) return value
        val host = runCatching { value.toHttpUrl().host.lowercase() }.getOrNull() ?: return value
        val isBangumi = host == "bgm.tv" || host.endsWith(".bgm.tv")
        return if (isBangumi) BANGUMI_IMAGE_PROXY + value else value
    }

    /**
     * Applies the mirror rewrite plus the per-source headers to a cover image request.
     * Unknown sources (no referer/cookie) still receive the browser user agent.
     */
    @JvmStatic
    fun apply(
        request: Request,
        source: CoverSourceKey,
    ): Request {
        val url = proxyUrl(request.url.toString())
        val builder =
            request.newBuilder()
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        referer(source)?.let { builder.header("Referer", it) }
        cookie(source)?.let { builder.header("Cookie", it) }
        if (url != request.url.toString()) builder.url(url)
        return builder.build()
    }
}

/** Minimal source identity for image-host policies, decoupled from the metadata enum. */
enum class CoverSourceKey {
    BANGUMI,
    NEXTMOE,
    HIKARINAGI,
    VNDB,
    UNKNOWN,
    ;

    companion object {
        @JvmStatic
        fun from(key: String?): CoverSourceKey =
            when (key?.trim()?.lowercase()) {
                "bangumi", "bangumi_mirror" -> BANGUMI
                "nextmoe" -> NEXTMOE
                "hikarinagi" -> HIKARINAGI
                "vndb" -> VNDB
                else -> UNKNOWN
            }
    }
}
