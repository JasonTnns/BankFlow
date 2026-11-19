package com.example.bankflow.presentation.common

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import com.example.bankflow.domain.model.Money

sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    data class Plain(val value: String) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Res -> {
        val formatted = args.map { it.forDisplay() }
        if (formatted.isEmpty()) stringResource(id) else stringResource(id, *formatted.toTypedArray())
    }
    is UiText.Plural -> {
        val resources = LocalContext.current.resources
        LocalConfiguration.current
        val formatted = args.map { it.forDisplay() }
        resources.getQuantityString(id, count, count, *formatted.toTypedArray())
    }
}

@Composable
fun uiString(@StringRes id: Int, vararg args: Any): String = UiText.Res(id, args.toList()).resolve()

@Composable
private fun Any.forDisplay(): Any = when (this) {
    is UiText -> resolve()
    is Money -> isolate(format())
    is String -> isolate(this)
    else -> this
}

fun isolate(text: String): String = BidiFormatter.getInstance().unicodeWrap(text)