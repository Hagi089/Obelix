package de.hagi089.obelix.data.campsites

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.hagi089.obelix.core.util.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Warum die aktuelle Position nicht ermittelt werden konnte. */
enum class LocationProblem {
    /** Die Standortberechtigung fehlt (nicht erteilt oder abgelehnt). */
    PERMISSION_DENIED,

    /** Der Standortdienst des Geräts ist ausgeschaltet. */
    SERVICE_DISABLED,

    /** Es gab keine Position (kein Empfang, Zeit abgelaufen oder Fehler). */
    UNAVAILABLE,
}

class LocationException(val problem: LocationProblem, cause: Throwable? = null) : Exception(problem.name, cause)

/**
 * Ermittelt die aktuelle Position **einmalig** auf ausdrücklichen Wunsch (Anforderung 23). Es gibt keine
 * Standortaufzeichnung und keine Verfolgung im Hintergrund.
 */
interface LocationProvider {
    /** Fehler sind eine [LocationException]. */
    suspend fun currentLocation(): Result<GeoPosition>
}

/** Fused Location Provider der Google Play Services (kostenlos, nur im Vordergrund). */
class FusedLocationProvider(context: Context) : LocationProvider {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission") // wird unten geprüft
    override suspend fun currentLocation(): Result<GeoPosition> {
        if (!hasPermission()) return Result.failure(LocationException(LocationProblem.PERMISSION_DENIED))
        val manager = appContext.getSystemService(LocationManager::class.java)
        if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
            return Result.failure(LocationException(LocationProblem.SERVICE_DISABLED))
        }
        val cancellation = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(0) // keine alte Position aus dem Speicher
                .setDurationMillis(SEARCH_MS)
                .build()
            val location = withTimeout(TIMEOUT_MS) { client.getCurrentLocation(request, cancellation.token).await() }
            if (location == null) {
                Result.failure(LocationException(LocationProblem.UNAVAILABLE))
            } else {
                Result.success(
                    GeoPosition(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null),
                )
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(LocationException(LocationProblem.UNAVAILABLE, e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Result.failure(LocationException(LocationProblem.PERMISSION_DENIED, e))
        } catch (e: Exception) {
            Log.w(TAG, "Position konnte nicht ermittelt werden", e)
            Result.failure(LocationException(LocationProblem.UNAVAILABLE, e))
        } finally {
            cancellation.cancel()
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "Obelix"
        /** Die Suche selbst endet nach 30 s; das Zeitlimit darüber fängt einen hängenden Aufruf ab. */
        const val SEARCH_MS = 30_000L
        const val TIMEOUT_MS = 40_000L
    }
}
