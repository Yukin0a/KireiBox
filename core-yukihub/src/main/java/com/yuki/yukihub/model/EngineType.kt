package com.yuki.yukihub.model

enum class EngineType(val displayName: String) {
    KIRIKIRI("Kirikiri"),
    ONS("ONScripter"),
    TYRANO("Tyrano"),
    ARTEMIS("Artemis"),
    ANDROID_APP("Android 应用"),
    WINDOWS("PC"),
    UNKNOWN("Unknown"),
    ;

    companion object {
        @JvmStatic
        fun fromString(value: String?): EngineType {
            if (value == null) return UNKNOWN
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.displayName.equals(value, ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}
