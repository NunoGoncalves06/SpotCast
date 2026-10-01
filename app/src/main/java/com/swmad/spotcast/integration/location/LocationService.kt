package com.swmad.spotcast.integration.location

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class LocationService(private val locationProvider: FusedLocationProviderClient) {

    // Returns null when the permission is missing or the device has no position yet.
    // The permission is requested in the composable before this is called.
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            val res =
                locationProvider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .await()
            Log.v(this::class.qualifiedName, res.toString())
            res
        } catch (ex: SecurityException) {
            Log.v(this::class.qualifiedName, ex.message.toString())
            null
        }
    }
}
