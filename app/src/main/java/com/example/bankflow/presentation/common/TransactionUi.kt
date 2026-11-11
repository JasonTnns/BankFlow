package com.example.bankflow.presentation.common

import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.Transaction
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus

fun Category.label(): String = when (this) {
    Category.FOOD -> "Food"
    Category.TRANSPORT -> "Transport"
    Category.SHOPPING -> "Shopping"
    Category.BILLS -> "Bills"
    Category.ENTERTAINMENT -> "Entertainment"
    Category.HEALTH -> "Health"
    Category.INCOME -> "Income"
    Category.TRANSFER -> "Transfer"
    Category.OTHER -> "Other"
}

fun AccountType.label(): String = when (this) {
    AccountType.CHECKING -> "Checking"
    AccountType.SAVINGS -> "Savings"
}

fun Transaction.signedAmount(locale: Locale = Locale.getDefault()): String {
    val formatted = amount.format(locale)
    return if (direction == Direction.IN) "+$formatted" else "\u2212$formatted"
}

fun Transaction.subtitle(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val what = description?.takeIf { it.isNotBlank() } ?: category.label()
    return "$what \u00B7 ${createdAt.dayLabel(now, zone, locale)}"
}

fun Transaction.spokenDescription(
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val verb = if (direction == Direction.IN) "Received" else "Sent"
    val preposition = if (direction == Direction.IN) "from" else "to"
    val what = description?.takeIf { it.isNotBlank() } ?: category.label()
    return "$verb ${amount.format(locale)} $preposition ${counterparty.name}, $what, " +
            createdAt.dayLabel(now, zone, locale)
}

fun Direction.label(): String = when (this) {
    Direction.IN -> "Money in"
    Direction.OUT -> "Money out"
}

fun TransferStatus.label(): String = when (this) {
    TransferStatus.PENDING -> "Pending"
    TransferStatus.COMPLETED -> "Completed"
    TransferStatus.FAILED -> "Failed"
    TransferStatus.REVERSED -> "Reversed"
}

fun TransferKind.label(): String = when (this) {
    TransferKind.TRANSFER -> "Transfer"
    TransferKind.DEPOSIT -> "Deposit"
}