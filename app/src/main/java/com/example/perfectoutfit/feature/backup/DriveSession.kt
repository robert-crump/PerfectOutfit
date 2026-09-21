package com.example.perfectoutfit.feature.backup

/** The connected-Google-account state that connect/disconnect flips. */
interface DriveSession {
    /** Persists the connected account. Call once authorization succeeds. */
    fun markConnected(accountEmail: String)

    /** Signs out and forgets the connected account. */
    suspend fun disconnect()
}
