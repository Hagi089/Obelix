package de.hagi089.obelix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.hagi089.obelix.R
import de.hagi089.obelix.core.error.AppError
import de.hagi089.obelix.ui.components.OfflineBanner

/** Die Benutzerdaten konnten nicht geladen werden: Fehler anzeigen, erneut versuchen oder abmelden. */
@Composable
fun ProfileErrorScreen(error: AppError, isOnline: Boolean, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isOnline) OfflineBanner()
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.profile_load_failed),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_retry))
                }
                TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) }
            }
        }
    }
}
