package com.example.bankflow.presentation.profile

import com.example.bankflow.domain.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileFormatTest {

    @Test
    fun `initials are the first letters of the first and last word`() {
        assertEquals("AD", initials("Alice Demo"))
        assertEquals("AS", initials("Alice Maria Smith"))
    }

    @Test
    fun `a single name gives a single letter`() {
        assertEquals("A", initials("alice"))
    }

    @Test
    fun `extra spaces and lower case are handled`() {
        assertEquals("AD", initials("   alice    demo  "))
    }

    @Test
    fun `an empty name still gives something to draw`() {
        assertEquals("?", initials(""))
        assertEquals("?", initials("   "))
    }

    @Test
    fun `a letter outside the basic plane is not cut in half`() {
        assertEquals("\uD835\uDC00\uD835\uDC01", initials("\uD835\uDC00lice \uD835\uDC01ob"))
    }

    @Test
    fun `every theme has its own label and description`() {
        val labels = ThemePreference.entries.map { it.themeLabel() }
        val descriptions = ThemePreference.entries.map { it.themeDescription() }

        assertEquals(labels.toSet().size, labels.size)
        assertEquals(descriptions.toSet().size, descriptions.size)
        assertNotEquals("", labels.first())
        assertEquals("System default", ThemePreference.SYSTEM.themeLabel())
    }
}