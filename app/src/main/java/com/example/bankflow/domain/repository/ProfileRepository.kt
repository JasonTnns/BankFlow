package com.example.bankflow.domain.repository

import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference

interface ProfileRepository {
    suspend fun getProfile(): AppResult<Profile>
    suspend fun updateProfile(fullName: String?, themePreference: ThemePreference?): AppResult<Profile>
}