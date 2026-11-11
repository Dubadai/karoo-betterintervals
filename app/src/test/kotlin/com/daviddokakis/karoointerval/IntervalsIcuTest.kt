package com.daviddokakis.karoointerval

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalsIcuTest {

    private fun event(steps: String, extra: String = "") = JSONObject(
        """{"id": 7, "name": "Test", "type": "Ride", "workout_doc": {"ftp": 280 $extra, "steps": $steps}}""",
    )

    @Test fun `power step in percent of FTP uses the resolved watts, its band is plus or minus 12 percent`() {
        val plan = IntervalsIcu.parseEvent(
            event("""[{"duration": 300, "power": {"value": 100, "units": "%ftp"}, "_power": {"value": 280, "start": 273, "end": 287}}]"""),
        )!!
        val step = plan.steps.single()
        assertEquals(280, step.startWatts)
        // intervals.icu's own 273-287 (±2.5 %) is kept but not used: one number gets ±12 %
        assertEquals(273, step.rangeLow)
        assertEquals(246..314, step.rangeAt(0))
        assertFalse(step.hr)
    }

    @Test fun `power step without resolved values converts percent of FTP`() {
        val step = IntervalsIcu.parseEvent(event("""[{"duration": 60, "power": {"value": 50, "units": "%ftp"}}]"""))!!.steps.single()
        assertEquals(140, step.startWatts)
    }

    @Test fun `ramp keeps both ends`() {
        val step = IntervalsIcu.parseEvent(
            event("""[{"duration": 600, "ramp": true, "power": {"start": 55, "end": 75, "units": "%ftp"}, "_power": {"start": 154, "end": 210}}]"""),
        )!!.steps.single()
        assertTrue(step.isRamp)
        assertEquals(154, step.targetAt(0))
        assertEquals(210, step.targetAt(600))
        assertEquals(182, step.targetAt(300))
    }

    @Test fun `repeat block is expanded`() {
        val plan = IntervalsIcu.parseEvent(
            event(
                """[{"reps": 3, "steps": [
                    {"duration": 240, "power": {"value": 300, "units": "w"}},
                    {"duration": 120, "power": {"value": 150, "units": "w"}}]}]""",
            ),
        )!!
        assertEquals(6, plan.steps.size)
        assertEquals(listOf(300, 150, 300, 150, 300, 150), plan.steps.map { it.startWatts })
        assertEquals(1080, plan.totalSec)
    }

    // Shape of a real HR-only intervals.icu step ("4m 85% HR"), resolved to bpm in _hr
    @Test fun `heart-rate step keeps bpm and the written percentage`() {
        val plan = IntervalsIcu.parseEvent(
            event(
                """[{"duration": 240, "hr": {"value": 85, "units": "%hr"}, "_hr": {"value": 166, "start": 163, "end": 169}}]""",
                extra = """, "lthr": 180, "target": "HR"""",
            ),
        )!!
        val step = plan.steps.single()
        assertTrue(step.hr)
        assertTrue(plan.hrOnly)
        assertEquals(166, step.startWatts)
        assertEquals(163..169, step.rangeAt(0))
        assertEquals("%hr", step.pctUnit)
        assertEquals(85, step.pctLow)
        assertEquals(180, plan.lthr)
    }

    @Test fun `percent of LTHR without resolved values converts with the workout's LTHR`() {
        val step = IntervalsIcu.parseEvent(
            event("""[{"duration": 60, "hr": {"value": 90, "units": "%lthr"}}]""", extra = """, "lthr": 180"""),
        )!!.steps.single()
        assertEquals(162, step.startWatts)
        assertEquals("%lthr", step.pctUnit)
    }

    @Test fun `a step with both targets runs on power`() {
        val step = IntervalsIcu.parseEvent(
            event("""[{"duration": 60, "power": {"value": 200, "units": "w"}, "hr": {"value": 80, "units": "%hr"}}]"""),
        )!!.steps.single()
        assertFalse(step.hr)
        assertEquals(200, step.startWatts)
    }

    @Test fun `distance steps are skipped and an empty workout is no plan`() {
        assertNull(IntervalsIcu.parseEvent(event("""[{"distance": 1000, "power": {"value": 200, "units": "w"}}]""")))
        assertNull(IntervalsIcu.parseEvent(JSONObject("""{"id": 1, "name": "No doc"}""")))
    }

    @Test fun `non-ride events are not listed`() {
        val run = JSONObject("""{"id": 1, "type": "Run", "start_date_local": "2026-11-08T00:00:00", "workout_doc": {"steps": []}}""")
        assertNull(IntervalsIcu.plannedFrom(run))
    }
}
