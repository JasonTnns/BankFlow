package com.example.bankflow.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.bankflow.data.security.DataStoreVaultStorage
import com.example.bankflow.data.security.KeyMode
import com.example.bankflow.data.security.StoredKey
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreVaultStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.newStorage(): Pair<DataStoreVaultStorage, DataStore<Preferences>> {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(folder.root, "vault.preferences_pb") },
        )
        return DataStoreVaultStorage(dataStore) to dataStore
    }

    @Test
    fun `nothing stored reads as null`() = runTest {
        assertNull(newStorage().first.read())
    }

    @Test
    fun `a key survives the round trip with its mode and every byte`() = runTest {
        val (storage, _) = newStorage()
        val wrapped = ByteArray(256) { it.toByte() }

        storage.write(StoredKey(KeyMode.PROTECTED, wrapped))

        val read = storage.read()!!
        assertEquals(KeyMode.PROTECTED, read.mode)
        assertArrayEquals(wrapped, read.wrapped)
    }

    @Test
    fun `writing again replaces it`() = runTest {
        val (storage, _) = newStorage()
        storage.write(StoredKey(KeyMode.PLAIN, byteArrayOf(1)))

        storage.write(StoredKey(KeyMode.PROTECTED, byteArrayOf(2)))

        assertEquals(KeyMode.PROTECTED, storage.read()!!.mode)
        assertArrayEquals(byteArrayOf(2), storage.read()!!.wrapped)
    }

    @Test
    fun `clearing removes it`() = runTest {
        val (storage, _) = newStorage()
        storage.write(StoredKey(KeyMode.PLAIN, byteArrayOf(1)))

        storage.clear()

        assertNull(storage.read())
    }

    @Test
    fun `damaged or unknown data reads as nothing stored`() = runTest {
        val (storage, dataStore) = newStorage()

        dataStore.edit {
            it[stringPreferencesKey("mode")] = "PROTECTED"
            it[stringPreferencesKey("wrapped_key")] = "!!! not base64 !!!"
        }
        assertNull(storage.read())

        dataStore.edit {
            it[stringPreferencesKey("mode")] = "SOMETHING_NEW"
            it[stringPreferencesKey("wrapped_key")] = "AQID"
        }
        assertNull(storage.read())
    }
}