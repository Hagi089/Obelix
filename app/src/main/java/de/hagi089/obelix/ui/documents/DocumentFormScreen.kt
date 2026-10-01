package de.hagi089.obelix.ui.documents

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.FileSize
import de.hagi089.obelix.ui.components.SelectorField
import de.hagi089.obelix.ui.components.SelectorOption
import de.hagi089.obelix.ui.finance.formatDay

/**
 * Formular eines Dokuments. Neu: Datei wählen, Name und Kategorie angeben, hochladen. Gespeichert: Name und Kategorie
 * ändern, Datei öffnen, löschen. Die Datei selbst bleibt unverändert.
 */
@Composable
fun DocumentFormScreen(
    viewModel: DocumentFormViewModel,
    onFinished: () -> Unit,
    onOpenFile: (FileRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Dateiauswahl des Systems (PDF oder Bild); braucht keine Berechtigung.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachFile(uri)
    }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    var confirmDelete by remember { mutableStateOf(false) }

    when {
        state.isLoading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.loadError != null -> Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(state.loadError!!.messageRes),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
            Button(onClick = viewModel::load, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
        }
        else -> Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val editable = !state.isSaving
            val buttonModifier = Modifier.heightIn(min = 48.dp)

            Text(stringResource(R.string.document_file), style = MaterialTheme.typography.labelLarge)
            val existing = state.existing
            val pending = state.pendingFile
            when {
                existing != null -> {
                    Text(
                        text = stringResource(R.string.document_file_info, existing.file.name, FileSize.format(existing.file.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.document_uploaded_on, formatDay(existing.date)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = { onOpenFile(existing.file) }, enabled = editable, modifier = buttonModifier.fillMaxWidth()) {
                        Text(stringResource(R.string.document_open))
                    }
                }
                pending != null -> {
                    Text(
                        text = stringResource(R.string.document_file_info, pending.name, FileSize.format(pending.sizeBytes)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickFile.launch(DOCUMENT_MIME_TYPES) },
                            enabled = editable && !state.isReadingFile,
                            modifier = buttonModifier,
                        ) { Text(stringResource(R.string.document_file_change)) }
                        TextButton(
                            onClick = viewModel::discardPendingFile,
                            enabled = editable && !state.isReadingFile,
                            modifier = buttonModifier,
                        ) { Text(stringResource(R.string.document_file_discard)) }
                    }
                }
                else -> OutlinedButton(
                    onClick = { pickFile.launch(DOCUMENT_MIME_TYPES) },
                    enabled = editable && !state.isReadingFile,
                    modifier = buttonModifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.document_file_choose)) }
            }
            if (state.isReadingFile) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.document_file_preparing), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.fileError?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            if (!state.isEdit) Text(stringResource(R.string.document_file_hint), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.field_document_name)) },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(stringResource(it)) } },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            val pleaseChoose = stringResource(R.string.document_category_select)
            SelectorField(
                label = stringResource(R.string.field_document_category),
                value = state.category?.let { stringResource(documentCategoryLabel(it)) } ?: pleaseChoose,
                options = DocumentCategory.entries.map { SelectorOption(it.name, stringResource(documentCategoryLabel(it))) },
                onSelect = { viewModel.setCategory(DocumentCategory.from(it)) },
                errorText = state.categoryError?.let { stringResource(it) },
                enabled = editable,
            )
            state.saveError?.let {
                Text(
                    text = stringResource(it.messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            Button(
                onClick = viewModel::save,
                enabled = editable && !state.isReadingFile,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (state.isEdit) R.string.action_save else R.string.document_upload))
            }
            if (state.isEdit) {
                TextButton(onClick = { confirmDelete = true }, enabled = editable, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            if (state.isSaving) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.document_delete_title)) },
            text = { Text(stringResource(R.string.document_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Erlaubt sind PDF und Bilder (Bilder werden beim Übernehmen als JPEG verkleinert), wie bei Belegen. */
private val DOCUMENT_MIME_TYPES = arrayOf("application/pdf", "image/*")
