package com.example.bankflow.presentation.common

import com.example.bankflow.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyFormatTest {

    @Test
    fun `formats cents exactly`() {
        assertEquals("$1,234.56", Money(123_456).format(Locale.US))
        assertEquals("$0.05", Money(5).format(Locale.US))
        assertEquals("$0.00", Money(0).format(Locale.US))
        assertEquals("$19.99", Money(1999).format(Locale.US))
    }

    @Test
    fun `negative amounts keep their sign`() {
        assertEquals("-$2.50", Money(-250).format(Locale.US))
    }

    @Test
    fun `very large amounts do not lose precision`() {
        assertEquals("$90,071,992,547,409.91", Money(9_007_199_254_740_991).format(Locale.US))
    }

    @Test
    fun `uses the amount's own currency`() {
        assertEquals("€10.00", Money(1000, "EUR").format(Locale.US))
    }
}