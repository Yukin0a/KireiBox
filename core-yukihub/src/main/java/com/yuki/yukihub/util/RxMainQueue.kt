package com.yuki.yukihub.util

import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 主线程任务队列（Kotlin 协程实现），支持按 Runnable 取消延迟任务。
 *
 * 替代原 RxJava-backed `RxMainQueue`。内部通过 [RxMainScheduler] 投递任务，
 * 并用 [IdentityHashMap] 跟踪每个 [Runnable] 关联的 [Disposable] 列表，
 * [removeCallbacks] 可批量取消同一 Runnable 的所有挂起延迟任务。
 *
 * 与原 Java 行为一致：
 * - [postDelayed] 容忍 `action == null`（返回 noop），匹配原 Java `if (action == null) return`
 * - 任务执行完毕后自动从跟踪表中移除 disposable，避免内存泄漏
 * - [removeCallbacks] 在 synchronized 块内移除引用，块外统一 dispose
 *
 * 并发说明：[postDelayed] 内部使用 [AtomicReference] 持有 disposable 引用，
 * 保证「调度后才发生的写」对任务执行线程可见。这是原 Java 实现的内存模型语义，
 * 作为可复用工具类应予保留——即使当前所有调用方均在主线程调用，
 * 也不能依赖单线程假设而退化并发保证。
 *
 * 调用方零修改（3 个 Fragment 使用 `new RxMainQueue()`）。
 */
class RxMainQueue {
    private val delayedTasks: MutableMap<Runnable, MutableList<Disposable>> = IdentityHashMap()

    fun post(action: Runnable) {
        RxMainScheduler.post(action)
    }

    fun postDelayed(action: Runnable?, delayMs: Long) {
        if (action == null) return
        // AtomicReference 提供 volatile 语义：launch 后的写（set）对协程执行线程
        // 的读（get）可见。普通 var 捕获编译为非 volatile 的 Ref.ObjectRef，
        // 不保证跨线程可见性。作为可复用工具类保留原 Java 的并发保证。
        val reference = AtomicReference<Disposable?>(null)
        val disposable = RxMainScheduler.postDelayed(Runnable {
            try {
                action.run()
            } finally {
                reference.get()?.let { removeTracked(action, it) }
            }
        }, delayMs)
        reference.set(disposable)
        synchronized(delayedTasks) {
            delayedTasks.computeIfAbsent(action) { ArrayList() }.add(disposable)
        }
    }

    fun removeCallbacks(action: Runnable?) {
        if (action == null) return
        val disposables: MutableList<Disposable>?
        synchronized(delayedTasks) {
            disposables = delayedTasks.remove(action)
        }
        disposables?.forEach { it.dispose() }
    }

    private fun removeTracked(action: Runnable, disposable: Disposable) {
        synchronized(delayedTasks) {
            val list = delayedTasks[action] ?: return
            list.remove(disposable)
            if (list.isEmpty()) delayedTasks.remove(action)
        }
    }
}

