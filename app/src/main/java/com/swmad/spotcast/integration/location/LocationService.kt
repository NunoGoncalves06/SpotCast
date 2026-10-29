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
            read(Priority.PRIORITY_HIGH_ACCURACY)
        } catch (ex: SecurityException) {
            // High accuracy needs the fine permission. The user may have given the approximate one only.
            Log.v(this::class.qualifiedName, ex.message.toString())
            try {
                read(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            } catch (ex2: SecurityException) {
                Log.v(this::class.qualifiedName, ex2.message.toString())
                null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun read(priority: Int): Location? {
        val res = locationProvider.getCurrentLocation(priority, null).await()
        Log.v(this::class.qualifiedName, res.toString())
        return res
    }
}
