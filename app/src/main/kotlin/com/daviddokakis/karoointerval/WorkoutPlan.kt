package com.daviddokakis.karoointerval

import kotlin.math.roundToInt

/**
 * One step of the workout.
 * - [ramp] true: target moves from [startWatts] to [endWatts] over the step.
 * - otherwise a single target ([startWatts] == [endWatts]).
 * [rangeLow]..[rangeHigh] is the "in range" band for a non-ramp step (intervals.icu sends one
 * for every target); [isRange] marks steps that were *written* as a range, e.g. 80–85%.
 *
 * [hr]: the step has a heart-rate target instead of a power target. Then the "watts" fields hold
 * beats per minute, and the screen runs the step on heart rate.
 * [pctUnit]: a heart-rate step written as a percentage, "%hr" (of max HR, "85% HR") or "%lthr"
 * ("95% LTHR"), with the written numbers: [pctLow]..[pctHigh] for a range or a ramp, else both
 * the same. Shown as written when the Target tile is tapped to %.
 */
data class PlanStep(
    val durationSec: Int,
    val startWatts: Int,
    val endWatts: Int,
    val ramp: Boolean = false,
    val rangeLow: Int? = null,
    val rangeHigh: Int? = null,
    val isRange: Boolean = false,
    val hr: Boolean = false,
    val pctUnit: String? = null,
    val pctLow: Int? = null,
    val pctHigh: Int? = null,
) {
    val avgWatts: Int get() = (startWatts + endWatts) / 2
    val isRamp: Boolean get() = ramp && startWatts != endWatts

    /** Target at [elapsedSec] into the step. */
    fun targetAt(elapsedSec: Int): Int = if (isRamp && durationSec > 0) {
        val f = elapsedSec.coerceIn(0, durationSec).toDouble() / durationSec
        (startWatts + (endWatts - startWatts) * f).roundToInt()
    } else {
        avgWatts
    }

    /** Acceptable range at [elapsedSec]: the step's own range, or ±[TOLERANCE] around the target. */
    fun rangeAt(elapsedSec: Int): IntRange {
        if (!isRamp && rangeLow != null && rangeHigh != null) return minOf(rangeLow, rangeHigh)..maxOf(rangeLow, rangeHigh)
        val t = targetAt(elapsedSec)
        val tol = maxOf(if (hr) 2 else 5, (t * TOLERANCE).roundToInt())
        return (t - tol)..(t + tol)
    }

    /** The step with its power targets at [pct] % (heart-rate targets stay as written). */
    fun scaled(pct: Int): PlanStep {
        if (hr || pct == 100) return this
        fun s(v: Int) = (v * pct / 100.0).roundToInt()
        return copy(startWatts = s(startWatts), endWatts = s(endWatts), rangeLow = rangeLow?.let(::s), rangeHigh = rangeHigh?.let(::s))
    }

    companion object {
        /** Same idea as intervals.icu's default power range for outdoor workouts. */
        const val TOLERANCE = 0.03
    }
}

/**
 * A workout loaded from intervals.icu, run by this app (not by Karoo's workout player).
 * [lthr]: the athlete's lactate threshold heart rate from the workout, for "% LTHR".
 * [scalePct]: Workouts › Scale workout (100 = as planned); the steps are already scaled.
 */
data class WorkoutPlan(
    val id: String,
    val name: String,
    val steps: List<PlanStep>,
    val ftp: Int?,
    val lthr: Int? = null,
    val scalePct: Int = 100,
) {
    val totalSec: Int = steps.sumOf { it.durationSec }
    fun startSecOf(index: Int): Int = steps.take(index).sumOf { it.durationSec }
    val maxWatts: Int = steps.filter { !it.hr }.maxOfOrNull { maxOf(it.startWatts, it.endWatts) } ?: 0

    /** Every step has a heart-rate target (no power targets at all). */
    val hrOnly: Boolean get() = steps.isNotEmpty() && steps.all { it.hr }

    /** This workout with every power target at [pct] % of the plan. Call it on the plan as loaded (100 %). */
    fun scaled(pct: Int): WorkoutPlan = if (pct == scalePct) this else copy(steps = steps.map { it.scaled(pct) }, scalePct = pct)
}
