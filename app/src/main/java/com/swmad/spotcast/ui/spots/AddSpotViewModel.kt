package com.swmad.spotcast.ui.spots

import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.GeocodingResponse
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Place
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.FireStoreSpotService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.geocoding.GeocodingService
import com.swmad.spotcast.integration.geocoding.KtorGeocodingService
import com.swmad.spotcast.integration.location.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AddSpotViewModel(
    private val spotService: SpotService = FireStoreSpotService(),
    private val authService: AuthService = AuthFireBaseService(),
    private val geocodingService: GeocodingService = KtorGeocodingService()
) : ViewModel() {

    // true: the spot is saved where the user stands. false: at a place the user picked.
    var useCurrentLocation by mutableStateOf(true)
        private set

    var location by mutableStateOf<Location?>(null)
        private set

    var isLocating by mutableStateOf(false)
        private set

    // The place that was found or typed and is waiting for the user to say "yes, this is it"
    var candidate by mutableStateOf<Place?>(null)
        private set

    // The place the user confirmed on the map
    var pickedPlace by mutableStateOf<Place?>(null)
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var isSaved by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    val hasPosition: Boolean
        get() = if (useCurrentLocation) location != null else pickedPlace != null

    fun chooseCurrentLocation() {
        useCurrentLocation = true
        error = null
    }

    fun chooseOtherPlace() {
        useCurrentLocation = false
        error = null
    }

    // The location service needs a context, so the composable creates it and hands it in.
    fun locate(locationService: LocationService) {
        if (isLocating) return
        viewModelScope.launch {
            isLocating = true
            error = null
            try {
                val res = locationService.getCurrentLocation()
                if (res == null) {
                    error = "The position could not be read. Check that location is turned on."
                } else {
                    location = res
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.v(TAG, e.toString())
                error = "The position could not be read."
            } finally {
                isLocating = false
            }
        }
    }

    // Looks the address up. A hit is not used right away: it becomes the candidate
    // that the user has to confirm on the map.
    fun findAddress(query: String) {
        if (isSearching) return
        if (query.isBlank()) {
            error = "Type an address or the name of a place"
            return
        }
        viewModelScope.launch {
            isSearching = true
            error = null
            when (val response = geocodingService.findPlace(query.trim())) {
                is GeocodingResponse.Success -> candidate = response.place

                GeocodingResponse.Failure.NotFound ->
                    error = "No place was found for that. Try adding the city or the country."

                GeocodingResponse.Failure.NetworkError ->
                    error = "No connection to the address service"

                is GeocodingResponse.Failure.GeneralError -> error = response.message
            }
            isSearching = false
        }
    }

    // Typed coordinates are checked here and then shown on the map like an address hit
    fun checkCoordinates(latitudeText: String, longitudeText: String) {
        val latitude = latitudeText.trim().replace(',', '.').toDoubleOrNull()
        val longitude = longitudeText.trim().replace(',', '.').toDoubleOrNull()
        when {
            latitude == null || longitude == null ->
                error = "Type both coordinates as numbers, for example 56.1736 and 10.2255"

            latitude !in -90.0..90.0 -> error = "The latitude has to be between -90 and 90"

            longitude !in -180.0..180.0 -> error = "The longitude has to be between -180 and 180"

            else -> {
                error = null
                candidate = Place("The coordinates you typed", latitude, longitude)
            }
        }
    }

    fun confirmCandidate() {
        pickedPlace = candidate
        candidate = null
    }

    fun rejectCandidate() {
        candidate = null
    }

    fun save(name: String, category: SpotCategory, note: String) {
        val latitude = if (useCurrentLocation) location?.latitude else pickedPlace?.latitude
        val longitude = if (useCurrentLocation) location?.longitude else pickedPlace?.longitude
        val userId = authService.currentUser?.id
        when {
            name.isBlank() -> error = "Give the spot a name"

            latitude == null || longitude == null ->
                error = if (useCurrentLocation) {
                    "Wait for the position before saving"
                } else {
                    "Find the place and confirm it on the map before saving"
                }

            userId == null -> error = "You are not signed in"

            else -> viewModelScope.launch {
                isSaving = true
                error = null
                try {
                    spotService.addSpot(
                        userId,
                        NewSpot(
                            name = name.trim(),
                            category = category,
                            note = note.trim(),
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                    isSaved = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.v(TAG, e.toString())
                    error = e.localizedMessage ?: "The spot could not be saved"
                } finally {
                    isSaving = false
                }
            }
        }
    }

    override fun onCleared() {
        geocodingService.close()
    }

    companion object {
        private const val TAG = "ADD SPOT VIEW MODEL"
    }
}
