package de.hagi089.obelix.ui.campsites

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.hagi089.obelix.R
import de.hagi089.obelix.data.campsites.Campsite
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

private const val TILE_CACHE_MAX_BYTES = 50L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 40L * 1024 * 1024

/**
 * Karte mit OpenStreetMap-Kacheln (osmdroid, ohne API-Schlüssel und ohne Kosten). Alles, was von der Bibliothek
 * abhängt, steht nur in dieser Datei, damit sie sich bei Bedarf leicht ersetzen lässt (osmdroid wird nicht mehr
 * weiterentwickelt, siehe docs/PROJEKTPLAN.md).
 *
 * Nutzungsregeln der OSM-Kacheln: eigener User-Agent (Paketname), Quellenangabe auf der Karte, Kacheln nur
 * bei Bedarf laden, kleiner Zwischenspeicher im Cache-Ordner der App.
 */
@Composable
fun CampsiteMap(
    campsites: List<Campsite>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnOpen by rememberUpdatedState(onOpen)
    val mapView = remember { createMapView(context) }
    val unnamed = stringResource(R.string.campsite_unnamed)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        // Der Bildschirm kann schon „resumed“ sein, wenn die Karte erst jetzt entsteht.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            view.overlays.removeAll { it is Marker }
            campsites.forEach { campsite ->
                val title = campsite.name ?: unnamed
                val marker = Marker(view).apply {
                    position = GeoPoint(campsite.latitude, campsite.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    this.title = title
                    // Tippen öffnet direkt den Stellplatz (keine zusätzliche Sprechblase).
                    setOnMarkerClickListener { _, _ ->
                        currentOnOpen(campsite.id)
                        true
                    }
                }
                view.overlays.add(marker)
            }
            frame(view, campsites)
            view.invalidate()
        },
    )
}

private fun createMapView(context: Context): MapView {
    val configuration = Configuration.getInstance()
    configuration.userAgentValue = context.packageName
    val base = File(context.cacheDir, "osmdroid")
    configuration.osmdroidBasePath = base
    configuration.osmdroidTileCache = File(base, "tiles")
    configuration.tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
    configuration.tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
    return MapView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        overlays.add(CopyrightOverlay(context))
    }
}

/** Zeigt alle Stellplätze (Ausschnitt um alle Markierungen) oder bei nur einem Platz eine Nahansicht. */
private fun frame(view: MapView, campsites: List<Campsite>) {
    if (campsites.isEmpty()) {
        view.controller.setZoom(5.0)
        view.controller.setCenter(GeoPoint(51.0, 10.0))
        return
    }
    if (campsites.size == 1) {
        view.controller.setZoom(14.0)
        view.controller.setCenter(GeoPoint(campsites[0].latitude, campsites[0].longitude))
        return
    }
    val box = BoundingBox.fromGeoPoints(campsites.map { GeoPoint(it.latitude, it.longitude) })
    // Erst nach dem Layout, sonst kennt die Karte ihre Größe noch nicht.
    view.post { view.zoomToBoundingBox(box, false, 96) }
}
