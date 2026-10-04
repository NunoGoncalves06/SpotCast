package com.swmad.spotcast.domain

// A position the user picked without standing there: found from an address or typed as coordinates.
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

// Result of looking up an address, same idea as WeatherResponse.
sealed class GeocodingResponse {
    data class Success(val place: Place) : GeocodingResponse()

    sealed class Failure : GeocodingResponse() {
        data object NotFound : Failure()
        data object NetworkError : Failure()
        data class GeneralError(val message: String) : Failure()
    }
}
