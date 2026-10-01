package de.hagi089.obelix.ui.documents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.documents.Document
import de.hagi089.obelix.data.documents.DocumentCategory
import de.hagi089.obelix.data.files.FileSize
import de.hagi089.obelix.ui.finance.formatDay

/** Dokumente: Filter nach Kategorie und Liste, neueste zuerst. Die Dateien werden erst beim Öffnen geladen. */
@Composable
fun DocumentListScreen(
    viewModel: DocumentListViewModel,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Beim Öffnen und nach der Rückkehr aus dem Formular neu vom Server laden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            !state.hasLoaded && state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(state.error!!.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
                Button(onClick = viewModel::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_retry)) }
            }
            !state.hasLoaded -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> DocumentContent(state = state, viewModel = viewModel, onOpen = onOpen)
        }
        if (state.hasLoaded) {
            FloatingActionButton(onClick = onAdd, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.document_add))
            }
        }
    }
}

@Composable
private fun DocumentContent(state: DocumentListState, viewModel: DocumentListViewModel, onOpen: (String) -> Unit) {
    val visible = remember(state.documents, state.category) { state.visible }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        state.error?.let { error ->
            item {
                Text(
                    text = stringResource(error.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
        item {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.category == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
                DocumentCategory.entries.forEach { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.setCategory(category) },
                        label = { Text(stringResource(documentCategoryLabel(category))) },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    text = stringResource(if (state.documents.isEmpty()) R.string.document_empty else R.string.document_empty_category),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            items(visible, key = { it.id }) { document ->
                DocumentRow(document = document, state = state, onClick = { onOpen(document.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DocumentRow(document: Document, state: DocumentListState, onClick: () -> Unit) {
    val uploader = state.userName(document.createdBy)
    val details = buildList {
        add(stringResource(documentCategoryLabel(document.category)))
        add(formatDay(document.date))
        if (uploader != null) add(stringResource(R.string.document_uploaded_by, uploader))
        add(FileSize.format(document.file.sizeBytes))
    }.joinToString(" · ")
    val isPdf = document.file.isPdf
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = if (isPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                contentDescription = stringResource(if (isPdf) R.string.document_type_pdf else R.string.document_type_image),
            )
        },
        headlineContent = { Text(document.name) },
        supportingContent = { Text(details) },
    )
}
