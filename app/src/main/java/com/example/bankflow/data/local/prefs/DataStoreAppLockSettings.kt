package com.example.bankflow.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.bankflow.domain.security.AppLockSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
val Context.appPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class DataStoreAppLockSettings(private val dataStore: DataStore<Preferences>) : AppLockSettings {

    private val enabledKey = booleanPreferencesKey("app_lock_enabled")

    override val enabled: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[enabledKey] ?: false }
        .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[enabledKey] = enabled }
    }
}