package com.example.bankflow.presentation.common

import com.example.bankflow.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

fun lastUpdatedText(
    savedAt: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): UiText {
    val age = Duration.between(savedAt, now)
    return when {
        age < Duration.ofMinutes(1) -> UiText.Res(R.string.saved_data_just_now)
        age < Duration.ofHours(1) -> UiText.Res(R.string.saved_data_minutes, listOf(age.toMinutes()))
        age < Duration.ofHours(24) -> UiText.Res(R.string.saved_data_hours, listOf(age.toHours()))
        else -> UiText.Res(R.string.saved_data_date, listOf(savedAt.formatDate(zone, locale)))
    }
}