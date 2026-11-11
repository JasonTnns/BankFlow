package com.example.bankflow.data

import com.example.bankflow.domain.repository.AccountChangeNotifier
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single

@Single
class InMemoryAccountChangeNotifier: AccountChangeNotifier {

    private val _changes = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}