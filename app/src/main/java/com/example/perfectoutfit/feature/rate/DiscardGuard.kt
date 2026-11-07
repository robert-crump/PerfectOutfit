package com.example.perfectoutfit.feature.rate

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Who asked to leave: the screen itself (back, cancel) or the app (a bottom-bar tab switch). */
private enum class LeaveRequest { INTERNAL, EXTERNAL }

/** How long the dialog's exit animation runs before the screen transition starts. */
private const val DIALOG_EXIT_MS = 300L

/**
 * The discard handshake for leaving a screen with work on it. Leaving with nothing at stake
 * ([hasUnsavedWork] false) happens straight away; otherwise a "discard?" dialog asks first, and
 * on confirm it hides, lets its exit animation finish, then leaves the way the request came:
 * [onLeave] for the screen's own back/cancel, [onExternalCancelConfirmed] for a tab switch
 * ([externalCancelRequested]). Keeping the work on a tab switch reports [onExternalCancelDismissed].
 *
 * Draws the dialog while asking; returns the function the screen's back/cancel calls.
 */
@Composable
fun rememberDiscardGuard(
    hasUnsavedWork: Boolean,
    title: String,
    text: String,
    onLeave: () -> Unit,
    externalCancelRequested: Boolean,
    onExternalCancelConfirmed: () -> Unit,
    onExternalCancelDismissed: () -> Unit
): () -> Unit {
    var asking by rememberSaveable { mutableStateOf<LeaveRequest?>(null) }
    val scope = rememberCoroutineScope()
    val unsaved by rememberUpdatedState(hasUnsavedWork)
    val leave by rememberUpdatedState(onLeave)
    val confirmed by rememberUpdatedState(onExternalCancelConfirmed)
    val dismissed by rememberUpdatedState(onExternalCancelDismissed)

    LaunchedEffect(externalCancelRequested) {
        if (externalCancelRequested) {
            if (unsaved) asking = LeaveRequest.EXTERNAL else confirmed()
        }
    }

    asking?.let { request ->
        val keep = {
            asking = null
            if (request == LeaveRequest.EXTERNAL) dismissed()
        }
        AlertDialog(
            onDismissRequest = keep,
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    scope.launch {
                        delay(DIALOG_EXIT_MS)
                        if (request == LeaveRequest.EXTERNAL) confirmed() else leave()
                    }
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = keep) { Text("Keep editing") } }
        )
    }

    return { if (unsaved) asking = LeaveRequest.INTERNAL else leave() }
}
