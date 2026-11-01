package com.example.bankflow.data.local.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bankflow.core.network.NetworkJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class EncryptedSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val json: Json = NetworkJson,
) : SessionStore {

    private val blobKey = stringPreferencesKey("session_blob")

    override val session: Flow<StoredSession?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> decode(preferences[blobKey]) }
        .distinctUntilChanged()

    override suspend fun current(): StoredSession? = session.first()

    override suspend fun save(session: StoredSession) {
        val blob = cipher.encrypt(json.encodeToString(StoredSession.serializer(), session))
        dataStore.edit { it[blobKey] = blob }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(blobKey) }
    }

    private fun decode(blob: String?): StoredSession? {
        if (blob == null) return null
        val plain = cipher.decrypt(blob) ?: return null
        return runCatching { json.decodeFromString(StoredSession.serializer(), plain) }.getOrNull()
    }
}