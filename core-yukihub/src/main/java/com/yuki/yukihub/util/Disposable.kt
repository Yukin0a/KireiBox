package com.yuki.yukihub.util

/**
 * 取消句柄接口，替代 RxJava `io.reactivex.disposables.Disposable`。
 *
 * 与原 RxJava Disposable API 完全一致：
 * - [dispose]：取消任务
 * - [isDisposed]：查询是否已取消/已完成
 *
 * 调用方仅需将 `import io.reactivex.disposables.Disposable` 替换为
 * `import com.yuki.yukihub.util.Disposable`，方法名与调用形式零修改。
 *
 * 引入此接口的目的是在移除 RxJava 依赖后保留最小破坏性的取消语义。
 * 实现方通常包装 [kotlinx.coroutines.Job]（见 [RxMainScheduler]）。
 */
interface Disposable {
    /** 取消关联任务。重复调用安全。 */
    fun dispose()

    /** 任务是否已取消或已完成。 */
    fun isDisposed(): Boolean
}
