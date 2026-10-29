package com.swmad.spotcast.integration.geocoding

import android.util.Log
import com.swmad.spotcast.domain.GeocodingResponse
import com.swmad.spotcast.domain.Place
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

// Model of one hit from https://nominatim.openstreetmap.org/search
// The API sends the coordinates as text.
@Serializable
data class NominatimPlaceDto(
    @SerialName("lat") val latitude: String,
    @SerialName("lon") val longitude: String,
    @SerialName("display_name") val displayName: String
)

// Address lookup with Nominatim, the search service of OpenStreetMap. No API key is needed,
// but the service asks every app to send a User-Agent and to keep to one request per second.
class KtorGeocodingService(private val client: HttpClient = createClient()) : GeocodingService {

    companion object {
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        // Nominatim asks for a User-Agent that says which app it is and where to reach its author
        private const val USER_AGENT = "SpotCast/1.0 (https://github.com/NunoGoncalves06/SpotCast)"
        private const val TAG = "GEOCODING SERVICE"

        private fun createClient() = HttpClient(Android) {
            expectSuccess = true

            engine {
                connectTimeout = 10_000
                socketTimeout = 10_000
            }
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
        }
    }

    override suspend fun findPlace(query: String): GeocodingResponse {
        return try {
            val response = client.get {
                url(BASE_URL)
                parameter("q", query)
                parameter("format", "jsonv2")
                parameter("limit", 1)
            }
            val hits: List<NominatimPlaceDto> = response.body()
            val best = hits.firstOrNull() ?: return GeocodingResponse.Failure.NotFound
            val latitude = best.latitude.toDoubleOrNull()
            val longitude = best.longitude.toDoubleOrNull()
            if (latitude == null || longitude == null) {
                GeocodingResponse.Failure.GeneralError("The address service sent a position that could not be read")
            } else {
                GeocodingResponse.Success(Place(best.displayName, latitude, longitude))
            }
        } catch (e: ResponseException) {
            Log.v(TAG, e.toString())
            GeocodingResponse.Failure.GeneralError("The address service answered with an error")
        } catch (e: IOException) {
            Log.v(TAG, e.toString())
            GeocodingResponse.Failure.NetworkError
        } catch (e: SerializationException) {
            Log.v(TAG, e.toString())
            GeocodingResponse.Failure.GeneralError("The answer from the address service could not be read")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // For example a JsonConvertException: an odd answer is a failure on the screen, not a crash
            Log.v(TAG, e.toString())
            GeocodingResponse.Failure.GeneralError("The answer from the address service could not be read")
        }
    }

    override fun close() {
        client.close()
    }
}
