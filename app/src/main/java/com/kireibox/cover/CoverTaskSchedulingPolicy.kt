package com.kireibox.cover

import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game

internal object CoverTaskSchedulingPolicy {
    fun requests(
        games: List<Game>,
        source: CoverSourceMode
    ): List<CoverTaskRequest> =
        games.mapNotNull { game ->
            if (
                game.id <= 0 ||
                !game.coverUri.isNullOrBlank() ||
                !supportsAutomaticCover(game) ||
                game.vndbBindingMode !in ELIGIBLE_BINDING_MODES
            ) {
                null
            } else {
                CoverTaskRequest(
                    gameId = game.id,
                    title = game.title.orEmpty(),
                    source = source,
                    automaticSearch = true,
                )
            }
        }

    private val SUPPORTED_ENGINES =
        setOf(
            EngineType.KIRIKIRI,
            EngineType.ONS,
            EngineType.TYRANO,
            EngineType.ARTEMIS,
        )

    private fun supportsAutomaticCover(game: Game): Boolean = game.engine in SUPPORTED_ENGINES || ExternalShortcutIdentity.isExternalShortcut(game)

    private val ELIGIBLE_BINDING_MODES = setOf("", "NONE", "RETRY", "REVIEW")
}
