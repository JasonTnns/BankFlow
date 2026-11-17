package com.example.bankflow.testutil

import com.example.bankflow.core.network.NetworkJson
import com.example.bankflow.data.local.cache.CacheStore
import com.example.bankflow.domain.model.Cached
import kotlinx.serialization.KSerializer
import java.time.Instant

class FakeCacheStore(var owner: String? = "user-1") : CacheStore {
    val entries = mutableMapOf<Pair<String, String>, Pair<String, Instant>>()
    var writes = 0
    var clears = 0
    var now: Instant = Instant.parse("2026-10-29T10:00:00Z")

    override suspend fun currentOwner(): String? = owner

    override suspend fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>? {
        val (json, savedAt) = owner?.let { entries[it to key] } ?: return null
        return runCatching { Cached(NetworkJson.decodeFromString(serializer, json), savedAt) }.getOrNull()
    }

    override suspend fun <T> write(owner: String?, key: String, serializer: KSerializer<T>, value: T) {
        if (owner == null || owner != this.owner) return
        writes++
        entries[owner to key] = NetworkJson.encodeToString(serializer, value) to now
    }

    override suspend fun clearAll() {
        clears++
        entries.clear()
    }
}