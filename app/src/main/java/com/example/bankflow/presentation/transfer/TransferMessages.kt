package com.example.bankflow.presentation.transfer

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.presentation.common.userMessage

fun AppError.transferFailureMessage(): String = when (this) {
    is AppError.Validation -> "Some details of this transfer weren't accepted. Please check them and try again."
    else -> userMessage()
}

fun AppError.unknownOutcomeMessage(): String {
    val lead = when (this) {
        AppError.NoConnection -> "We lost the connection while sending your transfer."
        AppError.Timeout -> "Your transfer is taking longer than expected."
        else -> "Something went wrong while sending your transfer."
    }
    return "$lead We couldn't confirm whether it went through. It's safe to try again: it won't be sent twice."
}