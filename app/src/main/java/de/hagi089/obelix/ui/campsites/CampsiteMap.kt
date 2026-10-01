package de.hagi089.obelix.ui.campsites

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.hagi089.obelix.R
import de.hagi089.obelix.data.campsites.Campsite
import de.hagi089.obelix.data.campsites.GeoPosition
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

/** Stärkste Vergrößerung beim automatischen Ausschnitt (Straßenebene); der Kartentyp hat Kacheln bis Stufe 19. */
private const val MAX_FRAME_ZOOM = 17.0
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
    val currentOnOpen by rememberUpdatedState(onOpen)
    val mapView = remember { createMapView(context) }
    val unnamed = stringResource(R.string.campsite_unnamed)

    BindMapLifecycle(mapView)

    AndroidView(
        modifier = modifier.clipToBounds(),
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

/** Verbindet die Karte mit dem Lebenszyklus des Bildschirms (onResume/onPause) und gibt sie beim Verlassen frei. */
@Composable
private fun BindMapLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
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
}

/**
 * Karte mit **einem** verschiebbaren Marker für das Stellplatz-Formular. [position] ist die einzige Quelle der Wahrheit:
 * Der Marker steht immer dort; ändert sie sich (auch wenn eine Speicherung scheiterte und die Position zurückspringt),
 * folgt der Marker und die Karte zentriert sich auf sie. Beim Öffnen wird die Karte auf [position] zentriert.
 *
 * Der Marker wird (Bibliotheksverhalten) durch langes Drücken aufgenommen und dann gezogen; beim Loslassen kommt
 * [onMarkerMoved] mit der neuen Position. Die Karte selbst lässt sich mit einem Finger verschieben, mit zwei Fingern
 * zoomen (und mit den Zoom-Knöpfen). [onTouching] meldet, solange die Karte berührt wird, damit der umgebende
 * scrollbare Bildschirm in dieser Zeit nicht mitscrollt.
 */
@SuppressLint("ClickableViewAccessibility") // Der Touch-Listener gibt alle Berührungen an die Karte weiter (false).
@Composable
fun CampsitePositionMap(
    position: GeoPosition,
    markerDraggable: Boolean,
    onMarkerMoved: (latitude: Double, longitude: Double) -> Unit,
    onTouching: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnMoved by rememberUpdatedState(onMarkerMoved)
    val currentOnTouching by rememberUpdatedState(onTouching)
    val mapView = remember { createMapView(context) }
    val holder = remember(mapView) { PositionMapHolder() }
    val description = stringResource(R.string.campsite_map_description)
    BindMapLifecycle(mapView)

    AndroidView(
        modifier = modifier.clipToBounds().semantics { contentDescription = description },
        factory = {
            mapView.apply {
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            currentOnTouching(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            currentOnTouching(false)
                        }
                    }
                    false // die Karte und der Marker verarbeiten die Berührung weiter
                }
            }
        },
        update = { view ->
            val marker = holder.marker ?: Marker(view).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                // Tippen tut nichts (keine Sprechblase): der Marker ist nur das Ziel zum Ziehen.
                setOnMarkerClickListener { _, _ -> true }
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {
                        holder.dragging = true
                    }

                    override fun onMarkerDrag(marker: Marker) = Unit

                    override fun onMarkerDragEnd(marker: Marker) {
                        holder.dragging = false
                        currentOnMoved(marker.position.latitude, marker.position.longitude)
                    }
                })
                view.overlays.add(this)
                holder.marker = this
            }
            marker.isDraggable = markerDraggable
            val target = GeoPoint(position.latitude, position.longitude)
            val moved = Math.abs(marker.position.latitude - target.latitude) > POSITION_EPSILON ||
                Math.abs(marker.position.longitude - target.longitude) > POSITION_EPSILON
            // Nur nachziehen, wenn die Position nicht vom eigenen Ziehen stammt (dann steht der Marker schon dort).
            if (moved && !holder.dragging) {
                marker.position = target
            }
            if (!holder.centered || (moved && !holder.dragging)) {
                if (!holder.centered) view.controller.setZoom(POSITION_ZOOM)
                view.controller.setCenter(target)
                holder.centered = true
            }
            view.invalidate()
        },
    )
}

private const val POSITION_ZOOM = 16.0
private const val POSITION_EPSILON = 1e-9

/** Merkt sich Marker und Zustand der Positionskarte zwischen den Aktualisierungen (keine Compose-Zustände nötig). */
private class PositionMapHolder {
    var marker: Marker? = null
    var centered = false
    var dragging = false
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
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        setMultiTouchControls(true)
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
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
        view.controller.setZoom(15.0)
        view.controller.setCenter(GeoPoint(campsites[0].latitude, campsites[0].longitude))
        return
    }
    val box = BoundingBox.fromGeoPoints(campsites.map { GeoPoint(it.latitude, it.longitude) })
    // Erst nach dem Layout, sonst kennt die Karte ihre Größe noch nicht.
    view.post {
        view.zoomToBoundingBox(box, false, 96)
        // Liegen die Plätze dicht beieinander, wäre der Ausschnitt stärker vergrößert, als es Kacheln gibt (leere Karte).
        if (view.zoomLevelDouble > MAX_FRAME_ZOOM) view.controller.setZoom(MAX_FRAME_ZOOM)
    }
}
