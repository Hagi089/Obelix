package de.hagi089.obelix.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hagi089.obelix.ObelixApplication
import java.util.concurrent.TimeUnit

/**
 * Hintergrundprüfung auf Änderungen anderer Benutzer (Entscheidung 53, Option 1). Etwa einmal pro Stunde (Wunsch des
 * Benutzers; Version 27 lief zum Testen mit 15 Minuten, dem kleinsten Wert von Android). Im Energiesparmodus oder bei
 * selten genutzter App auch später. Liefert immer „Erfolg“: Bei einem Fehler bleibt der Stand stehen und die nächste Prüfung holt alles nach (kein Wiederholen, schont den Akku).
 */
class ChangeCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as ObelixApplication).container
        if (!container.isFirebaseConfigured) return Result.success()
        val outcome = container.changeChecker.run(container.auth.currentUser?.uid)
        Log.d(TAG, "Prüfung: $outcome")
        return Result.success()
    }

    private companion object {
        const val TAG = "ChangeCheckWorker"
    }
}

/** Plant bzw. beendet die regelmäßige Prüfung. */
class ChangeCheckScheduler(context: Context) {

    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    /**
     * UPDATE (statt KEEP in Version 26): Eine geplante Prüfung übernimmt ein geändertes Intervall; mit KEEP wäre nach dem
     * Update das alte Intervall von 60 Minuten geblieben. Laut Android-Doku wird die geplante Arbeit auf die neue
     * Angabe aktualisiert (nicht neu angelegt).
     */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ChangeCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "change-check"
        // Entscheidung 53: 60 Minuten (Version 27 hatte zum Testen 15). UPDATE übernimmt den Wert nach dem Update.
        const val INTERVAL_MINUTES = 60L
    }
}
