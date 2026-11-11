package com.example.bankflow.core.util

import com.example.bankflow.domain.common.IdGenerator
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}