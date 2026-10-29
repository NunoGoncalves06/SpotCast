package com.swmad.spotcast.integration.geocoding

import com.swmad.spotcast.domain.GeocodingResponse
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class KtorGeocodingServiceTest {

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun service(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorGeocodingService(
            HttpClient(MockEngine(handler)) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { isLenient = true; ignoreUnknownKeys = true }) }
            }
        )

    @Test
    fun theBestMatchBecomesAPlace() = runTest {
        val api = service { json("""[{"lat":"56.1394","lon":"10.2203","display_name":"Marselisborg Havn"}]""") }

        val result = api.findPlace("Marselisborg Havn")

        assertTrue(result is GeocodingResponse.Success)
        assertEquals(56.1394, (result as GeocodingResponse.Success).place.latitude, 0.0)
    }

    @Test
    fun noHitIsNotFound() = runTest {
        assertEquals(GeocodingResponse.Failure.NotFound, service { json("[]") }.findPlace("nowhere"))
    }

    @Test
    fun aHitWithMissingFieldsIsAFailureAndNotACrash() = runTest {
        val api = service { json("""[{"foo":1}]""") }

        assertTrue(api.findPlace("x") is GeocodingResponse.Failure.GeneralError)
    }

    @Test
    fun noConnectionIsANetworkError() = runTest {
        val api = service { throw IOException("offline") }

        assertEquals(GeocodingResponse.Failure.NetworkError, api.findPlace("x"))
    }
}
