package com.example.bankflow.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject


@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String = "",
    val details: JsonObject? = null,
)


@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val fullName: String,
    val themePreference: String,
)

@Serializable
data class SessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val user: UserDto,
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val fullName: String,
    val deviceName: String? = null,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
    val deviceName: String? = null,
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String,
    val deviceName: String? = null,
)


@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: String,
    val accountNumber: String,
    val currency: String,
    val balanceMinor: Long,
    val status: String,
    val createdAt: String,
)

@Serializable
data class AccountsResponseDto(val accounts: List<AccountDto>)

@Serializable
data class AccountResponseDto(val account: AccountDto)

@Serializable
data class CounterpartyDto(
    val name: String,
    val accountNumberMasked: String? = null,
    val isOwnAccount: Boolean,
)

@Serializable
data class TransactionDto(
    val transferId: String,
    val accountId: String,
    val direction: String,
    val amountMinor: Long,
    val balanceAfterMinor: Long,
    val currency: String,
    val status: String,
    val kind: String,
    val category: String,
    val description: String? = null,
    val createdAt: String,
    val counterparty: CounterpartyDto,
)

@Serializable
data class TransactionPageDto(
    val items: List<TransactionDto>,
    val nextCursor: String? = null,
)

@Serializable
data class TotalDto(val currency: String, val balanceMinor: Long)

@Serializable
data class DashboardDto(
    val accounts: List<AccountDto>,
    val totals: List<TotalDto>,
    val recentTransactions: List<TransactionDto>,
)


@Serializable
data class TransferRequestDto(
    val sourceAccountId: String,
    val destinationAccountId: String? = null,
    val destinationAccountNumber: String? = null,
    val amountMinor: Long,
    val category: String? = null,
    val description: String? = null,
)

@Serializable
data class TransferDto(
    val id: String,
    val kind: String,
    val status: String,
    val failureReason: String? = null,
    val sourceAccountId: String,
    val destinationAccountId: String,
    val amountMinor: Long,
    val currency: String,
    val category: String,
    val description: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val direction: String? = null,
)

@Serializable
data class TransferResultDto(
    val transfer: TransferDto,
    val sourceAccountBalanceMinor: Long,
)

@Serializable
data class TransferEnvelopeDto(val transfer: TransferDto)

@Serializable
data class TransactionResponseDto(val transaction: TransactionDto)

@Serializable
data class RecipientDto(
    val displayName: String,
    val accountNumberMasked: String,
    val currency: String,
    val status: String,
    val isOwnAccount: Boolean,
)

@Serializable
data class RecipientResponseDto(val recipient: RecipientDto)