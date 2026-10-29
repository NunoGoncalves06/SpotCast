package com.swmad.spotcast.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swmad.spotcast.domain.DailyForecast
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.Weather
import com.swmad.spotcast.domain.weatherDescription
import com.swmad.spotcast.domain.Verdict
import com.swmad.spotcast.domain.bestRating
import com.swmad.spotcast.domain.rateAll
import com.swmad.spotcast.ui.components.DeleteSpotDialog
import com.swmad.spotcast.ui.components.ErrorMessage
import com.swmad.spotcast.ui.components.Loader
import com.swmad.spotcast.ui.components.MapPin
import com.swmad.spotcast.ui.components.SpotMap
import com.swmad.spotcast.ui.components.activityIcon
import com.swmad.spotcast.ui.components.verdictTint
import com.swmad.spotcast.ui.components.weatherIcon
import com.swmad.spotcast.ui.components.formatCoordinates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    spotId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DetailsViewModel = viewModel()
) {
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Fires when the screen enters the composition and again if the id changes (lesson 5)
    LaunchedEffect(spotId) {
        viewModel.load(spotId)
    }
    LaunchedEffect(viewModel.isDeleted) {
        if (viewModel.isDeleted) {
            onDeleted()
        }
    }

    val spot = viewModel.spot
    val error = viewModel.error

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spot?.name ?: "Spot") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (spot != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete spot")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                viewModel.isLoading -> Loader()

                spot == null -> ErrorMessage(message = error ?: "The spot could not be loaded")

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (error != null) {
                        Text(text = error, color = MaterialTheme.colorScheme.error)
                    }
                    SpotInfo(spot)
                    val today = (viewModel.weather as? WeatherUiState.Loaded)?.weather?.daily?.firstOrNull()
                    // The pin has the colour of the spot's best verdict for today, as on the Today map
                    SpotLocation(spot, verdict = today?.let { bestRating(it, spot.isByTheWater).verdict })
                    today?.let { TodayReport(spot, it) }
                    WeatherSection(
                        state = viewModel.weather,
                        onRefresh = { viewModel.refreshWeather() }
                    )
                }
            }
        }
    }

    if (showDeleteDialog && spot != null) {
        DeleteSpotDialog(
            spot = spot,
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun SpotInfo(spot: Spot, modifier: Modifier = Modifier) {
    val savedOn = remember(spot.createdAt) {
        Instant.ofEpochMilli(spot.createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = spot.category.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(text = formatCoordinates(spot.latitude, spot.longitude))
        Text(text = "Saved on $savedOn", style = MaterialTheme.typography.bodySmall)
        if (spot.note.isNotBlank()) {
            Text(text = spot.note, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// A small map that only shows where the spot is. Gestures are off so the page still scrolls.
@Composable
private fun SpotLocation(spot: Spot, verdict: Verdict?, modifier: Modifier = Modifier) {
    val pins = remember(spot.id, verdict) {
        listOf(MapPin(spot.id, spot.latitude, spot.longitude, spot.name, verdict))
    }
    SpotMap(
        pins = pins,
        interactive = false,
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
    )
}

// The full Go or Skip report: every activity, with the reason for its verdict
@Composable
private fun TodayReport(spot: Spot, day: DailyForecast, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Today here", style = MaterialTheme.typography.titleMedium)
            rateAll(day, spot.isByTheWater).forEach { rating ->
                val tint = verdictTint(rating.verdict)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tint.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(activityIcon(rating.activity), contentDescription = null, tint = tint.content)
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(text = rating.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = rating.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSection(
    state: WeatherUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Weather",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh, enabled = state !is WeatherUiState.Loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Update the weather")
                }
            }
            when (state) {
                WeatherUiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                is WeatherUiState.Error -> ErrorMessage(message = state.message, onRetry = onRefresh)

                is WeatherUiState.Loaded -> WeatherContent(state.weather)
            }
        }
    }
}

@Composable
private fun WeatherContent(weather: Weather) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = weatherIcon(weather.current.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        )
        Column {
            Text(
                text = "${weather.current.temperature.roundToInt()} °C",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(text = weatherDescription(weather.current.weatherCode))
            Text(
                text = "Wind ${weather.current.windSpeed.roundToInt()} km/h",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
    Text(text = "Next days", style = MaterialTheme.typography.labelLarge)
    // A plain Column is enough here: seven rows inside a page that already scrolls
    weather.daily.forEach { day ->
        ForecastRow(day)
    }
}

@Composable
private fun ForecastRow(day: DailyForecast, modifier: Modifier = Modifier) {
    val dayName = remember(day.date) {
        val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$weekday ${day.date.dayOfMonth}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = dayName, modifier = Modifier.width(64.dp))
        Icon(
            imageVector = weatherIcon(day.weatherCode),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(20.dp)
        )
        Text(
            text = weatherDescription(day.weatherCode),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = day.precipitationProbability?.let { "$it %" } ?: "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(44.dp)
        )
        Text(text = "${formatTemperature(day.minTemperature)} / ${formatTemperature(day.maxTemperature)}")
    }
}

private fun formatTemperature(value: Double?): String =
    value?.let { "${it.roundToInt()}°" } ?: "-"
