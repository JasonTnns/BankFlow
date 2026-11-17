package com.example.bankflow.core.di

import android.content.Context
import android.os.Build
import androidx.room.Room
import com.example.bankflow.BuildConfig
import com.example.bankflow.core.network.HttpClientFactory
import com.example.bankflow.data.local.db.AppDatabase
import com.example.bankflow.data.local.db.CacheDao
import com.example.bankflow.data.local.prefs.DataStoreAppLockSettings
import com.example.bankflow.data.local.prefs.appPrefsDataStore
import com.example.bankflow.data.local.session.EncryptedSessionStore
import com.example.bankflow.data.local.session.SecretCipher
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.local.session.sessionDataStore
import com.example.bankflow.data.remote.AuthRemoteDataSource
import com.example.bankflow.data.repository.AuthRepositoryImpl
import com.example.bankflow.data.security.DataStoreVaultStorage
import com.example.bankflow.data.security.KeyVault
import com.example.bankflow.data.security.KeystoreAesKeyWrapper
import com.example.bankflow.data.security.KeystoreRsaKeyWrapper
import com.example.bankflow.data.security.VaultCipher
import com.example.bankflow.data.security.VaultKeys
import com.example.bankflow.data.security.VaultStorage
import com.example.bankflow.data.security.vaultDataStore
import com.example.bankflow.domain.repository.AuthRepository
import com.example.bankflow.domain.security.AppLockSettings
import com.example.bankflow.domain.security.SessionVault
import io.ktor.client.HttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import java.time.Clock

private const val AUTH_VALIDITY_SECONDS = 30


@Module
class InfrastructureModule {

    @Single
    fun clock(): Clock = Clock.systemUTC()

    @Single
    fun vaultStorage(context: Context): VaultStorage = DataStoreVaultStorage(context.vaultDataStore)

    @Single
    fun keyVault(storage: VaultStorage, settings: AppLockSettings): KeyVault = KeyVault(
        storage = storage,
        plain = KeystoreAesKeyWrapper("bankflow_vault_plain_v1"),
        protectedWrapper = KeystoreRsaKeyWrapper("bankflow_vault_protected_v1", AUTH_VALIDITY_SECONDS),
        settings = settings,
    )

    @Single
    fun sessionVault(vault: KeyVault): SessionVault = vault

    @Single
    fun vaultKeys(vault: KeyVault): VaultKeys = vault

    @Single
    fun secretCipher(keys: VaultKeys): SecretCipher = VaultCipher(keys)

    @Single
    fun sessionStore(context: Context, cipher: SecretCipher, keys: VaultKeys): SessionStore =
        EncryptedSessionStore(context.sessionDataStore, cipher, keys)

    @Single
    fun httpClient(sessionStore: SessionStore): HttpClient = HttpClientFactory.create(
        baseUrl = BuildConfig.API_BASE_URL,
        sessionStore = sessionStore,
        enableLogging = BuildConfig.DEBUG,
    )

    @Single
    fun authRepository(
        remote: AuthRemoteDataSource,
        sessionStore: SessionStore,
        client: HttpClient,
    ): AuthRepository = AuthRepositoryImpl(remote, sessionStore, client, deviceName = Build.MODEL)

    @Single
    fun appDatabase(context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bankflow.db")
            .fallbackToDestructiveMigration()
            .build()

    @Single
    fun cacheDao(database: AppDatabase): CacheDao = database.cacheDao()

    @Single
    fun appLockSettings(context: Context): AppLockSettings = DataStoreAppLockSettings(context.appPrefsDataStore)
}