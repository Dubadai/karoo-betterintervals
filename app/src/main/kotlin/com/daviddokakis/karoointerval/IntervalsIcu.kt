package com.daviddokakis.karoointerval

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** A planned workout as listed by intervals.icu. */
data class PlannedWorkout(
    val plan: WorkoutPlan,
    val date: LocalDate,
    /** intervals.icu training load, if set. */
    val load: Int?,
    val json: String,
)

/**
 * Minimal intervals.icu client: lists planned ride workouts and converts their `workout_doc`
 * into a [WorkoutPlan]. Auth is HTTP basic with user "API_KEY" and the athlete's personal API key
 * (intervals.icu > Settings > Developer settings).
 *
 * Requests go through the Karoo ([karooHttpGet]), which uses Wi-Fi when connected and otherwise
 * the phone connected through the Hammerhead Companion app.
 */
class IntervalsIcu(private val athleteId: String, private val apiKey: String) {

    /** intervals.icu answered, but with an error (wrong key, server problem). */
    class ApiException(message: String) : Exception(message)

    /** Planned ride workouts from [oldest] to [newest] (inclusive). */
    suspend fun plannedWorkouts(
        oldest: LocalDate,
        newest: LocalDate,
        get: suspend (url: String, headers: Map<String, String>) -> HttpResult,
    ): List<PlannedWorkout> {
        val url = "https://intervals.icu/api/v1/athlete/${athleteId.ifBlank { "0" }}/events" +
            "?oldest=$oldest&newest=$newest&category=WORKOUT&resolve=true"
        val token = Base64.encodeToString("API_KEY:$apiKey".toByteArray(), Base64.NO_WRAP)
        val result = get(url, mapOf("Authorization" to "Basic $token", "Accept" to "application/json"))
        val code = result.statusCode
        if (code == 401 || code == 403) throw ApiException("intervals.icu rejected the API key or athlete ID")
        if (code !in 200..299) throw ApiException("intervals.icu returned HTTP $code")
        val events = JSONArray(result.body)
        return (0 until events.length()).mapNotNull { i -> plannedFrom(events.getJSONObject(i)) }
            .sortedBy { it.date }
    }

    companion object {
        /** A planned ride from one event, or null if it isn't a ride with a usable workout. */
        fun plannedFrom(event: JSONObject): PlannedWorkout? {
            val type = event.optString("type")
            if (type.isNotEmpty() && type != "Ride" && type != "VirtualRide") return null
            val date = event.optString("start_date_local").take(10)
                .let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            val load = event.optDoubleOrNull("icu_training_load")?.roundToInt()
            return parseEvent(event)?.let { PlannedWorkout(it, date, load, event.toString()) }
        }

        /** Parses one event (as returned by the events endpoint with resolve=true). */
        fun parseEvent(event: JSONObject): WorkoutPlan? {
            val doc = event.optJSONObject("workout_doc") ?: return null
            val ftp = doc.optPositiveInt("ftp") ?: event.optPositiveInt("icu_ftp")
            val steps = mutableListOf<PlanStep>()
            addSteps(doc.optJSONArray("steps") ?: return null, ftp, steps)
            if (steps.isEmpty()) return null
            return WorkoutPlan(
                id = event.opt("id")?.toString() ?: event.optString("name"),
                name = event.optString("name", "Workout"),
                steps = steps,
                ftp = ftp,
            )
        }

        private fun addSteps(array: JSONArray, ftp: Int?, out: MutableList<PlanStep>) {
            for (i in 0 until array.length()) {
                val step = array.optJSONObject(i) ?: continue
                val children = step.optJSONArray("steps")
                if (children != null) {
                    // Repeat block, e.g. "3x": expand it into its individual steps.
                    repeat(step.optInt("reps", 1).coerceAtLeast(1)) { addSteps(children, ftp, out) }
                    continue
                }
                val duration = step.optInt("duration", 0)
                if (duration <= 0) continue // distance-based or empty steps aren't supported yet
                out += stepOf(step, duration, ftp)
            }
        }

        /**
         * Converts one step. Uses the resolved watts in `_power` when present: for ramps its
         * start/end are the ramp ends; otherwise `value` is the target and start/end the range.
         */
        private fun stepOf(step: JSONObject, duration: Int, ftp: Int?): PlanStep {
            val ramp = step.optBoolean("ramp", false)
            val power = step.optJSONObject("power")
            val writtenAsRange = !ramp && power != null && power.has("start") && power.has("end") && !power.has("value")
            val resolved = step.optJSONObject("_power")
            val toWatts: (Double) -> Double = when (power?.optString("units")) {
                "w", "W", "watts" -> { v -> v }
                "%ftp", "", null -> { v -> if (ftp != null) v * ftp / 100.0 else 0.0 }
                else -> { _ -> 0.0 } // zones, %MMP etc. need the resolved values
            }
            fun resolvedOr(key: String): Int? =
                resolved?.optDoubleOrNull(key)?.roundToInt() ?: power?.optDoubleOrNull(key)?.let(toWatts)?.roundToInt()

            val low = resolvedOr("start")
            val high = resolvedOr("end")
            if (ramp && low != null && high != null) {
                return PlanStep(duration, low, high, ramp = true)
            }
            val target = resolvedOr("value") ?: if (low != null && high != null) (low + high) / 2 else 0
            return PlanStep(duration, target, target, rangeLow = low, rangeHigh = high, isRange = writtenAsRange)
        }

        internal fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

        private fun JSONObject.optPositiveInt(key: String): Int? =
            optDoubleOrNull(key)?.roundToInt()?.takeIf { it > 0 }
    }
}
