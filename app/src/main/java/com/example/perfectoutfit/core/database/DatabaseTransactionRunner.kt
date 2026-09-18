package com.example.perfectoutfit.core.database

/**
 * Runs a block of DAO calls atomically.
 * Two adapters exist: [RoomTransactionRunner] (a real Room transaction) and a
 * no-op fake under `app/src/test` for JVM unit tests.
 */
interface DatabaseTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
