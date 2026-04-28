package com.trading.coinflip.common.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Reentrant mutex extension for Kotlin coroutines.
 * Based on Roman Elizarov's pattern: https://gist.github.com/elizarov/9a48b9709ffd508909d34fab6786acfe
 *
 * Unlike standard Mutex.withLock, this allows the same coroutine to re-acquire
 * the lock without deadlocking. Uses CoroutineContext to track lock ownership.
 */
suspend fun <T> Mutex.withReentrantLock(block: suspend () -> T): T {
    val key = ReentrantMutexContextKey(this)
    // Call block directly when this mutex is already locked in the context
    if (coroutineContext[key] != null) return block()
    // Otherwise add it to the context and lock the mutex
    return withContext(ReentrantMutexContextElement(key)) {
        withLock { block() }
    }
}

private class ReentrantMutexContextElement(
    override val key: ReentrantMutexContextKey,
) : CoroutineContext.Element

private data class ReentrantMutexContextKey(
    val mutex: Mutex,
) : CoroutineContext.Key<ReentrantMutexContextElement>
