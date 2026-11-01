package com.example.bankflow.core.di

import android.content.Context
import android.os.Build
import com.example.bankflow.BuildConfig
import com.example.bankflow.core.network.HttpClientFactory
import com.example.bankflow.data.local.session.EncryptedSessionStore
import com.example.bankflow.data.local.session.KeystoreSecretCipher
import com.example.bankflow.data.local.session.SecretCipher
import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.local.session.sessionDataStore
import com.example.bankflow.data.remote.AuthRemoteDataSource
import com.example.bankflow.data.repository.AuthRepositoryImpl
import com.example.bankflow.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
class InfrastructureModule {

    @Single
    fun secretCipher(): SecretCipher = KeystoreSecretCipher()

    @Single
    fun sessionStore(context: Context, cipher: SecretCipher): SessionStore =
        EncryptedSessionStore(context.sessionDataStore, cipher)

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
}