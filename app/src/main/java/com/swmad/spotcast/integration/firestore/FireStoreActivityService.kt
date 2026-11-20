package com.swmad.spotcast.integration.firestore

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import com.swmad.spotcast.domain.CustomActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Own activities are stored per user: users/{userId}/activities/{activityId}
class FireStoreActivityService : ActivityService {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        const val ACTIVITIES_COLLECTION_NAME = "activities"
        private const val TAG = "ACTIVITY SERVICE"
    }

    private fun activities(userId: String): CollectionReference =
        db.collection(FireStoreSpotService.USERS_COLLECTION_NAME).document(userId).collection(ACTIVITIES_COLLECTION_NAME)

    // Same idea as the spots: the snapshot listener is wrapped in a Flow and removed when the collector stops
    override fun observeActivities(userId: String): Flow<List<CustomActivity>> = callbackFlow {
        val registration = activities(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val result = snapshot?.documents.orEmpty()
                .mapNotNull { it.toActivityOrNull() }
                .sortedBy { it.createdAt }
            trySend(result)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addActivity(userId: String, activity: CustomActivity): String {
        val reference = activities(userId).add(activity.toFS()).await()
        return reference.id
    }

    // update() and not set(): createdAt belongs to the first save and must not change
    override suspend fun updateActivity(userId: String, activity: CustomActivity) {
        val fs = activity.toFS()
        activities(userId).document(activity.id).update(
            mapOf(
                "name" to fs.name,
                "icon" to fs.icon,
                "waterOnly" to fs.waterOnly,
                "criteria" to fs.criteria
            )
        ).await()
    }

    override suspend fun deleteActivity(userId: String, activityId: String) {
        activities(userId).document(activityId).delete().await()
    }

    private fun DocumentSnapshot.toActivityOrNull(): CustomActivity? {
        return try {
            toObject<ActivityFS>()?.toDomain()
        } catch (e: Exception) {
            Log.v(TAG, "Skipping document $id: $e")
            null
        }
    }
}
