package com.swmad.spotcast.integration.weather

import com.swmad.spotcast.domain.CurrentWeather
import com.swmad.spotcast.domain.DailyForecast
import com.swmad.spotcast.domain.Weather
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

// Models of the data received from https://api.open-meteo.com/v1/forecast

@Serializable
data class ForecastDto(
    @SerialName("current") val current: CurrentDto,
    @SerialName("daily") val daily: DailyDto
)

@Serializable
data class CurrentDto(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("weather_code") val weatherCode: Int
)

// The API returns one list per variable, where index i belongs to day i.
// Single values can be null when the model has no data for that day.
@Serializable
data class DailyDto(
    @SerialName("time") val time: List<String>,
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemperature: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val minTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val maxWindSpeed: List<Double?> = emptyList(),
    @SerialName("cloud_cover_mean") val cloudCover: List<Int?> = emptyList(),
    @SerialName("sunset") val sunset: List<String?> = emptyList()
)

// Models of the data received from https://marine-api.open-meteo.com/v1/marine
@Serializable
data class MarineDto(@SerialName("daily") val daily: MarineDailyDto)

@Serializable
data class MarineDailyDto(
    @SerialName("time") val time: List<String>,
    @SerialName("wave_height_max") val waveHeight: List<Double?> = emptyList()
)

fun ForecastDto.toDomain(): Weather = Weather(
    current = CurrentWeather(
        temperature = current.temperature,
        windSpeed = current.windSpeed,
        weatherCode = current.weatherCode
    ),
    daily = daily.time.mapIndexed { index, day ->
        DailyForecast(
            date = LocalDate.parse(day),
            minTemperature = daily.minTemperature.getOrNull(index),
            maxTemperature = daily.maxTemperature.getOrNull(index),
            precipitationProbability = daily.precipitationProbability.getOrNull(index),
            weatherCode = daily.weatherCode.getOrNull(index),
            maxWindSpeed = daily.maxWindSpeed.getOrNull(index),
            cloudCover = daily.cloudCover.getOrNull(index),
            // Local time without a zone, for example 2026-10-26T16:51
            sunset = daily.sunset.getOrNull(index)?.let { LocalDateTime.parse(it).toLocalTime() }
        )
    }
)
