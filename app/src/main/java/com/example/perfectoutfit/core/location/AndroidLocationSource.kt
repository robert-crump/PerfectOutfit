package com.example.perfectoutfit.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume

/** [LocationSource] backed by the fused location provider and the platform [Geocoder]. */
class AndroidLocationSource @Inject constructor(
    @param:ApplicationContext private val context: Context
) : LocationSource {

    override suspend fun current(): LocationResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return LocationResult.PermissionDenied

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        // Try last known location first — instant cache hit, sufficient for city-level accuracy.
        val location = when (val last = fusedClient.lastLocation.await()) {
            is TaskResult.Success -> last.value ?: return fresh(fusedClient)
            // lastLocation failed; fall through to getCurrentLocation.
            is TaskResult.Failure -> return fresh(fusedClient)
        }
        return found(location)
    }

    private suspend fun fresh(fusedClient: FusedLocationProviderClient): LocationResult {
        val cancellationSource = CancellationTokenSource()
        val result = fusedClient
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
            .await { cancellationSource.cancel() }
        return when (result) {
            is TaskResult.Success -> result.value?.let { found(it) }
                ?: LocationResult.Unavailable("Could not determine location. Please try again.")
            is TaskResult.Failure -> LocationResult.Unavailable("Location unavailable. Please try again.")
        }
    }

    private suspend fun found(location: Location) = LocationResult.Found(
        location.latitude, location.longitude, reverseGeocode(location.latitude, location.longitude)
    )

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            cont.resume(
                                addresses.firstOrNull()?.locality
                                    ?: addresses.firstOrNull()?.subAdminArea
                                    ?: addresses.firstOrNull()?.adminArea
                                    ?: ""
                            )
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)
                        ?.firstOrNull()?.locality ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    private sealed interface TaskResult<out T> {
        data class Success<T>(val value: T?) : TaskResult<T>
        data object Failure : TaskResult<Nothing>
    }

    private suspend fun <T> Task<T>.await(onCancel: () -> Unit = {}): TaskResult<T> =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { cont.resume(TaskResult.Success(it)) }
            addOnFailureListener { cont.resume(TaskResult.Failure) }
            cont.invokeOnCancellation { onCancel() }
        }
}
