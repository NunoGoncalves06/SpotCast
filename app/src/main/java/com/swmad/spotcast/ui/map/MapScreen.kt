package com.swmad.spotcast.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.ui.components.Loader
import com.swmad.spotcast.ui.components.MapMarker
import com.swmad.spotcast.ui.components.OsmMap
import com.swmad.spotcast.ui.spots.SpotsViewModel

private const val PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

// Where the map starts before any spot is loaded (Aarhus)
private const val DEFAULT_LATITUDE = 56.1629
private const val DEFAULT_LONGITUDE = 10.2039

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenSpot: (String) -> Unit,
    viewModel: SpotsViewModel = viewModel()
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                context,
                PERMISSION
            )
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        granted = isGranted
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(PERMISSION)
        }
    }

    // The map centres on the newest spot once, the first time the spots arrive.
    // After that the user is in control of the map.
    var centerLatitude by remember { mutableStateOf(DEFAULT_LATITUDE) }
    var centerLongitude by remember { mutableStateOf(DEFAULT_LONGITUDE) }
    var zoom by remember { mutableStateOf(5.0) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.spots) {
        val newest = viewModel.spots.firstOrNull()
        if (newest != null && !placed) {
            centerLatitude = newest.latitude
            centerLongitude = newest.longitude
            zoom = 12.0
            placed = true
        }
    }

    val markers = viewModel.spots.map { spot ->
        MapMarker(
            id = spot.id,
            latitude = spot.latitude,
            longitude = spot.longitude,
            title = spot.name,
            snippet = "${spot.category.label}. Tap the marker again for the forecast"
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Map") }) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OsmMap(
                markers = markers,
                centerLatitude = centerLatitude,
                centerLongitude = centerLongitude,
                zoom = zoom,
                showMyLocation = granted,
                onMarkerOpen = onOpenSpot,
                modifier = Modifier.fillMaxSize()
            )

            if (viewModel.isLoading) {
                Loader()
            }

            val message = viewModel.error
                ?: if (!viewModel.isLoading && viewModel.spots.isEmpty()) {
                    "No spots yet. Add one from the Spots tab."
                } else {
                    null
                }
            if (message != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
