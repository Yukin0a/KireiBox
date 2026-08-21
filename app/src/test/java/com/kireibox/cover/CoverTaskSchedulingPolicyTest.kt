package com.kireibox.cover

import com.yuki.yukihub.metadata.CoverSourceMode
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverTaskSchedulingPolicyTest {
    @Test
    fun schedulesOnlyEligibleSupportedGamesWithSourceInInputOrder() {
        val source = CoverSourceMode.HIKARINAGI
        val requests = CoverTaskSchedulingPolicy.requests(
            games = listOf(
                game(id = 20, title = "Second", engine = EngineType.ONS),
                game(id = 10, title = "First", engine = EngineType.KIRIKIRI),
                game(id = 30, title = "Has cover", engine = EngineType.TYRANO, coverUri = "file:///cover.jpg"),
                game(id = 40, title = "Review", engine = EngineType.ARTEMIS, bindingMode = "REVIEW"),
                game(id = 50, title = "Retry", engine = EngineType.KIRIKIRI, bindingMode = "RETRY"),
                game(id = 60, title = "Disabled", engine = EngineType.ONS, bindingMode = "DISABLED"),
                game(id = 0, title = "Unsaved", engine = EngineType.TYRANO),
                game(id = -1, title = "Invalid", engine = EngineType.ARTEMIS),
                game(id = 70, title = "Android", engine = EngineType.ANDROID_APP),
                game(id = 80, title = "Unknown", engine = EngineType.UNKNOWN),
                game(id = 90, title = "Blank binding", engine = EngineType.KIRIKIRI, bindingMode = ""),
                game(id = 100, title = "None binding", engine = EngineType.ARTEMIS, bindingMode = "NONE"),
            ),
            source = source,
        )

        assertEquals(
            listOf(
                CoverTaskRequest(gameId = 20, title = "Second", source = source),
                CoverTaskRequest(gameId = 10, title = "First", source = source),
                CoverTaskRequest(gameId = 90, title = "Blank binding", source = source),
                CoverTaskRequest(gameId = 100, title = "None binding", source = source),
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
    ) = Game(
        id = id,
        title = title,
        engine = engine,
        coverUri = coverUri,
        vndbBindingMode = bindingMode,
    )
}
