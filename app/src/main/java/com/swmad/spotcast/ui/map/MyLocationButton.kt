package com.swmad.spotcast.ui.map

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

private fun locationServicesOn(context: Context): Boolean {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(manager)
}

// Follows the location switch of the phone, so the button changes as soon as the user turns it on or off
@Composable
fun rememberLocationServicesOn(): State<Boolean> {
    val context = LocalContext.current
    val on = remember { mutableStateOf(locationServicesOn(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                on.value = locationServicesOn(context)
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION).apply {
            addAction("android.location.MODE_CHANGED")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // The phone may have changed the switch while the screen was away
        on.value = locationServicesOn(context)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

// The "My location" button of Google Maps: it centers the map on the GPS position, and the icon has
// a slash through it while the location switch of the phone is off.
@Composable
fun MyLocationButton(
    servicesOn: Boolean,
    locating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = if (servicesOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        if (locating) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (servicesOn) Icons.Filled.MyLocation else Icons.Filled.LocationDisabled,
                contentDescription = if (servicesOn) "My location" else "My location, location is turned off"
            )
        }
    }
}
