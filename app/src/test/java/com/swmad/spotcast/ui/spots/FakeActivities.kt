package com.swmad.spotcast.ui.spots

import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.integration.firestore.ActivityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow

// An in-memory ActivityService for the view model tests
class FakeActivities(initial: List<CustomActivity> = emptyList(), private val answers: Boolean = true) : ActivityService {
    val activities = MutableStateFlow(initial)
    var failure: Exception? = null
    private var counter = 0

    // answers = false is a connection that has not delivered the first snapshot yet
    override fun observeActivities(userId: String): Flow<List<CustomActivity>> = if (answers) activities else emptyFlow()

    override suspend fun addActivity(userId: String, activity: CustomActivity): String {
        failure?.let { throw it }
        val id = "new${counter++}"
        activities.value = activities.value + activity.copy(id = id)
        return id
    }

    override suspend fun updateActivity(userId: String, activity: CustomActivity) {
        failure?.let { throw it }
        activities.value = activities.value.map { if (it.id == activity.id) activity else it }
    }

    override suspend fun deleteActivity(userId: String, activityId: String) {
        failure?.let { throw it }
        activities.value = activities.value.filter { it.id != activityId }
    }
}
