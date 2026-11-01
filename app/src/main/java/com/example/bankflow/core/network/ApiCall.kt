package com.example.bankflow.core.network

import com.example.bankflow.domain.common.AppResult
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException


internal suspend inline fun <reified Dto, Domain> apiCall(
    crossinline request: suspend () -> HttpResponse,
    crossinline map: (Dto) -> Domain,
): AppResult<Domain> = try {
    val response = request()
    if (response.status.isSuccess()) {
        AppResult.Success(map(response.body<Dto>()))
    } else {
        AppResult.Failure(ApiErrorMapper.fromResponse(response))
    }
} catch (e: CancellationException) {
    throw e // never swallow cancellation
} catch (e: Exception) {
    AppResult.Failure(ApiErrorMapper.fromThrowable(e))
}

internal suspend inline fun apiCallUnit(
    crossinline request: suspend () -> HttpResponse,
): AppResult<Unit> = try {
    val response = request()
    if (response.status.isSuccess()) {
        AppResult.Success(Unit)
    } else {
        AppResult.Failure(ApiErrorMapper.fromResponse(response))
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Failure(ApiErrorMapper.fromThrowable(e))
}