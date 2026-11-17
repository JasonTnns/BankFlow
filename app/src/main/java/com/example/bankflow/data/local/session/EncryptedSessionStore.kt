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
import com.example.bankflow.data.security.VaultKeys
import com.example.bankflow.domain.security.VaultState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@OptIn(ExperimentalCoroutinesApi::class)
class EncryptedSessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    private val keys: VaultKeys,
    private val json: Json = NetworkJson,
) : SessionStore {

    private val blobKey = stringPreferencesKey("session_blob")

    private val blobs: Flow<String?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[blobKey] }

    override val session: Flow<StoredSession?> = keys.state
        .flatMapLatest { state ->
            when (state) {
                VaultState.UNKNOWN, VaultState.LOCKED -> emptyFlow<StoredSession?>()
                VaultState.EMPTY, VaultState.OPEN -> blobs.map { blob -> decode(blob) }
            }
        }
        .distinctUntilChanged()

    override suspend fun current(): StoredSession? {
        val state = keys.state.first { it != VaultState.UNKNOWN }
        return if (state == VaultState.OPEN) decode(blobs.first()) else null
    }

    override suspend fun save(session: StoredSession) {
        keys.ensureOpen()
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
        keys.reset()
    }

    private fun encode(session: StoredSession): String =
        cipher.encrypt(json.encodeToString(StoredSession.serializer(), session))

    private fun decode(blob: String?): StoredSession? {
        if (blob == null) return null
        val plain = cipher.decrypt(blob) ?: return null
        return runCatching { json.decodeFromString(StoredSession.serializer(), plain) }.getOrNull()
    }
}