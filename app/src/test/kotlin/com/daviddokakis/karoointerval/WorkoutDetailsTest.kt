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

    @Test fun `intervals icu's own load and intensity win`() {
        val plan = WorkoutPlan("x", "FTP hour", listOf(PlanStep(3600, 250, 250)), ftp = 250)
        val s = WorkoutStats.of(plan, JSONObject().put("icu_training_load", 92).put("icu_intensity", 95.5))
        assertEquals(92, s.tss)
        assertEquals(0.955, s.intensity!!, 0.0001)
    }
}
