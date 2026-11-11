package com.daviddokakis.karoointerval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Switching workouts mid-ride (test ride 2026-11-10: the old jump landed in the new workout). */
class WorkoutSwitchTest {

    private fun plan(id: String, steps: Int) =
        WorkoutPlan(id, id, List(steps) { PlanStep(600, 200, 200) }, ftp = 280, lthr = 180)

    private val tempo = plan("tempo", 9)
    private val stairs = plan("stairs", 16)

    /** Tempo at interval 7 with a jump to interval 3 counting down, and another being chosen. */
    private val midJump = ScreenState(
        plan = tempo, stepIndex = 6, elapsedInStepSec = 87, notStarted = false,
        countdown = 3, jumpTarget = 2, selectTarget = 4, selectRestart = true, selectProgress = 1,
        autoConfirmLeft = 2, moveCancelled = true,
    )

    @Test fun `a newly loaded workout keeps nothing of the old one's jump`() {
        val st = midJump.loaded(stairs, index = 0, elapsedSec = 0, notStarted = true)
        assertEquals(stairs, st.plan)
        assertEquals(0, st.stepIndex)
        assertEquals(0, st.elapsedInStepSec)
        assertTrue(st.notStarted)
        assertNull(st.countdown)
        assertNull(st.jumpTarget)
        assertNull(st.selectTarget)
        assertFalse(st.selectRestart)
        assertEquals(0, st.selectProgress)
        assertNull(st.autoConfirmLeft)
        assertFalse(st.moveCancelled)
        assertNull(st.resumeFromSec)
        assertNull(st.landTarget)
    }

    @Test fun `a countdown lands only in the workout it was started for`() {
        assertEquals(2, midJump.countdownLanding("tempo", 2))
        // switched to the Stair climb while counting down: it goes nowhere
        val switched = midJump.loaded(stairs, index = 0, elapsedSec = 0, notStarted = true)
        assertNull(switched.countdownLanding("tempo", 2))
        // a step the workout doesn't have
        assertNull(midJump.countdownLanding("tempo", 12))
        assertNull(ScreenState().countdownLanding(null, 0))
    }

    @Test fun `the same workout picked again can still continue where it was`() {
        val st = midJump.loaded(tempo, index = 6, elapsedSec = 0, notStarted = true, resumeFromSec = 87)
        assertEquals(6, st.stepIndex)
        assertEquals(87, st.resumeFromSec)
        assertNull(st.jumpTarget)
    }

    @Test fun `ride log lines are one line with time and level`() {
        val line = rideLogLine(LocalDateTime.of(2026, 11, 10, 8, 12, 9, 5_000_000), "W", "Countdown dropped\nthe workout changed")
        assertEquals("2026-11-10 08:12:09.005 W Countdown dropped the workout changed\n", line)
    }

    @Test fun `an interval ending by itself counts down its last 5 seconds`() {
        assertNull(endingCountdown(5_001))
        assertEquals(5, endingCountdown(5_000))
        assertEquals(5, endingCountdown(4_200))
        assertEquals(1, endingCountdown(1))
        assertNull(endingCountdown(0))
    }

    @Test fun `the last interval's countdown is Workout finished`() {
        val last = ScreenState(plan = tempo, stepIndex = 8, notStarted = false, countdown = 3)
        assertTrue(last.finishing)
        // an earlier interval counts down to the next one instead
        assertFalse(last.copy(stepIndex = 4, jumpTarget = 5).finishing)
        // a jump counting down to an interval isn't finishing
        assertFalse(last.copy(jumpTarget = 2).finishing)
        assertFalse(last.copy(countdown = null).finishing)
    }

    @Test fun `the start screen's buttons go round at the ends`() {
        assertEquals(15, startStepAfter(0, -1, 16))   // ⏮ on the first: the last
        assertEquals(0, startStepAfter(15, 1, 16))    // ⏭ on the last: the first
        assertEquals(14, startStepAfter(15, -1, 16))
        assertEquals(3, startStepAfter(2, 1, 16))
        assertEquals(0, startStepAfter(0, 1, 1))      // one step: stays
    }
}
