package com.example.bankflow.presentation.common

import com.example.bankflow.domain.common.AppError

fun AppError?.withoutFieldError(field: String): AppError? = when (this) {
    null -> null
    is AppError.Validation -> {
        val remaining = fieldErrors - field
        if (remaining.isEmpty()) null else copy(fieldErrors = remaining)
    }
    else -> null
}

fun AppError?.toBanner(): AppError? = when (this) {
    null, AppError.EmailTaken -> null // EmailTaken is shown under the email field
    is AppError.Validation -> if (fieldErrors.isEmpty()) this else null
    else -> this
}