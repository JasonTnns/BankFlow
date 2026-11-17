package com.example.bankflow.domain.security

interface BiometricSupport {
    fun availability(): BiometricAvailability
}