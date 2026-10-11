package com.kireibox.ui

import com.yuki.yukihub.model.EngineType

/** The review page's edits as pure functions of the flow state (the controller applies them). */
internal object AddGameReview {
    fun selectEngine(
        ui: AddGameUi,
        key: String,
        engine: EngineType,
    ): AddGameUi = changed(ui, key) { AddGamePolicy.withEngine(it, engine) }

    fun selectLaunchTarget(
        ui: AddGameUi,
        key: String,
        target: String,
    ): AddGameUi = changed(ui, key) { if (target in it.launchOptions) it.copy(launchTarget = target) else it }

    fun setTitle(
        ui: AddGameUi,
        key: String,
        title: String,
    ): AddGameUi = changed(ui, key) { it.copy(title = title) }

    /** Ticks or unticks a row; one that cannot be imported yet opens its editor instead. */
    fun toggle(
        ui: AddGameUi,
        key: String,
    ): AddGameUi {
        val candidate = ui.queue.firstOrNull { it.key == key }
        return when {
            candidate == null -> ui
            !candidate.ready -> ui.copy(editing = key)
            key in ui.selected -> ui.copy(selected = ui.selected - key)
            else -> ui.copy(selected = ui.selected + key)
        }
    }

    fun toggleAll(ui: AddGameUi): AddGameUi {
        val ready = ui.queue.filter(AddGameCandidate::ready).mapTo(linkedSetOf(), AddGameCandidate::key)
        return ui.copy(selected = if (ready.isNotEmpty() && ui.selected.containsAll(ready)) emptySet() else ready)
    }

    /** The review state for a fresh queue: everything importable is ticked. */
    fun reviewing(
        ui: AddGameUi,
        queue: List<AddGameCandidate>,
    ): AddGameUi =
        ui.copy(
            stage = AddGameStage.REVIEW,
            queue = queue,
            selected = queue.filter(AddGameCandidate::ready).mapTo(linkedSetOf(), AddGameCandidate::key),
            editing = null,
            error = null,
        )

    /** Changes one candidate; one that just became importable is ticked for the user, one that stopped being importable is not. */
    private fun changed(
        ui: AddGameUi,
        key: String,
        transform: (AddGameCandidate) -> AddGameCandidate,
    ): AddGameUi {
        val old = ui.queue.firstOrNull { it.key == key } ?: return ui
        val new = transform(old)
        val selected =
            when {
                !new.ready -> ui.selected - key
                !old.ready -> ui.selected + key
                else -> ui.selected
            }
        return ui.copy(queue = ui.queue.map { if (it.key == key) new else it }, selected = selected)
    }
}
