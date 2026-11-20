package com.swmad.spotcast.domain

import java.util.Locale
import kotlin.math.roundToInt

const val MAX_CUSTOM_ACTIVITIES = 10
const val MAX_ACTIVITY_NAME_LENGTH = 40

// How one range of a condition counts for an activity
enum class Mark(val label: String, val verdict: Verdict) {
    GOOD("Good", Verdict.GO),
    OK("OK", Verdict.MAYBE),
    NO("No", Verdict.SKIP)
}

// The weather values an own activity can look at. Every condition has three ranges, split by two numbers.
// The numbers below are the defaults, the owner can move them within min and max.
enum class Condition(
    val label: String,
    val unit: String,
    val ranges: List<String>,
    val defaultFirst: Double,
    val defaultSecond: Double,
    val min: Double,
    val max: Double,
    val step: Double,
    val defaultMarks: List<Mark>,
    // only asked for spots by the water, so only rated there
    val byWater: Boolean = false
) {
    WIND(
        "Wind", "km/h", listOf("No wind", "Some wind", "Windy day"),
        10.0, 30.0, 0.0, 80.0, 1.0, listOf(Mark.GOOD, Mark.OK, Mark.NO)
    ),
    RAIN(
        "Rain", "%", listOf("Dry", "Some rain", "Rainy"),
        20.0, 50.0, 0.0, 100.0, 5.0, listOf(Mark.GOOD, Mark.OK, Mark.NO)
    ),
    SUN(
        "Sun", "% cloud", listOf("Sunny", "Cloudy", "No sun"),
        25.0, 80.0, 0.0, 100.0, 5.0, listOf(Mark.GOOD, Mark.GOOD, Mark.OK)
    ),
    TEMPERATURE(
        "Temperature", "\u00B0C", listOf("Cold", "Mild", "Warm"),
        10.0, 22.0, -10.0, 40.0, 1.0, listOf(Mark.OK, Mark.GOOD, Mark.GOOD)
    ),
    WAVES(
        "Waves", "m", listOf("Flat", "Small", "Big"),
        0.5, 1.2, 0.0, 4.0, 0.1, listOf(Mark.GOOD, Mark.GOOD, Mark.OK), byWater = true
    );

    fun valueOn(day: DailyForecast): Double? = when (this) {
        WIND -> day.maxWindSpeed
        RAIN -> day.precipitationProbability?.toDouble()
        SUN -> day.cloudCover?.toDouble()
        TEMPERATURE -> day.maxTemperature
        WAVES -> day.waveHeight
    }

    // "34 km/h", "70%", "0.8 m"
    fun format(value: Double): String {
        val number = if (step < 1) "%.1f".format(Locale.US, value) else value.roundToInt().toString()
        return if (unit.startsWith("%")) number + unit else "$number $unit"
    }

    fun defaultCriterion() = Criterion(defaultFirst, defaultSecond, defaultMarks)
}

// Below first is range 0, from first to below second is range 1, from second up is range 2
data class Criterion(val first: Double, val second: Double, val marks: List<Mark>) {
    fun rangeOf(value: Double): Int = when {
        value < first -> 0
        value < second -> 1
        else -> 2
    }

    // The same check the Firestore rules make
    fun isValid(condition: Condition): Boolean =
        marks.size == 3 &&
            first < second &&
            first >= condition.min && second <= condition.max
}

// The icons an own activity can have. The screens turn them into Material icons.
enum class ActivityIcon(val label: String) {
    FOOTBALL("Football"),
    BASKETBALL("Basketball"),
    TENNIS("Tennis"),
    BIKE("Cycling"),
    RUN("Running"),
    SAIL("Sailing"),
    SWIM("Swimming"),
    YOGA("Yoga")
}

// An activity made by the user, stored in their account. id is empty until it is saved.
data class CustomActivity(
    override val id: String = "",
    override val label: String,
    val icon: ActivityIcon = ActivityIcon.FOOTBALL,
    val waterOnly: Boolean = false,
    val criteria: Map<Condition, Criterion> = Condition.entries.associateWith { it.defaultCriterion() },
    val createdAt: Long = 0L
) : ActivityKind {
    override val purpose: String get() = label
    override val suggestion: String get() = "Great for $label"
    override val needsWater: Boolean get() = waterOnly

    override fun title(verdict: Verdict): String = when (verdict) {
        Verdict.GO -> "Go for $label"
        Verdict.MAYBE -> "Maybe for $label"
        Verdict.SKIP -> "Skip $label"
    }

    fun isValid(): Boolean =
        label.isNotBlank() &&
            label.length <= MAX_ACTIVITY_NAME_LENGTH &&
            Condition.entries.all { criteria[it]?.isValid(it) == true }
}

// The worst mark of all conditions wins. A condition without a value that day is left out.
internal fun rateCustom(activity: CustomActivity, day: DailyForecast, nearWater: Boolean): Rating {
    fun rating(verdict: Verdict, reason: String) = Rating(activity, verdict, reason)

    if (day.weatherCode in 95..99) return rating(Verdict.SKIP, "Thunderstorms are expected.")
    if (activity.waterOnly && !nearWater) return rating(Verdict.SKIP, "This spot is not by the water.")

    var worst = Verdict.GO
    var rated = 0
    val problems = mutableListOf<Pair<Verdict, String>>()
    for (condition in Condition.entries) {
        // waves belong to activities that need the water, and are only known for spots by the water
        if (condition.byWater && !(activity.waterOnly && nearWater)) continue
        val criterion = activity.criteria[condition] ?: continue
        val value = condition.valueOn(day) ?: continue
        rated++
        val range = criterion.rangeOf(value)
        val verdict = criterion.marks[range].verdict
        if (verdict != Verdict.GO) {
            problems += verdict to "${condition.label}: ${condition.ranges[range].lowercase()} (${condition.format(value)})"
        }
        if (verdict < worst) worst = verdict
    }
    if (rated == 0) return rating(Verdict.MAYBE, "There is no weather data for this day.")
    if (worst == Verdict.GO) return rating(Verdict.GO, "All the conditions are good.")
    return rating(worst, problems.filter { it.first == worst }.joinToString(", ") { it.second } + ".")
}
