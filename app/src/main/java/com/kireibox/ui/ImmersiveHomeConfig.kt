package com.kireibox.ui

import androidx.compose.runtime.Composable
import com.kireibox.designsystem.KireiColors
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.SpotlightSelectionMode
import com.yuki.yukihub.settings.SpotlightSource
import com.yuki.yukihub.settings.SpotlightStrategy

// Immersive home configuration sheet ("编辑沉浸式展示图"): content source / strategy / count editor.

@Composable
internal fun ImmersiveGalleryConfigSheet(
    games: List<Game>,
    mode: SpotlightSelectionMode,
    manualIds: List<Long>,
    source: SpotlightSource,
    strategy: SpotlightStrategy,
    count: Int,
    colors: KireiColors,
    onDismiss: () -> Unit,
    onMode: (SpotlightSelectionMode) -> Unit,
    onManualIds: (List<Long>) -> Unit,
    onSource: (SpotlightSource) -> Unit,
    onStrategy: (SpotlightStrategy) -> Unit,
    onCount: (Int) -> Unit,
) {
    val current =
        HomeContentSettings(
            mode = mode,
            manualGameIds = manualIds,
            source = source,
            strategy = strategy,
            count = count,
        ).normalized()
    KireiActionSheet(
        "编辑沉浸式展示图",
        colors,
        onDismiss,
        expanded = true,
        scrollableContent = false,
    ) {
        HomeContentSettingsPanel(
            settings = current,
            games = games,
            colors = colors,
            onSettingsChange = { next ->
                val normalized = next.normalized()
                if (normalized.mode != current.mode) onMode(normalized.mode)
                if (normalized.manualGameIds != current.manualGameIds) onManualIds(normalized.manualGameIds)
                if (normalized.source != current.source) onSource(normalized.source)
                if (normalized.strategy != current.strategy) onStrategy(normalized.strategy)
                if (normalized.count != current.count) onCount(normalized.count)
            },
        )
    }
}
