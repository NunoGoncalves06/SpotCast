package com.swmad.spotcast.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ActivityRulesTest {

    private fun day(
        temp: Double = 20.0,
        rain: Int = 10,
        wind: Double = 15.0,
        clouds: Int = 50,
        waves: Double? = null,
        code: Int = 2
    ) = DailyForecast(
        date = LocalDate.of(2026, 10, 26),
        minTemperature = temp - 6,
        maxTemperature = temp,
        precipitationProbability = rain,
        weatherCode = code,
        maxWindSpeed = wind,
        cloudCover = clouds,
        sunset = LocalTime.of(16, 51),
        waveHeight = waves
    )

    @Test
    fun surfNeedsWavesAndWater() {
        assertEquals(Verdict.GO, rate(Activity.SURF, day(waves = 1.2), category = SpotCategory.BEACH).verdict)
        assertEquals(Verdict.MAYBE, rate(Activity.SURF, day(waves = 0.6), category = SpotCategory.BEACH).verdict)
        assertEquals(Verdict.SKIP, rate(Activity.SURF, day(waves = 0.2), category = SpotCategory.BEACH).verdict)
        assertEquals(Verdict.SKIP, rate(Activity.SURF, day(waves = 1.2), category = SpotCategory.PARK).verdict)
    }

    @Test
    fun photosDependOnClouds() {
        val hidden = rate(Activity.PHOTOS, day(clouds = 95), category = SpotCategory.PARK)
        assertEquals(Verdict.SKIP, hidden.verdict)
        assertEquals("Clouds will hide the 16:51 sunset.", hidden.reason)
        assertEquals(Verdict.GO, rate(Activity.PHOTOS, day(clouds = 50), category = SpotCategory.PARK).verdict)
    }

    @Test
    fun skipNamesTheProblem() {
        val wet = rate(Activity.HIKE, day(rain = 80), category = SpotCategory.PARK)
        assertEquals(Verdict.SKIP, wet.verdict)
        assertEquals("80% chance of rain.", wet.reason)
    }

    @Test
    fun thunderstormIsAlwaysSkip() {
        Activity.entries.forEach {
            assertEquals(Verdict.SKIP, rate(it, day(code = 95), category = SpotCategory.BEACH).verdict)
        }
    }

    @Test
    fun bestPicksTheHighestVerdict() {
        // Cold, wet and cloudy, but big waves: surfing is the only Go
        val stormy = day(temp = 9.0, rain = 70, wind = 30.0, clouds = 100, waves = 1.4)
        assertEquals(Activity.SURF, bestRating(stormy, category = SpotCategory.BEACH).activity)
    }

    @Test
    fun fieldSportsGoWhenDryCalmAndMild() {
        val go = rate(Activity.FIELD_SPORTS, day(temp = 20.0, rain = 10, wind = 15.0), SpotCategory.SPORTS)
        assertEquals(Verdict.GO, go.verdict)
        assertEquals("Dry, 20 °C and 15 km/h wind.", go.reason)
        assertEquals(Verdict.GO, rate(Activity.FIELD_SPORTS, day(temp = 27.0), SpotCategory.SPORTS).verdict)
        assertEquals(Verdict.MAYBE, rate(Activity.FIELD_SPORTS, day(temp = 28.0), SpotCategory.SPORTS).verdict)
    }

    @Test
    fun fieldSportsAreMaybeWhenWindyOrDamp() {
        val maybe = rate(Activity.FIELD_SPORTS, day(temp = 15.0, rain = 50, wind = 35.0), SpotCategory.SPORTS)
        assertEquals(Verdict.MAYBE, maybe.verdict)
        assertEquals("15 °C, 50% chance of rain and 35 km/h wind.", maybe.reason)
        assertEquals(Verdict.MAYBE, rate(Activity.FIELD_SPORTS, day(temp = 2.0), SpotCategory.SPORTS).verdict)
        assertEquals(Verdict.SKIP, rate(Activity.FIELD_SPORTS, day(temp = 1.0), SpotCategory.SPORTS).verdict)
    }

    @Test
    fun fieldSportsNameTheProblemWhenSkipped() {
        assertEquals("70% chance of rain.", rate(Activity.FIELD_SPORTS, day(rain = 70), SpotCategory.SPORTS).reason)
        assertEquals("Wind up to 50 km/h.", rate(Activity.FIELD_SPORTS, day(wind = 50.0), SpotCategory.SPORTS).reason)
    }

    @Test
    fun fieldSportsNeedASportsGround() {
        val park = rate(Activity.FIELD_SPORTS, day(), SpotCategory.PARK)
        assertEquals(Verdict.SKIP, park.verdict)
        assertEquals("This is not an outdoor sports field.", park.reason)
        assertEquals(Verdict.SKIP, rate(Activity.FIELD_SPORTS, day(), SpotCategory.INDOORS).verdict)
    }

    @Test
    fun gymIsAlwaysAGoIndoors() {
        val calm = rate(Activity.GYM, day(), SpotCategory.INDOORS)
        assertEquals(Verdict.GO, calm.verdict)
        assertEquals("Indoors, the weather does not matter.", calm.reason)
    }

    @Test
    fun gymWarnsAboutTheWayThereWhenTheWeatherIsVeryBadButStaysAGo() {
        val storm = rate(Activity.GYM, day(code = 95), SpotCategory.INDOORS)
        assertEquals(Verdict.GO, storm.verdict)
        assertEquals("Indoors, but thunderstorms are expected. Take care getting there.", storm.reason)
        assertEquals("Indoors, but heavy rain is likely (80%). Take care getting there.", rate(Activity.GYM, day(rain = 80), SpotCategory.INDOORS).reason)
        assertEquals("Indoors, but the wind is strong, up to 60 km/h. Take care getting there.", rate(Activity.GYM, day(wind = 60.0), SpotCategory.INDOORS).reason)
    }

    @Test
    fun gymNeedsAnIndoorCentre() {
        val park = rate(Activity.GYM, day(), SpotCategory.PARK)
        assertEquals(Verdict.SKIP, park.verdict)
        assertEquals("This spot is not an indoor centre.", park.reason)
    }

    @Test
    fun everyOtherActivityIsSkipIndoors() {
        (Activity.entries - Activity.GYM).forEach {
            val rating = rate(it, day(), SpotCategory.INDOORS)
            assertEquals(Verdict.SKIP, rating.verdict)
            assertEquals("This spot is indoors.", rating.reason)
        }
    }

    @Test
    fun bestTodayIsTheGymIndoorsAndFieldSportsOnAPitch() {
        assertEquals(Activity.GYM, bestRating(day(code = 95), SpotCategory.INDOORS).activity)
        assertEquals(Activity.FIELD_SPORTS, bestRating(day(), SpotCategory.SPORTS).activity)
    }

    @Test
    fun theBarAndTheChipsAreInTheOrderOfTheBrief() {
        assertEquals(listOf(Activity.FIELD_SPORTS, Activity.BEACH, Activity.GYM), Activity.entries.take(3))
        assertEquals(
            listOf(SpotCategory.SPORTS, SpotCategory.BEACH, SpotCategory.INDOORS, SpotCategory.TRAIL),
            SpotCategory.entries.take(4)
        )
    }
}