package com.example.bankflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2 contrast: 4.5:1 for text, 3:1 for the borders of controls. Every colour pair the screens use. */
class ThemeContrastTest {

    private class Check(val name: String, val foreground: Color, val background: Color, val minimum: Double = 4.5)

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun checks(label: String, s: ColorScheme) = listOf(
        Check("$label onPrimary on primary (buttons)", s.onPrimary, s.primary),
        Check("$label onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Check("$label onSecondary on secondary", s.onSecondary, s.secondary),
        Check("$label onSecondaryContainer on secondaryContainer (icons)", s.onSecondaryContainer, s.secondaryContainer),
        Check("$label onSurface on surface (body text)", s.onSurface, s.surface),
        Check("$label onBackground on background", s.onBackground, s.background),
        Check("$label onSurfaceVariant on surface (hints, captions)", s.onSurfaceVariant, s.surface),
        Check("$label onSurfaceVariant on surfaceVariant (cards)", s.onSurfaceVariant, s.surfaceVariant),
        Check("$label onSurface on surfaceVariant", s.onSurface, s.surfaceVariant),
        Check("$label primary on surface (titles)", s.primary, s.surface),
        Check("$label secondary on surface (incoming amounts)", s.secondary, s.surface),
        Check("$label error on surface (error text)", s.error, s.surface),
        Check("$label onErrorContainer on errorContainer (banners)", s.onErrorContainer, s.errorContainer),
        Check("$label outline on surface (field borders)", s.outline, s.surface, minimum = 3.0),
    )

    @Test
    fun `every text and control colour pair is readable in light and dark`() {
        val failures = (checks("light", LightColors) + checks("dark", DarkColors)).mapNotNull { check ->
            val ratio = contrast(check.foreground, check.background)
            if (ratio < check.minimum) "${check.name}: ${"%.2f".format(ratio)} (needs ${check.minimum})" else null
        }

        assertTrue("Low contrast:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the contrast formula is right`() {
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.White) in 0.99..1.01)
    }
}