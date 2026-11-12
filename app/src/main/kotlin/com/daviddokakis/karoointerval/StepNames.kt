package com.daviddokakis.karoointerval

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The workout's steps as the plan has them (intervals.icu `workout_doc`): repeats written out, steps without a time left out. */
fun leafSteps(steps: JSONArray): List<JSONObject> = buildList {
    for (i in 0 until steps.length()) {
        val step = steps.optJSONObject(i) ?: continue
        val children = step.optJSONArray("steps")
        if (children != null) repeat(step.optInt("reps", 1).coerceAtLeast(1)) { addAll(leafSteps(children)) }
        else if (step.optInt("duration", 0) > 0) add(step)
    }
}

/** The event's steps, one per plan step, or null when they don't line up with the plan. */
fun leafStepsOf(event: JSONObject?, plan: WorkoutPlan): List<JSONObject>? =
    event?.optJSONObject("workout_doc")?.optJSONArray("steps")?.let { leafSteps(it) }?.takeIf { it.size == plan.steps.size }

/** An interval's name: the coach's words, Warm-up / Cool-down, else its zone's name (Heart rate for a heart-rate step). */
fun Context.stepName(json: JSONObject?, st: PlanStep, ftp: Int?): String {
    val za = if (st.hr) null else Zones.zoneOf(st.startWatts, ftp)
    val zb = if (st.hr) null else Zones.zoneOf(st.endWatts, ftp)
    return when {
        json != null && json.optString("text").isNotBlank() -> json.optString("text").trim()
        json?.optBoolean("warmup") == true -> getString(R.string.warm_up)
        json?.optBoolean("cooldown") == true -> getString(R.string.cool_down)
        za != null -> getString(WorkoutDetailsActivity.ZONE_NAMES[maxOf(za, zb ?: za) - 1])
        else -> getString(R.string.heart_rate)
    }
}
