package com.yuki.yukihub.metadata

import org.json.JSONObject

/**
 * YukiHub's metadata source vocabulary. The enum is deliberately source-keyed
 * so SharedPreferences and per-game visible-source values remain compatible
 * with the original YukiHub implementation.
 */
enum class CoverSourceMode(val key: String, val label: String) {
    VNDB("vndb", "VNDB"),
    BANGUMI("bangumi", "Bangumi"),
    BANGUMI_MIRROR("bangumi_mirror", "Bangumi镜像"),
    YMGAL("ymgal", "月幕Gal"),
    HIKARINAGI("hikarinagi", "Hikarinagi");

    val isBangumiFamily: Boolean
        get() = this == BANGUMI || this == BANGUMI_MIRROR

    companion object {
        @JvmStatic
        fun fromStored(value: String?): CoverSourceMode {
            val raw = value?.trim().orEmpty()
            return entries.firstOrNull {
                it.key.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: VNDB
        }

        @JvmStatic
        fun fromKey(value: String?): CoverSourceMode = fromStored(value)
    }
}

/** Source identity stored alongside a selected cover asset. */
enum class CoverProviderId(val label: String) {
    VNDB("VNDB"),
    BANGUMI("Bangumi"),
    BANGUMI_MIRROR("Bangumi镜像"),
    YMGAL("月幕Gal"),
    HIKARINAGI("Hikarinagi")
}

/** Provider-neutral candidate persisted in the compatible legacy JSON shape. */
data class ProviderCoverCandidate(
    val provider: CoverProviderId,
    val sourceId: String,
    val canonicalVndbId: String = "",
    val imageUrl: String,
    val metadata: VnMetadata,
    val score: Double = 0.0,
    val resultRank: Int = 0
) {
    val identityKey: String
        get() = canonicalVndbId.takeIf(String::isNotBlank)
            ?: "${provider.name.lowercase()}:$sourceId"

    fun toJson(): JSONObject = JSONObject()
        .put("provider", provider.name)
        .put("sourceId", sourceId)
        .put("canonicalVndbId", canonicalVndbId)
        .put("imageUrl", imageUrl)
        .put("score", score.coerceIn(0.0, 100.0))
        .put("resultRank", resultRank)
        .put("metadata", metadata.toJson())

    companion object {
        @JvmStatic
        fun fromJson(json: JSONObject): ProviderCoverCandidate? {
            val metadata = json.optJSONObject("metadata")
                ?.toString()
                ?.let(VnMetadata::fromJson)
                ?: return null
            val provider = runCatching {
                CoverProviderId.valueOf(json.optString("provider", "VNDB"))
            }.getOrDefault(CoverProviderId.VNDB)
            val sourceId = json.optString("sourceId", metadata.id).ifBlank { metadata.id }
            val canonicalVndbId = json.optString(
                "canonicalVndbId",
                metadata.id.takeIf { it.matches(Regex("""v\\d+""")) }.orEmpty()
            )
            val imageUrl = json.optString("imageUrl", metadata.coverUrl)
            if (sourceId.isBlank() || imageUrl.isBlank()) return null
            return ProviderCoverCandidate(
                provider = provider,
                sourceId = sourceId,
                canonicalVndbId = canonicalVndbId,
                imageUrl = imageUrl,
                metadata = metadata,
                score = json.optDouble("score", 0.0).coerceIn(0.0, 100.0),
                resultRank = json.optInt("resultRank", 0)
            )
        }
    }
}

/** Result of one YukiHub source search: an optional unambiguous binding plus all choices. */
data class CoverSourceMatchResult(
    val automatic: ProviderCoverCandidate? = null,
    val ranked: List<ProviderCoverCandidate> = emptyList()
)
