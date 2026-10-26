package com.swmad.spotcast.integration.weather

import com.swmad.spotcast.domain.WeatherResponse

interface WeatherService {
    // withWaves also asks the marine API for the wave height of each day
    suspend fun getForecast(latitude: Double, longitude: Double, withWaves: Boolean = false): WeatherResponse

    fun close()
}
