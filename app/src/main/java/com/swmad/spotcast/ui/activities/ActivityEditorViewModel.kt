package com.swmad.spotcast.ui.activities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.swmad.spotcast.domain.ActivityIcon
import com.swmad.spotcast.domain.Condition
import com.swmad.spotcast.domain.Criterion
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.DailyForecast
import com.swmad.spotcast.domain.MAX_ACTIVITY_NAME_LENGTH
import com.swmad.spotcast.domain.Mark
import com.swmad.spotcast.domain.Rating
import com.swmad.spotcast.domain.rate
import java.time.LocalDate
import kotlin.math.roundToInt

// State of the screen that makes or edits an activity. It lives in a view model so a rotation keeps it.
class ActivityEditorViewModel : ViewModel() {

    // Empty while a new activity is made
    var activityId by mutableStateOf("")
        private set

    // false until start() has filled the form, the screen shows a spinner before that
    var started by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set

    var icon by mutableStateOf(ActivityIcon.FOOTBALL)
        private set

    var waterOnly by mutableStateOf(false)
        private set

    var criteria by mutableStateOf(Condition.entries.associateWith { it.defaultCriterion() })
        private set

    // The conditions whose sliders are open
    var adjusting by mutableStateOf<Set<Condition>>(emptySet())
        private set

    // The day of the preview: one value per condition
    var sample by mutableStateOf(SAMPLE_DAY)
        private set

    val isNew: Boolean get() = activityId.isEmpty()

    // Waves only make sense for an activity at the water
    val conditions: List<Condition> get() = Condition.entries.filter { !it.byWater || waterOnly }

    // Fills the form once. Later calls, for example after a rotation, change nothing.
    fun start(existing: CustomActivity?) {
        if (started) return
        started = true
        if (existing != null) {
            activityId = existing.id
            name = existing.label
            icon = existing.icon
            waterOnly = existing.waterOnly
            criteria = Condition.entries.associateWith { existing.criteria[it] ?: it.defaultCriterion() }
        }
    }

    fun rename(value: String) {
        name = value.take(MAX_ACTIVITY_NAME_LENGTH)
    }

    fun chooseIcon(value: ActivityIcon) {
        icon = value
    }

    fun changeWaterOnly(value: Boolean) {
        waterOnly = value
    }

    // Good, then OK, then No, then Good again
    fun cycleMark(condition: Condition, range: Int) {
        val criterion = criteria.getValue(condition)
        val next = Mark.entries[(criterion.marks[range].ordinal + 1) % Mark.entries.size]
        criteria = criteria + (condition to criterion.copy(marks = criterion.marks.toMutableList().also { it[range] = next }))
    }

    // The first change always stays below the second, and the other way round
    fun moveFirst(condition: Condition, value: Double) {
        val criterion = criteria.getValue(condition)
        val snapped = snap(condition, value).coerceIn(condition.min, maxOf(condition.min, criterion.second - condition.step))
        criteria = criteria + (condition to criterion.copy(first = snapped))
    }

    fun moveSecond(condition: Condition, value: Double) {
        val criterion = criteria.getValue(condition)
        val snapped = snap(condition, value).coerceIn(minOf(criterion.first + condition.step, condition.max), condition.max)
        criteria = criteria + (condition to criterion.copy(second = snapped))
    }

    // Puts the two numbers back. The marks stay as the user set them.
    fun resetRanges(condition: Condition) {
        val criterion = criteria.getValue(condition)
        criteria = criteria + (condition to criterion.copy(first = condition.defaultFirst, second = condition.defaultSecond))
        adjusting = adjusting - condition
    }

    fun toggleAdjust(condition: Condition) {
        adjusting = if (condition in adjusting) adjusting - condition else adjusting + condition
    }

    fun moveSample(condition: Condition, value: Double) {
        sample = sample + (condition to snap(condition, value).coerceIn(condition.min, condition.max))
    }

    fun build() = CustomActivity(
        id = activityId,
        label = name.trim(),
        icon = icon,
        waterOnly = waterOnly,
        criteria = criteria
    )

    // What the activity would say on the sample day, with the same rule the map uses
    val preview: Rating
        get() = rate(build().copy(label = name.trim().ifEmpty { "this activity" }), sampleDay(), nearWater = waterOnly)

    private fun sampleDay() = DailyForecast(
        date = LocalDate.now(),
        minTemperature = null,
        maxTemperature = sample.getValue(Condition.TEMPERATURE),
        precipitationProbability = sample.getValue(Condition.RAIN).roundToInt(),
        weatherCode = 2,
        maxWindSpeed = sample.getValue(Condition.WIND),
        cloudCover = sample.getValue(Condition.SUN).roundToInt(),
        sunset = null,
        waveHeight = if (waterOnly) sample.getValue(Condition.WAVES) else null
    )

    private fun snap(condition: Condition, value: Double): Double {
        val steps = (value / condition.step).roundToInt()
        return (steps * condition.step * 100).roundToInt() / 100.0
    }

    companion object {
        private val SAMPLE_DAY = mapOf(
            Condition.WIND to 8.0,
            Condition.RAIN to 10.0,
            Condition.SUN to 20.0,
            Condition.TEMPERATURE to 16.0,
            Condition.WAVES to 0.3
        )
    }
}
