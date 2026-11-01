package com.example.bankflow.core.network

import kotlinx.serialization.json.Json

val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}