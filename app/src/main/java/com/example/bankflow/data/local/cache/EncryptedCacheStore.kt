package com.example.bankflow.data.local.cache

import com.example.bankflow.core.network.NetworkJson
import com.example.bankflow.data.local.db.CacheDao
import com.example.bankflow.data.local.db.CacheEntryEntity
import com.example.bankflow.data.local.session.SecretCipher
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.domain.model.Cached
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import org.koin.core.annotation.Single
import java.time.Clock
import java.time.Instant

@Single
class EncryptedCacheStore(
    private val dao: CacheDao,
    private val cipher: SecretCipher,
    private val sessionStore: SessionStore,
    private val clock: Clock,
) : CacheStore {

    override suspend fun currentOwner(): String? = sessionStore.current()?.user?.id

    override suspend fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>? = bestEffort {
        val owner = currentOwner() ?: return@bestEffort null
        val entry = dao.get(owner, key) ?: return@bestEffort null
        val plain = cipher.decrypt(entry.payload) ?: return@bestEffort null
        Cached(NetworkJson.decodeFromString(serializer, plain), Instant.ofEpochMilli(entry.savedAtEpochMs))
    }

    override suspend fun <T> write(owner: String?, key: String, serializer: KSerializer<T>, value: T) {
        bestEffort {
            if (owner == null || currentOwner() != owner) return@bestEffort null
            val payload = cipher.encrypt(NetworkJson.encodeToString(serializer, value))
            dao.put(CacheEntryEntity(owner, key, clock.millis(), payload))
        }
    }

    override suspend fun clearAll() {
        bestEffort { dao.clearAll() }
    }

    private suspend inline fun <R> bestEffort(block: () -> R?): R? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}