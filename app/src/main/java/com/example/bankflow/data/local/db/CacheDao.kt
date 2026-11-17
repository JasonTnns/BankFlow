package com.example.bankflow.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CacheDao {

    @Query("SELECT * FROM cache_entries WHERE user_id = :userId AND cache_key = :cacheKey")
    suspend fun get(userId: String, cacheKey: String): CacheEntryEntity?

    @Upsert
    suspend fun put(entry: CacheEntryEntity)

    @Query("DELETE FROM cache_entries WHERE user_id = :userId")
    suspend fun clearUser(userId: String)

    @Query("DELETE FROM cache_entries")
    suspend fun clearAll()
}