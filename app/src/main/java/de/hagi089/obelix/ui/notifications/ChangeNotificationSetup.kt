package de.hagi089.obelix.ui.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.AppContainer

/**
 * Nach der Anmeldung (Benutzer freigeschaltet): plant die Hintergrundprüfung, wenn der Schalter an ist, sonst
 * beendet sie sie. Fragt auf Android 13+ genau einmal nach der Erlaubnis (danach nur noch über die Einstellungen).
 */
@Composable
fun ChangeNotificationSetup(container: AppContainer) {
    val preference = container.changeNotificationPreference
    val enabled by preference.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(enabled) {
        if (enabled) {
            container.changeCheckScheduler.schedule()
            if (needsPermissionRequest(context) && !preference.permissionAsked) {
                preference.markPermissionAsked()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            container.changeCheckScheduler.cancel()
        }
    }
}

/** true auf Android 13+, wenn die Laufzeit-Erlaubnis für Benachrichtigungen fehlt. */
fun needsPermissionRequest(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/** Ob Android der App derzeit Benachrichtigungen erlaubt (Erlaubnis und Systemschalter). */
fun notificationsAllowed(context: Context): Boolean =
    !needsPermissionRequest(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

/** Öffnet die Benachrichtigungs-Einstellungen von OBELIX in Android. */
fun openSystemNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
