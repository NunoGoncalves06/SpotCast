package com.swmad.spotcast.ui.activities

import com.swmad.spotcast.domain.ActivityIcon
import com.swmad.spotcast.domain.ActivityPlace
import com.swmad.spotcast.domain.Condition
import com.swmad.spotcast.domain.Criterion
import com.swmad.spotcast.domain.CustomActivity
import com.swmad.spotcast.domain.MAX_ACTIVITY_NAME_LENGTH
import com.swmad.spotcast.domain.Mark
import com.swmad.spotcast.domain.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityEditorViewModelTest {

    private fun editor() = ActivityEditorViewModel().apply { start(null) }

    @Test
    fun aNewActivityStartsWithTheDefaults() {
        val vm = editor()

        assertTrue(vm.isNew)
        assertEquals(Condition.entries.associateWith { it.defaultCriterion() }, vm.criteria)
    }

    @Test
    fun startingAgainDoesNotUndoTheChanges() {
        val vm = editor()
        vm.rename("Padel")

        vm.start(CustomActivity(id = "x", label = "Other"))

        assertEquals("Padel", vm.name)
        assertTrue(vm.isNew)
    }

    @Test
    fun anExistingActivityFillsTheForm() {
        val vm = ActivityEditorViewModel()

        vm.start(CustomActivity(id = "x", label = "Sailing", icon = ActivityIcon.SAIL, place = ActivityPlace.WATER))

        assertFalse(vm.isNew)
        assertEquals("Sailing", vm.name)
        assertEquals(ActivityIcon.SAIL, vm.icon)
        assertTrue(Condition.WAVES in vm.conditions)
    }

    @Test
    fun theNameIsCutAtTheLimit() {
        val vm = editor()

        vm.rename("x".repeat(MAX_ACTIVITY_NAME_LENGTH + 10))

        assertEquals(MAX_ACTIVITY_NAME_LENGTH, vm.name.length)
    }

    @Test
    fun wavesAreOnlyAnOptionAtTheWater() {
        val vm = editor()
        assertFalse(Condition.WAVES in vm.conditions)

        vm.choosePlace(ActivityPlace.WATER)

        assertTrue(Condition.WAVES in vm.conditions)
    }

    @Test
    fun anActivityIsForOneKindOfSpotAtMost() {
        val vm = editor()

        vm.choosePlace(ActivityPlace.WATER)
        vm.choosePlace(ActivityPlace.INDOORS)

        assertEquals(ActivityPlace.INDOORS, vm.place)
        assertFalse(Condition.WAVES in vm.conditions)
        assertEquals(ActivityPlace.INDOORS, vm.build().place)
    }

    @Test
    fun aTapCyclesGoodOkNoAndBack() {
        val vm = editor()
        fun mark() = vm.criteria.getValue(Condition.WIND).marks[0]
        assertEquals(Mark.GOOD, mark())

        vm.cycleMark(Condition.WIND, 0)
        assertEquals(Mark.OK, mark())
        vm.cycleMark(Condition.WIND, 0)
        assertEquals(Mark.NO, mark())
        vm.cycleMark(Condition.WIND, 0)
        assertEquals(Mark.GOOD, mark())
    }

    @Test
    fun theFirstChangeCannotPassTheSecondOne() {
        val vm = editor()

        vm.moveFirst(Condition.WIND, 70.0)

        val wind = vm.criteria.getValue(Condition.WIND)
        assertEquals(wind.second - Condition.WIND.step, wind.first, 0.0001)
    }

    @Test
    fun theSecondChangeCannotGoBelowTheFirstOne() {
        val vm = editor()

        vm.moveSecond(Condition.WIND, 2.0)

        val wind = vm.criteria.getValue(Condition.WIND)
        assertEquals(wind.first + Condition.WIND.step, wind.second, 0.0001)
    }

    @Test
    fun aRangeTighterThanOneStepDoesNotCrashTheSliders() {
        // Another client can write such a range, and the rules accept it
        val vm = ActivityEditorViewModel()
        vm.start(
            CustomActivity(
                id = "x", label = "Odd",
                criteria = Condition.entries.associateWith { it.defaultCriterion() } +
                    (Condition.WAVES to Criterion(0.0, 0.05, Condition.WAVES.defaultMarks))
            )
        )

        vm.moveFirst(Condition.WAVES, 0.5)
        vm.moveSecond(Condition.WAVES, 0.0)

        assertTrue(vm.criteria.getValue(Condition.WAVES).first >= 0.0)
    }

    @Test
    fun slidersSnapToTheStepOfTheCondition() {
        val vm = editor()

        vm.moveFirst(Condition.RAIN, 22.0)
        vm.moveFirst(Condition.WAVES, 0.34)

        assertEquals(20.0, vm.criteria.getValue(Condition.RAIN).first, 0.0001)
        assertEquals(0.3, vm.criteria.getValue(Condition.WAVES).first, 0.0001)
    }

    @Test
    fun resetPutsTheNumbersBackButKeepsTheMarks() {
        val vm = editor()
        vm.cycleMark(Condition.RAIN, 2)
        vm.moveFirst(Condition.RAIN, 40.0)
        vm.toggleAdjust(Condition.RAIN)

        vm.resetRanges(Condition.RAIN)

        val rain = vm.criteria.getValue(Condition.RAIN)
        assertEquals(Condition.RAIN.defaultFirst, rain.first, 0.0001)
        assertEquals(Mark.GOOD, rain.marks[2])
        assertFalse(Condition.RAIN in vm.adjusting)
    }

    @Test
    fun thePreviewFollowsTheSampleDay() {
        val vm = editor()
        vm.rename("Football")
        assertEquals(Verdict.GO, vm.preview.verdict)

        vm.moveSample(Condition.WIND, 40.0)

        assertEquals(Verdict.SKIP, vm.preview.verdict)
        assertTrue(vm.preview.reason.startsWith("Wind: windy day"))
    }

    @Test
    fun buildGivesTheActivityTheScreenShows() {
        val vm = ActivityEditorViewModel()
        vm.start(CustomActivity(id = "x", label = "Old"))
        vm.rename("  Padel ")
        vm.chooseIcon(ActivityIcon.TENNIS)

        val built = vm.build()

        assertEquals(CustomActivity(id = "x", label = "Padel", icon = ActivityIcon.TENNIS), built)
        assertTrue(built.isValid())
    }
}
