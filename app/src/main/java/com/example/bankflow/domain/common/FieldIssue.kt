package com.example.bankflow.domain.common

import com.example.bankflow.domain.model.Money

sealed interface FieldIssue {
    data object EmailRequired : FieldIssue
    data object EmailInvalid : FieldIssue
    data object PasswordRequired : FieldIssue
    data class PasswordTooShort(val min: Int) : FieldIssue
    data object PasswordNeedsLetterAndNumber : FieldIssue
    data object PasswordTooLong : FieldIssue
    data object NameRequired : FieldIssue
    data class NameTooLong(val max: Int) : FieldIssue
    data object AmountRequired : FieldIssue
    data object AmountInvalid : FieldIssue
    data object AmountNotPositive : FieldIssue
    data object AmountTooLarge : FieldIssue
    data object AmountMaxBelowMin : FieldIssue
    data class InsufficientFunds(val available: Money) : FieldIssue
    data object AccountNumberInvalid : FieldIssue
    data object DestinationRequired : FieldIssue
    data object DestinationNotFound : FieldIssue
    data object DestinationIsSource : FieldIssue
    data object OwnAccount : FieldIssue
    data class DescriptionTooLong(val max: Int) : FieldIssue
    data object NicknameRequired : FieldIssue
    data class NicknameTooLong(val max: Int) : FieldIssue

    data object Invalid : FieldIssue
}