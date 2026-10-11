package com.kireibox.ui

import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.scanner.ScanResult
import com.yuki.yukihub.storage.StorageRootIdentity

// "添加游戏" flow state. Single and batch add share one pipeline: pick folders -> detect -> review
// each candidate (engine + launch file) -> import the confirmed ones.

/** What a folder pick is for; a null pick is the settings' scan-root picker. */
data class AddGamePick(
    val kind: Kind,
    /** Engine the user chose before picking the folder; null = detect it. */
    val engine: EngineType? = null,
) {
    enum class Kind { BATCH_FOLDER, SINGLE_FOLDER }
}

enum class AddGameStage { SINGLE, BATCH, DETECTING, REVIEW }

/** One game waiting for the user's confirmation. */
data class AddGameCandidate(
    val rootUri: String,
    val title: String,
    val engine: EngineType,
    /** The engine the detector recognised, or UNKNOWN; the review page marks it as 识别结果. */
    val detectedEngine: EngineType,
    val launchTarget: String,
    /** Launch files the user can choose from for the current engine (several XP3 or several exe). */
    val launchOptions: List<String> = emptyList(),
    val coverUri: String = "",
    /** How sure the detector was about [detectedEngine]. */
    val confidence: Int = 0,
    /** A visible library card already points at this folder; adding it again only refreshes that card. */
    val inLibrary: Boolean = false,
    /** What [detectedEngine] launches, kept so switching away from it and back restores it. */
    val detectedTarget: String = AddGamePolicy.DIRECTORY_TARGET,
    val detectedOptions: List<String> = emptyList(),
    /** The folder's exe files, the likeliest first: what a Winlator game of this folder launches. */
    val exeOptions: List<String> = emptyList(),
) {
    val key: String get() = rootUri

    /** Importable: a known engine, a name, and for a Winlator game an exe to start. */
    val ready: Boolean
        get() =
            engine != EngineType.UNKNOWN && title.isNotBlank() &&
                (engine != EngineType.WINDOWS || launchTarget.endsWith(".exe", ignoreCase = true))

    fun toScanResult(): ScanResult =
        ScanResult(
            title = title.trim(),
            uri = rootUri,
            engine = engine,
            confidence = if (engine == detectedEngine) confidence else 0,
            launchTarget = launchTarget,
            coverUri = coverUri,
        )
}

data class AddGameFolder(
    val uri: String,
    val name: String,
)

data class AddGameUi(
    val stage: AddGameStage = AddGameStage.SINGLE,
    /** Folders collected on the batch page. */
    val folders: List<AddGameFolder> = emptyList(),
    val queue: List<AddGameCandidate> = emptyList(),
    /** Keys of the candidates that will be imported; the review page toggles them. */
    val selected: Set<String> = emptySet(),
    /** The candidate whose engine / launch file is being edited from the list (batch review). */
    val editing: String? = null,
    val addedCount: Int = 0,
    val skippedCount: Int = 0,
    /** Candidates dropped before review because the library already holds them. */
    val alreadyInLibrary: Int = 0,
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** One game gets the detailed page; several get the compact list. */
    val listReview: Boolean get() = queue.size > 1

    val single: AddGameCandidate? get() = queue.singleOrNull()

    val editingCandidate: AddGameCandidate? get() = queue.firstOrNull { it.key == editing }

    /** What 添加 imports: selected candidates that are ready. */
    val importable: List<AddGameCandidate> get() = queue.filter { it.key in selected && it.ready }

    val canConfirm: Boolean get() = !saving && importable.isNotEmpty()
}

/** Pure rules of the add-game flow. */
internal object AddGamePolicy {
    const val DIRECTORY_TARGET = "[游戏目录]"

    /** Engines the user can pick by hand for a folder. */
    val FOLDER_ENGINES: List<EngineType> =
        listOf(EngineType.KIRIKIRI, EngineType.ONS, EngineType.TYRANO, EngineType.ARTEMIS)

    /** Engines the review pages offer: the four folder engines and Winlator. */
    val REVIEW_ENGINES: List<EngineType> = FOLDER_ENGINES + EngineType.WINDOWS

    /**
     * Candidate for a folder the detector looked at. [chosen] is an engine the user picked beforehand;
     * it wins over detection, and the detector's launch file is only kept when both agree.
     */
    fun candidateFor(
        result: ScanResult,
        chosen: EngineType? = null,
    ): AddGameCandidate {
        val detected = result.engine?.takeIf { it != EngineType.UNKNOWN } ?: EngineType.UNKNOWN
        val exes = result.exeCandidates.orEmpty()
        val target = result.launchTarget?.takeIf(String::isNotBlank) ?: DIRECTORY_TARGET
        val options = result.xp3Candidates.orEmpty()
        val detectedCandidate =
            AddGameCandidate(
                rootUri = result.uri.orEmpty(),
                title = result.title?.takeIf(String::isNotBlank) ?: "未命名游戏",
                engine = detected,
                detectedEngine = detected,
                launchTarget = target,
                launchOptions = if (detected == EngineType.WINDOWS) exes else options,
                coverUri = result.coverUri.orEmpty(),
                confidence = result.confidence,
                detectedTarget = target,
                detectedOptions = options,
                exeOptions = exes,
            )
        return if (chosen == null) detectedCandidate else withEngine(detectedCandidate, chosen)
    }

    /** Switching engine swaps in the launch file that belongs to it (the detector's, the folder's exe, or the whole folder). */
    fun withEngine(
        candidate: AddGameCandidate,
        engine: EngineType,
    ): AddGameCandidate =
        when {
            engine == candidate.engine -> candidate
            engine == EngineType.WINDOWS ->
                candidate.copy(
                    engine = engine,
                    launchTarget = candidate.exeOptions.firstOrNull() ?: DIRECTORY_TARGET,
                    launchOptions = candidate.exeOptions,
                )
            engine == candidate.detectedEngine ->
                candidate.copy(engine = engine, launchTarget = candidate.detectedTarget, launchOptions = candidate.detectedOptions)
            else -> candidate.copy(engine = engine, launchTarget = DIRECTORY_TARGET, launchOptions = emptyList())
        }

    /** Merges candidates found under several folders: one per game folder (first wins), none already in the library. */
    fun buildQueue(found: List<AddGameCandidate>): Pair<List<AddGameCandidate>, Int> {
        val unique = found.distinctBy { StorageRootIdentity.key(it.rootUri).ifEmpty { it.rootUri } }
        val fresh = unique.filterNot(AddGameCandidate::inLibrary)
        return fresh to (unique.size - fresh.size)
    }

    fun summary(ui: AddGameUi): String =
        when {
            ui.addedCount == 0 -> "没有添加游戏"
            ui.skippedCount == 0 -> "已添加 ${ui.addedCount} 个游戏"
            else -> "已添加 ${ui.addedCount} 个游戏，跳过 ${ui.skippedCount} 个"
        }
}
