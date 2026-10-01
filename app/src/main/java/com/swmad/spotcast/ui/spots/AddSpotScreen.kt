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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.integration.location.LocationService
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

            PositionCard(
                granted = granted,
                isLocating = viewModel.isLocating,
                coordinates = viewModel.location?.let {
                    formatCoordinates(it.latitude, it.longitude)
                },
                onRequestPermission = { launcher.launch(PERMISSION) },
                onRefresh = { viewModel.locate(locationService) }
            )

            viewModel.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(name, category, note) },
                enabled = !viewModel.isSaving && viewModel.location != null && name.isNotBlank(),
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
