package com.yuki.yukihub.metadata.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * VNDB 元数据快照，对应 SQLite 表 `vn_metadata` 的一行。
 *
 * - `@JvmField var` 暴露 16 个可变字段（保留 Java 直接字段访问）。
 * - `@JvmField val screenshotUrls` 暴露 final 列表字段（与原 Java `final List` 一致），
 *   Java 调用方可继续使用 `m.screenshotUrls.add(...)`。
 * - `@JvmOverloads` 让 Java 调用方继续使用 `new VnMetadata()` 无参构造。
 * - `toJson()` / `fromJson()` 复刻原 Java 实现，保持 JSON 序列化格式字节级兼容。
 */
data class VnMetadata
    @JvmOverloads
    constructor(
        @JvmField var id: String = "",
        @JvmField var externalRefs: String = "",
        @JvmField val covers: MutableList<MetadataCover> = ArrayList(),
        @JvmField var chineseTitle: String = "",
        @JvmField var originalTitle: String = "",
        @JvmField var romanTitle: String = "",
        @JvmField var coverUrl: String = "",
        @JvmField var thumbnailUrl: String = "",
        @JvmField var fullCoverUrl: String = "",
        @JvmField var description: String = "",
        @JvmField var translatedDescription: String = "",
        @JvmField var released: String = "",
        @JvmField var developer: String = "",
        @JvmField var tagsText: String = "",
        @JvmField var ratingText: String = "",
        @JvmField var lengthText: String = "",
        @JvmField var lengthMinutes: Int = 0,
        @JvmField var lengthVotes: Int = 0,
        @JvmField var coverSexual: Double = 0.0,
        @JvmField var coverViolence: Double = 0.0,
        @JvmField val titleAliases: MutableList<String> = ArrayList(),
        @JvmField val screenshotUrls: MutableList<String> = ArrayList(),
    ) {
        fun toJson(): JSONObject {
            val o = JSONObject()
            try {
                o.put("id", id)
                o.put("externalRefs", externalRefs)
                o.put("covers", JSONArray().apply { covers.forEach { put(it.toJson()) } })
                o.put("chineseTitle", chineseTitle)
                o.put("originalTitle", originalTitle)
                o.put("romanTitle", romanTitle)
                o.put("coverUrl", coverUrl)
                o.put("thumbnailUrl", thumbnailUrl)
                o.put("fullCoverUrl", fullCoverUrl)
                o.put("description", description)
                o.put("translatedDescription", translatedDescription)
                o.put("released", released)
                o.put("developer", developer)
                o.put("tagsText", tagsText)
                o.put("ratingText", ratingText)
                o.put("lengthText", lengthText)
                o.put("lengthMinutes", lengthMinutes)
                o.put("lengthVotes", lengthVotes)
                o.put("coverSexual", coverSexual)
                o.put("coverViolence", coverViolence)
                val aliases = JSONArray()
                for (title in titleAliases) aliases.put(title)
                o.put("titleAliases", aliases)
                val arr = JSONArray()
                for (s in screenshotUrls) arr.put(s)
                o.put("screenshotUrls", arr)
            } catch (ignored: Exception) {
            }
            return o
        }

        companion object {
            @JvmStatic
            fun fromJson(json: String?): VnMetadata? {
                if (json == null || json.trim().isEmpty()) return null
                return try {
                    val o = JSONObject(json)
                    val m = VnMetadata()
                    m.id = o.optString("id", "")
                    m.externalRefs = o.optString("externalRefs", "")
                    o.optJSONArray("covers")?.let { rows ->
                        for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { MetadataCover.fromJson(it) }?.let(m.covers::add)
                    }
                    m.chineseTitle = o.optString("chineseTitle", "")
                    m.originalTitle = o.optString("originalTitle", "")
                    m.romanTitle = o.optString("romanTitle", "")
                    m.coverUrl = o.optString("coverUrl", "")
                    m.thumbnailUrl = o.optString("thumbnailUrl", "")
                    m.fullCoverUrl = o.optString("fullCoverUrl", m.coverUrl)
                    m.description = o.optString("description", "")
                    m.translatedDescription = o.optString("translatedDescription", "")
                    m.released = o.optString("released", "")
                    m.developer = o.optString("developer", "")
                    m.tagsText = o.optString("tagsText", "")
                    m.ratingText = o.optString("ratingText", "")
                    m.lengthText = o.optString("lengthText", "")
                    m.lengthMinutes = o.optInt("lengthMinutes", 0)
                    m.lengthVotes = o.optInt("lengthVotes", 0)
                    m.coverSexual = o.optDouble("coverSexual", 0.0)
                    m.coverViolence = o.optDouble("coverViolence", 0.0)
                    o.optJSONArray("titleAliases")?.let { aliases ->
                        for (i in 0 until aliases.length()) {
                            aliases.optString(i).takeIf(String::isNotBlank)?.let(m.titleAliases::add)
                        }
                    }
                    val arr = o.optJSONArray("screenshotUrls")
                    if (arr != null) for (i in 0 until arr.length()) m.screenshotUrls.add(arr.optString(i, ""))
                    m
                } catch (ignored: Exception) {
                    null
                }
            }
        }
    }

data class MetadataCover(val url: String, val width: Int = 0, val height: Int = 0, val source: String = "", val sexual: String = "unknown") {
    fun toJson(): JSONObject =
        JSONObject().put(
            "url",
            url,
        ).put("width", width).put("height", height).put("source", source).put("sexual", sexual)

    companion object {
        fun fromJson(o: JSONObject): MetadataCover? =
            o.optString("url").takeIf { it.startsWith("https://") }?.let {
                MetadataCover(it, o.optInt("width"), o.optInt("height"), o.optString("source"), o.optString("sexual", "unknown"))
            }
    }
}
