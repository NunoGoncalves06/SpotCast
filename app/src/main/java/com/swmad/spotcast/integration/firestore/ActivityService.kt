package com.swmad.spotcast.integration.firestore

import com.swmad.spotcast.domain.CustomActivity
import kotlinx.coroutines.flow.Flow

// The activities a user made, stored below users/{userId}/activities
interface ActivityService {
    // Emits the user's activities, oldest first, and emits again every time they change
    fun observeActivities(userId: String): Flow<List<CustomActivity>>

    // Returns the id of the new document
    suspend fun addActivity(userId: String, activity: CustomActivity): String

    suspend fun updateActivity(userId: String, activity: CustomActivity)

    suspend fun deleteActivity(userId: String, activityId: String)
}
