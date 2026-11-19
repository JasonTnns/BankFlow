package com.example.bankflow.presentation.common

import com.example.bankflow.R
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Transaction
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

fun Transaction.signedAmount(locale: Locale = Locale.getDefault()): String {
    val formatted = amount.format(locale)
    return if (direction == Direction.IN) "+$formatted" else "\u2212$formatted"
}

private fun Transaction.whatText(): UiText =
    description?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) } ?: UiText.Res(category.labelRes())

fun Transaction.subtitleText(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): UiText = UiText.Res(R.string.pair_dot, listOf(whatText(), createdAt.dayLabel(now, zone, locale)))

fun Transaction.spokenText(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): UiText = UiText.Res(
    if (direction == Direction.IN) R.string.transaction_spoken_in else R.string.transaction_spoken_out,
    listOf(amount, counterparty.name, whatText(), createdAt.dayLabel(now, zone, locale)),
)