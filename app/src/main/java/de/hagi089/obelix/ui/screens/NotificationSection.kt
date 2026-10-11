package de.hagi089.obelix.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.hagi089.obelix.R
import de.hagi089.obelix.data.notifications.CheckOutcome
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import de.hagi089.obelix.ui.notifications.needsPermissionRequest
import de.hagi089.obelix.ui.notifications.notificationsAllowed
import de.hagi089.obelix.ui.notifications.openSystemNotificationSettings

/**
 * Einstellungen → „Benachrichtigungen“ (Version 26, für alle Benutzer): Schalter für den Hinweis bei Änderungen
 * anderer. Ist der Schalter an, Android erlaubt aber keine Benachrichtigungen, erscheint ein Hinweis mit Weg in die
 * Android-Einstellungen (nach zweimaligem Ablehnen zeigt Android den Erlaubnis-Dialog nicht mehr).
 */
@Composable
fun NotificationSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    lastCheck: () -> Pair<Long, CheckOutcome>? = { null },
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var last by remember { mutableStateOf(lastCheck()) }
    // Nach der Rückkehr aus den Android-Einstellungen (oder aus dem Hintergrund) neu prüfen.
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        last = lastCheck()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = notificationsAllowed(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.settings_notifications), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = { on ->
                        onEnabledChange(on)
                        if (on && needsPermissionRequest(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_notifications_switch),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // Bedienung über die ganze Zeile (toggleable), der Schalter zeigt nur den Zustand.
            Switch(checked = enabled, onCheckedChange = null)
        }
        Text(
            text = stringResource(R.string.settings_notifications_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Version 27: Zeitpunkt und Ergebnis der letzten Hintergrundprüfung (Hilfe beim Testen).
        val lastText = last?.let { (at, outcome) ->
            stringResource(R.string.settings_notifications_last, formatCheckTime(at), stringResource(outcome.labelRes()))
        } ?: stringResource(R.string.settings_notifications_last_none)
        Text(text = lastText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (enabled && !allowed) {
            Text(
                text = stringResource(R.string.settings_notifications_blocked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = { openSystemNotificationSettings(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings_notifications_open_system))
            }
        }
    }
}

private val CHECK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

private fun formatCheckTime(millis: Long): String =
    CHECK_TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun CheckOutcome.labelRes(): Int = when (this) {
    CheckOutcome.NO_USER -> R.string.check_outcome_no_user
    CheckOutcome.DISABLED -> R.string.check_outcome_disabled
    CheckOutcome.NOT_ALLOWED -> R.string.check_outcome_not_allowed
    CheckOutcome.STARTED -> R.string.check_outcome_started
    CheckOutcome.FAILED -> R.string.check_outcome_failed
    CheckOutcome.NOTHING_NEW -> R.string.check_outcome_nothing_new
    CheckOutcome.POSTED -> R.string.check_outcome_posted
}
