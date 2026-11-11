package com.daviddokakis.karoointerval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErgPlannerTest {

    private val plan = WorkoutPlan(
        "erg", "erg",
        listOf(
            PlanStep(600, 150, 150),
            PlanStep(300, 300, 300),
            PlanStep(300, 120, 240, ramp = true),
            PlanStep(300, 150, 150, hr = true),
        ),
        ftp = 280,
    )
    private val running = ScreenState(plan = plan, stepIndex = 1, elapsedInStepSec = 30, notStarted = false)
    private fun on(s: ScreenState = running, indoor: Boolean = true, enabled: Boolean = true, recording: Boolean = true) =
        ErgInputs(indoor, enabled, recording, s)
    private fun target(w: Int) = ErgCommand.Target(w)

    @Test fun `holds the interval's target`() {
        assertEquals(target(300), ErgPlanner().next(on(), 0))
    }

    @Test fun `a ramp follows its target second by second`() {
        val p = ErgPlanner()
        assertEquals(target(120), p.next(on(running.copy(stepIndex = 2, elapsedInStepSec = 0)), 0))
        assertEquals(target(180), p.next(on(running.copy(stepIndex = 2, elapsedInStepSec = 150)), 150))
    }

    @Test fun `hands the trainer back unless indoors, on and recording`() {
        val p = ErgPlanner()
        assertEquals(ErgCommand.Release, p.next(on(indoor = false), 0))
        assertEquals(ErgCommand.Release, p.next(on(enabled = false), 0))
        assertEquals(ErgCommand.Release, p.next(on(recording = false), 0))
        assertEquals(ErgCommand.Release, p.next(on(running.copy(notStarted = true), recording = false), 0))
    }

    @Test fun `between workouts and before START mid-ride it holds the When paused watts`() {
        val p = ErgPlanner()
        assertEquals(target(140), p.next(on(running.copy(notStarted = true)), 0))       // a new workout's start screen
        assertEquals(target(100), p.next(on(ScreenState()), 0))                          // removed, no FTP known
        assertEquals(target(165), p.next(on(ScreenState(profileFtp = 330)), 0))         // removed, the Karoo's FTP
    }

    @Test fun `a finished workout hands the trainer back until the next one starts`() {
        val p = ErgPlanner()
        fun after(s: ScreenState) = p.next(ErgInputs(true, true, true, s, afterFinish = true), 0)
        assertEquals(ErgCommand.Release, p.next(on(running.copy(finished = true)), 0))
        assertEquals(ErgCommand.Release, after(ScreenState()))                            // closed with DONE
        assertEquals(ErgCommand.Release, after(running.copy(notStarted = true)))          // the next one loaded
        assertEquals(target(300), after(running))                                         // and started
    }

    @Test fun `paused spins easy at half FTP`() {
        val p = ErgPlanner()
        assertEquals(target(140), p.next(on(running.copy(workoutPaused = true)), 0))
        assertEquals(target(140), p.next(on(running.copy(paused = true)), 0))
    }

    @Test fun `recovery has a floor and a fallback`() {
        assertEquals(80, ErgPlanner.recoveryWatts(120))
        assertEquals(100, ErgPlanner.recoveryWatts(null))
    }

    @Test fun `resume climbs from recovery to the target over 10 s`() {
        val p = ErgPlanner()
        p.next(on(running.copy(workoutPaused = true)), 0)
        assertEquals(target(140), p.next(on(), 100))
        assertEquals(target(220), p.next(on(), 105))
        assertEquals(target(300), p.next(on(), 110))
        assertEquals(target(300), p.next(on(), 111))
    }

    @Test fun `a jump during the soft start lands at full target`() {
        val p = ErgPlanner()
        p.next(on(running.copy(workoutPaused = true)), 0)
        p.next(on(), 100)
        assertEquals(target(150), p.next(on(running.copy(stepIndex = 0)), 103))
    }

    @Test fun `a heart-rate step leaves the trainer free`() {
        assertEquals(ErgCommand.Release, ErgPlanner().next(on(running.copy(stepIndex = 3)), 0))
    }

    @Test fun `targets are capped`() {
        assertEquals(315, ErgPlanner.capFor(plan, 280))            // plan top 300 + 5 %
        assertEquals(400, ErgPlanner.capFor(plan.copy(steps = listOf(PlanStep(60, 900, 900))), 200)) // 2 × FTP
        assertEquals(1500, ErgPlanner.capFor(plan.copy(steps = listOf(PlanStep(60, 2000, 2000))), null))
    }

    @Test fun `resends every 2 s, changes at once, a release once`() {
        val pacer = ErgSendPacer()
        assertTrue(pacer.due(target(200), 0))
        assertFalse(pacer.due(target(200), 1_500))
        assertTrue(pacer.due(target(200), 2_000))
        assertTrue(pacer.due(target(210), 3_000))
        assertFalse(pacer.due(target(220), 3_500))        // under a second after the last write
        assertTrue(pacer.due(ErgCommand.Release, 3_600))
        assertFalse(pacer.due(ErgCommand.Release, 9_000))
        assertTrue(pacer.due(target(150), 9_100))
    }

    @Test fun `ERG off for the ride and back on picks the target up again`() {
        val p = ErgPlanner()
        val pacer = ErgSendPacer()
        var now = 0L
        fun step(enabled: Boolean): Pair<ErgCommand, Boolean> {
            val cmd = p.next(on(enabled = enabled), now / 1000)
            val sent = pacer.due(cmd, now)
            now += 1_000
            return cmd to sent
        }
        assertEquals(target(300) to true, step(enabled = true))          // holding
        assertEquals(ErgCommand.Release to true, step(enabled = false))  // off: handed back at once
        assertEquals(ErgCommand.Release to false, step(enabled = false)) // and only once
        assertEquals(target(300) to true, step(enabled = true))          // back on: the target at once
        assertEquals(target(300) to false, step(enabled = true))
        now += 1_000
        assertEquals(target(300) to true, step(enabled = true))          // then the 2 s keep-alive
    }
}
