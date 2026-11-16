package com.example.bankflow.data.local.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bankflow.core.network.NetworkJson
import com.example.bankflow.data.remote.dto.UserDto
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
        dataStore.edit { it[blobKey] = encode(session) }
    }

    override suspend fun updateUser(transform: (UserDto) -> UserDto) {

        dataStore.edit { preferences ->
            val current = decode(preferences[blobKey]) ?: return@edit
            preferences[blobKey] = encode(current.copy(user = transform(current.user)))
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(blobKey) }
    }

    private fun encode(session: StoredSession): String =
        cipher.encrypt(json.encodeToString(StoredSession.serializer(), session))

    private fun decode(blob: String?): StoredSession? {
        if (blob == null) return null
        val plain = cipher.decrypt(blob) ?: return null
        return runCatching { json.decodeFromString(StoredSession.serializer(), plain) }.getOrNull()
    }
}