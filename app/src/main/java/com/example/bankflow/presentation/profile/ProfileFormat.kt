package com.example.bankflow.presentation.profile

import com.example.bankflow.domain.model.ThemePreference

fun initials(fullName: String): String {
    val words = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"

    fun firstLetter(word: String) = String(Character.toChars(word.codePointAt(0))).uppercase()

    return if (words.size == 1) firstLetter(words.first()) else firstLetter(words.first()) + firstLetter(words.last())
}

fun ThemePreference.themeLabel(): String = when (this) {
    ThemePreference.SYSTEM -> "System default"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

fun ThemePreference.themeDescription(): String = when (this) {
    ThemePreference.SYSTEM -> "Matches your phone's setting"
    ThemePreference.LIGHT -> "Always light"
    ThemePreference.DARK -> "Always dark"
}