package com.example.bankflow.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object HomeRoute

@Serializable
data class AccountDetailsRoute(val accountId: String)

@Serializable
data class TransactionDetailsRoute(val accountId: String, val transferId: String)

@Serializable
data object DashboardRoute

@Serializable
data object TransferRoute
