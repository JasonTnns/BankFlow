package com.example.bankflow.core.util

import com.example.bankflow.domain.model.ZoneProvider
import org.koin.core.annotation.Single
import java.time.ZoneId


@Single
class SystemZoneProvider: ZoneProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()
}