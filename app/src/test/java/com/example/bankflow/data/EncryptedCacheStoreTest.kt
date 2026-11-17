package com.example.bankflow.data

import com.example.bankflow.data.local.cache.EncryptedCacheStore
import com.example.bankflow.data.local.db.CacheDao
import com.example.bankflow.data.local.db.CacheEntryEntity
import com.example.bankflow.data.local.session.SecretCipher
import com.example.bankflow.data.local.session.StoredSession
import com.example.bankflow.data.remote.dto.UserDto
import com.example.bankflow.testutil.TestClock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class EncryptedCacheStoreTest {

    private class FakeCacheDao : CacheDao {
        val rows = mutableMapOf<Pair<String, String>, CacheEntryEntity>()
        override suspend fun get(userId: String, cacheKey: String) = rows[userId to cacheKey]
        override suspend fun put(entry: CacheEntryEntity) { rows[entry.userId to entry.cacheKey] = entry }
        override suspend fun clearUser(userId: String) { rows.keys.removeAll { it.first == userId } }
        override suspend fun clearAll() = rows.clear()
    }

    private class ReverseCipher(private val canDecrypt: Boolean = true) : SecretCipher {
        override fun encrypt(plainText: String) = plainText.reversed()
        override fun decrypt(encoded: String): String? = if (canDecrypt) encoded.reversed() else null
    }

    private val serializer = ListSerializer(String.serializer())
    private val dao = FakeCacheDao()
    private val clock = TestClock()
    private val session = FakeSessionStore(storedSession("access", "refresh"))

    private fun store(cipher: SecretCipher = ReverseCipher()) = EncryptedCacheStore(dao, cipher, session, clock)

    private fun signInAs(userId: String) = StoredSession("a", "r", UserDto(userId, "$userId@example.com", "Name", "SYSTEM"))

    @Test
    fun `a saved value is read back with the time it was saved`() = runTest {
        val store = store()

        store.write("user-1", "k", serializer, listOf("Everyday Checking", "Savings"))

        val cached = store.read("k", serializer)!!
        assertEquals(listOf("Everyday Checking", "Savings"), cached.value)
        assertEquals(clock.instant(), cached.savedAt)
    }

    @Test
    fun `what reaches the database is not readable`() = runTest {
        store().write("user-1", "k", serializer, listOf("Everyday Checking"))

        val payload = dao.rows.values.single().payload
        assertFalse(payload.contains("Everyday Checking"))
    }

    @Test
    fun `writing again replaces the value and refreshes the time`() = runTest {
        val store = store()
        store.write("user-1", "k", serializer, listOf("old"))
        clock.advance(Duration.ofMinutes(5))

        store.write("user-1", "k", serializer, listOf("new"))

        val cached = store.read("k", serializer)!!
        assertEquals(listOf("new"), cached.value)
        assertEquals(clock.instant(), cached.savedAt)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `another signed-in user never sees it`() = runTest {
        val store = store()
        store.write("user-1", "k", serializer, listOf("alice's data"))

        session.save(signInAs("user-2"))

        assertNull(store.read("k", serializer))
    }

    @Test
    fun `nothing is readable or writable when signed out`() = runTest {
        val store = store()
        store.write("user-1", "k", serializer, listOf("data"))

        session.clear()

        assertNull(store.currentOwner())
        assertNull(store.read("k", serializer))
        store.write("user-1", "other", serializer, listOf("late"))
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `a response for a previous user is not saved under the new one`() = runTest {
        val store = store()
        val ownerWhenRequestStarted = store.currentOwner()
        session.save(signInAs("user-2"))

        store.write(ownerWhenRequestStarted, "k", serializer, listOf("alice's data"))

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `a write with no owner is ignored`() = runTest {
        store().write(null, "k", serializer, listOf("data"))

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `data that cannot be decrypted counts as nothing saved`() = runTest {
        store().write("user-1", "k", serializer, listOf("data"))

        assertNull(store(ReverseCipher(canDecrypt = false)).read("k", serializer))
    }

    @Test
    fun `data that no longer decodes counts as nothing saved`() = runTest {
        dao.put(CacheEntryEntity("user-1", "k", 0L, "not json".reversed()))

        assertNull(store().read("k", serializer))
    }

    @Test
    fun `clearing removes everything`() = runTest {
        val store = store()
        store.write("user-1", "a", serializer, listOf("1"))
        store.write("user-1", "b", serializer, listOf("2"))

        store.clearAll()

        assertTrue(dao.rows.isEmpty())
    }
}