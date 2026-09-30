package de.hagi089.obelix.ui.importing

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.core.money.Money
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_FILE_BYTES = 2 * 1024 * 1024

/** Einmaliger Excel-Import (nur ADMIN): Datei wählen, Kontrollwerte prüfen, Zahler zuordnen, importieren. */
@Composable
fun ImportScreen(viewModel: ImportViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readLimited(MAX_FILE_BYTES)?.let { String(it, Charsets.UTF_8) }
                        }
                    } catch (e: java.io.IOException) {
                        null
                    }
                }
                if (text == null) viewModel.onFileUnreadable() else viewModel.onFileText(text)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyMedium)

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        state.loadError?.let {
            ErrorText(stringResource(it.messageRes))
            Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_retry))
            }
        }

        if (!state.isLoading && state.loadError == null) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                enabled = !state.isImporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.import_choose_file)) }
            state.fileError?.let { ErrorText(it) }

            val file = state.file
            val control = state.controlValues
            if (file != null && control != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.import_check_title), style = MaterialTheme.typography.titleMedium)
                        Line(stringResource(R.string.import_count), "${file.bookings.size}")
                        Line(stringResource(R.string.finance_income_total), Money.format(control.incomeTotalCents))
                        Line(stringResource(R.string.finance_expense_total), Money.format(control.expenseTotalCents))
                        Line(stringResource(R.string.finance_balance), Money.format(control.balanceCents))
                        file.payers.forEach { payer ->
                            Line(stringResource(R.string.import_open_claim, payer), Money.format(control.openByPayerCents[payer] ?: 0L))
                        }
                        Line(stringResource(R.string.finance_after_settlement), Money.format(control.afterSettlementCents))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (state.mismatches.isEmpty()) {
                            Text(stringResource(R.string.import_check_ok), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            ErrorText(stringResource(R.string.import_check_failed))
                            state.mismatches.forEach { ErrorText(it) }
                        }
                    }
                }

                if (file.skipped.isNotEmpty()) {
                    Text(stringResource(R.string.import_skipped_title, file.skipped.size), style = MaterialTheme.typography.titleSmall)
                    file.skipped.forEach {
                        Text("• ${stringResource(R.string.import_skipped_line, it.row, it.description, it.reason)}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(stringResource(R.string.import_map_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.import_map_intro), style = MaterialTheme.typography.bodyMedium)
                val pleaseChoose = stringResource(R.string.select_please)
                file.payers.forEach { payer ->
                    SelectorField(
                        label = stringResource(R.string.import_payer_label, payer),
                        value = state.users.firstOrNull { it.uid == state.payerMapping[payer] }?.displayName ?: pleaseChoose,
                        options = state.users.map { SelectorOption(it.uid, it.displayName) },
                        onSelect = { viewModel.mapPayer(payer, it) },
                        enabled = !state.isImporting && state.importedCount == null,
                    )
                }
                if (file.payers.all { state.payerMapping[it] != null } && !state.mappingComplete) {
                    ErrorText(stringResource(R.string.import_map_distinct))
                }

                val alreadyImported = file.bookings.size - state.pendingCount
                if (alreadyImported > 0) {
                    Text(stringResource(R.string.import_already, alreadyImported, file.bookings.size), style = MaterialTheme.typography.bodyMedium)
                }

                if (state.isImporting) {
                    LinearProgressIndicator(progress = { state.progress.toFloat() / state.pendingCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.import_progress, state.progress, state.pendingCount), style = MaterialTheme.typography.bodySmall)
                }
                state.importError?.let { ErrorText(stringResource(it.messageRes)) }
                state.importedCount?.let {
                    Text(
                        text = stringResource(R.string.import_done, it),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    onClick = { confirm = true },
                    enabled = state.canImport,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.import_start, state.pendingCount)) }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text, state.pendingCount)) },
            confirmButton = {
                TextButton(onClick = { confirm = false; viewModel.startImport() }) { Text(stringResource(R.string.import_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Liest höchstens [max] Bytes; null, wenn die Datei größer ist (kein `readNBytes`, das braucht Android 13). */
private fun java.io.InputStream.readLimited(max: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > max) return null
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
