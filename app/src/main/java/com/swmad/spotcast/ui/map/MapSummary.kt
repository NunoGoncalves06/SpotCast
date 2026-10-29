package com.swmad.spotcast.ui.map

import com.swmad.spotcast.domain.Activity
import com.swmad.spotcast.domain.Verdict

// What the pill under the activity bar says. dot is the colour of its dot, null is grey.
// canRetry is true when some forecasts failed, so a tap on the pill asks for them again.
data class MapSummary(val text: String, val checking: Boolean, val dot: Verdict?, val canRetry: Boolean = false)

// total spots, of which goes are a Go. loading still wait for their forecast and failed did not get one.
// A forecast that failed must not look like one that is still loading, or the pill would spin forever offline.
fun mapSummary(total: Int, goes: Int, loading: Int, failed: Int, activity: Activity?): MapSummary {
    if (loading > 0) {
        return MapSummary("Checking the forecast for ${spots(total)}", checking = true, dot = null)
    }
    if (failed == total) {
        return MapSummary(
            "No forecast right now. Tap to try again.",
            checking = false,
            dot = null,
            canRetry = true
        )
    }
    val verb = if (total == 1) "is" else "are"
    val what = activity?.purpose ?: "something"
    val withoutForecast = if (failed > 0) ", $failed without forecast" else ""
    return MapSummary(
        "$goes of ${spots(total)} $verb a Go for $what today$withoutForecast",
        checking = false,
        dot = if (goes > 0) Verdict.GO else Verdict.SKIP,
        canRetry = failed > 0
    )
}

private fun spots(count: Int) = if (count == 1) "1 spot" else "$count spots"
