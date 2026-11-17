package com.example.bankflow.data.local.cache

import com.example.bankflow.data.local.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class CacheCleaner(
    private val sessionStore: SessionStore,
    private val cache: CacheStore,
) {
    fun start(scope: CoroutineScope): Job = scope.launch {
        sessionStore.session
            .map { it?.user?.id }
            .distinctUntilChanged()
            .collect { userId -> if (userId == null) cache.clearAll() }
    }
}