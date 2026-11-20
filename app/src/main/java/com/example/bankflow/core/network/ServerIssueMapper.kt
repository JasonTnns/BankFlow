package com.example.bankflow.core.network

import com.example.bankflow.domain.common.FieldIssue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object ServerIssueMapper {

    fun formField(serverField: String): String = when (serverField) {
        "amountMinor" -> "amount"
        "destinationAccountNumber", "destinationAccountId" -> "destination"
        else -> serverField
    }

    fun map(serverField: String, code: String, params: JsonObject?): FieldIssue? {
        val min = params.int("min")
        val max = params.int("max")

        return when (formField(serverField)) {
            "email" -> when (code) {
                "REQUIRED" -> FieldIssue.EmailRequired
                "INVALID_EMAIL", "INVALID_FORMAT", "INVALID_TYPE", "TOO_LONG" -> FieldIssue.EmailInvalid
                else -> null
            }
            "password" -> when (code) {
                "REQUIRED" -> FieldIssue.PasswordRequired
                "TOO_SHORT" -> FieldIssue.PasswordTooShort(min ?: DEFAULT_MIN_PASSWORD)
                "PASSWORD_NEEDS_LETTER_AND_NUMBER" -> FieldIssue.PasswordNeedsLetterAndNumber
                "PASSWORD_TOO_LONG", "TOO_LONG" -> FieldIssue.PasswordTooLong
                else -> null
            }
            "fullName" -> when (code) {
                "REQUIRED", "INVALID_TYPE" -> FieldIssue.NameRequired
                "TOO_LONG" -> FieldIssue.NameTooLong(max ?: DEFAULT_MAX_NAME)
                else -> null
            }
            "nickname" -> when (code) {
                "REQUIRED", "INVALID_TYPE" -> FieldIssue.NicknameRequired
                "TOO_LONG" -> FieldIssue.NicknameTooLong(max ?: DEFAULT_MAX_NICKNAME)
                else -> null
            }
            "accountNumber" -> when (code) {
                "REQUIRED", "INVALID_FORMAT", "INVALID_TYPE", "TOO_SHORT", "TOO_LONG" -> FieldIssue.AccountNumberInvalid
                "OWN_ACCOUNT" -> FieldIssue.OwnAccount
                else -> null
            }
            "destination" -> when (code) {
                "REQUIRED", "DESTINATION_CONFLICT", "INVALID_UUID" -> FieldIssue.DestinationRequired
                "INVALID_FORMAT", "INVALID_TYPE", "TOO_SHORT", "TOO_LONG" -> FieldIssue.AccountNumberInvalid
                else -> null
            }
            "amount" -> when (code) {
                "REQUIRED" -> FieldIssue.AmountRequired
                "TOO_SMALL" -> FieldIssue.AmountNotPositive
                "TOO_LARGE" -> FieldIssue.AmountTooLarge
                "NOT_INTEGER", "INVALID_TYPE" -> FieldIssue.AmountInvalid
                else -> null
            }
            "description" -> when (code) {
                "TOO_LONG" -> FieldIssue.DescriptionTooLong(max ?: DEFAULT_MAX_DESCRIPTION)
                else -> null
            }
            else -> null
        }
    }

    private fun JsonObject?.int(name: String): Int? =
        runCatching { this?.get(name)?.jsonPrimitive?.intOrNull }.getOrNull()

    private const val DEFAULT_MIN_PASSWORD = 8
    private const val DEFAULT_MAX_NAME = 100
    private const val DEFAULT_MAX_NICKNAME = 40
    private const val DEFAULT_MAX_DESCRIPTION = 140
}