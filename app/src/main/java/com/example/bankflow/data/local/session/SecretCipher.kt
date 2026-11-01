package com.example.bankflow.data.local.session

interface SecretCipher {
    fun encrypt(plainText: String): String

    fun decrypt(encoded: String): String?
}