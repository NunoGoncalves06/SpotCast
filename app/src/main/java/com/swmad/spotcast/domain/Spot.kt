package com.swmad.spotcast.domain

enum class SpotCategory(val label: String) {
    BEACH("Beach"),
    TRAIL("Trail"),
    VIEWPOINT("Viewpoint"),
    PARK("Park"),
    OTHER("Other")
}

// Domain model. It has no coupling to Firestore and the id is never null.
data class Spot(
    val id: String,
    val name: String,
    val category: SpotCategory,
    val note: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long
) {
    // Only beach spots are rated for beach days and surfing
    val isByTheWater: Boolean get() = category == SpotCategory.BEACH
}

// A spot that has not been stored yet, so it has no id.
data class NewSpot(
    val name: String,
    val category: SpotCategory,
    val note: String,
    val latitude: Double,
    val longitude: Double
)
