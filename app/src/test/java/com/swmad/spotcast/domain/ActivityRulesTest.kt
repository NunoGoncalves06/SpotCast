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
        assertEquals(Verdict.GO, rate(Activity.SURF, day(waves = 1.2), nearWater = true).verdict)
        assertEquals(Verdict.MAYBE, rate(Activity.SURF, day(waves = 0.6), nearWater = true).verdict)
        assertEquals(Verdict.SKIP, rate(Activity.SURF, day(waves = 0.2), nearWater = true).verdict)
        assertEquals(Verdict.SKIP, rate(Activity.SURF, day(waves = 1.2), nearWater = false).verdict)
    }

    @Test
    fun photosDependOnClouds() {
        val hidden = rate(Activity.PHOTOS, day(clouds = 95), nearWater = false)
        assertEquals(Verdict.SKIP, hidden.verdict)
        assertEquals("Clouds will hide the 16:51 sunset.", hidden.reason)
        assertEquals(Verdict.GO, rate(Activity.PHOTOS, day(clouds = 50), nearWater = false).verdict)
    }

    @Test
    fun skipNamesTheProblem() {
        val wet = rate(Activity.HIKE, day(rain = 80), nearWater = false)
        assertEquals(Verdict.SKIP, wet.verdict)
        assertEquals("80% chance of rain.", wet.reason)
    }

    @Test
    fun thunderstormIsAlwaysSkip() {
        Activity.entries.forEach {
            assertEquals(Verdict.SKIP, rate(it, day(code = 95), nearWater = true).verdict)
        }
    }

    @Test
    fun bestPicksTheHighestVerdict() {
        // Cold, wet and cloudy, but big waves: surfing is the only Go
        val stormy = day(temp = 9.0, rain = 70, wind = 30.0, clouds = 100, waves = 1.4)
        assertEquals(Activity.SURF, bestRating(stormy, nearWater = true).activity)
    }
}
