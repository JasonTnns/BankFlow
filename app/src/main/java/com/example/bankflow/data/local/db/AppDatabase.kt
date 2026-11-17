package com.example.bankflow.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CacheEntryEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
}