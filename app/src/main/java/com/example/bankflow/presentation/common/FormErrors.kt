package com.example.bankflow.presentation.common

import com.example.bankflow.domain.common.AppError


fun AppError?.withoutFieldError(field: String): AppError? = when (this) {
    null -> null
    is AppError.Validation -> {
        val serverText = fieldErrors - field
        val ourIssues = issues - field
        if (serverText.isEmpty() && ourIssues.isEmpty()) null else copy(fieldErrors = serverText, issues = ourIssues)
    }
    else -> null
}

fun AppError?.toBanner(): AppError? = when (this) {
    null, AppError.EmailTaken -> null
    is AppError.Validation -> if (fields.isEmpty()) this else null
    else -> this
}