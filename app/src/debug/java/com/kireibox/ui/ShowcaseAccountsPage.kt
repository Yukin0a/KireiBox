package com.kireibox.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.KireiSpacing
import com.yuki.yukihub.auth.CoverAccountStatus
import com.yuki.yukihub.metadata.model.CoverSourceMode

/**
 * `accounts`: the real 封面来源账号 cards on fixture account states — Bangumi and NextMoe signed in
 * (NextMoe with a bundled image as its avatar), Hikarinagi signed in without an avatar. Only the UI
 * state is faked; no credential is stored, so the buttons do nothing useful here.
 */
@Composable
internal fun ShowcaseAccountsPage(
    activity: ComponentActivity,
    colors: KireiColors,
) {
    val avatar = ShowcaseFixtures.libraryGames(activity).firstNotNullOfOrNull { it.coverUri }.orEmpty()
    ShowcasePageHost(
        activity = activity,
        colors = colors,
        dockTab = null,
        transform = { s ->
            s.copy(
                coverAccounts =
                    mapOf(
                        CoverSourceMode.BANGUMI to CoverAccountStatus(connected = true, name = "Yukin0a", id = "297264"),
                        CoverSourceMode.NEXTMOE to CoverAccountStatus(connected = true, name = "Yukin0a123", id = "72acc252", avatarUrl = avatar),
                        CoverSourceMode.HIKARINAGI to CoverAccountStatus(connected = true, name = "yuki", id = "9"),
                    ),
            )
        },
    ) { env ->
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(env.safeDrawingPadding)
                .padding(KireiSpacing.md),
        ) {
            CoverAccountSettings(env.state, env.colors, env.viewModel)
        }
    }
}
