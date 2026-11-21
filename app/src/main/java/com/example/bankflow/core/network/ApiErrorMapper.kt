package com.example.bankflow.core.network

import com.example.bankflow.data.remote.dto.ErrorEnvelopeDto
import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.FieldIssue
import com.example.bankflow.domain.model.Money
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.io.InterruptedIOException

class RefreshFailedException(val error: AppError) : Exception("Token refresh failed: $error")


internal object ApiErrorMapper {

    suspend fun fromResponse(response: HttpResponse): AppError {
        val status = response.status.value
        val body = runCatching {
            NetworkJson.decodeFromString(ErrorEnvelopeDto.serializer(), response.bodyAsText()).error
        }.getOrNull()
        val details = body?.details

        return when (body?.code) {
            "VALIDATION_ERROR" -> validation(details)
            "RATE_LIMITED" -> AppError.RateLimited
            "UNAUTHORIZED", "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN" -> AppError.SessionExpired
            "INVALID_CREDENTIALS" -> AppError.InvalidCredentials
            "FORBIDDEN", "NOT_FOUND" -> AppError.NotFound
            "PASSWORD_INCORRECT" -> AppError.PasswordIncorrect
            "EMAIL_TAKEN" -> AppError.EmailTaken
            "BENEFICIARY_EXISTS" -> AppError.BeneficiaryExists
            "BENEFICIARY_LIMIT_REACHED" -> AppError.BeneficiaryLimitReached
            "INSUFFICIENT_FUNDS" -> AppError.InsufficientFunds
            "LIMIT_EXCEEDED" -> AppError.LimitExceeded(
                limit = details.money("limitMinor"),
                usedToday = details.money("usedTodayMinor"),
                remaining = details.money("remainingMinor"),
            )
            "SAME_ACCOUNT" -> AppError.SameAccount
            "ACCOUNT_INACTIVE" -> AppError.AccountInactive
            "CURRENCY_MISMATCH" -> AppError.CurrencyMismatch
            "IDEMPOTENCY_CONFLICT" -> AppError.IdempotencyConflict
            "INTERNAL_ERROR" -> AppError.Server(status)
            else -> fromStatus(status)
        }
    }

    fun fromThrowable(error: Throwable): AppError = when (error) {
        is RefreshFailedException -> error.error
        is HttpRequestTimeoutException,
        is ConnectTimeoutException,
        is SocketTimeoutException,
        is InterruptedIOException -> AppError.Timeout
        is IOException -> AppError.NoConnection
        else -> AppError.Unknown(error.message)
    }

    private fun fromStatus(status: Int): AppError = when (status) {
        400 -> AppError.Validation()
        401 -> AppError.SessionExpired
        404 -> AppError.NotFound
        429 -> AppError.RateLimited
        in 500..599 -> AppError.Server(status)
        else -> AppError.Unknown("HTTP $status")
    }


    private class ServerIssue(val code: String, val params: JsonObject?)

    internal fun validation(details: JsonObject?): AppError.Validation {
        val texts = fieldTexts(details)
        val codes = fieldCodes(details)
        val issues = linkedMapOf<String, MutableList<FieldIssue>>()
        val leftoverText = linkedMapOf<String, MutableList<String>>()

        for (serverField in texts.keys + codes.keys) {
            val formField = ServerIssueMapper.formField(serverField)
            val messages = texts[serverField].orEmpty()
            val entries = codes[serverField].orEmpty()

            for (index in 0 until maxOf(messages.size, entries.size)) {
                val entry = entries.getOrNull(index)
                val mapped = entry?.let { ServerIssueMapper.map(serverField, it.code, it.params) }
                if (mapped != null) {
                    issues.getOrPut(formField) { mutableListOf() } += mapped
                } else {
                    messages.getOrNull(index)?.let { leftoverText.getOrPut(formField) { mutableListOf() } += it }
                }
            }
        }
        return AppError.Validation(message = null, fieldErrors = leftoverText, issues = issues)
    }

    private fun fieldTexts(details: JsonObject?): Map<String, List<String>> = runCatching {
        details?.get("fields")?.jsonObject?.mapValues { (_, messages) ->
            messages.jsonArray.map { it.jsonPrimitive.content }
        }
    }.getOrNull().orEmpty()

    private fun fieldCodes(details: JsonObject?): Map<String, List<ServerIssue>> = runCatching {
        details?.get("issues")?.jsonObject?.mapValues { (_, entries) ->
            entries.jsonArray.map { entry ->
                val item = entry.jsonObject
                ServerIssue(item.getValue("code").jsonPrimitive.content, item["params"] as? JsonObject)
            }
        }
    }.getOrNull().orEmpty()

    private fun JsonObject?.money(key: String): Money? =
        this?.get(key)?.jsonPrimitive?.longOrNull?.let { Money(it) }
}