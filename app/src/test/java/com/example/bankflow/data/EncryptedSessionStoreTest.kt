package com.example.bankflow.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bankflow.data.local.session.EncryptedSessionStore
import com.example.bankflow.data.local.session.SecretCipher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EncryptedSessionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class PrefixCipher : SecretCipher {
        override fun encrypt(plainText: String) = "enc:$plainText"
        override fun decrypt(encoded: String): String? =
            if (encoded.startsWith("enc:")) encoded.removePrefix("enc:") else null
    }

    private fun kotlinx.coroutines.test.TestScope.newStore(): Pair<EncryptedSessionStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(folder.root, "session.preferences_pb") },
        )
        return EncryptedSessionStore(dataStore, PrefixCipher()) to dataStore
    }

    @Test
    fun `a saved session is read back`() = runTest {
        val (store, _) = newStore()

        store.save(storedSession("access", "refresh"))

        assertEquals("access", store.current()?.accessToken)
        assertEquals("Alice", store.current()?.user?.fullName)
    }

    @Test
    fun `updating the user changes the profile fields and leaves the tokens alone`() = runTest {
        val (store, _) = newStore()
        store.save(storedSession("access", "refresh"))

        store.updateUser { it.copy(fullName = "Alice Renamed", themePreference = "DARK") }

        val saved = store.current()!!
        assertEquals("Alice Renamed", saved.user.fullName)
        assertEquals("DARK", saved.user.themePreference)
        assertEquals("alice@example.com", saved.user.email)
        assertEquals("access", saved.accessToken)
        assertEquals("refresh", saved.refreshToken)
    }

    @Test
    fun `updating while signed out does nothing`() = runTest {
        val (store, _) = newStore()

        store.updateUser { it.copy(fullName = "Ghost") }

        assertNull(store.current())
    }

    @Test
    fun `updating after logout does not bring the session back`() = runTest {
        val (store, _) = newStore()
        store.save(storedSession("access", "refresh"))
        store.clear()

        store.updateUser { it.copy(fullName = "Ghost") }

        assertNull(store.current())
    }

    @Test
    fun `unreadable stored data counts as signed out and is not modified`() = runTest {
        val (store, dataStore) = newStore()
        dataStore.edit { it[stringPreferencesKey("session_blob")] = "garbage" }

        store.updateUser { it.copy(fullName = "Ghost") }

        assertNull(store.current())
    }
}