package com.swmad.spotcast.ui.spots

import com.swmad.spotcast.domain.AuthResult
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.GeocodingResponse
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Password
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.domain.Status
import com.swmad.spotcast.domain.User
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.geocoding.GeocodingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddSpotViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class RecordingSpots : SpotService {
        val added = mutableListOf<NewSpot>()
        override fun observeSpots(userId: String): Flow<List<Spot>> = emptyFlow()
        override suspend fun getSpot(userId: String, spotId: String): Spot? = null
        override suspend fun addSpot(userId: String, spot: NewSpot): String {
            added += spot
            return "new"
        }
        override suspend fun deleteSpot(userId: String, spotId: String) {}
    }

    private class FakeAuth : AuthService {
        override val currentUser = User("u1", Email("a@b.dk"))
        override suspend fun signUp(email: Email, password: Password) = AuthResult(null, Status.ERROR)
        override suspend fun signIn(email: Email, password: Password) = AuthResult(null, Status.ERROR)
        override fun signOut() {}
    }

    private class NoGeocoding : GeocodingService {
        override suspend fun findPlace(query: String) = GeocodingResponse.Failure.NotFound
        override fun close() {}
    }

    private val spots = RecordingSpots()
    private val viewModel = AddSpotViewModel(spots, FakeAuth(), NoGeocoding())

    @Test
    fun aPointChosenOnTheMapIsTheSavedPosition() {
        viewModel.chooseOnMap(56.1, 10.2)

        assertFalse(viewModel.useCurrentLocation)
        assertTrue(viewModel.hasPosition)

        viewModel.save("Pier", SpotCategory.OTHER, "")

        assertEquals(listOf(NewSpot("Pier", SpotCategory.OTHER, "", 56.1, 10.2)), spots.added)
    }

    @Test
    fun theUserCanStillChangeThePlace() {
        viewModel.chooseOnMap(56.1, 10.2)
        viewModel.chooseCurrentLocation()

        // the current position has not been read, so there is nothing to save yet
        assertFalse(viewModel.hasPosition)
    }

    @Test
    fun aSecondCallDoesNotUndoALaterChoice() {
        viewModel.chooseOnMap(56.1, 10.2)
        viewModel.chooseCurrentLocation()
        // the screen asks again after a rotation
        viewModel.chooseOnMap(56.1, 10.2)

        assertTrue(viewModel.useCurrentLocation)
    }
}
