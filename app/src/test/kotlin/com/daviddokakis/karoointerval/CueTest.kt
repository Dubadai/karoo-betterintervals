package com.daviddokakis.karoointerval

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The coach's words per step (intervals.icu step text) for the workout screen. */
class CueTest {
    private fun step(text: String?, dur: Int, pct: Int) = JSONObject().apply {
        if (text != null) put("text", text)
        put("duration", dur)
        put("power", JSONObject().put("value", pct).put("units", "%ftp"))
    }

    private fun event(vararg steps: JSONObject) = JSONObject()
        .put("id", 1).put("name", "Cues").put("type", "Ride")
        .put("workout_doc", JSONObject().put("ftp", 250).put("steps", org.json.JSONArray(steps.toList())))

    @Test fun `a step's text is its cue, a repeat block's text goes on its steps with the round`() {
        val block = JSONObject().put("text", "Main set 2x").put("reps", 2)
            .put("steps", org.json.JSONArray(listOf(step(null, 60, 120), step("Spin easy", 180, 50))))
        val plan = IntervalsIcu.parseEvent(event(step("Warm up easy", 600, 55), step(null, 300, 90), block))!!
        assertEquals(listOf("Warm up easy", null, "Main set 1/2", "Spin easy", "Main set 2/2", "Spin easy"), plan.steps.map { it.cue })
        assertEquals("Warm up easy", plan.steps[0].cueAt(0))
        assertNull(plan.steps[1].cueAt(100))
    }

    @Test fun `timed cues change mid-step`() {
        val s = PlanStep(60, 300, 300, cues = listOf(Cue(0, "Get ready"), Cue(30, "Surge now!")))
        assertEquals("Get ready", s.cueAt(0))
        assertEquals("Get ready", s.cueAt(29))
        assertEquals("Surge now!", s.cueAt(30))
        assertEquals("Get ready", s.cue)
        // Scaling a workout keeps its words
        assertEquals(s.cues, s.scaled(90).cues)
    }

    @Test fun `timed prompts are read from the step text as intervals_icu sends it`() {
        // Real step text from intervals.icu for "- Get ready 30^Surge now! <!> 1m 120%"
        assertEquals(listOf(Cue(0, "Get ready"), Cue(30, "Surge now!")), IntervalsIcu.cuesOfText("Get ready 30^Surge now! "))
        // offset^length, and no words before the first marker
        val c = IntervalsIcu.cuesOfText("20^ First 60^30 Second")
        assertEquals(listOf(Cue(20, "First"), Cue(60, "Second", 90)), c)
        val s = PlanStep(120, 200, 200, cues = c)
        assertNull(s.cueAt(10))
        assertEquals("First", s.cueAt(20))
        assertEquals("Second", s.cueAt(75))
        assertEquals("First", s.cueAt(95)) // Second's 30 s are over: back to First
        assertEquals(listOf(Cue(0, "Hold steady")), IntervalsIcu.cuesOfText("Hold steady"))
    }
}
