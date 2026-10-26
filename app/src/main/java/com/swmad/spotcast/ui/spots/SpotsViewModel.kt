package com.swmad.spotcast.ui.spots

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.Activity
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.FireStoreSpotService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.weather.KtorWeatherService
import com.swmad.spotcast.integration.weather.WeatherService
import com.swmad.spotcast.ui.details.WeatherUiState
import com.swmad.spotcast.ui.details.toUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// State for every screen that shows the list of spots: the map and the list.
// Each spot also gets its 7-day forecast, so every pin can be rated.
class SpotsViewModel(
    private val spotService: SpotService = FireStoreSpotService(),
    private val authService: AuthService = AuthFireBaseService(),
    private val weatherService: WeatherService = KtorWeatherService()
) : ViewModel() {

    var spots by mutableStateOf<List<Spot>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    // Forecast per spot id
    var forecasts by mutableStateOf<Map<String, WeatherUiState>>(emptyMap())
        private set

    // null means "Best today": every spot shows its best activity
    var activity by mutableStateOf<Activity?>(null)
        private set

    var selectedSpotId by mutableStateOf<String?>(null)
        private set

    fun selectActivity(newActivity: Activity?) {
        activity = newActivity
    }

    fun selectSpot(spotId: String?) {
        selectedSpotId = spotId
    }

    // Firestore removes the document and the listener emits the list without it
    fun deleteSpot(spot: Spot) {
        val userId = authService.currentUser?.id ?: return
        if (selectedSpotId == spot.id) selectedSpotId = null
        viewModelScope.launch {
            try {
                spotService.deleteSpot(userId, spot.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                error = e.localizedMessage ?: "The spot could not be deleted"
            }
        }
    }

    fun retryForecast(spot: Spot) {
        loadForecast(spot)
    }

    // One coroutine per spot, so the forecasts are fetched in parallel
    // and every pin gets its colour as soon as its own answer arrives
    private fun loadForecasts(newSpots: List<Spot>) {
        newSpots.filter { it.id !in forecasts }.forEach { loadForecast(it) }
    }

    private fun loadForecast(spot: Spot) {
        viewModelScope.launch {
            forecasts = forecasts + (spot.id to WeatherUiState.Loading)
            val state = weatherService.getForecast(spot.latitude, spot.longitude, spot.isByTheWater).toUiState()
            forecasts = forecasts + (spot.id to state)
        }
    }

    init {
        val userId = authService.currentUser?.id
        if (userId == null) {
            isLoading = false
            error = "You are not signed in"
        } else {
            // The collection runs in viewModelScope, so the Firestore listener
            // is removed when the view model is cleared.
            viewModelScope.launch {
                spotService.observeSpots(userId)
                    .catch { e ->
                        Log.v(TAG, e.toString())
                        error = e.localizedMessage ?: "The spots could not be loaded"
                        isLoading = false
                    }
                    .collect { result ->
                        spots = result
                        error = null
                        isLoading = false
                        if (result.none { it.id == selectedSpotId }) selectedSpotId = null
                        loadForecasts(result)
                    }
            }
        }
    }

    override fun onCleared() {
        weatherService.close()
    }

    companion object {
        private const val TAG = "SPOTS VIEW MODEL"
    }
}
