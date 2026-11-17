package com.example.bankflow.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.bankflow.domain.model.Money

sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plain(val value: String) : UiText
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Res -> {
        val formatted = args.map { arg ->
            when (arg) {
                is Money -> arg.format()
                is UiText -> arg.resolve()
                else -> arg
            }
        }.toTypedArray()
        stringResource(id, *formatted)
    }
}