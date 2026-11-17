package com.example.bankflow.data

import com.example.bankflow.data.security.KeyMode
import com.example.bankflow.data.security.KeyVault
import com.example.bankflow.data.security.StoredKey
import com.example.bankflow.data.security.VaultLockedException
import com.example.bankflow.domain.security.UnlockResult
import com.example.bankflow.domain.security.VaultException
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.testutil.FakeAppLockSettings
import com.example.bankflow.testutil.FakeKeyWrapper
import com.example.bankflow.testutil.FakeVaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KeyVaultTest {

    private val storage = FakeVaultStorage()
    private val plain = FakeKeyWrapper("plain")
    private val protectedWrapper = FakeKeyWrapper("protected", requiresAuth = true)
    private val settings = FakeAppLockSettings(initial = false)

    private fun vault() = KeyVault(storage, plain, protectedWrapper, settings, Dispatchers.Unconfined)

    private fun KeyVault.keyCopy(): ByteArray = currentKey()!!.copyOf()

    private suspend fun KeyVault.isRefused(): Boolean =
        runCatching { ensureOpen() }.exceptionOrNull() is VaultLockedException

    private suspend fun kotlinx.coroutines.test.TestScope.restart(): KeyVault {
        val vault = vault()
        vault.start(backgroundScope)
        runCurrent()
        return vault
    }


    @Test
    fun `it starts unknown and becomes empty when nothing is stored`() = runTest {
        val vault = vault()
        assertEquals(VaultState.UNKNOWN, vault.state.value)

        vault.start(backgroundScope)
        runCurrent()

        assertEquals(VaultState.EMPTY, vault.state.value)
        assertNull(vault.currentKey())
    }

    @Test
    fun `the first sign-in creates a key stored wrapped, never in the clear`() = runTest {
        val vault = vault()

        vault.ensureOpen()

        assertEquals(VaultState.OPEN, vault.state.value)
        assertEquals(32, vault.currentKey()!!.size)
        val stored = storage.stored!!
        assertEquals(KeyMode.PLAIN, stored.mode)
        assertFalse(stored.wrapped.contentEquals(vault.currentKey()))
    }

    @Test
    fun `asking again keeps the same key`() = runTest {
        val vault = vault()
        vault.ensureOpen()
        val first = vault.keyCopy()

        vault.ensureOpen()

        assertArrayEquals(first, vault.currentKey())
        assertEquals(1, storage.writes)
    }

    @Test
    fun `with the lock on, a new key is created already protected, without any prompt`() = runTest {
        settings.state.value = true
        val vault = vault()

        vault.ensureOpen()

        assertEquals(KeyMode.PROTECTED, storage.stored!!.mode)
        assertEquals(VaultState.OPEN, vault.state.value)
    }

    @Test
    fun `if a protected key cannot be created, sign-in still works with a plain one`() = runTest {
        settings.state.value = true
        protectedWrapper.failWrap = true
        val vault = vault()

        vault.ensureOpen()

        assertEquals(KeyMode.PLAIN, storage.stored!!.mode)
        assertEquals(VaultState.OPEN, vault.state.value)
    }

    @Test
    fun `a storage failure while creating leaves the vault empty`() = runTest {
        storage.failOnWrite = true
        val vault = vault()

        val error = runCatching { vault.ensureOpen() }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(VaultState.EMPTY, vault.state.value)
        assertNull(vault.currentKey())
    }

    @Test
    fun `a storage failure at startup counts as nothing stored`() = runTest {
        storage.failOnRead = true

        assertEquals(VaultState.EMPTY, restart().state.value)
    }


    @Test
    fun `a restart with a plain key opens it again with the same key`() = runTest {
        val first = vault()
        first.ensureOpen()
        val original = first.keyCopy()

        val second = restart()

        assertEquals(VaultState.OPEN, second.state.value)
        assertArrayEquals(original, second.currentKey())
    }

    @Test
    fun `a plain key that can no longer be unwrapped is discarded`() = runTest {
        storage.stored = StoredKey(KeyMode.PLAIN, byteArrayOf(9, 9, 9))

        val vault = restart()

        assertEquals(VaultState.EMPTY, vault.state.value)
        assertNull(storage.stored)
    }

    @Test
    fun `a restart with a protected key stays locked until the user authenticates`() = runTest {
        settings.state.value = true
        val first = vault()
        first.ensureOpen()
        val original = first.keyCopy()

        val second = restart()

        assertEquals(VaultState.LOCKED, second.state.value)
        assertNull(second.currentKey())
        assertTrue(second.isRefused())
        assertEquals(UnlockResult.AUTHENTICATION_REQUIRED, second.unlock())
        assertEquals(VaultState.LOCKED, second.state.value)

        protectedWrapper.authenticated = true
        assertEquals(UnlockResult.SUCCESS, second.unlock())

        assertEquals(VaultState.OPEN, second.state.value)
        assertArrayEquals(original, second.currentKey())
    }

    @Test
    fun `a destroyed protected key cannot be recovered, and everything is forgotten`() = runTest {
        settings.state.value = true
        vault().ensureOpen()
        val second = restart()
        protectedWrapper.invalidated = true

        assertEquals(UnlockResult.KEY_INVALIDATED, second.unlock())

        assertEquals(VaultState.EMPTY, second.state.value)
        assertNull(storage.stored)
        assertTrue(plain.destroyed >= 1 && protectedWrapper.destroyed >= 1)
    }

    @Test
    fun `damaged stored data counts as a destroyed key`() = runTest {
        storage.stored = StoredKey(KeyMode.PROTECTED, byteArrayOf(1, 2, 3))
        val vault = restart()
        protectedWrapper.authenticated = true

        assertEquals(UnlockResult.KEY_INVALIDATED, vault.unlock())
        assertEquals(VaultState.EMPTY, vault.state.value)
    }

    @Test
    fun `unlocking something that is already open or empty is a harmless success`() = runTest {
        val empty = restart()
        assertEquals(UnlockResult.SUCCESS, empty.unlock())

        val open = vault()
        open.ensureOpen()
        assertEquals(UnlockResult.SUCCESS, open.unlock())
    }


    @Test
    fun `protecting wraps the SAME key with the protected wrapper and destroys the plain one`() = runTest {
        val vault = vault()
        vault.ensureOpen()
        val original = vault.keyCopy()

        vault.protect()

        assertEquals(KeyMode.PROTECTED, storage.stored!!.mode)
        assertEquals(1, plain.destroyed)
        assertArrayEquals("data encrypted before must stay readable", original, vault.currentKey())
        assertEquals(VaultState.OPEN, vault.state.value)

        val restarted = restart()
        assertEquals(VaultState.LOCKED, restarted.state.value)
        protectedWrapper.authenticated = true
        restarted.unlock()
        assertArrayEquals(original, restarted.currentKey())
    }

    @Test
    fun `protecting twice is harmless`() = runTest {
        val vault = vault()
        vault.ensureOpen()
        vault.protect()
        val writes = storage.writes

        vault.protect()

        assertEquals(writes, storage.writes)
        assertEquals(1, plain.destroyed)
    }

    @Test
    fun `a protected key that cannot be created changes nothing`() = runTest {
        val vault = vault()
        vault.ensureOpen()
        protectedWrapper.failWrap = true

        val error = runCatching { vault.protect() }.exceptionOrNull()

        assertTrue(error is VaultException)
        assertEquals(KeyMode.PLAIN, storage.stored!!.mode)
        assertEquals(0, plain.destroyed)
    }

    @Test
    fun `protecting needs an open vault`() = runTest {
        val empty = restart()
        assertTrue(runCatching { empty.protect() }.exceptionOrNull() is VaultException)

        storage.stored = StoredKey(KeyMode.PROTECTED, byteArrayOf(1))
        val locked = restart()
        assertTrue(runCatching { locked.protect() }.exceptionOrNull() is VaultException)
    }

    @Test
    fun `unprotecting returns to the plain wrapper and destroys the protected key`() = runTest {
        settings.state.value = true
        val vault = vault()
        vault.ensureOpen()
        val original = vault.keyCopy()

        vault.unprotect()

        assertEquals(KeyMode.PLAIN, storage.stored!!.mode)
        assertEquals(1, protectedWrapper.destroyed)
        val restarted = restart()
        assertEquals("a plain key opens without a prompt", VaultState.OPEN, restarted.state.value)
        assertArrayEquals(original, restarted.currentKey())
    }

    @Test
    fun `unprotecting a plain key is harmless`() = runTest {
        val vault = vault()
        vault.ensureOpen()

        vault.unprotect()

        assertEquals(0, protectedWrapper.destroyed)
        assertEquals(KeyMode.PLAIN, storage.stored!!.mode)
    }


    @Test
    fun `reset forgets everything and wipes the key from memory`() = runTest {
        val vault = vault()
        vault.ensureOpen()
        val held = vault.currentKey()!!
        val original = held.copyOf()

        vault.reset()

        assertEquals(VaultState.EMPTY, vault.state.value)
        assertNull(vault.currentKey())
        assertNull(storage.stored)
        assertTrue("the array in memory is zeroed", held.all { it == 0.toByte() })
        assertTrue(plain.destroyed >= 1 && protectedWrapper.destroyed >= 1)

        vault.ensureOpen()
        assertNotNull(vault.currentKey())
        assertFalse("a new sign-in gets a new key", original.contentEquals(vault.currentKey()))
    }
}