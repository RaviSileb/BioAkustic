package com.bioakustic.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot GPS fixes for history entries (used by the BirdNET meta-model
 * and for the editable place coordinates).
 */
class LocationProvider(private val context: Context) {

    data class Fix(val latitude: Double, val longitude: Double, val accuracyM: Float?)

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns a fresh fix within [timeoutMs], falling back to the last known
     * location. Null when permission is missing or no fix is available.
     */
    suspend fun getFix(timeoutMs: Long = 6000): Fix? {
        if (!hasPermission()) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
            location?.let { Fix(it.latitude, it.longitude, it.accuracy.takeIf { acc -> acc > 0 }) }
        } catch (_: Exception) {
            null
        }
    }
}
