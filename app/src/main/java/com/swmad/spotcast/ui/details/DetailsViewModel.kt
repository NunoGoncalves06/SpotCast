package com.swmad.spotcast.ui.details

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.Weather
import com.swmad.spotcast.domain.WeatherResponse
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.FireStoreSpotService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.weather.KtorWeatherService
import com.swmad.spotcast.integration.weather.WeatherService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    data object Loading : WeatherUiState()
    data class Loaded(val weather: Weather) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class DetailsViewModel(
    private val spotService: SpotService = FireStoreSpotService(),
    private val authService: AuthService = AuthFireBaseService(),
    private val weatherService: WeatherService = KtorWeatherService()
) : ViewModel() {

    var spot by mutableStateOf<Spot?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var weather by mutableStateOf<WeatherUiState>(WeatherUiState.Loading)
        private set

    var isDeleted by mutableStateOf(false)
        private set

    fun load(spotId: String) {
        // The view model outlives recomposition, so the spot is only fetched once
        if (spot?.id == spotId) return
        val userId = authService.currentUser?.id
        if (userId == null) {
            isLoading = false
            error = "You are not signed in"
            return
        }
        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val result = spotService.getSpot(userId, spotId)
                if (result == null) {
                    error = "This spot does not exist any more"
                } else {
                    spot = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                error = e.localizedMessage ?: "The spot could not be loaded"
            } finally {
                isLoading = false
            }
            spot?.let { loadWeather(it) }
        }
    }

    fun refreshWeather() {
        val current = spot ?: return
        viewModelScope.launch {
            loadWeather(current)
        }
    }

    fun delete() {
        val current = spot ?: return
        val userId = authService.currentUser?.id ?: return
        viewModelScope.launch {
            try {
                spotService.deleteSpot(userId, current.id)
                isDeleted = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                error = e.localizedMessage ?: "The spot could not be deleted"
            }
        }
    }

    // Ktor suspends on its own dispatcher, so the main thread is not blocked here
    private suspend fun loadWeather(spot: Spot) {
        weather = WeatherUiState.Loading
        weather = when (val response = weatherService.getForecast(spot.latitude, spot.longitude)) {
            is WeatherResponse.Success -> WeatherUiState.Loaded(response.weather)

            is WeatherResponse.Failure.BadRequestError ->
                WeatherUiState.Error("The weather service did not accept this position")

            WeatherResponse.Failure.UnAvailable ->
                WeatherUiState.Error("The weather service is not available right now")

            WeatherResponse.Failure.NetworkError ->
                WeatherUiState.Error("No connection to the weather service")

            is WeatherResponse.Failure.GeneralError -> WeatherUiState.Error(response.message)
        }
    }

    // When to close the client is application specific (lesson 8).
    // Here it lives as long as the details screen is on the back stack.
    override fun onCleared() {
        weatherService.close()
    }

    companion object {
        private const val TAG = "DETAILS VIEW MODEL"
    }
}
