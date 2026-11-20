package com.swmad.spotcast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CustomActivityRulesTest {

    private fun day(
        temp: Double? = 16.0,
        rain: Int? = 10,
        wind: Double? = 8.0,
        clouds: Int? = 20,
        waves: Double? = null,
        code: Int = 2
    ) = DailyForecast(
        date = LocalDate.of(2026, 11, 20),
        minTemperature = null,
        maxTemperature = temp,
        precipitationProbability = rain,
        weatherCode = code,
        maxWindSpeed = wind,
        cloudCover = clouds,
        sunset = null,
        waveHeight = waves
    )

    // The defaults: no wind, dry and sunny are good, mild and warm are good, cold is OK
    private val football = CustomActivity(label = "Football")

    @Test
    fun theSecondNumberOfEachRangeStartsTheNextRange() {
        val wind = Condition.WIND.defaultCriterion()

        assertEquals(0, wind.rangeOf(9.9))
        assertEquals(1, wind.rangeOf(10.0))
        assertEquals(1, wind.rangeOf(29.9))
        assertEquals(2, wind.rangeOf(30.0))
    }

    @Test
    fun allGoodMarksGiveAGo() {
        val rating = rate(football, day(), nearWater = false)

        assertEquals(Verdict.GO, rating.verdict)
        assertEquals("Go for Football", rating.title)
    }

    @Test
    fun theWorstMarkWinsAndTheReasonNamesIt() {
        val rating = rate(football, day(wind = 34.0, rain = 30), nearWater = false)

        // wind 34 is a No, rain 30 is only OK: the Skip is named, the Maybe is not
        assertEquals(Verdict.SKIP, rating.verdict)
        assertEquals("Wind: windy day (34 km/h).", rating.reason)
        assertEquals("Skip Football", rating.title)
    }

    @Test
    fun severalConditionsWithTheWorstMarkAreAllNamed() {
        val rating = rate(football, day(wind = 34.0, rain = 70), nearWater = false)

        assertEquals("Wind: windy day (34 km/h), Rain: rainy (70%).", rating.reason)
    }

    @Test
    fun anOkMarkGivesAMaybe() {
        val rating = rate(football, day(rain = 30), nearWater = false)

        assertEquals(Verdict.MAYBE, rating.verdict)
        assertEquals("Rain: some rain (30%).", rating.reason)
    }

    @Test
    fun aDayWithoutAnyValueIsNotAGo() {
        val rating = rate(football, day(wind = null, rain = null, clouds = null, temp = null), nearWater = false)

        assertEquals(Verdict.MAYBE, rating.verdict)
        assertEquals("There is no weather data for this day.", rating.reason)
    }

    @Test
    fun aConditionWithoutAValueThatDayIsLeftOut() {
        val rating = rate(football, day(wind = null, rain = 10, clouds = null, temp = null), nearWater = false)

        assertEquals(Verdict.GO, rating.verdict)
    }

    @Test
    fun aThunderstormIsAlwaysASkip() {
        assertEquals(Verdict.SKIP, rate(football, day(code = 95), nearWater = false).verdict)
    }

    @Test
    fun anActivityAtTheWaterNeedsASpotAtTheWater() {
        val sailing = football.copy(label = "Sailing", waterOnly = true)

        assertEquals(Verdict.SKIP, rate(sailing, day(waves = 0.3), nearWater = false).verdict)
        assertEquals(Verdict.GO, rate(sailing, day(waves = 0.3), nearWater = true).verdict)
    }

    @Test
    fun wavesOnlyCountForAnActivityAtTheWater() {
        val bigWaves = day(waves = 2.0)
        val atTheWater = football.copy(waterOnly = true)

        assertEquals(Verdict.GO, rate(football, bigWaves, nearWater = true).verdict)
        assertEquals(Verdict.MAYBE, rate(atTheWater, bigWaves, nearWater = true).verdict)
    }

    @Test
    fun anOwnActivityCanBeTheBestOfTheDay() {
        val windy = day(wind = 45.0, rain = 10, temp = 18.0)
        val surfInstead = football.copy(
            label = "Kite",
            criteria = football.criteria + (Condition.WIND to Criterion(10.0, 30.0, listOf(Mark.NO, Mark.OK, Mark.GOOD)))
        )

        assertEquals("Kite", bestRating(windy, nearWater = false, activities = Activity.entries + surfInstead).activity.label)
    }

    @Test
    fun anActivityAtTheWaterIsLeftOutOfBestTodayInland() {
        val sailing = football.copy(label = "Sailing", waterOnly = true)

        val best = bestRating(day(), nearWater = false, activities = listOf(sailing, Activity.HIKE))

        assertEquals(Activity.HIKE, best.activity)
    }

    @Test
    fun theBuiltInActivitiesKeepTheirRules() {
        assertEquals(Verdict.GO, rate(Activity.HIKE, day(temp = 15.0, rain = 10, wind = 10.0), nearWater = false).verdict)
        assertTrue(Activity.SURF.needsWater)
        assertFalse(Activity.HIKE.needsWater)
    }

    @Test
    fun anActivityIsValidOnlyWithANameAndRangesInOrder() {
        assertTrue(football.isValid())
        assertFalse(football.copy(label = "  ").isValid())
        assertFalse(football.copy(label = "x".repeat(MAX_ACTIVITY_NAME_LENGTH + 1)).isValid())
        assertFalse(
            football.copy(criteria = football.criteria + (Condition.WIND to Criterion(30.0, 10.0, Condition.WIND.defaultMarks))).isValid()
        )
    }
}
