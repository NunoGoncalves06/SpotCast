package com.swmad.spotcast.integration.weather

import com.swmad.spotcast.domain.WeatherResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.LocalTime
import java.util.concurrent.CancellationException

class KtorWeatherServiceTest {

    private val forecastJson = """
        {"current":{"temperature_2m":12.0,"wind_speed_10m":10.0,"weather_code":3},
         "daily":{"time":["2026-10-29","2026-10-30"],"weather_code":[3,61],
           "temperature_2m_max":[13.0,12.0],"temperature_2m_min":[8.0,7.0],
           "precipitation_probability_max":[20,80],"wind_speed_10m_max":[15.0,30.0],
           "cloud_cover_mean":[50,100],"sunset":["2026-10-29T16:40","2026-10-30T16:38"]}}
    """.trimIndent()

    private val marineJson =
        """{"daily":{"time":["2026-10-29","2026-10-30"],"wave_height_max":[0.9,0.4]}}"""

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun service(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorWeatherService(
            HttpClient(MockEngine(handler)) {
                // The same settings as the real client
                expectSuccess = true
                install(ContentNegotiation) { json(Json { isLenient = true; ignoreUnknownKeys = true }) }
            }
        )

    private fun isMarine(request: HttpRequestData) = request.url.host.startsWith("marine")

    @Test
    fun forecastWithWavesIsMappedToTheDomain() = runTest {
        val api = service { request -> if (isMarine(request)) json(marineJson) else json(forecastJson) }

        val result = api.getForecast(56.0, 10.0, withWaves = true)

        assertTrue(result is WeatherResponse.Success)
        val days = (result as WeatherResponse.Success).weather.daily
        assertEquals(2, days.size)
        assertEquals(0.9, days[0].waveHeight!!, 0.0)
        assertEquals(LocalTime.of(16, 40), days[0].sunset)
        assertEquals(80, days[1].precipitationProbability)
    }

    @Test
    fun aFailingMarineCallOnlyRemovesTheWaves() = runTest {
        val api = service { request ->
            if (isMarine(request)) json("{}", HttpStatusCode.InternalServerError) else json(forecastJson)
        }

        val result = api.getForecast(56.0, 10.0, withWaves = true)

        assertTrue(result is WeatherResponse.Success)
        assertNull((result as WeatherResponse.Success).weather.daily[0].waveHeight)
    }

    @Test
    fun anAnswerWithMissingFieldsIsAFailureAndNotACrash() = runTest {
        val api = service { json("""{"current":{}}""") }

        assertTrue(api.getForecast(56.0, 10.0) is WeatherResponse.Failure.GeneralError)
    }

    @Test
    fun anAnswerWithABadDateIsAFailureAndNotACrash() = runTest {
        val api = service { json(forecastJson.replace("2026-10-29\",\"2026-10-30", "not-a-date\",\"2026-10-30")) }

        assertTrue(api.getForecast(56.0, 10.0) is WeatherResponse.Failure.GeneralError)
    }

    @Test
    fun anUnreadableMarineAnswerOnlyRemovesTheWaves() = runTest {
        val api = service { request -> if (isMarine(request)) json("""{"daily":5}""") else json(forecastJson) }

        assertTrue(api.getForecast(56.0, 10.0, withWaves = true) is WeatherResponse.Success)
    }

    @Test
    fun serverErrorsAreMappedToNamedFailures() = runTest {
        val unavailable = service { json("{}", HttpStatusCode.ServiceUnavailable) }
        val badRequest = service { json("{}", HttpStatusCode.BadRequest) }

        assertEquals(WeatherResponse.Failure.UnAvailable, unavailable.getForecast(56.0, 10.0))
        assertTrue(badRequest.getForecast(56.0, 10.0) is WeatherResponse.Failure.BadRequestError)
    }

    @Test
    fun noConnectionIsANetworkError() = runTest {
        val api = service { throw IOException("offline") }

        assertEquals(WeatherResponse.Failure.NetworkError, api.getForecast(56.0, 10.0))
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        val api = service { throw CancellationException("cancelled") }

        try {
            api.getForecast(56.0, 10.0)
            fail("the cancellation should have been rethrown")
        } catch (expected: CancellationException) {
            // a cancelled screen must stop its work, not turn the cancellation into an error message
        }
    }
}
