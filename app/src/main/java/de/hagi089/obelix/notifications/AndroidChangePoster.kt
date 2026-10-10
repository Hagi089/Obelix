package de.hagi089.obelix.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.hagi089.obelix.MainActivity
import de.hagi089.obelix.R
import de.hagi089.obelix.data.notifications.ChangeAction
import de.hagi089.obelix.data.notifications.ChangeArea
import de.hagi089.obelix.data.notifications.ChangePoster
import de.hagi089.obelix.data.notifications.PersonChange

/**
 * Zeigt je Bereich eine Benachrichtigung mit einer Zeile je Person, z. B. „Benutzer A hat etwas unter Kalender
 * eingetragen.“ Bewusst ohne Titel, Beträge oder Texte der Einträge (Sperrbildschirm). Eine neue Meldung desselben
 * Bereichs ersetzt die vorige. Antippen öffnet den Bereich (Extra [EXTRA_AREA] an [MainActivity]).
 */
class AndroidChangePoster(context: Context) : ChangePoster {

    private val context = context.applicationContext
    private val manager = NotificationManagerCompat.from(this.context)

    override fun canPost(): Boolean = manager.areNotificationsEnabled() && hasPermission()

    override fun post(area: ChangeArea, changes: List<PersonChange>) {
        if (changes.isEmpty()) return
        // Prüfung hier unmittelbar vor notify (Android 13+: ohne Erlaubnis keine Benachrichtigung).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val areaLabel = context.getString(area.labelRes())
        val lines = changes.map { line(it, areaLabel) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(areaLabel)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setNumber(lines.size)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openArea(area))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_BASE_ID + area.ordinal, notification)
        } catch (e: SecurityException) {
            // Erlaubnis zwischen Prüfung und Anzeige entzogen: nichts anzeigen, nie abstürzen.
            Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
        }
    }

    private fun line(change: PersonChange, areaLabel: String): String {
        val name = change.name ?: context.getString(R.string.change_unknown_person)
        val res = when (change.action) {
            ChangeAction.CREATED -> R.string.change_line_created
            ChangeAction.UPDATED -> R.string.change_line_updated
            ChangeAction.CREATED_AND_UPDATED -> R.string.change_line_created_and_updated
        }
        return context.getString(res, name, areaLabel)
    }

    private fun openArea(area: ChangeArea): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_AREA, area.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            area.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "changes"
        const val EXTRA_AREA = "de.hagi089.obelix.OPEN_AREA"
        private const val NOTIFICATION_BASE_ID = 2600
        private const val TAG = "ChangeNotifications"

        /** Einmal beim App-Start; wiederholtes Anlegen ändert nichts (Name und Beschreibung werden aktualisiert). */
        fun createChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.change_channel_name))
                .setDescription(context.getString(R.string.change_channel_description))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }

        /** Bereich aus dem Antippen einer Benachrichtigung, sonst null. */
        fun areaFrom(intent: Intent?): ChangeArea? =
            intent?.getStringExtra(EXTRA_AREA)?.let { name -> ChangeArea.entries.firstOrNull { it.name == name } }
    }
}

internal fun ChangeArea.labelRes(): Int = when (this) {
    ChangeArea.CALENDAR -> R.string.nav_calendar
    ChangeArea.FINANCE -> R.string.nav_finance
    ChangeArea.TASKS -> R.string.nav_tasks
    ChangeArea.CAMPSITES -> R.string.nav_campsites
    ChangeArea.DOCUMENTS -> R.string.nav_documents
}
