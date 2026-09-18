package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.DatabaseTransactionRunner

/** Runs the block directly — no real transaction — so tests can stay pure JVM. */
class FakeDatabaseTransactionRunner : DatabaseTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
