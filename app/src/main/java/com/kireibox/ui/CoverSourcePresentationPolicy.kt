package com.kireibox.ui

import com.yuki.yukihub.metadata.model.CoverSourceMode

internal data class CoverSourceEditorState(
    val selectedSource: CoverSourceMode,
    val tokenEditorOpen: Boolean,
    val storedToken: String,
)

internal data class CoverSourcePresentation(
    val source: CoverSourceMode,
    val isSelected: Boolean,
    val isHighlighted: Boolean,
    val muted: Boolean,
    val enabled: Boolean,
    val accessible: Boolean,
    val alpha: Float,
    val tokenVisible: Boolean,
)

internal object CoverSourcePresentationPolicy {
    const val NORMAL_ALPHA = 1f
    const val MUTED_ALPHA = .58f

    fun presentation(
        source: CoverSourceMode,
        selectedSource: CoverSourceMode?
    ): CoverSourcePresentation {
        val isSelected = source == (selectedSource ?: CoverSourceMode.BANGUMI)
        val muted = source != CoverSourceMode.BANGUMI && !isSelected
        return CoverSourcePresentation(
            source = source,
            isSelected = isSelected,
            isHighlighted = isSelected,
            muted = muted,
            enabled = source.isActive,
            accessible = true,
            alpha = if (muted) MUTED_ALPHA else NORMAL_ALPHA,
            tokenVisible = source == CoverSourceMode.BANGUMI,
        )
    }

    fun options(selectedSource: CoverSourceMode?): List<CoverSourcePresentation> = CoverSourceMode.active.map { source -> presentation(source, selectedSource) }

    fun tokenVisible(source: CoverSourceMode): Boolean = source == CoverSourceMode.BANGUMI

    fun switchSource(
        state: CoverSourceEditorState,
        source: CoverSourceMode
    ): CoverSourceEditorState = state.copy(selectedSource = source, tokenEditorOpen = false)
}
