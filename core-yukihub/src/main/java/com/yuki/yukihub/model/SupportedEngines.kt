package com.yuki.yukihub.model

/** The only engines exposed by KireiBox. */
object SupportedEngines {
    @JvmField
    val ALL: Set<EngineType> =
        setOf(
            EngineType.KIRIKIRI,
            EngineType.ONS,
            EngineType.TYRANO,
            EngineType.ARTEMIS,
            EngineType.ANDROID_APP,
            EngineType.WINDOWS,
        )

    @JvmStatic
    fun contains(engine: EngineType?): Boolean = engine != null && ALL.contains(engine)
}
