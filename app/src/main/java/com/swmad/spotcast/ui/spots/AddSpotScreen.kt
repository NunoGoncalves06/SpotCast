package com.swmad.spotcast.ui.spots

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.swmad.spotcast.domain.Place
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.integration.location.LocationService
import com.swmad.spotcast.ui.components.MapMarker
import com.swmad.spotcast.ui.components.OsmMap
import com.swmad.spotcast.ui.components.formatCoordinates

private const val PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpotScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddSpotViewModel = viewModel()
) {
    val context = LocalContext.current
    val locationService = remember {
        LocationService(LocationServices.getFusedLocationProviderClient(context))
    }

    // Permission handling as in lesson 10
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

    // Form fields survive rotation
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(SpotCategory.OTHER) }
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(PERMISSION)
        }
    }
    // Reads the position as soon as the permission is there
    LaunchedEffect(granted) {
        if (granted && viewModel.location == null) {
            viewModel.locate(locationService)
        }
    }
    LaunchedEffect(viewModel.isSaved) {
        if (viewModel.isSaved) {
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add spot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpotCategory.entries.forEach { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.label) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Where is it?", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewModel.useCurrentLocation,
                    onClick = { viewModel.chooseCurrentLocation() },
                    label = { Text("Where I am now") }
                )
                FilterChip(
                    selected = !viewModel.useCurrentLocation,
                    onClick = { viewModel.chooseOtherPlace() },
                    label = { Text("Another place") }
                )
            }

            if (viewModel.useCurrentLocation) {
                PositionCard(
                    granted = granted,
                    isLocating = viewModel.isLocating,
                    coordinates = viewModel.location?.let {
                        formatCoordinates(it.latitude, it.longitude)
                    },
                    onRequestPermission = { launcher.launch(PERMISSION) },
                    onRefresh = { viewModel.locate(locationService) }
                )
            } else {
                OtherPlaceCard(
                    pickedPlace = viewModel.pickedPlace,
                    isSearching = viewModel.isSearching,
                    onFindAddress = { viewModel.findAddress(it) },
                    onCheckCoordinates = { latitude, longitude ->
                        viewModel.checkCoordinates(latitude, longitude)
                    }
                )
            }

            viewModel.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(name, category, note) },
                enabled = !viewModel.isSaving && viewModel.hasPosition && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save spot")
                }
            }
        }
    }

    // A place that was found or typed has to be confirmed on the map before it is used
    viewModel.candidate?.let { place ->
        ConfirmPlaceDialog(
            place = place,
            onConfirm = { viewModel.confirmCandidate() },
            onReject = { viewModel.rejectCandidate() }
        )
    }
}

@Composable
private fun PositionCard(
    granted: Boolean,
    isLocating: Boolean,
    coordinates: String?,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Position", style = MaterialTheme.typography.labelLarge)
            when {
                !granted -> {
                    Text("SpotCast needs the location permission to save where you are.")
                    TextButton(onClick = onRequestPermission) {
                        Text("Allow location")
                    }
                }

                isLocating -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Reading the GPS position")
                }

                else -> {
                    Text(
                        text = coordinates ?: "No position yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onRefresh) {
                        Text("Read position again")
                    }
                }
            }
        }
    }
}

// The user picks a place they are not standing at, by address or by coordinates.
@Composable
private fun OtherPlaceCard(
    pickedPlace: Place?,
    isSearching: Boolean,
    onFindAddress: (String) -> Unit,
    onCheckCoordinates: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var byAddress by rememberSaveable { mutableStateOf(true) }
    var address by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    // Clearing the focus closes the keyboard, so it does not cover the map dialog
    val focusManager = LocalFocusManager.current

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Position", style = MaterialTheme.typography.labelLarge)

            if (pickedPlace != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = formatCoordinates(pickedPlace.latitude, pickedPlace.longitude),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = pickedPlace.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "Search again below to pick a different place.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = byAddress,
                    onClick = { byAddress = true },
                    label = { Text("Address") }
                )
                FilterChip(
                    selected = !byAddress,
                    onClick = { byAddress = false },
                    label = { Text("Coordinates") }
                )
            }

            if (byAddress) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address or place name") },
                    singleLine = true,
                    // The search key on the keyboard does the same as the button below
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onFindAddress(address)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(
                    onClick = {
                        focusManager.clearFocus()
                        onFindAddress(address)
                    },
                    enabled = !isSearching && address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Find on the map")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
                FilledTonalButton(
                    onClick = {
                        focusManager.clearFocus()
                        onCheckCoordinates(latitude, longitude)
                    },
                    enabled = latitude.isNotBlank() && longitude.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show on the map")
                }
            }
        }
    }
}

// "Is this the right place?" The user sees the pin on the map and decides.
@Composable
private fun ConfirmPlaceDialog(
    place: Place,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val markers = remember(place) {
        listOf(MapMarker("candidate", place.latitude, place.longitude, place.name))
    }
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Is this the right place?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = place.name,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatCoordinates(place.latitude, place.longitude),
                    style = MaterialTheme.typography.bodySmall
                )
                OsmMap(
                    markers = markers,
                    centerLatitude = place.latitude,
                    centerLongitude = place.longitude,
                    zoom = 15.0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Text(
                    text = "Move and zoom the map to check. The spot is saved at the pin.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes, use this place")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("No, try again")
            }
        }
    )
}
