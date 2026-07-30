package com.yuki.yukihub.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 全局 ExecutorService 包装器。
 *
 * 提供 IO 线程池、单线程执行器和调度执行器，供 Java/Kotlin 调用方使用。
 * 与原 Java 实现 API 二进制兼容：`AppExecutors.io()` / `runOnIo(Runnable)` 等调用形式不变。
 */
object AppExecutors {
    private val IO_POOL_SIZE: Int = maxOf(2, minOf(Runtime.getRuntime().availableProcessors(), 6))

    private val ioExecutor: ExecutorService = Executors.newFixedThreadPool(IO_POOL_SIZE) { r ->
        Thread(r, "YukiHub-IO").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val singleExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "YukiHub-Single").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private val scheduledExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "YukiHub-Scheduled").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    @JvmStatic
    fun io(): ExecutorService = ioExecutor

    @JvmStatic
    fun single(): ExecutorService = singleExecutor

    @JvmStatic
    fun scheduled(): ScheduledExecutorService = scheduledExecutor

    @JvmStatic
    fun runOnIo(command: Runnable) {
        ioExecutor.execute(command)
    }

    @JvmStatic
    fun runOnSingle(command: Runnable) {
        singleExecutor.execute(command)
    }

    @JvmStatic
    fun schedule(command: Runnable, delayMs: Long): ScheduledFuture<*> =
        scheduledExecutor.schedule(command, delayMs, TimeUnit.MILLISECONDS)
}
