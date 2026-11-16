package com.example.bankflow.domain.model

import java.time.ZoneId

fun interface ZoneProvider {
    fun zone(): ZoneId
}