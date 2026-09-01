package com.yuki.yukihub.launcher

import java.util.Locale

enum class ArtemisVersion(
    val displayName: String,
    val emulatorPackage: String,
) {
    AUTO("自动", "internal.artemis"),
    V1("v1", "internal.artemis.v1"),
    V2("v2", "internal.artemis.compat"),
    V3("v3", "internal.artemis.compat.v2");

    companion object {
        fun fromStored(value: String?): ArtemisVersion {
            val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return entries.firstOrNull { it.emulatorPackage == normalized } ?: AUTO
        }
    }
}
