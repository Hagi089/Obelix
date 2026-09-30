package de.hagi089.obelix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hagi089.obelix.R
import de.hagi089.obelix.data.auth.AuthUser

/** Einstellungen. Haushalt, Benutzerverwaltung und Kategorien folgen in späteren Phasen. */
@Composable
fun SettingsScreen(user: AuthUser, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_account),
            style = MaterialTheme.typography.titleMedium,
        )
        user.displayName?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodyLarge)
        }
        user.email?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.action_sign_out))
        }
    }
}
