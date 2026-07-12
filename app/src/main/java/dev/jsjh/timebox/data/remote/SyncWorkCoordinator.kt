package dev.jsjh.timebox.data.remote

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes sync work for one account inside a process. Persistence and retry
 * belong to Room + WorkManager; this object deliberately owns no retry loop.
 */
object SyncWorkCoordinator {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withUserLock(userId: String, block: suspend () -> T): T {
        val lock = locks.computeIfAbsent(userId) { Mutex() }
        return lock.withLock { block() }
    }
}
