package com.swmad.spotcast.ui.spots

import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.FireStoreSpotService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.location.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AddSpotViewModel(
    private val spotService: SpotService = FireStoreSpotService(),
    private val authService: AuthService = AuthFireBaseService()
) : ViewModel() {

    var location by mutableStateOf<Location?>(null)
        private set

    var isLocating by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var isSaved by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

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

    fun save(name: String, category: SpotCategory, note: String) {
        val position = location
        val userId = authService.currentUser?.id
        when {
            name.isBlank() -> error = "Give the spot a name"

            position == null -> error = "Wait for the position before saving"

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
                            latitude = position.latitude,
                            longitude = position.longitude
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

    companion object {
        private const val TAG = "ADD SPOT VIEW MODEL"
    }
}
