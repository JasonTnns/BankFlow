package com.example.bankflow.domain.common

val AppError.isOutcomeUnknown: Boolean
    get() = isRetryable || this is AppError.Unknown