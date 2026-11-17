package com.example.bankflow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.example.bankflow.data.local.session.EncryptedSessionStore
import com.example.bankflow.data.local.session.SecretCipher
import com.example.bankflow.data.security.VaultKeys
import com.example.bankflow.data.security.VaultLockedException
import com.example.bankflow.domain.security.VaultState
import com.example.bankflow.testutil.FakeVaultKeys
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class KeyedCipher(private val keys: VaultKeys) : SecretCipher {
        override fun encrypt(plainText: String) = "enc:$plainText"
        override fun decrypt(encoded: String): String? =
            if (keys.currentKey() == null || !encoded.startsWith("enc:")) null else encoded.removePrefix("enc:")
    }

    private class Fixture(
        val store: EncryptedSessionStore,
        val dataStore: DataStore<Preferences>,
        val keys: FakeVaultKeys,
    )

    private fun TestScope.fixture(initial: VaultState = VaultState.OPEN): Fixture {
        val keys = FakeVaultKeys(initial)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(folder.root, "session.preferences_pb") },
        )
        return Fixture(EncryptedSessionStore(dataStore, KeyedCipher(keys), keys), dataStore, keys)
    }


    @Test
    fun `a saved session is read back`() = runTest {
        val f = fixture()

        f.store.save(storedSession("access", "refresh"))

        assertEquals("access", f.store.current()?.accessToken)
        assertEquals("Alice", f.store.current()?.user?.fullName)
    }

    @Test
    fun `the first sign-in creates the key`() = runTest {
        val f = fixture(VaultState.EMPTY)

        f.store.save(storedSession("access", "refresh"))

        assertEquals(1, f.keys.ensureOpenCalls)
        assertEquals(VaultState.OPEN, f.keys.stateFlow.value)
        assertEquals("access", f.store.current()?.accessToken)
    }

    @Test
    fun `updating the user changes the profile fields and leaves the tokens alone`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))

        f.store.updateUser { it.copy(fullName = "Alice Renamed", themePreference = "DARK") }

        val saved = f.store.current()!!
        assertEquals("Alice Renamed", saved.user.fullName)
        assertEquals("DARK", saved.user.themePreference)
        assertEquals("access", saved.accessToken)
        assertEquals("refresh", saved.refreshToken)
    }

    @Test
    fun `updating while signed out does nothing`() = runTest {
        val f = fixture()

        f.store.updateUser { it.copy(fullName = "Ghost") }

        assertNull(f.store.current())
    }

    @Test
    fun `clearing signs out and also forgets the key`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))

        f.store.clear()

        assertNull(f.store.current())
        assertEquals(1, f.keys.resets)
        assertEquals(VaultState.EMPTY, f.keys.stateFlow.value)
    }

    @Test
    fun `updating after logout does not bring the session back`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))
        f.store.clear()

        f.store.updateUser { it.copy(fullName = "Ghost") }

        assertNull(f.store.current())
    }

    @Test
    fun `unreadable stored data counts as signed out`() = runTest {
        val f = fixture()
        f.dataStore.edit { it[stringPreferencesKey("session_blob")] = "garbage" }

        assertNull(f.store.current())
    }


    @Test
    fun `while locked there is no session to report, and asking returns null at once`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))
        f.keys.key = null
        f.keys.stateFlow.value = VaultState.LOCKED

        assertNull(f.store.current())
        f.store.session.test { expectNoEvents() }
    }

    @Test
    fun `when the vault opens the session appears`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))
        f.keys.key = null
        f.keys.stateFlow.value = VaultState.LOCKED

        f.store.session.test {
            expectNoEvents()

            f.keys.key = ByteArray(32) { it.toByte() }
            f.keys.stateFlow.value = VaultState.OPEN

            assertEquals("access", awaitItem()?.accessToken)
        }
    }

    @Test
    fun `nothing can be saved while locked`() = runTest {
        val f = fixture(VaultState.LOCKED)

        val error = runCatching { f.store.save(storedSession("access", "refresh")) }.exceptionOrNull()

        assertTrue(error is VaultLockedException)
    }


    @Test
    fun `a leftover session with no key is signed out`() = runTest {
        val f = fixture()
        f.store.save(storedSession("access", "refresh"))
        f.keys.key = null
        f.keys.stateFlow.value = VaultState.EMPTY

        f.store.session.test { assertNull(awaitItem()) }
    }
}