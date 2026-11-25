package com.daviddokakis.karoointerval

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceStepsTest {
    private val eps = 1e-9

    @Test fun trainerCountGivesTheMetresBetweenReadings() {
        val s = DistanceSteps()
        assertEquals(0.0, s.next(120, 9.5, 1_000), eps) // first reading: the baseline
        assertEquals(10.0, s.next(130, 9.5, 2_000), eps)
        assertEquals(9.0, s.next(139, 9.5, 3_000), eps)
        assertEquals(0.0, s.next(139, 0.0, 4_000), eps) // standing still
    }

    @Test fun withoutACountSpeedTimesTime() {
        val s = DistanceSteps()
        assertEquals(0.0, s.next(null, 8.0, 1_000), eps)
        assertEquals(8.0, s.next(null, 8.0, 2_000), eps)
        assertEquals(4.5, s.next(null, 9.0, 2_500), eps)
        assertEquals(0.0, s.next(null, null, 3_500), eps)
    }

    @Test fun noJumpAfterAGapOrReset() {
        val s = DistanceSteps()
        s.next(100, 9.0, 1_000)
        assertEquals(0.0, s.next(200, 9.0, 10_000), eps) // 9 s without readings
        assertEquals(9.0, s.next(209, 9.0, 11_000), eps)
        s.reset()
        assertEquals(0.0, s.next(250, 9.0, 12_000), eps)
        assertEquals(10.0, s.next(260, 9.0, 13_000), eps)
    }

    @Test fun countGoingBackOrGlitchAddsNothing() {
        val s = DistanceSteps()
        s.next(500, 9.0, 1_000)
        assertEquals(0.0, s.next(3, 9.0, 2_000), eps) // trainer restarted its count
        assertEquals(10.0, s.next(13, 9.0, 3_000), eps)
        assertEquals(0.0, s.next(5_000, 9.0, 4_000), eps) // 5 km in a second
        assertEquals(9.0, s.next(5_009, 9.0, 5_000), eps)
    }
}
