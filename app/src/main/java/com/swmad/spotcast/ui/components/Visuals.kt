package com.swmad.spotcast.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.swmad.spotcast.domain.Activity
import com.swmad.spotcast.domain.SpotCategory
import com.swmad.spotcast.domain.Verdict
import com.swmad.spotcast.ui.theme.GoTint
import com.swmad.spotcast.ui.theme.GoTintDark
import com.swmad.spotcast.ui.theme.MaybeTint
import com.swmad.spotcast.ui.theme.MaybeTintDark
import com.swmad.spotcast.ui.theme.OnGoTint
import com.swmad.spotcast.ui.theme.OnGoTintDark
import com.swmad.spotcast.ui.theme.OnMaybeTint
import com.swmad.spotcast.ui.theme.OnMaybeTintDark
import com.swmad.spotcast.ui.theme.OnSkipTint
import com.swmad.spotcast.ui.theme.OnSkipTintDark
import com.swmad.spotcast.ui.theme.SkipTint
import com.swmad.spotcast.ui.theme.SkipTintDark
import com.swmad.spotcast.ui.theme.VerdictGo
import com.swmad.spotcast.ui.theme.VerdictMaybe
import com.swmad.spotcast.ui.theme.VerdictNone
import com.swmad.spotcast.ui.theme.VerdictSkip

// null is the "Best today" chip
fun activityIcon(activity: Activity?): ImageVector = when (activity) {
    null -> Icons.Filled.AutoAwesome
    Activity.BEACH -> Icons.Filled.BeachAccess
    Activity.SURF -> Icons.Filled.Surfing
    Activity.HIKE -> Icons.Filled.Hiking
    Activity.PHOTOS -> Icons.Filled.PhotoCamera
    Activity.PICNIC -> Icons.Filled.Deck
}

fun categoryIcon(category: SpotCategory): ImageVector = when (category) {
    SpotCategory.BEACH -> Icons.Filled.BeachAccess
    SpotCategory.TRAIL -> Icons.Filled.Terrain
    SpotCategory.VIEWPOINT -> Icons.Filled.Landscape
    SpotCategory.PARK -> Icons.Filled.Park
    SpotCategory.OTHER -> Icons.Filled.Place
}

// WMO weather codes, as used by Open-Meteo
fun weatherIcon(code: Int?): ImageVector = when (code) {
    0, 1 -> Icons.Filled.WbSunny
    2 -> Icons.Filled.FilterDrama
    3 -> Icons.Filled.Cloud
    45, 48 -> Icons.Filled.Dehaze
    51, 53, 55, 56, 57 -> Icons.Filled.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Filled.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.AutoMirrored.Filled.HelpOutline
}

// The strong colour of a pin. null is a spot whose forecast has not arrived.
fun verdictColor(verdict: Verdict?): Color = when (verdict) {
    Verdict.GO -> VerdictGo
    Verdict.MAYBE -> VerdictMaybe
    Verdict.SKIP -> VerdictSkip
    null -> VerdictNone
}

data class Tint(val container: Color, val content: Color)

// Soft background and text colour for a verdict, readable in the light and the dark theme
@Composable
fun verdictTint(verdict: Verdict): Tint {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (verdict) {
        Verdict.GO -> if (dark) Tint(GoTintDark, OnGoTintDark) else Tint(GoTint, OnGoTint)
        Verdict.MAYBE -> if (dark) Tint(MaybeTintDark, OnMaybeTintDark) else Tint(MaybeTint, OnMaybeTint)
        Verdict.SKIP -> if (dark) Tint(SkipTintDark, OnSkipTintDark) else Tint(SkipTint, OnSkipTint)
    }
}
