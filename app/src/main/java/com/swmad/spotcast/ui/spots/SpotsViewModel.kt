package com.swmad.spotcast.ui.spots

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.integration.auth.AuthFireBaseService
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.FireStoreSpotService
import com.swmad.spotcast.integration.firestore.SpotService
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

// State for every screen that shows the list of spots: the list, the map and the profile.
class SpotsViewModel(
    spotService: SpotService = FireStoreSpotService(),
    authService: AuthService = AuthFireBaseService()
) : ViewModel() {

    var spots by mutableStateOf<List<Spot>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

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
                    }
            }
        }
    }

    companion object {
        private const val TAG = "SPOTS VIEW MODEL"
    }
}
