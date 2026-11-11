package com.example.bankflow.presentation.common

import com.example.bankflow.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun Money.format(locale: Locale = Locale.getDefault()): String {
    val formatter = NumberFormat.getCurrencyInstance(locale)
    runCatching { Currency.getInstance(currency) }.getOrNull()?.let { formatter.currency = it }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(BigDecimal.valueOf(minorUnits, 2))
}

fun currencySymbol(currencyCode: String, locale: Locale = Locale.getDefault()): String =
    runCatching { Currency.getInstance(currencyCode).getSymbol(locale) }.getOrDefault(currencyCode)