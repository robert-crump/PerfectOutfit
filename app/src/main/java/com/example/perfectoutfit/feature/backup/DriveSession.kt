package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** The Google account behind Drive backup: sign-in, Drive consent, and the connected-account state. */
interface DriveSession {
    /** The connected account's email, or null while disconnected. */
    val connectedAccount: StateFlow<String?>

    /** Shows the account chooser and returns the chosen email, or null if the user cancelled. */
    suspend fun signIn(activity: Activity): String?

    /** Requests `drive.file` access for the signed-in [account]. */
    suspend fun authorize(activity: Activity, account: String): DriveAuthorization

    /** Finishes [authorize] after its consent UI returned [data] successfully. Throws if access was not granted. */
    fun completeAuthorization(data: Intent)

    /** Persists the connected account. Call once authorization succeeds. */
    fun markConnected(accountEmail: String)

    /** Signs out and forgets the connected account. */
    suspend fun disconnect()
}
