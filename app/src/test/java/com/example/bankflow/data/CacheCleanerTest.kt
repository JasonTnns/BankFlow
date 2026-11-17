package com.example.bankflow.data

import com.example.bankflow.data.local.cache.CacheCleaner
import com.example.bankflow.testutil.FakeCacheStore
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CacheCleanerTest {

    @Test
    fun `a signed-in start keeps the saved data`() = runTest {
        val cache = FakeCacheStore()

        CacheCleaner(FakeSessionStore(storedSession("a", "r")), cache).start(backgroundScope)
        runCurrent()

        assertEquals(0, cache.clears)
    }

    @Test
    fun `signing out wipes it`() = runTest {
        val session = FakeSessionStore(storedSession("a", "r"))
        val cache = FakeCacheStore()
        CacheCleaner(session, cache).start(backgroundScope)
        runCurrent()

        session.clear()
        runCurrent()

        assertEquals(1, cache.clears)
    }

    @Test
    fun `a start with no session wipes leftovers`() = runTest {
        val cache = FakeCacheStore()

        CacheCleaner(FakeSessionStore(null), cache).start(backgroundScope)
        runCurrent()

        assertEquals(1, cache.clears)
    }

    @Test
    fun `signing in does not wipe the data that is about to be saved`() = runTest {
        val session = FakeSessionStore(null)
        val cache = FakeCacheStore()
        CacheCleaner(session, cache).start(backgroundScope)
        runCurrent()
        assertEquals(1, cache.clears)

        session.save(storedSession("a", "r"))
        runCurrent()

        assertEquals(1, cache.clears)
    }
}