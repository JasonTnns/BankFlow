package com.example.bankflow.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.Base64

val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault")

class DataStoreVaultStorage(private val dataStore: DataStore<Preferences>) : VaultStorage {

    private val modeKey = stringPreferencesKey("mode")
    private val wrappedKey = stringPreferencesKey("wrapped_key")

    override suspend fun read(): StoredKey? {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()

        val mode = preferences[modeKey]?.let { name -> KeyMode.entries.firstOrNull { it.name == name } } ?: return null
        val wrapped = preferences[wrappedKey]?.let { text ->
            try {
                Base64.getDecoder().decode(text)
            } catch (e: IllegalArgumentException) {
                null
            }
        } ?: return null

        return StoredKey(mode, wrapped)
    }

    override suspend fun write(key: StoredKey) {
        dataStore.edit {
            it[modeKey] = key.mode.name
            it[wrappedKey] = Base64.getEncoder().encodeToString(key.wrapped)
        }
    }

    override suspend fun clear() {
        dataStore.edit {
            it.remove(modeKey)
            it.remove(wrappedKey)
        }
    }
}