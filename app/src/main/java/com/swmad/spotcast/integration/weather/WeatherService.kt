package com.swmad.spotcast.integration.weather

import com.swmad.spotcast.domain.WeatherResponse

interface WeatherService {
    suspend fun getForecast(latitude: Double, longitude: Double): WeatherResponse

    fun close()
}
