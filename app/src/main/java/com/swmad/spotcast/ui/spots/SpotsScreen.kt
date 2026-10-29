package com.swmad.spotcast.ui.spots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.ui.components.DeleteSpotDialog
import com.swmad.spotcast.ui.components.EmptyState
import com.swmad.spotcast.ui.components.ErrorMessage
import com.swmad.spotcast.ui.components.Loader
import com.swmad.spotcast.ui.components.SpotCard
import com.swmad.spotcast.ui.details.WeatherUiState
import com.swmad.spotcast.ui.map.ratingFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsScreen(
    onAddSpot: () -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: SpotsViewModel = viewModel()
) {
    // Only the id is kept, so the dialog survives a rotation. The spot is looked up in the list.
    var toDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val toDelete = viewModel.spots.firstOrNull { it.id == toDeleteId }
    val snackbarHost = remember { SnackbarHostState() }

    // A failed delete is a message at the bottom. The list stays on screen.
    // The message is cleared after it was shown: clearing it first would restart this effect and cancel the snackbar.
    LaunchedEffect(viewModel.deleteError) {
        val message = viewModel.deleteError ?: return@LaunchedEffect
        snackbarHost.showSnackbar("The spot could not be deleted: $message")
        viewModel.clearDeleteError()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("My spots") }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddSpot,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add spot") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val error = viewModel.error
            when {
                viewModel.isLoading -> Loader()

                error != null -> ErrorMessage(message = error)

                viewModel.spots.isEmpty() -> EmptyState(
                    title = "No spots yet",
                    text = "Save the places you like. Each one gets a Go or Skip for the day."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        // room for the floating button under the last card
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(viewModel.spots, key = { it.id }) { spot ->
                        val day = (viewModel.forecasts[spot.id] as? WeatherUiState.Loaded)
                            ?.weather?.daily?.firstOrNull()
                        SpotCard(
                            spot = spot,
                            rating = day?.let { ratingFor(spot, it, null) },
                            onClick = { onOpenSpot(spot.id) },
                            onLongClick = { toDeleteId = spot.id }
                        )
                    }
                }
            }
        }
    }

    toDelete?.let { spot ->
        DeleteSpotDialog(
            spot = spot,
            onConfirm = {
                viewModel.deleteSpot(spot)
                toDeleteId = null
            },
            onDismiss = { toDeleteId = null }
        )
    }
}
