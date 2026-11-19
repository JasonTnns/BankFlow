package com.example.bankflow.presentation

import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

val ARABIC: Locale = Locale.forLanguageTag("ar")

@Composable
fun WithLocale(locale: Locale, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val config = remember(locale) {
        Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localized = remember(config) { base.createConfigurationContext(config) }
    val direction = if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides config,
        LocalLayoutDirection provides direction,
        content = content,
    )
}

class DefaultLocaleRule(private val locale: Locale) : TestWatcher() {
    private lateinit var previous: Locale

    override fun starting(description: Description) {
        previous = Locale.getDefault()
        Locale.setDefault(locale)
    }

    override fun finished(description: Description) = Locale.setDefault(previous)
}