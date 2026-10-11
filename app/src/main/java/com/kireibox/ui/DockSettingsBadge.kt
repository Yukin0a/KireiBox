package com.kireibox.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kireibox.designsystem.KireiColors
import com.kireibox.designsystem.components.KireiBadgeDefaults
import com.kireibox.designsystem.components.KireiBadgeTone
import com.kireibox.designsystem.components.KireiDotBadge

/**
 * What a Dock tab carries next to its icon (r5 · B, spec §10「r5 · B」). Only the 设置 tab has a
 * badge: the cover-review count, or the one-time 快速设置 dot.
 */
internal sealed interface DockTabBadge {
    data object None : DockTabBadge

    /** Covers waiting for the user's confirmation (the existing review badge). */
    data class Count(val count: Int) : DockTabBadge

    /** 快速设置 is neither finished nor dismissed: a small accent dot, no number. */
    data object Suggestion : DockTabBadge
}

/**
 * The 设置 tab's badge. The dot follows the same flag as the settings root suggestion card
 * (`kireibox_quick_setup_done`, [com.yuki.yukihub.settings.YukiSettingsStore.Snapshot.quickSetupDone]):
 * it shows while the flag is false and disappears as soon as 快速设置 is finished, skipped, swiped
 * away or its suggestion card is closed. Settings that are not loaded yet (`null`) show no dot, so
 * it never flashes for a user who already finished. A pending cover review or a failed cover task
 * wins: one badge at a time.
 */
internal object DockSettingsBadgePolicy {
    /** Read after the tab name (「设置，有新的设置建议」) while the dot shows. */
    const val SUGGESTION_DESCRIPTION = "有新的设置建议"

    fun resolve(
        attentionCount: Int,
        quickSetupDone: Boolean?,
    ): DockTabBadge =
        when {
            attentionCount > 0 -> DockTabBadge.Count(attentionCount)
            quickSetupDone == false -> DockTabBadge.Suggestion
            else -> DockTabBadge.None
        }

    /** The tab's accessible name; null keeps the visible label as the name. */
    fun description(
        label: String,
        badge: DockTabBadge,
    ): String? = if (badge == DockTabBadge.Suggestion) "$label，$SUGGESTION_DESCRIPTION" else null
}

/**
 * The 快速设置 dot on a Dock icon (spec badge tokens: [KireiBadgeDefaults.DotSize], accent tone). Its
 * center sits on the icon box's top-end corner, outside the round 设置 glyph, so it reads as its own
 * mark even when the selected tab tints the icon with the same accent.
 */
@Composable
internal fun DockSuggestionDot(
    colors: KireiColors,
    modifier: Modifier = Modifier,
) {
    val half = KireiBadgeDefaults.DotSize / 2
    KireiDotBadge(colors, modifier.offset(x = half, y = -half), tone = KireiBadgeTone.ACCENT)
}
