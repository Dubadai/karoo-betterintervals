package com.daviddokakis.karoointerval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMathTest {

    private val power = PlanStep(300, 280, 280, rangeLow = 273, rangeHigh = 287)
    private val hr = PlanStep(240, 166, 166, rangeLow = 163, rangeHigh = 169, hr = true, pctUnit = "%hr", pctLow = 85, pctHigh = 85)
    private val plan = WorkoutPlan("p", "Test", listOf(power, hr), ftp = 280, lthr = 180)

    @Test fun `scaling changes power targets only`() {
        val scaled = plan.scaled(105)
        assertEquals(294, scaled.steps[0].startWatts)
        assertEquals(259..329, scaled.steps[0].rangeAt(0))
        assertSame(hr, scaled.steps[1])
        assertEquals(105, scaled.scalePct)
        assertSame(plan, plan.scaled(100))
    }

    @Test fun `one power number is the target plus or minus 12 percent, a written range as it is`() {
        assertEquals(176..224, PlanStep(60, 200, 200).rangeAt(0))
        // the ride's interval: intervals.icu sent 193-203 with it, the band ignores that
        assertEquals(174..222, PlanStep(1200, 198, 198, rangeLow = 193, rangeHigh = 203).rangeAt(0))
        // a range the workout writes is the band, narrow or wide
        assertEquals(190..200, PlanStep(600, 195, 195, rangeLow = 190, rangeHigh = 200, isRange = true).rangeAt(0))
        assertEquals(148..191, PlanStep(600, 170, 170, rangeLow = 148, rangeHigh = 191, isRange = true).rangeAt(0))
        // heart rate keeps its own range
        assertEquals(140..150, PlanStep(600, 145, 145, rangeLow = 140, rangeHigh = 150, isRange = true, hr = true).rangeAt(0))
        // very low targets: at least 5 W
        assertEquals(35..45, PlanStep(60, 40, 40).rangeAt(0))
        // heart rate: at least 2 bpm either side
        assertEquals(58..62, PlanStep(60, 60, 60, hr = true).rangeAt(0))
    }

    @Test fun `percent shows as written for percent-of-max steps`() {
        val s = ScreenState(plan = plan, stepIndex = 1, hrTargetLthr = true)
        assertTrue(s.hrStep)
        assertTrue(s.showsPct)
        assertEquals("%hr", s.pctUnitOf(hr))
        assertEquals(85 to 85, s.pctOf(hr))
    }

    @Test fun `bpm-only steps fall back to percent of LTHR`() {
        val bpm = PlanStep(60, 162, 162, rangeLow = 160, rangeHigh = 164, hr = true)
        val s = ScreenState(plan = plan.copy(steps = listOf(bpm)), hrTargetLthr = true)
        assertEquals("%lthr", s.pctUnitOf(bpm))
        assertEquals(89 to 91, s.pctOf(bpm))
    }

    @Test fun `no LTHR and no written percentage means bpm only`() {
        val bpm = PlanStep(60, 162, 162, hr = true)
        val s = ScreenState(plan = WorkoutPlan("p", "T", listOf(bpm), ftp = null), hrTargetLthr = true)
        assertNull(s.pctUnitOf(bpm))
        assertFalse(s.showsPct)
    }

    @Test fun `the big number follows the step`() {
        val s = ScreenState(plan = plan, powerShown = 281, heartRate = 150)
        assertEquals(281, s.mainValue)
        assertEquals(150, s.copy(stepIndex = 1).mainValue)
    }

    @Test fun `heart rate zone from the rider profile`() {
        val s = ScreenState(hrZones = listOf(0..119, 120..139, 140..154, 155..169, 170..220))
        assertEquals(2, s.zoneOf(132))
        assertEquals(5, s.zoneOf(230))
        assertNull(s.zoneOf(null))
    }

    @Test fun `power zones as percent of FTP`() {
        assertEquals(1, Zones.zoneOf(150, 280))
        assertEquals(4, Zones.zoneOf(280, 280))
        assertEquals(7, Zones.zoneOf(500, 280))
        assertNull(Zones.zoneOf(200, null))
    }

    @Test fun `Karoo HR colour picks depend on the zone count`() {
        assertEquals(R.color.karoo_hr_c1, Zones.hrZoneColor(1, 5))
        assertEquals(R.color.karoo_hr_c6, Zones.hrZoneColor(5, 5))
        assertEquals(R.color.karoo_hr_c3, Zones.hrZoneColor(1, 1))
        assertNull(Zones.hrZoneColor(6, 5))
    }

    @Test fun `clock format`() {
        assertEquals("3:00", formatClock(180))
        assertEquals("1:15:00", formatClock(4500))
        assertEquals("0:00", formatClock(-5))
    }
}
