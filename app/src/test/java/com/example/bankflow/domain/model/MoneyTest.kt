package com.example.bankflow.domain.model

import com.example.bankflow.testutil.toArabicIndic
import com.example.bankflow.testutil.toFullWidthDigits
import com.example.bankflow.testutil.toPersianDigits
import com.example.bankflow.testutil.withArabicDecimalSeparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun `parse reads whole and fractional amounts exactly`() {
        val cases = mapOf(
            "12.34" to 1234L,
            "7" to 700L,
            "0.5" to 50L,
            "0.05" to 5L,
            "1000" to 100_000L,
            " 3.10 " to 310L,
        )
        cases.forEach { (text, expected) ->
            assertEquals("parsing '$text'", expected, Money.parse(text)?.minorUnits)
        }
    }

    @Test
    fun `parse rejects anything that is not a plain amount`() {
        listOf("12.345", "-5", "+5", "abc", "", "1,000", ".5", "5.", "1e3", "12.3.4").forEach { text ->
            assertNull("'$text' should be rejected", Money.parse(text))
        }
    }

    @Test
    fun `arithmetic is exact where floating point is not`() {
        assertEquals(Money(30), Money.parse("0.10")!! + Money.parse("0.20")!!)
        assertEquals(Money(1999), Money.parse("19.99"))
    }

    @Test
    fun `plus minus and negation`() {
        assertEquals(Money(150), Money(100) + Money(50))
        assertEquals(Money(50), Money(100) - Money(50))
        assertEquals(Money(-100), -Money(100))
    }

    @Test
    fun `mixing currencies fails loudly`() {
        assertThrows(IllegalArgumentException::class.java) { Money(100, "USD") + Money(100, "EUR") }
        assertThrows(IllegalArgumentException::class.java) { Money(100, "USD") < Money(100, "EUR") }
    }

    @Test
    fun `overflow fails loudly instead of wrapping around`() {
        assertThrows(ArithmeticException::class.java) { Money(Long.MAX_VALUE) + Money(1) }
    }

    @Test
    fun `comparison and sign helpers`() {
        assertTrue(Money(100) < Money(200))
        assertTrue(Money.zero().isZero)
        assertTrue(Money(1).isPositive)
        assertTrue(Money(-1).isNegative)
        assertFalse(Money(0).isPositive)
    }

    @Test
    fun `currency code must be three letters`() {
        assertThrows(IllegalArgumentException::class.java) { Money(100, "US") }
    }

    @Test
    fun `amounts typed with another script's digits parse exactly`() {
        assertEquals(Money(2_550), Money.parse("25.5".toArabicIndic().withArabicDecimalSeparator()))
        assertEquals(Money(100_000), Money.parse("1000".toPersianDigits()))
        assertEquals(Money(5), Money.parse("0.05".toFullWidthDigits()))
    }

    @Test
    fun `a thousands separator is still rejected, in any script`() {
        assertNull(Money.parse("1\u066C000".toArabicIndic()))
        assertNull(Money.parse("1,000".toArabicIndic()))
    }
}