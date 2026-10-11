package com.kireibox.cover

import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverTaskSchedulingPolicyTest {
    @Test
    fun schedulesOnlyEligibleSupportedGamesWithSourceInInputOrder() {
        val source = CoverSourceMode.HIKARINAGI
        val requests =
            CoverTaskSchedulingPolicy.requests(
                games =
                    listOf(
                        game(id = 20, title = "Second", engine = EngineType.ONS),
                        game(id = 10, title = "First", engine = EngineType.KIRIKIRI),
                        game(id = 30, title = "Has cover", engine = EngineType.TYRANO, coverUri = "file:///cover.jpg"),
                        game(id = 40, title = "Review", engine = EngineType.ARTEMIS, bindingMode = "REVIEW"),
                        game(id = 50, title = "Retry", engine = EngineType.KIRIKIRI, bindingMode = "RETRY"),
                        game(id = 60, title = "Disabled", engine = EngineType.ONS, bindingMode = "DISABLED"),
                        game(id = 0, title = "Unsaved", engine = EngineType.TYRANO),
                        game(id = -1, title = "Invalid", engine = EngineType.ARTEMIS),
                        game(id = 70, title = "Android", engine = EngineType.ANDROID_APP),
                        game(
                            id = 75,
                            title = "External shortcut",
                            engine = EngineType.ANDROID_APP,
                            rootUri = ExternalShortcutIdentity.rootUri(0, "com.example.game", "game-id"),
                        ),
                        game(
                            id = 76,
                            title = "Second external shortcut",
                            engine = EngineType.ANDROID_APP,
                            rootUri = ExternalShortcutIdentity.rootUri(0, "other.package", "shortcut"),
                        ),
                        game(id = 80, title = "Unknown", engine = EngineType.UNKNOWN),
                        game(id = 90, title = "Blank binding", engine = EngineType.KIRIKIRI, bindingMode = ""),
                        game(id = 100, title = "None binding", engine = EngineType.ARTEMIS, bindingMode = "NONE"),
                    ),
                source = source,
            )

        assertEquals(
            listOf(
                CoverTaskRequest(gameId = 20, title = "Second", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 10, title = "First", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 40, title = "Review", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 50, title = "Retry", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 75, title = "External shortcut", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 76, title = "Second external shortcut", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 90, title = "Blank binding", source = source, automaticSearch = true),
                CoverTaskRequest(gameId = 100, title = "None binding", source = source, automaticSearch = true),
            ),
            requests,
        )
    }

    private fun game(
        id: Long,
        title: String,
        engine: EngineType,
        coverUri: String? = null,
        bindingMode: String = "NONE",
        rootUri: String? = null,
    ) = Game(
        id = id,
        title = title,
        engine = engine,
        coverUri = coverUri,
        vndbBindingMode = bindingMode,
        rootUri = rootUri,
    )
}
