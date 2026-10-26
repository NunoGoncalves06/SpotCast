package com.swmad.spotcast.integration.weather

import android.util.Log
import com.swmad.spotcast.domain.WeatherResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

// The external HTTP API of the app: Open-Meteo, called with the Ktor client (no SDK, no API key).
class KtorWeatherService : WeatherService {

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val MARINE_URL = "https://marine-api.open-meteo.com/v1/marine"
        private const val USER_AGENT = "SpotCast/1.0"
        private const val TAG = "WEATHER SERVICE"
        private const val FORECAST_DAYS = 7
    }

    private val client = HttpClient(Android) {
        // Makes the client throw ResponseException on 4xx and 5xx answers
        expectSuccess = true

        engine {
            connectTimeout = 10_000
            socketTimeout = 10_000
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.v(TAG, message)
                }
            }
            level = LogLevel.INFO
        }
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(DefaultRequest) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
    }

    override suspend fun getForecast(latitude: Double, longitude: Double, withWaves: Boolean): WeatherResponse {
        return try {
            val response = client.get {
                url(BASE_URL)
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", "temperature_2m,wind_speed_10m,weather_code")
                parameter(
                    "daily",
                    "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
                        "wind_speed_10m_max,cloud_cover_mean,sunset"
                )
                parameter("timezone", "auto")
                parameter("forecast_days", FORECAST_DAYS)
            }
            val forecast: ForecastDto = response.body()
            val weather = forecast.toDomain()
            if (!withWaves) return WeatherResponse.Success(weather)
            val waves = getWaveHeights(latitude, longitude)
            WeatherResponse.Success(
                weather.copy(daily = weather.daily.map { it.copy(waveHeight = waves[it.date.toString()]) })
            )
        } catch (e: ResponseException) {
            Log.v(TAG, e.toString())
            when (e.response.status) {
                HttpStatusCode.BadRequest -> WeatherResponse.Failure.BadRequestError(e.message.toString())
                HttpStatusCode.ServiceUnavailable -> WeatherResponse.Failure.UnAvailable
                else -> WeatherResponse.Failure.GeneralError(e.message.toString())
            }
        } catch (e: IOException) {
            // No connection, unknown host or a timeout
            Log.v(TAG, e.toString())
            WeatherResponse.Failure.NetworkError
        } catch (e: SerializationException) {
            Log.v(TAG, e.toString())
            WeatherResponse.Failure.GeneralError("The answer from the weather service could not be read")
        }
    }

    // Waves are extra information: when the marine API fails, the spot simply has no wave data
    private suspend fun getWaveHeights(latitude: Double, longitude: Double): Map<String, Double?> = try {
        val marine: MarineDto = client.get {
            url(MARINE_URL)
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("daily", "wave_height_max")
            parameter("timezone", "auto")
            parameter("forecast_days", FORECAST_DAYS)
        }.body()
        marine.daily.time.zip(marine.daily.waveHeight).toMap()
    } catch (e: ResponseException) {
        Log.v(TAG, e.toString())
        emptyMap()
    } catch (e: IOException) {
        Log.v(TAG, e.toString())
        emptyMap()
    } catch (e: SerializationException) {
        Log.v(TAG, e.toString())
        emptyMap()
    }

    override fun close() {
        client.close()
    }
}
