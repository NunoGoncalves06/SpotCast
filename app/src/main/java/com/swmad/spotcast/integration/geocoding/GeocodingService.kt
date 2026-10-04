package com.swmad.spotcast.integration.geocoding

import com.swmad.spotcast.domain.GeocodingResponse

interface GeocodingService {
    // Turns an address or a place name into a position
    suspend fun findPlace(query: String): GeocodingResponse

    fun close()
}
