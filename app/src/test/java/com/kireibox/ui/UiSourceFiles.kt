package com.kireibox.ui

import java.io.File

/**
 * Source-text access for wiring tests.
 *
 * Historically every wiring test read a single `KireiBoxApp.kt`. The shell was since split into
 * several files (`KireiBoxApp.kt`, `LibraryPages.kt`, `PickerPages.kt`, `KireiShellModels.kt`, ...),
 * so these helpers resolve the UI source directory and expose either one file, a name-prefixed
 * group of files, or the concatenation of every Kotlin file under `ui/` and `designsystem/` (recursively).
 * Concatenated reads keep wiring assertions working no matter which file a given call site
 * currently lives in, including files moved into ui sub-packages or into the design system.
 */
object UiSourceFiles {
    private val uiDir: File by lazy {
        listOf(
            File("src/main/java/com/kireibox/ui"),
            File("../app/src/main/java/com/kireibox/ui"),
        ).firstOrNull { it.isDirectory }
            ?: error("ui source dir not found from ${File("").absolutePath}")
    }

    private val designSystemDir: File by lazy {
        File(uiDir.parentFile, "designsystem").also {
            // Fail loudly instead of silently shrinking all(): "must not contain" assertions would
            // otherwise pass vacuously if the design system moved.
            check(it.isDirectory) { "designsystem source dir not found next to ${uiDir.absolutePath}" }
        }
    }

    /** A single file in the ui package (e.g. `KireiBoxApp.kt`, `ImmersiveHome.kt`). */
    fun file(name: String): String {
        val target = File(uiDir, name)
        check(target.isFile) { "$name not found under ${uiDir.absolutePath}" }
        return target.readText()
    }

    /** A single design-system file by its path relative to `designsystem/` (e.g. `components/KireiSheet.kt`). */
    fun designSystemFile(relative: String): String {
        val target = File(designSystemDir, relative)
        check(target.isFile) { "$relative not found under ${designSystemDir.absolutePath}" }
        return target.readText()
    }

    /** Every design-system `.kt` file (recursively) as (relative path, text) pairs. */
    fun designSystemSources(): List<Pair<String, String>> = nestedKotlinFiles(designSystemDir, includeTopLevel = true).map { it.relativeTo(designSystemDir).invariantSeparatorsPath to it.readText() }

    /**
     * Concatenation of every `.kt` file under `designsystem/components/`, recursively (sorted by
     * relative path). Negative assertions run on it, so a component moved into a sub-folder must
     * stay covered instead of making them pass vacuously (plan §4.3).
     */
    fun components(): String {
        val dir = File(designSystemDir, "components")
        check(dir.isDirectory) { "designsystem/components not found under ${designSystemDir.absolutePath}" }
        val files = nestedKotlinFiles(dir, includeTopLevel = true)
        check(files.isNotEmpty()) { "no component sources under ${dir.absolutePath}" }
        return files.joinToString("\n") { it.readText() }
    }

    /**
     * Concatenation of every `.kt` file under `ui/` and `designsystem/` (recursively).
     *
     * Order is stable and backwards compatible: top-level ui files first, name-sorted exactly as
     * before (so existing indexOf-based slices still resolve to the same first occurrence), then
     * files in ui sub-folders, then design-system files (each sorted by relative path).
     */
    fun all(): String =
        (topLevelUiFiles() + nestedKotlinFiles(uiDir) + nestedKotlinFiles(designSystemDir, includeTopLevel = true))
            .joinToString("\n") { it.readText() }

    /**
     * Name-sorted concatenation of the top-level ui files whose name starts with [prefix]
     * (e.g. `group("ImmersiveHome")` reads `ImmersiveHome.kt`, `ImmersiveHomeCards.kt`, ...), so a
     * feature can be split across sibling files without breaking its wiring assertions.
     */
    fun group(prefix: String): String {
        val files = topLevelUiFiles().filter { it.name.startsWith(prefix) }
        check(files.isNotEmpty()) { "no top-level ui file starts with $prefix under ${uiDir.absolutePath}" }
        return files.joinToString("\n") { it.readText() }
    }

    private fun File.isKotlinSource(): Boolean = isFile && extension == "kt" && !name.startsWith("._")

    private fun topLevelUiFiles(): List<File> =
        uiDir.listFiles { file -> file.isKotlinSource() }
            .orEmpty()
            .sortedBy { it.name }

    private fun nestedKotlinFiles(
        root: File,
        includeTopLevel: Boolean = false,
    ): List<File> =
        root.walkTopDown()
            .filter { it.isKotlinSource() && (includeTopLevel || it.parentFile != root) }
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            .toList()
}
