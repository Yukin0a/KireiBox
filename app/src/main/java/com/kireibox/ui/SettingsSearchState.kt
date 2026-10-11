package com.kireibox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// The settings root's search state (r4 · S2): the query, focus, back handling and the collapsed
// bar's shortcut back to the capsule. The capsule and results are SettingsSearchPage.kt.

/**
 * The root's search: the field's text (kept across pages and rotation), whether it has focus, and
 * [resultsQuery] — the text the results are for. While the keyboard composes (pinyin 「shense」 on
 * its way to 「深色」) the results wait for the commit or a pause instead of flashing matches for
 * 「s」 / 「sh」 / 「she」 ([SettingsSearchResultsSync]).
 */
@Stable
internal class SettingsSearchState(
    saved: MutableState<String>,
) {
    var query by saved
    var focused by mutableStateOf(false)
    var composing by mutableStateOf(false)
    var resultsQuery by mutableStateOf(saved.value)
    val focusRequester = FocusRequester()

    /** 「取消」 shows and back closes the search while the field has focus or text. */
    val active: Boolean get() = focused || query.isNotEmpty()

    /** Results replace the root groups once the results' query has something to match. */
    val searching: Boolean by derivedStateOf { SettingsSearch.words(resultsQuery).isNotEmpty() }

    /** A word set from outside the keyboard (a suggestion): results follow at once. */
    fun fill(text: String) {
        query = text
        resultsQuery = text
    }

    fun cancel() = fill("")
}

/** How long composing text has to rest before results follow it anyway (English keyboards compose words too). */
private const val COMPOSING_SETTLE_MS = 300L

/**
 * Keeps [SettingsSearchState.resultsQuery] in step with the field: at once for committed text; while the
 * keyboard composes, after a short pause — and never to a query that finds nothing (no empty-state flash
 * for a half-typed pinyin syllable; the commit decides).
 */
@Composable
internal fun SettingsSearchResultsSync(search: SettingsSearchState) {
    LaunchedEffect(search) {
        snapshotFlow { search.query to search.composing }.collectLatest { (text, composing) ->
            if (!composing) {
                search.resultsQuery = text
            } else {
                delay(COMPOSING_SETTLE_MS)
                if (SettingsSearch.search(SettingsSearchIndex.entries, text).isNotEmpty()) search.resultsQuery = text
            }
        }
    }
}

@Composable
internal fun rememberSettingsSearchState(): SettingsSearchState {
    val saved = rememberSaveable { mutableStateOf("") }
    return remember(saved) { SettingsSearchState(saved) }
}

/**
 * Everything the root's search runs beside its UI: back handling, the results' query, and — like iOS and
 * Android Settings — putting the keyboard away on the first drag of the page, so nobody keeps typing into
 * a field that scrolled under the bar (the collapsed bar's search button brings it back).
 */
@Composable
internal fun SettingsSearchEffects(
    search: SettingsSearchState,
    scroll: ScrollState,
) {
    SettingsSearchBackHandler(search)
    SettingsSearchResultsSync(search)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(search, scroll) {
        scroll.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start && search.focused) focusManager.clearFocus()
        }
    }
}

/** Back first clears the query (and drops focus) before it leaves the settings root. */
@Composable
internal fun SettingsSearchBackHandler(search: SettingsSearchState) {
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = search.active) {
        search.cancel()
        focusManager.clearFocus(force = true)
    }
}

/** The collapsed bar's search button: scrolls back to the capsule and focuses it. */
@Composable
internal fun rememberSettingsSearchShortcut(
    search: SettingsSearchState,
    scroll: ScrollState,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(search, scroll) {
        {
            scope.launch {
                scroll.animateScrollTo(0)
                search.focusRequester.requestFocus()
            }
        }
    }
}
