package com.swmad.spotcast.integration.firestore

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Spot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Spots are stored per user: users/{userId}/spots/{spotId}
class FireStoreSpotService : SpotService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        const val USERS_COLLECTION_NAME = "users"
        const val SPOTS_COLLECTION_NAME = "spots"
        private const val TAG = "SPOT SERVICE"
    }

    private fun spots(userId: String): CollectionReference =
        db.collection(USERS_COLLECTION_NAME).document(userId).collection(SPOTS_COLLECTION_NAME)

    // Real-time updates: the snapshot listener is wrapped in a Flow.
    // The listener is removed when the collecting coroutine is cancelled.
    override fun observeSpots(userId: String): Flow<List<Spot>> = callbackFlow {
        val registration = spots(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val result = snapshot?.documents.orEmpty()
                .mapNotNull { it.toSpotOrNull() }
                .sortedByDescending { it.createdAt }
            trySend(result)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun getSpot(userId: String, spotId: String): Spot? {
        val document = spots(userId).document(spotId).get().await()
        return document.toSpotOrNull()
    }

    override suspend fun addSpot(userId: String, spot: NewSpot): String {
        val reference = spots(userId).add(spot.toFS()).await()
        return reference.id
    }

    override suspend fun deleteSpot(userId: String, spotId: String) {
        spots(userId).document(spotId).delete().await()
    }

    // Uses SpotFS to deserialize from Firestore but maps to the domain model
    private fun DocumentSnapshot.toSpotOrNull(): Spot? {
        return try {
            toObject<SpotFS>()?.toDomain()
        } catch (e: Exception) {
            Log.v(TAG, "Skipping document $id: $e")
            null
        }
    }
}
