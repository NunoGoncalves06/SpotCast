package com.swmad.spotcast.ui.spots

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.Activity
import com.swmad.spotcast.domain.ActivityKind
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.MAX_CUSTOM_ACTIVITIES
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.ActivityService
import com.swmad.spotcast.integration.firestore.FireStoreActivityService
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
    private val weatherService: WeatherService = KtorWeatherService(),
    private val activityService: ActivityService = FireStoreActivityService()
) : ViewModel() {

    var spots by mutableStateOf<List<Spot>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    // A problem with the whole list, such as no connection to Firestore. The screens show it instead of the list.
    var error by mutableStateOf<String?>(null)
        private set

    // A failed delete is a one-off message. The list stays on screen, so it has its own state.
    var deleteError by mutableStateOf<String?>(null)
        private set

    // Forecast per spot id
    var forecasts by mutableStateOf<Map<String, WeatherUiState>>(emptyMap())
        private set

    // The activities the user made. The five built-in ones are always there and cannot be changed.
    var customActivities by mutableStateOf<List<CustomActivity>>(emptyList())
        private set

    // Everything a spot is rated for, in the order of the activity bar
    val activities: List<ActivityKind> get() = Activity.entries + customActivities

    // true after the first answer from Firestore. Until then the limit of 10 cannot be checked.
    var activitiesReady by mutableStateOf(false)
        private set

    val canAddActivity: Boolean get() = customActivities.size < MAX_CUSTOM_ACTIVITIES

    // Problem while saving or deleting an activity, shown on the screen that asked for it
    var activityError by mutableStateOf<String?>(null)
        private set

    var isSavingActivity by mutableStateOf(false)
        private set

    // null means "Best today": every spot shows its best activity
    var activity by mutableStateOf<ActivityKind?>(null)
        private set

    var selectedSpotId by mutableStateOf<String?>(null)
        private set

    fun selectActivity(newActivity: ActivityKind?) {
        activity = newActivity
    }

    fun clearActivityError() {
        activityError = null
    }

    // A new activity (empty id) is added, an existing one is updated. onDone runs after Firestore accepted it.
    fun saveActivity(activity: CustomActivity, onDone: () -> Unit) {
        val userId = authService.currentUser?.id ?: return
        if (activity.id.isEmpty() && !activitiesReady) {
            activityError = "Your activities are not loaded yet. Check the connection and try again."
            return
        }
        if (activity.id.isEmpty() && !canAddActivity) {
            activityError = "You can have up to $MAX_CUSTOM_ACTIVITIES activities of your own. Delete one to add another."
            return
        }
        if (!activity.isValid()) {
            activityError = "Give the activity a name and check its ranges"
            return
        }
        viewModelScope.launch {
            isSavingActivity = true
            activityError = null
            try {
                if (activity.id.isEmpty()) {
                    activityService.addActivity(userId, activity)
                } else {
                    activityService.updateActivity(userId, activity)
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                activityError = e.localizedMessage ?: "The activity could not be saved"
            } finally {
                isSavingActivity = false
            }
        }
    }

    // The listener emits the list without it, and a selected activity falls back to "Best today"
    fun deleteActivity(activityId: String, onDone: () -> Unit) {
        val userId = authService.currentUser?.id ?: return
        viewModelScope.launch {
            isSavingActivity = true
            activityError = null
            try {
                activityService.deleteActivity(userId, activityId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                activityError = e.localizedMessage ?: "The activity could not be deleted"
            } finally {
                isSavingActivity = false
            }
        }
    }

    fun selectSpot(spotId: String?) {
        selectedSpotId = spotId
    }

    fun clearDeleteError() {
        deleteError = null
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
                deleteError = e.localizedMessage ?: "The spot could not be deleted"
            }
        }
    }

    fun retryForecast(spot: Spot) {
        loadForecast(spot)
    }

    // For when the connection is back: asks again for the spots that have no forecast
    fun retryFailedForecasts() {
        spots.filter { forecasts[it.id] is WeatherUiState.Error }.forEach { loadForecast(it) }
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
            viewModelScope.launch {
                activityService.observeActivities(userId)
                    // The map works without them: a failure here only leaves the built-in activities
                    .catch { e -> Log.v(TAG, e.toString()) }
                    .collect { result ->
                        customActivities = result
                        activitiesReady = true
                        // An activity that was edited is the same chip with new ranges, a deleted one is gone
                        activity = activity?.let { selected -> result.firstOrNull { it.id == selected.id } ?: selected.takeIf { it is Activity } }
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
