package com.swmad.spotcast.integration.firestore

import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Spot
import kotlinx.coroutines.flow.Flow

interface SpotService {
    // Emits the user's spots, and emits again every time they change in Firestore
    fun observeSpots(userId: String): Flow<List<Spot>>

    suspend fun getSpot(userId: String, spotId: String): Spot?

    // Returns the id of the new document
    suspend fun addSpot(userId: String, spot: NewSpot): String

    suspend fun deleteSpot(userId: String, spotId: String)
}
