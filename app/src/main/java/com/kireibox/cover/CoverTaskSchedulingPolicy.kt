package com.kireibox.cover

import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game

internal object CoverTaskSchedulingPolicy {
    fun requests(games: List<Game>, source: CoverSourceMode): List<CoverTaskRequest> = games.mapNotNull { game ->
        if (
            game.id <= 0 ||
            !game.coverUri.isNullOrBlank() ||
            game.engine !in SUPPORTED_ENGINES ||
            game.vndbBindingMode !in ELIGIBLE_BINDING_MODES
        ) {
            null
        } else {
            CoverTaskRequest(gameId = game.id, title = game.title.orEmpty(), source = source)
        }
    }

    private val SUPPORTED_ENGINES = setOf(
        EngineType.KIRIKIRI,
        EngineType.ONS,
        EngineType.TYRANO,
        EngineType.ARTEMIS,
    )
    private val ELIGIBLE_BINDING_MODES = setOf("", "NONE")
}
