package com.kireibox.cover

import com.yuki.yukihub.model.Game
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCoverCommitPolicyTest {
    @Test
    fun automaticCommitRechecksLatestCoverState() {
        assertTrue(AutomaticCoverCommitPolicy.canCommit(game(bindingMode = "RETRY")))
        assertFalse(
            AutomaticCoverCommitPolicy.canCommit(
                game(coverUri = "file:///manual.png", bindingMode = "MANUAL"),
            ),
        )
        assertFalse(AutomaticCoverCommitPolicy.canCommit(game(bindingMode = "DISABLED")))
    }

    private fun game(coverUri: String? = null, bindingMode: String) = Game(
        id = 1,
        title = "Game",
        coverUri = coverUri,
        vndbBindingMode = bindingMode,
    )
}
