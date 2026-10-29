package com.example.bankflow.domain.model

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class User(
    val id: String,
    val email: String,
    val fullName: String,
    val themePreference: ThemePreference
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long
)

data class AuthSession(
    val tokens: AuthTokens,
    val user: User
)