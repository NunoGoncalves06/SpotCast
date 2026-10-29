package com.swmad.spotcast.ui.spots

import com.swmad.spotcast.domain.AuthResult
import com.swmad.spotcast.domain.CurrentWeather
import com.swmad.spotcast.domain.Email
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Password
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.domain.User
import com.swmad.spotcast.domain.Weather
import com.swmad.spotcast.domain.WeatherResponse
import com.swmad.spotcast.integration.auth.AuthService
import com.swmad.spotcast.integration.firestore.SpotService
import com.swmad.spotcast.integration.weather.WeatherService
import com.swmad.spotcast.ui.details.WeatherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotsViewModelTest {

    // viewModelScope runs on Dispatchers.Main, which does not exist in a plain JVM test
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSpots(initial: List<Spot>) : SpotService {
        val spots = MutableStateFlow(initial)
        var deleteFailure: Exception? = null

        override fun observeSpots(userId: String): Flow<List<Spot>> = spots
        override suspend fun getSpot(userId: String, spotId: String) = spots.value.firstOrNull { it.id == spotId }
        override suspend fun addSpot(userId: String, spot: NewSpot) = "new"
        override suspend fun deleteSpot(userId: String, spotId: String) {
            deleteFailure?.let { throw it }
            spots.value = spots.value.filter { it.id != spotId }
        }
    }

    private class FakeAuth(override val currentUser: User? = User("u1", Email("a@b.dk"))) : AuthService {
        override suspend fun signUp(email: Email, password: Password) = AuthResult(null, com.swmad.spotcast.domain.Status.ERROR)
        override suspend fun signIn(email: Email, password: Password) = AuthResult(null, com.swmad.spotcast.domain.Status.ERROR)
        override fun signOut() {}
    }

    private class FakeWeather(var response: WeatherResponse) : WeatherService {
        var calls = 0
        override suspend fun getForecast(latitude: Double, longitude: Double, withWaves: Boolean): WeatherResponse {
            calls++
            return response
        }
        override fun close() {}
    }

    private val sunny = WeatherResponse.Success(Weather(CurrentWeather(20.0, 5.0, 0), emptyList()))

    private fun spot(id: String) = Spot(id, "Spot $id", SpotCategory.PARK, "", 56.0, 10.0, 0L)

    private fun viewModel(
        spots: FakeSpots,
        weather: FakeWeather = FakeWeather(sunny),
        auth: AuthService = FakeAuth()
    ) = SpotsViewModel(spots, auth, weather)

    @Test
    fun aFailedDeleteKeepsTheListAndReportsSeparately() {
        val spots = FakeSpots(listOf(spot("a"), spot("b"))).apply { deleteFailure = RuntimeException("denied") }
        val vm = viewModel(spots)

        vm.deleteSpot(vm.spots.first())

        assertEquals("the list stays on screen", 2, vm.spots.size)
        assertNull("the list error is not used for a delete", vm.error)
        assertEquals("denied", vm.deleteError)
        vm.clearDeleteError()
        assertNull(vm.deleteError)
    }

    @Test
    fun aDeleteRemovesTheSpotAndTheSelection() {
        val spots = FakeSpots(listOf(spot("a"), spot("b")))
        val vm = viewModel(spots)
        vm.selectSpot("a")

        vm.deleteSpot(vm.spots.first { it.id == "a" })

        assertEquals(listOf("b"), vm.spots.map { it.id })
        assertNull(vm.selectedSpotId)
        assertNull(vm.deleteError)
    }

    @Test
    fun aFailedForecastIsRecordedAndCanBeRetried() {
        val weather = FakeWeather(WeatherResponse.Failure.NetworkError)
        val vm = viewModel(FakeSpots(listOf(spot("a"))), weather)
        assertTrue(vm.forecasts.getValue("a") is WeatherUiState.Error)

        weather.response = sunny
        vm.retryForecast(vm.spots.first())

        assertTrue(vm.forecasts.getValue("a") is WeatherUiState.Loaded)
    }

    @Test
    fun retryingFailedForecastsAsksOnlyForTheFailedOnes() {
        val spots = FakeSpots(listOf(spot("a"), spot("b")))
        val weather = FakeWeather(WeatherResponse.Failure.NetworkError)
        val vm = viewModel(spots, weather)
        assertEquals(2, weather.calls)

        weather.response = sunny
        vm.retryFailedForecasts()
        assertEquals("both failed, so both are asked again", 4, weather.calls)
        assertTrue(vm.forecasts.values.all { it is WeatherUiState.Loaded })

        vm.retryFailedForecasts()
        assertEquals("nothing failed any more, so nothing is asked", 4, weather.calls)
    }

    @Test
    fun theForecastOfASpotIsFetchedOnceEvenWhenTheListChanges() {
        val spots = FakeSpots(listOf(spot("a")))
        val weather = FakeWeather(sunny)
        viewModel(spots, weather)

        spots.spots.value = listOf(spot("a"), spot("b"))

        assertEquals("one call for a, one for b", 2, weather.calls)
    }

    @Test
    fun withoutAUserThereIsAListErrorAndNoForecast() {
        val weather = FakeWeather(sunny)
        val vm = viewModel(FakeSpots(emptyList()), weather, FakeAuth(currentUser = null))

        assertEquals("You are not signed in", vm.error)
        assertEquals(0, weather.calls)
    }
}
