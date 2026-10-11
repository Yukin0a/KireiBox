package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import com.kireibox.designsystem.KireiColors

/** What every part of the cover picker reads: the search, the app state (accounts, adult filter) and its actions. */
internal class CoverPickerScope(
    val search: CoverSearchUi,
    val state: KireiUiState,
    val colors: KireiColors,
    val viewModel: KireiViewModel,
    val actions: KireiActions,
    /** Inside the tablet card the candidate cards sit one surface higher than the card. */
    val raised: Boolean = false,
) {
    val cardFill: Color get() = if (raised) colors.surfaceRaised else colors.surface
}
