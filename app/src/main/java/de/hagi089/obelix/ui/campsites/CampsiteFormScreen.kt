package de.hagi089.obelix.ui.campsites

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.campsites.GeoFormat
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.files.FileRef
import de.hagi089.obelix.data.files.NewFile
import de.hagi089.obelix.ui.finance.formatDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Formular eines Stellplatzes: neu (mit der ermittelten Position) oder bearbeiten; Fotos, Bewertung, Navigation, Löschen. */
@Composable
fun CampsiteFormScreen(
    viewModel: CampsiteFormViewModel,
    newCaptureUri: () -> Uri,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            state.position?.let { PositionBlock(position = it, label = state.name) }
            Text(
                text = stringResource(R.string.campsite_date, formatDay(state.date)),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::setComment,
                label = { Text(stringResource(R.string.campsite_field_comment)) },
                isError = state.commentError != null,
                supportingText = { state.commentError?.let { Text(stringResource(it)) } },
                minLines = 2,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.campsite_field_name)) },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(stringResource(it)) } },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::setAddress,
                label = { Text(stringResource(R.string.campsite_field_address)) },
                isError = state.addressError != null,
                supportingText = { state.addressError?.let { Text(stringResource(it)) } },
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            RatingRow(rating = state.rating, enabled = editable, onRate = viewModel::setRating)
            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.campsite_field_note)) },
                isError = state.noteError != null,
                supportingText = { state.noteError?.let { Text(stringResource(it)) } },
                minLines = 2,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )
            PhotoSection(viewModel = viewModel, newCaptureUri = newCaptureUri)
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
                enabled = editable && !state.isReadingPhoto,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.action_save)) }
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
            title = { Text(stringResource(R.string.campsite_delete_title)) },
            text = { Text(stringResource(R.string.campsite_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PositionBlock(position: GeoPosition, label: String) {
    val context = LocalContext.current
    var noApp by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.campsite_position), style = MaterialTheme.typography.labelMedium)
        Text(GeoFormat.display(position.latitude, position.longitude), style = MaterialTheme.typography.titleMedium)
        position.accuracyMeters?.let {
            Text(stringResource(R.string.campsite_accuracy, Math.round(it)), style = MaterialTheme.typography.bodySmall)
        }
        FilledTonalButton(
            onClick = { noApp = !startNavigation(context, position, label) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.campsite_navigate)) }
        if (noApp) {
            Text(
                text = stringResource(R.string.campsite_navigate_none),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

/** Startet die Navigation in einer externen App (zuerst Google Maps, sonst jede App für `geo:`). false, wenn keine da ist. */
private fun startNavigation(context: Context, position: GeoPosition, label: String): Boolean {
    val uris = listOf(
        GeoFormat.navigationUri(position.latitude, position.longitude),
        GeoFormat.geoUri(position.latitude, position.longitude, label),
    )
    for (uri in uris) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // Nächste Möglichkeit versuchen.
        }
    }
    return false
}

@Composable
private fun RatingRow(rating: Int?, enabled: Boolean, onRate: (Int?) -> Unit) {
    Column {
        Text(stringResource(R.string.campsite_rating), style = MaterialTheme.typography.labelMedium)
        Row {
            for (star in 1..5) {
                val filled = rating != null && star <= rating
                IconButton(
                    // Nochmaliges Tippen auf den gewählten Stern entfernt die Bewertung.
                    onClick = { onRate(if (rating == star) null else star) },
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(R.string.campsite_rating_star, star),
                        tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(viewModel: CampsiteFormViewModel, newCaptureUri: () -> Uri) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Die Zieladresse der Kamera übersteht das Drehen des Geräts und das Beenden der App im Hintergrund.
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val uri = captureUri
        if (taken && uri != null) viewModel.addPhoto(uri)
        captureUri = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.addPhoto(uri)
    }
    val photos = state.photos
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.campsite_photos), style = MaterialTheme.typography.labelMedium)
        photos.kept.forEach { ref ->
            SavedPhoto(ref = ref, viewModel = viewModel, enabled = !state.isSaving, onRemove = { viewModel.removeSavedPhoto(ref.fileId) })
        }
        photos.added.forEachIndexed { index, file ->
            NewPhoto(file = file, enabled = !state.isSaving, onRemove = { viewModel.removeNewPhoto(index) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val uri = newCaptureUri()
                    captureUri = uri
                    camera.launch(uri)
                },
                enabled = state.canAddPhoto,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.campsite_photo_camera)) }
            OutlinedButton(
                onClick = { gallery.launch(arrayOf("image/*")) },
                enabled = state.canAddPhoto,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.campsite_photo_gallery)) }
        }
        if (state.isReadingPhoto) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.campsite_photo_reading))
            }
        }
        state.photoError?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

@Composable
private fun SavedPhoto(ref: FileRef, viewModel: CampsiteFormViewModel, enabled: Boolean, onRemove: () -> Unit) {
    // null = lädt noch, Failure = Fehler (Foto wird erst beim Anzeigen geladen, nicht schon in der Liste).
    val image by produceState<Result<ImageBitmap>?>(initialValue = null, ref.fileId) {
        value = viewModel.loadPhoto(ref).mapCatching { bytes -> decodeThumbnail(bytes) ?: error("decode") }
    }
    PhotoFrame(image = image, enabled = enabled, onRemove = onRemove)
}

@Composable
private fun NewPhoto(file: NewFile, enabled: Boolean, onRemove: () -> Unit) {
    val image by produceState<Result<ImageBitmap>?>(initialValue = null, file) {
        value = runCatching { decodeThumbnail(file.bytes) ?: error("decode") }
    }
    PhotoFrame(image = image, enabled = enabled, onRemove = onRemove)
}

@Composable
private fun PhotoFrame(image: Result<ImageBitmap>?, enabled: Boolean, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bitmap = image?.getOrNull()
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.campsite_photo_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            )
            image == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.campsite_photo_loading))
            }
            else -> Text(
                text = stringResource(R.string.campsite_photo_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.campsite_photo_remove))
        }
    }
}

/** Dekodiert ein Foto auf Vorschaugröße (höchstens etwa 1000 Pixel Breite), damit mehrere Fotos wenig Speicher brauchen. */
private suspend fun decodeThumbnail(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 1000) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
