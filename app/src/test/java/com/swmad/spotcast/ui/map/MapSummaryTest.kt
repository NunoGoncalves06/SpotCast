package com.swmad.spotcast.ui.map

import com.swmad.spotcast.domain.Activity
import com.swmad.spotcast.domain.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapSummaryTest {

    @Test
    fun whileForecastsLoadThePillChecks() {
        val summary = mapSummary(total = 7, goes = 2, loading = 3, failed = 0, activity = null)

        assertTrue(summary.checking)
        assertEquals("Checking the forecast for 7 spots", summary.text)
        assertNull(summary.dot)
    }

    @Test
    fun whenEveryForecastFailedThePillStopsCheckingAndSaysSo() {
        val summary = mapSummary(total = 7, goes = 0, loading = 0, failed = 7, activity = null)

        assertFalse(summary.checking)
        assertTrue(summary.text.startsWith("No forecast right now"))
        assertNull(summary.dot)
        assertTrue("a tap asks for the forecasts again", summary.canRetry)
    }

    @Test
    fun aFailureAmongOthersIsCountedAndTheRestStillCounts() {
        val summary = mapSummary(total = 7, goes = 2, loading = 0, failed = 1, activity = Activity.SURF)

        assertEquals("2 of 7 spots are a Go for surfing today, 1 without forecast", summary.text)
        assertEquals(Verdict.GO, summary.dot)
        assertTrue(summary.canRetry)
    }

    @Test
    fun noGoGivesARedDot() {
        val summary = mapSummary(total = 3, goes = 0, loading = 0, failed = 0, activity = null)

        assertEquals("0 of 3 spots are a Go for something today", summary.text)
        assertEquals(Verdict.SKIP, summary.dot)
        assertFalse("nothing failed, so there is nothing to retry", summary.canRetry)
    }

    @Test
    fun oneSpotReadsAsOne() {
        val summary = mapSummary(total = 1, goes = 1, loading = 0, failed = 0, activity = Activity.PHOTOS)

        assertEquals("1 of 1 spot is a Go for photos today", summary.text)
    }
}
