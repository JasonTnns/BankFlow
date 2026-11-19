package com.example.bankflow.presentation.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.bankflow.R
import com.example.bankflow.domain.model.AccountType
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Direction
import com.example.bankflow.domain.model.ThemePreference
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferStatus

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.FOOD -> R.string.category_food
    Category.TRANSPORT -> R.string.category_transport
    Category.SHOPPING -> R.string.category_shopping
    Category.BILLS -> R.string.category_bills
    Category.ENTERTAINMENT -> R.string.category_entertainment
    Category.HEALTH -> R.string.category_health
    Category.INCOME -> R.string.category_income
    Category.TRANSFER -> R.string.category_transfer
    Category.OTHER -> R.string.category_other
}

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.CHECKING -> R.string.account_type_checking
    AccountType.SAVINGS -> R.string.account_type_savings
}

@StringRes
fun Direction.labelRes(): Int = when (this) {
    Direction.IN -> R.string.direction_in
    Direction.OUT -> R.string.direction_out
}

@StringRes
fun TransferStatus.labelRes(): Int = when (this) {
    TransferStatus.PENDING -> R.string.status_pending
    TransferStatus.COMPLETED -> R.string.status_completed
    TransferStatus.FAILED -> R.string.status_failed
    TransferStatus.REVERSED -> R.string.status_reversed
}

@StringRes
fun TransferKind.labelRes(): Int = when (this) {
    TransferKind.TRANSFER -> R.string.kind_transfer
    TransferKind.DEPOSIT -> R.string.kind_deposit
}

@StringRes
fun ThemePreference.labelRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system
    ThemePreference.LIGHT -> R.string.theme_light
    ThemePreference.DARK -> R.string.theme_dark
}

@StringRes
fun ThemePreference.descriptionRes(): Int = when (this) {
    ThemePreference.SYSTEM -> R.string.theme_system_description
    ThemePreference.LIGHT -> R.string.theme_light_description
    ThemePreference.DARK -> R.string.theme_dark_description
}

@Composable fun Category.label(): String = stringResource(labelRes())
@Composable fun AccountType.label(): String = stringResource(labelRes())
@Composable fun Direction.label(): String = stringResource(labelRes())
@Composable fun TransferStatus.label(): String = stringResource(labelRes())
@Composable fun TransferKind.label(): String = stringResource(labelRes())