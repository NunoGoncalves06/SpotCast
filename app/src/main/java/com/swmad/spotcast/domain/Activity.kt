package com.swmad.spotcast.domain

import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Ordered from worst to best, so maxBy picks the best verdict
enum class Verdict { SKIP, MAYBE, GO }

// The things a person can plan for a day. Every spot is rated for every activity.
enum class Activity(
    val label: String,
    val goTitle: String,
    val maybeTitle: String,
    val skipTitle: String,
    val suggestion: String
) {
    BEACH("Beach day", "Great beach day", "Beach day, with a layer", "Not a beach day", "Great for a beach day"),
    SURF("Surf", "Go surfing", "Small waves to surf", "Not a surf day", "Great for surfing"),
    HIKE("Hike", "Go for a hike", "Hike with a jacket", "Not a hiking day", "Great for a hike"),
    PHOTOS("Photos", "Great light for photos", "Soft light for photos", "Not a photo day", "Great for photos"),
    PICNIC("Picnic", "Picnic weather", "A picnic, if you wrap up", "Not a picnic day", "Great for a picnic");

    // "surfing", "a beach day": for sentences like "a Go for surfing"
    val purpose: String get() = suggestion.removePrefix("Great for ")

    fun title(verdict: Verdict): String = when (verdict) {
        Verdict.GO -> goTitle
        Verdict.MAYBE -> maybeTitle
        Verdict.SKIP -> skipTitle
    }
}

data class Rating(val activity: Activity, val verdict: Verdict, val reason: String) {
    val title: String get() = activity.title(verdict)
}

// Fixed rules, the same for every user. nearWater is true for beach spots: only those
// can be a beach or surf day, because the marine model snaps inland points to the coast.
fun rate(activity: Activity, day: DailyForecast, nearWater: Boolean): Rating {
    val temp = day.maxTemperature ?: 0.0
    val rain = day.precipitationProbability ?: 0
    val wind = day.maxWindSpeed ?: 0.0
    fun rating(verdict: Verdict, reason: String) = Rating(activity, verdict, reason)

    if (day.weatherCode in 95..99) return rating(Verdict.SKIP, "Thunderstorms are expected.")

    return when (activity) {
        Activity.BEACH -> when {
            !nearWater -> rating(Verdict.SKIP, "This spot is not by the water.")
            temp >= 22 && rain < 30 && wind < 30 ->
                rating(Verdict.GO, "Warm at ${deg(temp)} with little chance of rain.")
            temp >= 17 && rain < 50 && wind < 40 ->
                rating(Verdict.MAYBE, "Mild at ${deg(temp)}, bring something warm.")
            else -> rating(Verdict.SKIP, mainProblem(temp, 17.0, rain, 50, wind, 40.0))
        }

        Activity.SURF -> {
            val waves = day.waveHeight
            when {
                !nearWater || waves == null -> rating(Verdict.SKIP, "No waves here, this spot is not by the sea.")
                waves >= 0.8 && wind < 40 ->
                    rating(Verdict.GO, "${metres(waves)} waves and ${kmh(wind)} wind.")
                waves >= 0.5 && wind < 50 -> rating(Verdict.MAYBE, "Small ${metres(waves)} waves.")
                waves < 0.5 -> rating(Verdict.SKIP, "Flat sea, the waves reach only ${metres(waves)}.")
                else -> rating(Verdict.SKIP, "Too windy, gusts up to ${kmh(wind)}.")
            }
        }

        Activity.HIKE -> when {
            rain < 30 && wind < 40 && temp in 5.0..28.0 ->
                rating(Verdict.GO, "Dry, ${deg(temp)} and ${kmh(wind)} wind.")
            rain < 60 && wind < 55 && temp in 0.0..32.0 ->
                rating(Verdict.MAYBE, "$rain% chance of rain, pack a jacket.")
            else -> rating(Verdict.SKIP, mainProblem(temp, 0.0, rain, 60, wind, 55.0))
        }

        Activity.PHOTOS -> {
            val clouds = day.cloudCover ?: 50
            val sunset = day.sunset?.format(TIME) ?: "evening"
            when {
                rain >= 50 -> rating(Verdict.SKIP, "$rain% chance of rain around the $sunset sunset.")
                clouds >= 80 -> rating(Verdict.SKIP, "Clouds will hide the $sunset sunset.")
                clouds >= 25 -> rating(Verdict.GO, "Broken clouds should light up the $sunset sunset.")
                else -> rating(Verdict.MAYBE, "Clear sky, soft light at the $sunset sunset.")
            }
        }

        Activity.PICNIC -> when {
            temp >= 18 && rain < 20 && wind < 25 ->
                rating(Verdict.GO, "${deg(temp)}, dry and calm.")
            temp >= 13 && rain < 40 && wind < 35 ->
                rating(Verdict.MAYBE, "Only ${deg(temp)}, bring a blanket.")
            else -> rating(Verdict.SKIP, mainProblem(temp, 13.0, rain, 40, wind, 35.0))
        }
    }
}

fun rateAll(day: DailyForecast, nearWater: Boolean): List<Rating> =
    Activity.entries.map { rate(it, day, nearWater) }

// The activity that suits this day best. Ties go to the first activity in the bar.
// Beach and surf are left out inland, so a bad day names a real reason and not "no water".
fun bestRating(day: DailyForecast, nearWater: Boolean): Rating =
    rateAll(day, nearWater)
        .filter { nearWater || (it.activity != Activity.BEACH && it.activity != Activity.SURF) }
        .maxBy { it.verdict }

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

// Names the first rule that failed, so a Skip always says why
private fun mainProblem(
    temp: Double, minTemp: Double,
    rain: Int, maxRain: Int,
    wind: Double, maxWind: Double
): String = when {
    rain >= maxRain -> "$rain% chance of rain."
    wind >= maxWind -> "Wind up to ${kmh(wind)}."
    temp < minTemp -> "Only ${deg(temp)} at its warmest."
    else -> "Too hot at ${deg(temp)}."
}

private fun deg(value: Double) = "${value.roundToInt()} °C"
private fun kmh(value: Double) = "${value.roundToInt()} km/h"
private fun metres(value: Double) = "${"%.1f".format(java.util.Locale.US, value)} m"
