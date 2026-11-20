package com.swmad.spotcast.integration.firestore

import com.swmad.spotcast.domain.ActivityIcon
import com.swmad.spotcast.domain.Condition
import com.swmad.spotcast.domain.Criterion
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityFSTest {

    private val padel = CustomActivity(
        label = "Padel",
        icon = ActivityIcon.TENNIS,
        waterOnly = false,
        criteria = Condition.entries.associateWith { it.defaultCriterion() } +
            (Condition.WIND to Criterion(12.0, 25.0, listOf(Mark.GOOD, Mark.NO, Mark.NO)))
    )

    @Test
    fun anActivityGoesToFirestoreAndBackUnchanged() {
        val fs = padel.toFS().apply { id = "abc" }

        val back = fs.toDomain()

        assertEquals(padel.copy(id = "abc", createdAt = back.createdAt), back)
    }

    @Test
    fun theDocumentHasTheFieldsTheRulesAllow() {
        val fs = padel.toFS()

        assertEquals(setOf("wind", "rain", "sun", "temperature", "waves"), fs.criteria.keys)
        assertEquals(listOf("GOOD", "NO", "NO"), fs.criteria.getValue("wind").marks)
        assertEquals("TENNIS", fs.icon)
    }

    @Test
    fun aDamagedConditionFallsBackToItsDefault() {
        val fs = padel.toFS().apply {
            id = "abc"
            criteria = criteria + ("wind" to CriterionFS(30.0, 10.0, listOf("GOOD", "OK", "NO")))
        }

        assertEquals(Condition.WIND.defaultCriterion(), fs.toDomain().criteria.getValue(Condition.WIND))
    }

    @Test
    fun aMissingConditionAndAnUnknownIconFallBack() {
        val fs = padel.toFS().apply {
            id = "abc"
            icon = "ROCKET"
            criteria = criteria - "rain"
        }

        val back = fs.toDomain()

        assertEquals(ActivityIcon.FOOTBALL, back.icon)
        assertEquals(Condition.RAIN.defaultCriterion(), back.criteria.getValue(Condition.RAIN))
    }

    @Test
    fun aDocumentWithoutAnIdIsRefused() {
        val result = runCatching { padel.toFS().toDomain() }

        assertTrue(result.isFailure)
    }
}
