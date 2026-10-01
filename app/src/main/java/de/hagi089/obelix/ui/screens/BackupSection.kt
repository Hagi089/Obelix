package de.hagi089.obelix.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.backup.BackupFiles
import de.hagi089.obelix.data.backup.BackupProgress
import de.hagi089.obelix.data.backup.BackupStep
import de.hagi089.obelix.ui.settings.BackupViewModel
import java.time.LocalDate

private const val BACKUP_MIME_TYPE = "application/zip"
private const val MAX_LISTED_FAILURES = 5

/** Backup in den Einstellungen (nur für ADMIN eingebunden): alle Daten und Dateien als ZIP an einen frei gewählten Ort. */
@Composable
fun BackupSection(viewModel: BackupViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Der Android-Dateidialog legt die Datei am gewählten Ort an (auch Google Drive); dafür ist keine Berechtigung nötig.
    val chooseTarget = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        if (uri != null) viewModel.start { context.contentResolver.openOutputStream(uri, "wt") }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(R.string.settings_backup_intro), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.settings_backup_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { chooseTarget.launch(BackupFiles.fileName(LocalDate.now())) },
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_backup_create)) }

        if (state.isRunning) {
            ProgressInfo(state.progress)
        }
        state.result?.let { result ->
            if (result.isComplete) {
                Text(
                    text = stringResource(R.string.settings_backup_done, result.documentTotal, result.fileCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.settings_backup_incomplete,
                        result.failedFiles.size,
                        result.fileCount + result.failedFiles.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                result.failedFiles.take(MAX_LISTED_FAILURES).forEach {
                    Text(text = stringResource(R.string.settings_backup_failed_file, it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.error?.let { error ->
            Text(
                text = stringResource(error.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Text(
                text = stringResource(R.string.settings_backup_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProgressInfo(progress: BackupProgress?) {
    val label = if (progress == null) {
        stringResource(R.string.settings_backup_starting)
    } else {
        when (progress.step) {
            BackupStep.READ_DATA -> stringResource(R.string.settings_backup_running_data, progress.done, progress.total)
            BackupStep.SAVE_FILES -> stringResource(R.string.settings_backup_running_files, progress.done, progress.total)
        }
    }
    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (progress != null && progress.total > 0) {
        LinearProgressIndicator(progress = { progress.done.toFloat() / progress.total }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}
