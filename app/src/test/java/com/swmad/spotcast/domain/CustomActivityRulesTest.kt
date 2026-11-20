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
        val rating = rate(football, day(), category = SpotCategory.PARK)

        assertEquals(Verdict.GO, rating.verdict)
        assertEquals("Go for Football", rating.title)
    }

    @Test
    fun theWorstMarkWinsAndTheReasonNamesIt() {
        val rating = rate(football, day(wind = 34.0, rain = 30), category = SpotCategory.PARK)

        // wind 34 is a No, rain 30 is only OK: the Skip is named, the Maybe is not
        assertEquals(Verdict.SKIP, rating.verdict)
        assertEquals("Wind: windy day (34 km/h).", rating.reason)
        assertEquals("Skip Football", rating.title)
    }

    @Test
    fun severalConditionsWithTheWorstMarkAreAllNamed() {
        val rating = rate(football, day(wind = 34.0, rain = 70), category = SpotCategory.PARK)

        assertEquals("Wind: windy day (34 km/h), Rain: rainy (70%).", rating.reason)
    }

    @Test
    fun anOkMarkGivesAMaybe() {
        val rating = rate(football, day(rain = 30), category = SpotCategory.PARK)

        assertEquals(Verdict.MAYBE, rating.verdict)
        assertEquals("Rain: some rain (30%).", rating.reason)
    }

    @Test
    fun aDayWithoutAnyValueIsNotAGo() {
        val rating = rate(football, day(wind = null, rain = null, clouds = null, temp = null), category = SpotCategory.PARK)

        assertEquals(Verdict.MAYBE, rating.verdict)
        assertEquals("There is no weather data for this day.", rating.reason)
    }

    @Test
    fun aConditionWithoutAValueThatDayIsLeftOut() {
        val rating = rate(football, day(wind = null, rain = 10, clouds = null, temp = null), category = SpotCategory.PARK)

        assertEquals(Verdict.GO, rating.verdict)
    }

    @Test
    fun aThunderstormIsAlwaysASkip() {
        assertEquals(Verdict.SKIP, rate(football, day(code = 95), category = SpotCategory.PARK).verdict)
    }

    @Test
    fun anActivityAtTheWaterNeedsASpotAtTheWater() {
        val sailing = football.copy(label = "Sailing", place = ActivityPlace.WATER)

        assertEquals(Verdict.SKIP, rate(sailing, day(waves = 0.3), category = SpotCategory.PARK).verdict)
        assertEquals(Verdict.GO, rate(sailing, day(waves = 0.3), category = SpotCategory.BEACH).verdict)
    }

    @Test
    fun wavesOnlyCountForAnActivityAtTheWater() {
        val bigWaves = day(waves = 2.0)
        val atTheWater = football.copy(place = ActivityPlace.WATER)

        assertEquals(Verdict.GO, rate(football, bigWaves, category = SpotCategory.BEACH).verdict)
        assertEquals(Verdict.MAYBE, rate(atTheWater, bigWaves, category = SpotCategory.BEACH).verdict)
    }

    @Test
    fun anOwnActivityCanBeTheBestOfTheDay() {
        val windy = day(wind = 45.0, rain = 10, temp = 18.0)
        val surfInstead = football.copy(
            label = "Kite",
            criteria = football.criteria + (Condition.WIND to Criterion(10.0, 30.0, listOf(Mark.NO, Mark.OK, Mark.GOOD)))
        )

        assertEquals("Kite", bestRating(windy, category = SpotCategory.PARK, activities = Activity.entries + surfInstead).activity.label)
    }

    @Test
    fun anActivityAtTheWaterIsLeftOutOfBestTodayInland() {
        val sailing = football.copy(label = "Sailing", place = ActivityPlace.WATER)

        val best = bestRating(day(), category = SpotCategory.PARK, activities = listOf(sailing, Activity.HIKE))

        assertEquals(Activity.HIKE, best.activity)
    }

    @Test
    fun theBuiltInActivitiesKeepTheirRules() {
        assertEquals(Verdict.GO, rate(Activity.HIKE, day(temp = 15.0, rain = 10, wind = 10.0), category = SpotCategory.PARK).verdict)
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

    @Test
    fun anActivityForSportsFieldsIsOnlyRatedThere() {
        val padel = football.copy(label = "Padel", place = ActivityPlace.SPORTS)

        assertEquals(Verdict.GO, rate(padel, day(), category = SpotCategory.SPORTS).verdict)
        val park = rate(padel, day(), category = SpotCategory.PARK)
        assertEquals(Verdict.SKIP, park.verdict)
        assertEquals("This is not an outdoor sports field.", park.reason)
    }

    @Test
    fun anActivityForIndoorCentresIsOnlyRatedThere() {
        val climbing = football.copy(label = "Climbing", place = ActivityPlace.INDOORS)

        assertEquals(Verdict.GO, rate(climbing, day(), category = SpotCategory.INDOORS).verdict)
        // Its conditions do not count: a storm is only a warning
        val storm = rate(climbing, day(code = 95), category = SpotCategory.INDOORS)
        assertEquals(Verdict.GO, storm.verdict)
        assertEquals("Indoors, but thunderstorms are expected. Take care getting there.", storm.reason)
        assertEquals("This spot is not an indoor centre.", rate(climbing, day(), category = SpotCategory.PARK).reason)
        // Any other activity is Skip indoors
        assertEquals("This spot is indoors.", rate(football, day(), category = SpotCategory.INDOORS).reason)
    }
}
