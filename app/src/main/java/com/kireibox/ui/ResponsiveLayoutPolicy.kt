package com.kireibox.ui

object ResponsiveLayoutPolicy {
    enum class LibraryGrid { PHONE_TWO_COLUMNS, MEDIUM_ADAPTIVE, WIDE_ADAPTIVE }

    fun useTwoPane(widthDp: Float, heightDp: Float): Boolean {
        if (widthDp >= 840f) return true
        if (widthDp < 720f || heightDp <= 0f) return false
        return widthDp / heightDp >= .68f
    }

    fun libraryGrid(widthDp: Float): LibraryGrid = when {
        widthDp < 600f -> LibraryGrid.PHONE_TWO_COLUMNS
        widthDp < 840f -> LibraryGrid.MEDIUM_ADAPTIVE
        else -> LibraryGrid.WIDE_ADAPTIVE
    }

    fun heroHeightDp(availableHeightDp: Float, twoPane: Boolean): Float =
        if (twoPane) {
            (availableHeightDp * .46f).coerceIn(300f, 410f)
        } else {
            (availableHeightDp * .38f).coerceIn(270f, 370f)
        }
}
