package com.yuki.yukihub.scanner

import java.util.Collections

/**
 * Partial scan output. Results remain usable even if the request is stopped.
 *
 * - 非 `data class`：内部 `MutableList` 与 `var` 状态可变，仅暴露只读视图与计算属性。
 * - 嵌套 `enum class StopReason` 列出 5 种扫描终止原因；`stopsBatch()` 判断该原因是否
 *   影响整批共享请求（CANCELLED / NODE_LIMIT / DEADLINE 为 true）。
 * - Java 同包调用方（`GameScanner`）使用的包私有修改方法（addResult / addError /
 *   tryVisit / shouldStop / setStopReason）保留为 `internal`，并加 `@JvmName` 提供
 *   未混淆的 JVM 方法名，避免 `internal` 默认的 `$module` 后缀导致 Java 调用方编译失败。
 * - `getResults()` / `getErrors()` 复刻原 `Collections.unmodifiableList(...)` 行为，
 *   返回不可修改视图而非拷贝；与原 Java 实现二进制等价。
 */
class ScanReport {
    enum class StopReason {
        COMPLETED, CANCELLED, NODE_LIMIT, DEADLINE, INVALID_ROOT;

        /** Whether this condition applies to the shared request rather than one root only. */
        fun stopsBatch(): Boolean = this == CANCELLED || this == NODE_LIMIT || this == DEADLINE
    }

    private val _results: MutableList<ScanResult> = ArrayList()
    private val _errors: MutableList<String> = ArrayList()
    private var _visitedNodes: Int = 0
    private var _skippedDirectories: Int = 0
    private var _stopReason: StopReason = StopReason.COMPLETED

    val results: List<ScanResult> get() = Collections.unmodifiableList(_results)
    val errors: List<String> get() = Collections.unmodifiableList(_errors)
    val visitedNodes: Int get() = _visitedNodes
    val skippedDirectories: Int get() = _skippedDirectories
    val stopReason: StopReason get() = _stopReason
    val isPartial: Boolean get() = _stopReason != StopReason.COMPLETED

    @JvmName("addResult")
    internal fun addResult(result: ScanResult?) {
        if (result != null) _results.add(result)
    }

    @JvmName("addError")
    internal fun addError(error: String?) {
        if (error != null && error.trim().isNotEmpty()) _errors.add(error)
    }

    @JvmName("addSkippedDirectory")
    internal fun addSkippedDirectory() { _skippedDirectories++ }

    @JvmName("tryVisit")
    internal fun tryVisit(request: ScanRequest, currentUri: String?): Boolean {
        if (request.isCancelled) {
            _stopReason = StopReason.CANCELLED
            return false
        }
        if (request.isDeadlineReached) {
            _stopReason = StopReason.DEADLINE
            return false
        }
        val globalVisitedNodes = request.tryAcquireNode()
        if (globalVisitedNodes == 0) {
            _stopReason = StopReason.NODE_LIMIT
            return false
        }
        _visitedNodes++
        val listener = request.getProgressListener()
        listener?.onProgress(globalVisitedNodes, _results.size, currentUri ?: "")
        return true
    }

    @JvmName("shouldStop")
    internal fun shouldStop(request: ScanRequest): Boolean = !tryCheck(request)

    private fun tryCheck(request: ScanRequest): Boolean {
        if (request.isCancelled) {
            _stopReason = StopReason.CANCELLED
            return false
        }
        if (request.isDeadlineReached) {
            _stopReason = StopReason.DEADLINE
            return false
        }
        return _stopReason == StopReason.COMPLETED
    }

    @JvmName("setStopReason")
    internal fun setStopReason(reason: StopReason?) {
        if (reason != null) _stopReason = reason
    }
}
