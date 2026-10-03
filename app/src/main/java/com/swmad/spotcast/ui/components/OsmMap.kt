package com.swmad.spotcast.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String = ""
)

// A map from OpenStreetMap, shown with the osmdroid library. It needs no API key.
// osmdroid is a classic Android view, so it is wrapped in AndroidView.
@Composable
fun OsmMap(
    markers: List<MapMarker>,
    centerLatitude: Double,
    centerLongitude: Double,
    zoom: Double,
    modifier: Modifier = Modifier,
    // false gives a still picture: the map ignores touches, so a scrolling page keeps scrolling
    interactive: Boolean = true,
    showMyLocation: Boolean = false,
    // The first tap on a marker shows its name, the second tap calls this with the marker id
    onMarkerOpen: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnMarkerOpen by rememberUpdatedState(onMarkerOpen)

    val mapView = remember {
        // The tile server asks every app to identify itself
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = context.packageName

        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // "(c) OpenStreetMap contributors" in the corner, required by the tile licence
            overlays.add(CopyrightOverlay(context))
        }
    }

    // Moves the camera when the caller asks for a new centre
    LaunchedEffect(centerLatitude, centerLongitude, zoom) {
        mapView.controller.setZoom(zoom)
        mapView.controller.setCenter(GeoPoint(centerLatitude, centerLongitude))
    }

    // The view has to follow the lifecycle and be cleaned up when it leaves the composition
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.matchParentSize(),
            update = { map ->
                map.overlays.removeAll { it is Marker }
                markers.forEach { item ->
                    val marker = Marker(map)
                    marker.position = GeoPoint(item.latitude, item.longitude)
                    marker.title = item.title
                    marker.snippet = item.snippet
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.setOnMarkerClickListener { clicked, _ ->
                        if (clicked.isInfoWindowShown) {
                            currentOnMarkerOpen?.invoke(item.id)
                        } else {
                            clicked.showInfoWindow()
                        }
                        true
                    }
                    map.overlays.add(marker)
                }

                // The blue dot for the user's own position. It needs the location permission.
                val hasMyLocation = map.overlays.any { it is MyLocationNewOverlay }
                if (showMyLocation && !hasMyLocation) {
                    val myLocation = MyLocationNewOverlay(GpsMyLocationProvider(map.context), map)
                    myLocation.enableMyLocation()
                    map.overlays.add(myLocation)
                }
                map.invalidate()
            }
        )
        if (!interactive) {
            // An empty pointerInput on top takes the touches before the map does
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {}
            )
        }
    }
}
