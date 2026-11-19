package com.example.bankflow.domain.common

import com.example.bankflow.testutil.toArabicIndic
import com.example.bankflow.testutil.toFullWidthDigits
import com.example.bankflow.testutil.toPersianDigits
import com.example.bankflow.testutil.withArabicDecimalSeparator
import org.junit.Assert.assertEquals
import org.junit.Test

class DigitsTest {

    @Test
    fun `digits from other scripts become plain digits`() {
        assertEquals("0123456789", "0123456789".toArabicIndic().toAsciiDigits())
        assertEquals("0123456789", "0123456789".toPersianDigits().toAsciiDigits())
        assertEquals("0123456789", "0123456789".toFullWidthDigits().toAsciiDigits())
    }

    @Test
    fun `plain digits and other text are left alone`() {
        assertEquals("200000000001", "200000000001".toAsciiDigits())
        assertEquals("abc-25 x", "abc-25 x".toArabicIndic().toAsciiDigits())
        assertEquals("", "".toAsciiDigits())
    }

    @Test
    fun `the arabic decimal separator becomes a point`() {
        assertEquals("25.5", "25.5".toArabicIndic().withArabicDecimalSeparator().toAsciiDigits())
    }

    @Test
    fun `the arabic thousands separator is not guessed at`() {
        assertEquals("1\u066C000", "1\u066C000".toArabicIndic().toAsciiDigits().replace("\u0661", "1"))
    }

    @Test
    fun `mixed scripts in one number all normalise`() {
        val mixed = "1" + "2".toArabicIndic() + "3".toPersianDigits() + "4".toFullWidthDigits()

        assertEquals("1234", mixed.toAsciiDigits())
    }
}