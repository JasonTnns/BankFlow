package com.example.bankflow.domain.model

import com.example.bankflow.domain.common.toAsciiDigits


data class Money(
    val minorUnits: Long,
    val currency: String = DEFAULT_CURRENCY,
) : Comparable<Money> {

    init {
        require(currency.length == 3) { "Currency must be a 3-letter code" }
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    operator fun unaryMinus(): Money = copy(minorUnits = Math.negateExact(minorUnits))

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
    }

    companion object {
        const val DEFAULT_CURRENCY = "USD"

        /** Largest single transfer the backend accepts: $1,000,000.00. */
        const val MAX_TRANSFER_MINOR = 100_000_000L

        private val DECIMAL_INPUT = Regex("""^(\d{1,12})(?:\.(\d{1,2}))?$""")

        fun zero(currency: String = DEFAULT_CURRENCY) = Money(0, currency)

        fun parse(text: String, currency: String = DEFAULT_CURRENCY): Money? {
            val match = DECIMAL_INPUT.matchEntire(text.trim().toAsciiDigits()) ?: return null
            val whole = match.groupValues[1].toLong()
            val cents = match.groupValues[2].padEnd(2, '0').toLong()
            return Money(whole * 100 + cents, currency)
        }
    }
}