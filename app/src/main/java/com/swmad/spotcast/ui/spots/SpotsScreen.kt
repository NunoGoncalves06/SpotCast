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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.ui.components.EmptyState
import com.swmad.spotcast.ui.components.ErrorMessage
import com.swmad.spotcast.ui.components.Loader
import com.swmad.spotcast.ui.components.SpotCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotsScreen(
    onAddSpot: () -> Unit,
    onOpenSpot: (String) -> Unit,
    viewModel: SpotsViewModel = viewModel()
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("My spots") }) },
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
                    text = "Go somewhere you like and press Add spot to save the place."
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
                        SpotCard(spot = spot, onClick = { onOpenSpot(spot.id) })
                    }
                }
            }
        }
    }
}
