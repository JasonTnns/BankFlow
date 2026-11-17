package com.example.bankflow.presentation.security

import androidx.annotation.StringRes
import com.example.bankflow.R
import com.example.bankflow.domain.security.AuthFailure

@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.LOCKED_OUT -> R.string.lock_failure_locked_out
    AuthFailure.UNAVAILABLE -> R.string.lock_failure_unavailable
    AuthFailure.CANCELLED, AuthFailure.ERROR -> R.string.lock_failure_error
}