package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [DriveSession] whose sign-in and consent answers each test sets up. */
class FakeDriveSession : DriveSession {
    private val account = MutableStateFlow<String?>(null)
    override val connectedAccount: StateFlow<String?> = account

    /** What the account chooser returns; null means the user cancelled. */
    var chosenAccount: String? = "me@example.com"
    var authorization: DriveAuthorization = DriveAuthorization.Granted("token")

    /** Thrown by [completeAuthorization] when set. */
    var consentError: Exception? = null
    val completedConsents = mutableListOf<Intent>()

    override suspend fun signIn(activity: Activity): String? = chosenAccount

    override suspend fun authorize(activity: Activity, account: String): DriveAuthorization = authorization

    override fun completeAuthorization(data: Intent) {
        consentError?.let { throw it }
        completedConsents += data
    }

    override fun markConnected(accountEmail: String) {
        account.value = accountEmail
    }

    override suspend fun disconnect() {
        account.value = null
    }
}
