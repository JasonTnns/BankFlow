package com.example.bankflow.domain.repository

import kotlinx.coroutines.flow.Flow

interface AccountChangeNotifier {
    val changes: Flow<Unit>
    fun notifyChanged()
}