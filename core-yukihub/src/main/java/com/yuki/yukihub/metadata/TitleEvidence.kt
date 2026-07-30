package com.yuki.yukihub.metadata

enum class TitleEvidenceSource(val strong: Boolean) {
    FOLDER(true),
    CONFIG(true),
    XP3_FILE(false)
}

data class TitleEvidence(
    val value: String,
    val source: TitleEvidenceSource
)
