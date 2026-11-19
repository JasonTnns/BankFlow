package com.example.bankflow.presentation.common

import com.example.bankflow.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun Instant.formatDate(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(zone).format(this)

fun Instant.dayLabel(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): UiText {
    val day = atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) {
        today -> UiText.Res(R.string.date_today)
        today.minusDays(1) -> UiText.Res(R.string.date_yesterday)
        else -> UiText.Plain(formatDate(zone, locale))
    }
}

fun Instant.formatDateTime(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = DateTimeFormatter
    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    .withLocale(locale)
    .withZone(zone)
    .format(this)