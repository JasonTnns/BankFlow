package com.example.bankflow.testutil

import com.example.bankflow.domain.common.AppError
import com.example.bankflow.domain.common.AppResult
import com.example.bankflow.domain.common.IdGenerator
import com.example.bankflow.domain.model.Category
import com.example.bankflow.domain.model.Money
import com.example.bankflow.domain.model.Transfer
import com.example.bankflow.domain.model.TransferKind
import com.example.bankflow.domain.model.TransferRequest
import com.example.bankflow.domain.model.TransferResult
import com.example.bankflow.domain.model.TransferStatus
import com.example.bankflow.domain.repository.AccountChangeNotifier
import com.example.bankflow.domain.repository.TransferRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

fun successResult(request: TransferRequest, newBalance: Long = 97_500) = TransferResult(
    transfer = Transfer(
        id = "transfer-1",
        kind = TransferKind.TRANSFER,
        status = TransferStatus.COMPLETED,
        failureReason = null,
        sourceAccountId = request.sourceAccountId,
        destinationAccountId = "dest-1",
        amount = request.amount,
        category = request.category ?: Category.TRANSFER,
        description = request.description,
        createdAt = Instant.parse("2026-10-29T10:00:00Z"),
        completedAt = Instant.parse("2026-10-29T10:00:00Z"),
    ),
    sourceAccountBalance = Money(newBalance),
)

class StubTransferRepository : TransferRepository {
    val requests = mutableListOf<TransferRequest>()
    var gate: CompletableDeferred<Unit>? = null
    var handler: suspend (TransferRequest) -> AppResult<TransferResult> =
        { request -> AppResult.Success(successResult(request)) }

    fun script(vararg results: AppResult<TransferResult>) {
        val queue = ArrayDeque(results.toList())
        handler = { queue.removeFirst() }
    }

    override suspend fun transfer(request: TransferRequest): AppResult<TransferResult> {
        requests += request
        gate?.await()
        return handler(request)
    }

    override suspend fun getTransfer(transferId: String): AppResult<Transfer> =
        AppResult.Failure(AppError.NotFound)
}

class SequentialIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "key-${++counter}"
}

class TestClock(private var now: Instant = Instant.parse("2026-10-29T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class FakeAccountChangeNotifier : AccountChangeNotifier {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val changes: Flow<Unit> = flow
    var notifications = 0
        private set

    override fun notifyChanged() {
        notifications++
        flow.tryEmit(Unit)
    }
}