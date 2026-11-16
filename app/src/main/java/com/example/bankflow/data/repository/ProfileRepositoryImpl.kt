package com.example.bankflow.data.repository

import com.example.bankflow.data.local.session.SessionStore
import com.example.bankflow.data.remote.ProfileRemoteDataSource
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

@Single
class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource,
    private val sessionStore: SessionStore,
) : ProfileRepository {

    override suspend fun getProfile(): AppResult<Profile> = remote.getProfile().syncSession()

    override suspend fun updateProfile(
        fullName: String?,
        themePreference: ThemePreference?,
    ): AppResult<Profile> = remote.updateProfile(fullName, themePreference).syncSession()

    private suspend fun AppResult<Profile>.syncSession(): AppResult<Profile> {
        if (this is AppResult.Success) {
            try {
                sessionStore.updateUser {
                    it.copy(fullName = value.user.fullName, themePreference = value.user.themePreference.name)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        return this
    }
}