package com.prplegryn.bd.download

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DownloadGate {
    private val mutex = Mutex()
    private val active = mutableSetOf<String>()

    suspend fun acquire(id: String, limit: Int) {
        while (true) {
            val acquired = mutex.withLock {
                if (id in active) {
                    true
                } else if (active.size < limit.coerceIn(1, 4)) {
                    active += id
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            delay(350)
        }
    }

    suspend fun release(id: String) {
        mutex.withLock { active -= id }
    }
}

