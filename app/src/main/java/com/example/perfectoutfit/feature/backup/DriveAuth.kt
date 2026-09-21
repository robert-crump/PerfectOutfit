package com.example.perfectoutfit.feature.backup

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.perfectoutfit.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Result of asking Google for `drive.file` access. */
sealed interface DriveAuthorization {
    data class Granted(val accessToken: String) : DriveAuthorization

    /** Consent UI is needed: launch [pendingIntent], then pass its result to [DriveAuth.completeAuthorization]. */
    data class ResolutionRequired(val pendingIntent: PendingIntent) : DriveAuthorization
}

/** Silent authorization is no longer possible (e.g. access was revoked); the user must reconnect in Settings. */
class DriveReconnectRequiredException :
    IllegalStateException("Drive access needs to be reconnected in Settings")

/**
 * Facade for Google Drive auth: Credential Manager sign-in, Authorization API `drive.file`
 * consent, silent token refresh, and the connected-account state. It performs no Drive reads or
 * writes itself.
 */
@Singleton
class DriveAuth @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DriveTokenProvider, DriveSession {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _connectedAccount = MutableStateFlow(prefs.getString(KEY_ACCOUNT_EMAIL, null))

    /** The connected account's email, or null while disconnected. */
    val connectedAccount: StateFlow<String?> = _connectedAccount.asStateFlow()

    val isConnected: Boolean get() = _connectedAccount.value != null

    /**
     * Step 1: shows the Credential Manager account chooser and returns the chosen email, or null
     * if the user cancelled. Throws on any other failure.
     */
    suspend fun signIn(activity: Activity): String? {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(BuildConfig.DRIVE_OAUTH_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Unexpected credential type")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).id
    }

    /** Step 2: requests `drive.file` for the signed-in [account] via the Authorization API. */
    suspend fun authorize(activity: Activity, account: String): DriveAuthorization =
        authorize(Identity.getAuthorizationClient(activity), account)

    /** Finishes step 2 after the consent UI returns successfully. Throws if consent was denied. */
    fun completeAuthorization(data: Intent): String =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
            ?: throw DriveReconnectRequiredException()

    /**
     * Silently re-authorizes the granted scope for a fresh access token, for background work with
     * no UI. Throws [DriveReconnectRequiredException] when consent would be needed again.
     */
    override suspend fun accessToken(): String {
        val account = _connectedAccount.value ?: throw DriveReconnectRequiredException()
        val result = authorize(Identity.getAuthorizationClient(context), account)
        return (result as? DriveAuthorization.Granted)?.accessToken
            ?: throw DriveReconnectRequiredException()
    }

    override fun markConnected(accountEmail: String) {
        prefs.edit { putString(KEY_ACCOUNT_EMAIL, accountEmail) }
        _connectedAccount.value = accountEmail
    }

    override suspend fun disconnect() {
        prefs.edit { remove(KEY_ACCOUNT_EMAIL) }
        _connectedAccount.value = null
        // Best effort: local state is already cleared, so a failure here must not block disconnect.
        runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun authorize(
        client: AuthorizationClient,
        account: String
    ): DriveAuthorization {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .setAccount(Account(account, "com.google"))
            .build()
        val result: AuthorizationResult = withTimeout(AUTHORIZE_TIMEOUT_MS) { client.authorize(request).await() }
        val pendingIntent = result.pendingIntent
        return when {
            result.hasResolution() && pendingIntent != null -> DriveAuthorization.ResolutionRequired(pendingIntent)
            else -> DriveAuthorization.Granted(result.accessToken ?: throw DriveReconnectRequiredException())
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val PREFS_NAME = "DriveAuthPrefs"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val AUTHORIZE_TIMEOUT_MS = 30_000L
    }
}
