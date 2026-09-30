package de.hagi089.obelix.ui.campsites

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hagi089.obelix.R
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.GeoPosition
import de.hagi089.obelix.data.campsites.LocationProblem
import de.hagi089.obelix.ui.finance.formatDay

/** Stellplätze: Knopf „Aktuellen Standort speichern“, Umschalter Liste/Karte und der jeweilige Inhalt. */
@Composable
fun CampsiteListScreen(
    viewModel: CampsiteListViewModel,
    onLocated: (GeoPosition) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Beim Öffnen und nach der Rückkehr aus dem Formular neu vom Server laden.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val locate = state.locate
    LaunchedEffect(locate) {
        if (locate is LocateState.Found) {
            onLocated(locate.position)
            viewModel.consumeLocation()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) viewModel.locate() else viewModel.permissionDenied()
    }
    val onSaveLocation = {
        val granted = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) viewModel.locate() else permissionLauncher.launch(LOCATION_PERMISSIONS)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val locating = locate is LocateState.Locating
            Button(
                onClick = onSaveLocation,
                enabled = !locating,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(imageVector = Icons.Filled.MyLocation, contentDescription = null)
                Text(
                    text = stringResource(if (locating) R.string.campsite_locating else R.string.campsite_save_location),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (locating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (locate is LocateState.Failed) LocationProblemMessage(problem = locate.problem)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == CampsiteMode.LIST,
                    onClick = { viewModel.setMode(CampsiteMode.LIST) },
                    label = { Text(stringResource(R.string.campsite_mode_list)) },
                )
                FilterChip(
                    selected = state.mode == CampsiteMode.MAP,
                    onClick = { viewModel.setMode(CampsiteMode.MAP) },
                    label = { Text(stringResource(R.string.campsite_mode_map)) },
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                state.mode == CampsiteMode.MAP -> CampsiteMap(campsites = state.campsites, onOpen = onOpen, modifier = Modifier.fillMaxSize())
                else -> CampsiteList(state = state, onOpen = onOpen)
            }
        }
    }
}

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
private fun LocationProblemMessage(problem: LocationProblem) {
    val context = LocalContext.current
    val (messageRes, buttonRes) = when (problem) {
        LocationProblem.PERMISSION_DENIED -> R.string.campsite_location_denied to R.string.campsite_open_app_settings
        LocationProblem.SERVICE_DISABLED -> R.string.campsite_location_disabled to R.string.campsite_open_location_settings
        LocationProblem.UNAVAILABLE -> R.string.campsite_location_unavailable to null
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        if (buttonRes != null) {
            OutlinedButton(
                onClick = {
                    val intent = when (problem) {
                        LocationProblem.SERVICE_DISABLED -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // Keine Einstellungs-App gefunden: die Meldung bleibt stehen.
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(buttonRes)) }
        }
    }
}

@Composable
private fun CampsiteList(state: CampsiteListState, onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
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
        if (state.campsites.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.campsite_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
        } else {
            items(state.campsites, key = { it.id }) { campsite ->
                CampsiteRow(campsite = campsite, creator = state.userName(campsite.createdBy), onClick = { onOpen(campsite.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CampsiteRow(campsite: Campsite, creator: String?, onClick: () -> Unit) {
    val details = buildList {
        add(formatDay(campsite.date))
        if (creator != null) add(stringResource(R.string.campsite_created_by, creator))
        campsite.rating?.let { add(stringResource(R.string.campsite_rating_value, it)) }
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(campsite.name ?: stringResource(R.string.campsite_unnamed)) },
        supportingContent = {
            Column {
                Text(details)
                Text(campsite.comment, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}
