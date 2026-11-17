package com.example.bankflow.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "cache_entries", primaryKeys = ["user_id", "cache_key"])
data class CacheEntryEntity(
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "cache_key") val cacheKey: String,
    @ColumnInfo(name = "saved_at") val savedAtEpochMs: Long,
    @ColumnInfo(name = "payload") val payload: String,
)