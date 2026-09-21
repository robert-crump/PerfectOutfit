package com.example.perfectoutfit.feature.backup

/** Supplies a valid `drive.file` OAuth access token for each Drive request. */
fun interface DriveTokenProvider {
    suspend fun accessToken(): String
}
