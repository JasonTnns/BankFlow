package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Profile
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.User
import com.example.bankflow.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant

fun testProfile(name: String = "Alice Demo", theme: ThemePreference = ThemePreference.SYSTEM) = Profile(
    user = User("u1", "alice@example.com", name, theme),
    dailyTransferLimit = Money(500_000),
    memberSince = Instant.parse("2026-01-15T10:00:00Z"),
)

class StubProfileRepository : ProfileRepository {
    var profile: Profile = testProfile()

    /** null means "succeed with the current [profile]". */
    var getResult: AppResult<Profile>? = null
    var updateError: AppError? = null

    var getGate: CompletableDeferred<Unit>? = null
    var updateGate: CompletableDeferred<Unit>? = null

    var getCalls = 0
    val updates = mutableListOf<Pair<String?, ThemePreference?>>()

    override suspend fun getProfile(): AppResult<Profile> {
        getCalls++
        val snapshot = getResult ?: AppResult.Success(profile)
        getGate?.await()
        return snapshot
    }

    override suspend fun updateProfile(fullName: String?, themePreference: ThemePreference?): AppResult<Profile> {
        updates += fullName to themePreference
        updateGate?.await()
        updateError?.let { return AppResult.Failure(it) }
        profile = profile.copy(
            user = profile.user.copy(
                fullName = fullName ?: profile.user.fullName,
                themePreference = themePreference ?: profile.user.themePreference,
            ),
        )
        return AppResult.Success(profile)
    }
}