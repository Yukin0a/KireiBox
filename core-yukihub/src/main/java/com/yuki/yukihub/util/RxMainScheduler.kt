package com.yuki.yukihub.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 主线程调度器（Kotlin 协程实现）。
 *
 * 替代原 RxJava `AndroidSchedulers.mainThread().scheduleDirect(...)` 方案。
 * 内部使用 [MainScope]（即 [kotlinx.coroutines.Dispatchers.Main] + SupervisorJob），
 * 行为与原实现等价：
 * - [post]：立即投递到主线程执行
 * - [postDelayed]：延迟 `delayMs` 毫秒后投递到主线程执行
 *
 * 返回 [Disposable] 句柄，调用方可通过 [Disposable.dispose] 取消任务，
 * 通过 [Disposable.isDisposed] 查询状态。语义与原 RxJava Disposable 一致：
 * 任务正常完成或被取消后 `isDisposed() == true`。
 *
 * 该类为 Java 调用方提供静态方法形式（`@JvmStatic`），调用方零修改：
 * ```java
 * Disposable d = RxMainScheduler.post(() -> { ... });
 * Disposable d2 = RxMainScheduler.postDelayed(() -> { ... }, 500L);
 * if (!d.isDisposed()) d.dispose();
 * ```
 */
object RxMainScheduler {
    private val mainScope: CoroutineScope = MainScope()

    @JvmStatic
    fun post(action: Runnable): Disposable {
        val job = mainScope.launch { action.run() }
        return JobDisposable(job)
    }

    @JvmStatic
    fun postDelayed(action: Runnable, delayMs: Long): Disposable {
        val job = mainScope.launch {
            delay(delayMs)
            action.run()
        }
        return JobDisposable(job)
    }

    /** 包装 [Job] 为 [Disposable]，转译协程状态为 RxJava 风格 API。 */
    private class JobDisposable(private val job: Job) : Disposable {
        override fun dispose() {
            job.cancel()
        }

        override fun isDisposed(): Boolean {
            // job.isActive 在等待/运行时为 true；完成或取消后为 false。
            // RxJava isDisposed() 在正常完成或取消后均返回 true，语义一致。
            return !job.isActive
        }
    }
}
