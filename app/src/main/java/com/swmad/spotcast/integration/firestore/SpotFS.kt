package com.swmad.spotcast.integration.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import com.swmad.spotcast.domain.NewSpot
import com.swmad.spotcast.domain.Spot
import com.swmad.spotcast.domain.SpotCategory

// Model for handling Firestore data directly.
// @DocumentId fills in the id, and every field has a default so there is a default constructor.
data class SpotFS(
    @DocumentId var id: String? = null,
    var name: String = "",
    var category: String = SpotCategory.OTHER.name,
    var note: String = "",
    var location: GeoPoint? = null,
    // Set by the server. It is null for a moment on a document that was just written locally.
    @ServerTimestamp var createdAt: Timestamp? = null
)

fun SpotFS.toDomain(): Spot {
    val position = location ?: throw IllegalStateException("Location invalid")
    return Spot(
        id = id ?: throw IllegalStateException("Id invalid"),
        name = name,
        category = SpotCategory.entries.firstOrNull { it.name == category } ?: SpotCategory.OTHER,
        note = note,
        latitude = position.latitude,
        longitude = position.longitude,
        createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}

fun NewSpot.toFS(): SpotFS = SpotFS(
    name = name,
    category = category.name,
    note = note,
    location = GeoPoint(latitude, longitude)
)
