package com.kireibox.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shares one decode per cache key and cancels work when its last consumer leaves. */
internal class CoverLoadCoordinator<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val readCache: (K) -> V?,
    private val writeCache: (K, V) -> Unit,
) {
    private data class Flight<V : Any>(val deferred: Deferred<V?>, var consumers: Int)

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Flight<V>>()

    suspend fun load(key: K, decode: suspend () -> V?): V? {
        readCache(key)?.let { return it }
        val flight = acquire(key, decode)
        return try {
            flight.deferred.await()
        } finally {
            release(key, flight)
        }
    }

    private suspend fun acquire(key: K, decode: suspend () -> V?): Flight<V> = mutex.withLock {
        inFlight[key]?.also {
            it.consumers += 1
            return@withLock it
        }
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            currentCoroutineContext().ensureActive()
            decode()?.also { value ->
                currentCoroutineContext().ensureActive()
                writeCache(key, value)
            }
        }
        Flight(deferred, consumers = 1).also {
            inFlight[key] = it
            deferred.start()
        }
    }

    private suspend fun release(key: K, flight: Flight<V>) {
        withContext(NonCancellable) {
            val cancelFlight = mutex.withLock {
                if (inFlight[key] !== flight) return@withLock false
                flight.consumers -= 1
                if (flight.consumers == 0) {
                    inFlight.remove(key)
                    !flight.deferred.isCompleted
                } else {
                    false
                }
            }
            if (cancelFlight) flight.deferred.cancelAndJoin()
        }
    }
}
