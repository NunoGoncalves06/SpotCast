package com.swmad.spotcast.domain

import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// Ordered from worst to best, so maxBy picks the best verdict
enum class Verdict { SKIP, MAYBE, GO }

// Anything a spot can be rated for: one of the built-in activities or an activity the user made.
sealed interface ActivityKind {
    val id: String
    val label: String
    // "surfing", "a beach day": for sentences like "a Go for surfing"
    val purpose: String
    val suggestion: String
    // true when only spots by the water can be a Go, because the marine model snaps inland points to the coast
    val needsWater: Boolean

    fun title(verdict: Verdict): String
}

// The things a person can plan for a day. Every spot is rated for every activity.
enum class Activity(
    override val label: String,
    val goTitle: String,
    val maybeTitle: String,
    val skipTitle: String,
    override val suggestion: String
) : ActivityKind {
    FIELD_SPORTS("Field sports", "Great day to play", "Play, if you don't mind the weather", "Not a day to play", "Great for field sports"),
    BEACH("Beach day", "Great beach day", "Beach day, with a layer", "Not a beach day", "Great for a beach day"),
    GYM("Gym", "Go to the gym", "Gym day", "No gym here", "Great for the gym"),
    SURF("Surf", "Go surfing", "Small waves to surf", "Not a surf day", "Great for surfing"),
    HIKE("Hike", "Go for a hike", "Hike with a jacket", "Not a hiking day", "Great for a hike"),
    PHOTOS("Photos", "Great light for photos", "Soft light for photos", "Not a photo day", "Great for photos"),
    PICNIC("Picnic", "Picnic weather", "A picnic, if you wrap up", "Not a picnic day", "Great for a picnic");

    override val id: String get() = name

    override val purpose: String get() = suggestion.removePrefix("Great for ")

    override val needsWater: Boolean get() = this == BEACH || this == SURF

    override fun title(verdict: Verdict): String = when (verdict) {
        Verdict.GO -> goTitle
        Verdict.MAYBE -> maybeTitle
        Verdict.SKIP -> skipTitle
    }
}

data class Rating(val activity: ActivityKind, val verdict: Verdict, val reason: String) {
    val title: String get() = activity.title(verdict)
}

// The built-in rules are fixed and the same for every user. An own activity is rated by the
// ranges its owner chose (CustomActivity.kt). Some activities need a certain kind of spot (see placeProblem):
// a beach for the sea, a sports ground for field sports, an indoor centre for the gym.
fun rate(activity: ActivityKind, day: DailyForecast, category: SpotCategory): Rating {
    placeProblem(activity, category)?.let { return Rating(activity, Verdict.SKIP, it) }
    // Indoors the weather never decides, so an indoor activity of your own has no conditions either
    if (activity is CustomActivity && activity.place == ActivityPlace.INDOORS) return indoorRating(activity, day)
    return when (activity) {
        is CustomActivity -> rateCustom(activity, day, category == SpotCategory.BEACH)
        is Activity -> rateBuiltIn(activity, day)
    }
}

// Why this kind of spot cannot be rated for this activity at all, or null when it can.
// Indoor centres are only for the gym and everything else needs the outdoors.
internal fun placeProblem(activity: ActivityKind, category: SpotCategory): String? {
    val needed = when {
        activity == Activity.GYM || (activity is CustomActivity && activity.place == ActivityPlace.INDOORS) -> SpotCategory.INDOORS
        activity == Activity.FIELD_SPORTS || (activity is CustomActivity && activity.place == ActivityPlace.SPORTS) -> SpotCategory.SPORTS
        activity.needsWater -> SpotCategory.BEACH
        else -> null
    }
    return when {
        needed == category -> null
        category == SpotCategory.INDOORS -> "This spot is indoors."
        needed == SpotCategory.INDOORS -> "This spot is not an indoor centre."
        needed == SpotCategory.SPORTS -> "This is not an outdoor sports field."
        needed == SpotCategory.BEACH -> "This spot is not by the water."
        else -> null
    }
}

// Indoors the weather never rules an activity out. A bad day only gets a warning for the way there.
private fun indoorRating(activity: ActivityKind, day: DailyForecast): Rating {
    val rain = day.precipitationProbability ?: 0
    val wind = day.maxWindSpeed ?: 0.0
    val warning = when {
        day.weatherCode in 95..99 -> "thunderstorms are expected"
        rain >= 70 -> "heavy rain is likely ($rain%)"
        wind >= 50 -> "the wind is strong, up to ${kmh(wind)}"
        else -> null
    }
    val reason = if (warning == null) "Indoors, the weather does not matter." else "Indoors, but $warning. Take care getting there."
    return Rating(activity, Verdict.GO, reason)
}

private fun rateBuiltIn(activity: Activity, day: DailyForecast): Rating {
    val temp = day.maxTemperature ?: 0.0
    val rain = day.precipitationProbability ?: 0
    val wind = day.maxWindSpeed ?: 0.0
    fun rating(verdict: Verdict, reason: String) = Rating(activity, verdict, reason)

    if (activity == Activity.GYM) return indoorRating(activity, day)
    if (day.weatherCode in 95..99) return rating(Verdict.SKIP, "Thunderstorms are expected.")

    return when (activity) {
        Activity.GYM -> error("handled above")

        Activity.FIELD_SPORTS -> when {
            rain < 30 && wind < 30 && temp in 8.0..27.0 ->
                rating(Verdict.GO, "Dry, ${deg(temp)} and ${kmh(wind)} wind.")
            rain < 60 && wind < 45 && temp in 2.0..32.0 ->
                rating(Verdict.MAYBE, "${deg(temp)}, $rain% chance of rain and ${kmh(wind)} wind.")
            else -> rating(Verdict.SKIP, mainProblem(temp, 2.0, rain, 60, wind, 45.0))
        }

        Activity.BEACH -> when {
            temp >= 22 && rain < 30 && wind < 30 ->
                rating(Verdict.GO, "Warm at ${deg(temp)} with little chance of rain.")
            temp >= 17 && rain < 50 && wind < 40 ->
                rating(Verdict.MAYBE, "Mild at ${deg(temp)}, bring something warm.")
            else -> rating(Verdict.SKIP, mainProblem(temp, 17.0, rain, 50, wind, 40.0))
        }

        Activity.SURF -> {
            val waves = day.waveHeight
            when {
                waves == null -> rating(Verdict.SKIP, "No waves here, this spot is not by the sea.")
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

fun rateAll(
    day: DailyForecast,
    category: SpotCategory,
    activities: List<ActivityKind> = Activity.entries
): List<Rating> = activities.map { rate(it, day, category) }

// The activity that suits this day best. Ties go to the first activity in the bar.
// Activities this kind of spot cannot be rated for are left out, so a bad day names a real reason and not "no water".
fun bestRating(
    day: DailyForecast,
    category: SpotCategory,
    activities: List<ActivityKind> = Activity.entries
): Rating =
    rateAll(day, category, activities)
        .filter { placeProblem(it.activity, category) == null }
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
