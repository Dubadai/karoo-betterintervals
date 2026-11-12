package com.daviddokakis.karoointerval

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutDetailsTest {

    @Test fun `notes keep the coach's words and drop the steps and their headings`() {
        val event = JSONObject().put(
            "description",
            "Steady tempo.\n\nEat every 20 minutes.\n\nWarmup\n- 20m ramp 43-64%\n\nMain set 3x\n- 15m 80%\n- 5m 57%\n\nCooldown\n- 15m 50%",
        )
        assertEquals("Steady tempo.\n\nEat every 20 minutes.", WorkoutDetailsActivity.notesOf(event))
    }

    @Test fun `a description that is only steps has no notes`() {
        assertNull(WorkoutDetailsActivity.notesOf(JSONObject().put("description", "- 10m 55%\n3x\n- 1m 120%")))
        assertNull(WorkoutDetailsActivity.notesOf(JSONObject()))
    }

    @Test fun `short time`() {
        assertEquals("2h20", WorkoutStats.shortTime(8400))
        assertEquals("1h05", WorkoutStats.shortTime(3900))
        assertEquals("45m", WorkoutStats.shortTime(2700))
    }

    @Test fun `steady hour at FTP is IF 1, TSS 100, its work in kcal`() {
        val plan = WorkoutPlan("x", "FTP hour", listOf(PlanStep(3600, 250, 250)), ftp = 250)
        val s = WorkoutStats.of(plan, JSONObject())
        assertEquals(250, s.avg)
        assertEquals(250, s.np)
        assertEquals(1.0, s.intensity!!, 0.001)
        assertEquals(100, s.tss)
        assertEquals(900, s.kcal)   // 250 W for an hour: 900 kJ
    }

    @Test fun `a tap on the picture finds the step under it`() {
        // 10 min, 20 min, 30 min on a 600 px picture: 100 px, 200 px, 300 px
        val plan = WorkoutPlan("x", "Three", listOf(PlanStep(600, 150, 150), PlanStep(1200, 250, 250), PlanStep(1800, 180, 180)), ftp = 250)
        assertEquals(0, ChartRenderer.stepAt(plan, 600, 0f))
        assertEquals(0, ChartRenderer.stepAt(plan, 600, 99f))
        assertEquals(1, ChartRenderer.stepAt(plan, 600, 101f))
        assertEquals(2, ChartRenderer.stepAt(plan, 600, 301f))
        assertEquals(2, ChartRenderer.stepAt(plan, 600, 600f))
        assertEquals(2, ChartRenderer.stepAt(plan, 600, 700f))   // past the edge: the last step
        assertNull(ChartRenderer.stepAt(WorkoutPlan("x", "Empty", emptyList(), ftp = 250), 600, 10f))
    }

    @Test fun `intervals icu's own load and intensity win`() {
        val plan = WorkoutPlan("x", "FTP hour", listOf(PlanStep(3600, 250, 250)), ftp = 250)
        val s = WorkoutStats.of(plan, JSONObject().put("icu_training_load", 92).put("icu_intensity", 95.5))
        assertEquals(92, s.tss)
        assertEquals(0.955, s.intensity!!, 0.0001)
    }
}
