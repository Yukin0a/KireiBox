package com.kireibox.ui

internal data class CoverTaskBadgePresentation(
    val visible: Boolean,
    val label: String,
    val accessibilityCount: Int,
    val heightDp: Float,
    val minWidthDp: Float,
    val horizontalPaddingDp: Float,
)

internal object CoverTaskBadgePolicy {
    const val HEIGHT_DP = 22f
    const val MIN_WIDTH_DP = 22f
    const val HORIZONTAL_PADDING_DP = 6f

    fun presentation(pendingCount: Int): CoverTaskBadgePresentation {
        val count = pendingCount.coerceAtLeast(0)
        val label =
            when {
                count == 0 -> ""
                count >= 100 -> "99+"
                else -> count.toString()
            }
        return CoverTaskBadgePresentation(
            visible = count > 0,
            label = label,
            accessibilityCount = count,
            heightDp = HEIGHT_DP,
            minWidthDp = MIN_WIDTH_DP,
            horizontalPaddingDp = HORIZONTAL_PADDING_DP,
        )
    }
}
