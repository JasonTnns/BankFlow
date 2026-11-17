package com.example.bankflow.presentation.transfer

import androidx.compose.runtime.Composable
import com.example.bankflow.R
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.common.UiText
import com.example.bankflow.presentation.common.resolve
import com.example.bankflow.presentation.common.userText

fun AppError.transferFailureText(): UiText = when (this) {
    is AppError.Validation -> UiText.Res(R.string.transfer_failure_validation)
    else -> userText()
}

fun AppError.unknownOutcomeText(): UiText {
    val lead = when (this) {
        AppError.NoConnection -> R.string.unknown_outcome_lost_connection
        AppError.Timeout -> R.string.unknown_outcome_slow
        else -> R.string.unknown_outcome_generic
    }
    return UiText.Res(R.string.unknown_outcome_format, listOf(UiText.Res(lead)))
}

@Composable
fun AppError.transferFailureMessage(): String = transferFailureText().resolve()

@Composable
fun AppError.unknownOutcomeMessage(): String = unknownOutcomeText().resolve()