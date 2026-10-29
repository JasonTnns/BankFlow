package com.example.bankflow.domain.model

data class `Money.kt`(
    val minorUnits: Long,
    val currency: String = DEFAULT_CURRENCY,
) : Comparable<`Money.kt`> {

    init {
        require(currency.length == 3) { "Currency must be a 3-letter code" }
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    operator fun plus(other: `Money.kt`): `Money.kt` {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: `Money.kt`): `Money.kt` {
        requireSameCurrency(other)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    operator fun unaryMinus(): `Money.kt` = copy(minorUnits = Math.negateExact(minorUnits))

    override fun compareTo(other: `Money.kt`): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    private fun requireSameCurrency(other: `Money.kt`) {
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }
    }

    companion object {
        const val DEFAULT_CURRENCY = "USD"

        const val MAX_TRANSFER_MINOR = 100_000_000L

        private val DECIMAL_INPUT = Regex("""^(\d{1,12})(?:\.(\d{1,2}))?$""")

        fun zero(currency: String = DEFAULT_CURRENCY) = `Money.kt`(0, currency)

        fun parse(text: String, currency: String = DEFAULT_CURRENCY): `Money.kt`? {
            val match = DECIMAL_INPUT.matchEntire(text.trim()) ?: return null
            val whole = match.groupValues[1].toLong()
            val cents = match.groupValues[2].padEnd(2, '0').toLong()
            return `Money.kt`(whole * 100 + cents, currency)
        }
    }
}