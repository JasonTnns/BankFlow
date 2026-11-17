package com.example.bankflow.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.bankflow.data.local.db.AppDatabase
import com.example.bankflow.data.local.db.CacheDao
import com.example.bankflow.data.local.db.CacheEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CacheDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: CacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cacheDao()
    }

    @After
    fun tearDown() = database.close()

    private fun entry(user: String, key: String, payload: String = "p", savedAt: Long = 1L) =
        CacheEntryEntity(user, key, savedAt, payload)

    @Test
    fun aSavedRowIsReadBack() = runBlocking {
        dao.put(entry("u1", "dashboard", payload = "secret", savedAt = 42L))

        val row = dao.get("u1", "dashboard")!!

        assertEquals("secret", row.payload)
        assertEquals(42L, row.savedAtEpochMs)
    }

    @Test
    fun savingTheSameKeyAgainReplacesTheRow() = runBlocking {
        dao.put(entry("u1", "dashboard", payload = "old"))
        dao.put(entry("u1", "dashboard", payload = "new", savedAt = 2L))

        assertEquals("new", dao.get("u1", "dashboard")?.payload)
    }

    @Test
    fun rowsAreSeparatedByUserAndByKey() = runBlocking {
        dao.put(entry("u1", "dashboard", payload = "alice"))
        dao.put(entry("u2", "dashboard", payload = "bob"))
        dao.put(entry("u1", "history:acc-1", payload = "other key"))

        assertEquals("alice", dao.get("u1", "dashboard")?.payload)
        assertEquals("bob", dao.get("u2", "dashboard")?.payload)
        assertNull(dao.get("u3", "dashboard"))
    }

    @Test
    fun clearingOneUserLeavesTheOthers() = runBlocking {
        dao.put(entry("u1", "dashboard"))
        dao.put(entry("u2", "dashboard"))

        dao.clearUser("u1")

        assertNull(dao.get("u1", "dashboard"))
        assertEquals("p", dao.get("u2", "dashboard")?.payload)
    }

    @Test
    fun clearingEverythingEmptiesTheTable() = runBlocking {
        dao.put(entry("u1", "dashboard"))
        dao.put(entry("u2", "history:acc-1"))

        dao.clearAll()

        assertNull(dao.get("u1", "dashboard"))
        assertNull(dao.get("u2", "history:acc-1"))
    }
}