package com.yuki.yukihub.metadata.model

/**
 * YukiHub's metadata source vocabulary. The enum is deliberately source-keyed
 * so SharedPreferences and per-game visible-source values remain compatible
 * with the original YukiHub implementation.
 */
enum class CoverSourceMode(val key: String, val label: String) {
    BANGUMI("bangumi", "Bangumi"),
    NEXTMOE("nextmoe", "NextMoe"),
    HIKARINAGI("hikarinagi", "Hikarinagi"),
    VNDB("vndb", "VNDB"),
    BANGUMI_MIRROR("bangumi_mirror", "Bangumi镜像"),
    YMGAL("ymgal", "月幕Gal"), // Historical cache identity only.
    ;

    val isBangumiFamily: Boolean
        get() = this == BANGUMI || this == BANGUMI_MIRROR

    val isActive: Boolean get() = this in active
    val requiresLogin: Boolean get() = this == NEXTMOE || this == HIKARINAGI

    companion object {
        val active = listOf(BANGUMI, NEXTMOE, HIKARINAGI, VNDB)

        fun selected(value: String?): CoverSourceMode = fromStored(value).takeIf { it.isActive } ?: BANGUMI

        @JvmStatic
        fun fromStored(value: String?): CoverSourceMode {
            val raw = value?.trim().orEmpty()
            return entries.firstOrNull {
                it.key.equals(raw, ignoreCase = true) ||
                    it.name.equals(raw, ignoreCase = true)
            } ?: BANGUMI
        }

        @JvmStatic
        fun fromKey(value: String?): CoverSourceMode = fromStored(value)
    }
}
