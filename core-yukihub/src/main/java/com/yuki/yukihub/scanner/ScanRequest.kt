package com.yuki.yukihub.scanner

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls one scan batch. A request may be shared across multiple roots so cancellation,
 * deadline, and node limits remain global and consistent for every caller.
 *
 * - 顶层 `class ScanRequest` 保留 Builder 模式：私有主构造器 + 嵌套 `Builder`。
 * - `fun interface ProgressListener` 允许 Java 调用方使用 SAM lambda（Kotlin 函数式接口），
 *   同时 Kotlin 调用方仍可使用 lambda / 函数引用。
 * - 公共静态常量用 `const val` 等价 `static final`；`defaults` 用 `@JvmStatic` 暴露为静态方法。
 * - `is` 前缀的布尔属性自动生成 `isXxx()` JVM 方法，与原 Java getter 命名一致；
 *   `maxDepth` / `maxNodes` / `deadlineAtElapsedMs` / `visitedNodes` 用 `val` 属性生成对应 `getXxx()`。
 * - 包私有方法（`getProgressListener` / `tryAcquireNode`）保留为 `internal` + `@JvmName`，
 *   让 Java 同包调用方（`GameScanner` / `ScanReport`）继续使用原名。
 */
class ScanRequest private constructor(builder: Builder) {
    val maxDepth: Int = builder.maxDepth
    val maxNodes: Int = builder.maxNodes
    val deadlineAtElapsedMs: Long = builder.deadlineAtElapsedMs
    private val cancelled: AtomicBoolean = builder.cancelled ?: AtomicBoolean(false)
    private val progressListener: ProgressListener? = builder.progressListener
    private val excludedDirectoryPatterns: Set<String> = builder.excludedDirectoryPatterns.toSet()
    private val ignoredUriKeys: Set<String> = builder.ignoredUriKeys.toSet()

    /** Shared by every root scanned with this request, so maxNodes is a batch-wide limit. */
    private val _visitedNodes: AtomicInteger = AtomicInteger()

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
    }

    val visitedNodes: Int get() = _visitedNodes.get()

    val isDeadlineReached: Boolean
        get() = deadlineAtElapsedMs > 0 && SystemClock.elapsedRealtime() >= deadlineAtElapsedMs

    val isNodeLimitReached: Boolean
        get() = maxNodes > 0 && _visitedNodes.get() >= maxNodes

    @JvmName("getProgressListener")
    internal fun getProgressListener(): ProgressListener? = progressListener

    @JvmName("shouldSkipDirectory")
    internal fun shouldSkipDirectory(
        name: String?,
        uri: String?,
    ): Boolean =
        ScanExclusionMatcher.matchesName(name, excludedDirectoryPatterns) ||
            ScanExclusionMatcher.matchesUri(uri, ignoredUriKeys)

    /** Returns the global visited count after acquiring a slot, or 0 when the limit is full. */
    @JvmName("tryAcquireNode")
    internal fun tryAcquireNode(): Int {
        while (true) {
            val current = _visitedNodes.get()
            if (maxNodes > 0 && current >= maxNodes) return 0
            if (_visitedNodes.compareAndSet(current, current + 1)) return current + 1
        }
    }

    /**
     * Progress callback for a scan batch.
     *
     * `fun interface` 使 Java 调用方可使用 SAM lambda（`request.setProgressListener((v, g, uri) -> {...})`），
     * Kotlin 调用方亦可使用 lambda 或函数引用。
     */
    fun interface ProgressListener {
        fun onProgress(
            visitedNodes: Int,
            foundGames: Int,
            currentUri: String,
        )
    }

    // Kotlin 与 Java 不同：外部类无法访问嵌套类的 private 成员（Java 允许）。
    // 因此 Builder 内 ScanRequest 需读取的字段使用 `internal`（模块内可见，比 Java 的
    // private 略宽，但比 public 窄），保留对外不可见的封装。
    class Builder(internal val maxDepth: Int) {
        internal var maxNodes: Int = DEFAULT_MAX_NODES
        internal var deadlineAtElapsedMs: Long = 0
        internal var cancelled: AtomicBoolean? = null
        internal var progressListener: ProgressListener? = null
        internal val excludedDirectoryPatterns: MutableSet<String> = LinkedHashSet()
        internal val ignoredUriKeys: MutableSet<String> = LinkedHashSet()

        /** A value <= 0 means no node-count limit. */
        fun setMaxNodes(value: Int): Builder {
            maxNodes = value
            return this
        }

        /** A value <= 0 means no deadline. */
        fun setDeadlineAfterMs(value: Long): Builder {
            deadlineAtElapsedMs = if (value <= 0) 0 else SystemClock.elapsedRealtime() + value
            return this
        }

        fun setDeadlineAtElapsedMs(value: Long): Builder {
            deadlineAtElapsedMs = value
            return this
        }

        fun setCancellationFlag(value: AtomicBoolean?): Builder {
            cancelled = value
            return this
        }

        fun setProgressListener(value: ProgressListener?): Builder {
            progressListener = value
            return this
        }

        fun setExcludedDirectoryPatterns(value: Collection<String>?): Builder {
            excludedDirectoryPatterns.clear()
            if (value != null) excludedDirectoryPatterns.addAll(value.filter(String::isNotBlank))
            return this
        }

        fun setIgnoredUriKeys(value: Collection<String>?): Builder {
            ignoredUriKeys.clear()
            if (value != null) ignoredUriKeys.addAll(value.filter(String::isNotBlank))
            return this
        }

        fun build(): ScanRequest = ScanRequest(this)
    }

    companion object {
        const val DEFAULT_MAX_NODES: Int = 10_000
        const val DEFAULT_DEADLINE_MS: Long = 2L * 60L * 1000L

        @JvmStatic
        fun defaults(maxDepth: Int): ScanRequest =
            Builder(maxDepth)
                .setMaxNodes(DEFAULT_MAX_NODES)
                .setDeadlineAfterMs(DEFAULT_DEADLINE_MS)
                .build()
    }
}
