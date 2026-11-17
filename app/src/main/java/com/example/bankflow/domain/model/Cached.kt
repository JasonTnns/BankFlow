package com.example.bankflow.domain.model

import java.time.Instant

data class Cached<out T>(val value: T, val savedAt: Instant)