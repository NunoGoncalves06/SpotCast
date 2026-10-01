package com.swmad.spotcast.domain

import java.time.LocalDate

data class CurrentWeather(
    val temperature: Double,
    val windSpeed: Double,
    val weatherCode: Int
)

data class DailyForecast(
    val date: LocalDate,
    val minTemperature: Double?,
    val maxTemperature: Double?,
    val precipitationProbability: Int?,
    val weatherCode: Int?
)

data class Weather(
    val current: CurrentWeather,
    val daily: List<DailyForecast>
)

// Result of a call to the weather API, same idea as NasaResponse in lesson 8.
sealed class WeatherResponse {
    data class Success(val weather: Weather) : WeatherResponse()

    sealed class Failure : WeatherResponse() {
        data class BadRequestError(val message: String) : Failure()
        data object UnAvailable : Failure()
        data object NetworkError : Failure()
        data class GeneralError(val message: String) : Failure()
    }
}

// WMO weather interpretation codes, as used by Open-Meteo.
fun weatherDescription(code: Int?): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

fun weatherSymbol(code: Int?): String = when (code) {
    0, 1 -> "☀️"
    2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "❓"
}
