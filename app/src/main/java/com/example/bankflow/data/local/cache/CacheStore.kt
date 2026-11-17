package com.example.bankflow.data.local.cache

import com.example.bankflow.domain.model.Cached
import kotlinx.serialization.KSerializer


interface CacheStore {

    suspend fun currentOwner(): String?

    suspend fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>?

    suspend fun <T> write(owner: String?, key: String, serializer: KSerializer<T>, value: T)

    suspend fun clearAll()
}