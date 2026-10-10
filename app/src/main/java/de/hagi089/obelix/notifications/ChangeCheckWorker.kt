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
 * Hintergrundprüfung auf Änderungen anderer Benutzer (Entscheidung 53, Option 1). Android führt sie frühestens alle
 * 15 Minuten aus; im Energiesparmodus oder bei selten genutzter App auch deutlich später. Liefert immer „Erfolg“:
 * Bei einem Fehler bleibt der Stand stehen und die nächste Prüfung holt alles nach (kein Wiederholen, schont den Akku).
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

    /** KEEP: Eine bereits geplante Prüfung läuft unverändert weiter (kein Neustart bei jedem App-Start). */
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<ChangeCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "change-check"
        const val INTERVAL_MINUTES = 15L
    }
}
