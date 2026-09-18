package com.example.perfectoutfit.core.database

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val appDatabase: AppDatabase
) : DatabaseTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        appDatabase.withTransaction { block() }
}
