package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch

/**
 * DEBUG ONLY: what the field gallery (FieldGallery.kt) draws: the workout screen in its main looks
 * (ExampleScreen's made-up workout), full page: BetterIntervals is always a full-page field.
 */
// gallery: no-power-neighbours (this app shows power itself)
object GalleryFields {
    private const val ROUTE_PX = 79
    private val HR_ZONES = listOf(0..119, 120..139, 140..154, 155..169, 170..220)

    /** A ride for the README GIF (seq-<look>-00…): interval 4 runs out (beeps at 3 s), then the recovery starts and the watts drop. step, seconds in, watts. */
    private val SEQ = listOf(Triple(3, 280, 284), Triple(3, 285, 287), Triple(3, 290, 281), Triple(3, 294, 288), Triple(3, 297, 285), Triple(3, 299, 283), Triple(4, 1, 241), Triple(4, 3, 192), Triple(4, 6, 166), Triple(4, 9, 153), Triple(4, 12, 149), Triple(4, 15, 151))

    /** The workout screen's looks and the states the guide explains (Karoo style is the only style). */
    val variants = listOf(
        "ready", "ready-countdown", "karoo", "karoo-number", "karoo-background", "karoo-colours", "karoo-colours-route", "gauge-countdown", "gauge-route", "gauge", "free", "free-no-zones", "free-first-lap", "free-instant", "free-5s", "free-10s", "free-lap-np", "free-ride", "top-target", "top-cadence", "gauge-top-hr", "gauge-top-cadence", "gauge-top-target", "countdown-gauge", "done", "done-route",
        "choose", "restart", "cancelled", "countdown", "natural-countdown", "natural-countdown-gauge", "finish-countdown-gauge", "finish-countdown", "paused", "zoomed", "target-range", "lap-np",
        "outline", "cadence",
        // Heart-rate targets, two values at the top, the gear while the Karoo has the ride paused
        "hr-gauge", "hr-gauge-pct", "hr-number", "hr-top-two", "top-two", "ride-paused", "ride-auto-paused", "hr-ready",
        // The Karoo's "Searching GPS…" strip over the top: the gear moves out from under it
        "gps-gauge", "gps-free", "gps-ready", "gps-done",
        // The Karoo's empty states: no power meter paired, heart rate strap searching
        "empty-free", "empty-gauge", "empty-number", "empty-ready",
        // A workout selected again mid-ride: CONTINUE (interval 4, 2:13 in) or START OVER
        "resume-ready",
        // Indoor ERG: the pill right of the gear (docs/design/indoor-erg)
        "erg-gauge", "erg-number", "erg-connecting", "erg-lost", "erg-free", "erg-paused", "erg-top-two", "erg-gauge-route", "erg-off", "erg-off-top-two",
    ) + SEQ.indices.flatMap { i -> listOf("seq-gauge-%02d", "seq-both-%02d", "seq-background-%02d").map { it.format(i) } }
    val sizes: List<String>? = listOf("full")   // BetterIntervals is always a full-page field

    /** 10 min warm-up, 5 × (4 min hard, 2 min easy), 10 min cool-down, in bpm; LTHR 180. */
    private val HR_PLAN = WorkoutPlan(
        id = "example-hr",
        name = "HR intervals 5 × 4 min",
        // Written as % of max HR (max 200), as in intervals.icu: "10m 67% HR", 5 × ("4m 82% HR", "2m 69% HR"), "10m 62% HR"
        steps = listOf(PlanStep(600, 134, 134, rangeLow = 131, rangeHigh = 137, hr = true, pctUnit = "%hr", pctLow = 67, pctHigh = 67)) +
            List(5) {
                listOf(
                    PlanStep(240, 164, 164, rangeLow = 160, rangeHigh = 168, hr = true, pctUnit = "%hr", pctLow = 82, pctHigh = 82),
                    PlanStep(120, 138, 138, rangeLow = 135, rangeHigh = 141, hr = true, pctUnit = "%hr", pctLow = 69, pctHigh = 69),
                )
            }.flatten() +
            PlanStep(600, 124, 124, rangeLow = 122, rangeHigh = 126, hr = true, pctUnit = "%hr", pctLow = 62, pctHigh = 62),
        ftp = 280,
        lthr = 180,
    )

    /** Interval 4 of 12 (the second hard one), 1:33 left: 164 bpm in range, 236 W, lap 162 bpm. */
    private fun hrWorkout(mode: WattsMode) = ExampleScreen.state(wattsMode = mode).copy(
        plan = HR_PLAN, stepIndex = 3, elapsedInStepSec = 147, heartRate = 164, lapHrAvg = 162, powerShown = 236, power = 236, hrZones = HR_ZONES,
    )

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val k = ExampleScreen.state(wattsMode = WattsMode.PLAIN)
        val seq = Regex("seq-(gauge|both|background)-(\\d+)").matchEntire(variant)?.destructured
        val s = if (seq != null) {
            val (look, n) = seq
            val (step, sec, watts) = SEQ[n.toInt()]
            val mode = when (look) { "both" -> WattsMode.BOTH; "background" -> WattsMode.BACKGROUND; else -> WattsMode.GAUGE }
            ExampleScreen.state(wattsMode = mode).copy(stepIndex = step, elapsedInStepSec = sec, power = watts, powerShown = watts)
        } else when (variant) {
            // A workout loaded, waiting at the first interval for START WORKOUT
            "ready" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0)
            // START WORKOUT tapped: the 5 s countdown in the button and the box
            "ready-countdown" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0, countdown = 3)
            "karoo" -> k
            "karoo-number" -> ExampleScreen.state(wattsMode = WattsMode.TEXT)
            "karoo-colours" -> ExampleScreen.state(wattsMode = WattsMode.BOTH)
            "karoo-background" -> ExampleScreen.state(wattsMode = WattsMode.BACKGROUND)
            // With a route: the Karoo makes the field 79 px shorter (the route pill goes below it)
            "karoo-colours-route" -> ExampleScreen.state(wattsMode = WattsMode.BOTH).copy(navigating = true)
            // The gauge (the README's main look): counting down into the next interval, and with a route
            // (an interval ending: 3 s left, the beeps; the next one starts after them)
            "gauge-countdown" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(elapsedInStepSec = 297)
            "gauge-route" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(navigating = true)
            "gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE)
            // Five HR zones from a rider profile, so the HR tile shows the Karoo's zone colour (132: Z2)
            "free" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES)
            "free-no-zones" -> ExampleScreen.freeRide().copy(hrZones = emptyList(), freeSecond = FreeSecond.LAP)
            "free-first-lap" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, lastLapTimeSec = null)
            // The watts label follows Settings › Power
            "free-instant" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, powerSmoothing = Smoothing.INSTANT)
            "free-5s" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, powerSmoothing = Smoothing.S5)
            "free-10s" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, powerSmoothing = Smoothing.S10)
            // Free ride's tiles, tapped on: LAP NP / LAP HR, then RIDE AVG / RIDE HR
            "free-lap-np" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, freePower = FreePower.LAP_NP, freeLapNp = 289, freeSecond = FreeSecond.LAP, lapHrAvg = 138)
            "free-ride" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, freePower = FreePower.RIDE_AVG, rideAvgPower = 268, freeSecond = FreeSecond.RIDE, rideAvgHr = 131)
            // Settings › Top row
            "top-target" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, topRow = TopRow.TARGET)
            "top-cadence" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, topRow = TopRow.CADENCE)
            "gauge-top-hr" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE, topRow = TopRow.HEART_RATE)
            "gauge-top-target" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE, topRow = TopRow.TARGET)
            "countdown-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE, topRow = TopRow.TARGET).copy(jumpTarget = 4, countdown = 3)
            "gauge-top-cadence" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE, topRow = TopRow.CADENCE)
            // The whole workout done: 52:00, averages that fit the showcase ride
            "done" -> k.copy(finished = true, stepIndex = 9, summary = WorkoutSummary(3120, 232, 251, 132, 88))
            "done-route" -> k.copy(finished = true, stepIndex = 9, summary = WorkoutSummary(3120, 232, 251, 132, 88))
            // Jumping: choosing 1 ahead, restarting, cancelled, the countdown
            "choose" -> k.copy(selectTarget = 4)
            "restart" -> k.copy(selectTarget = 3, selectRestart = true)
            "cancelled" -> k.copy(moveCancelled = true)
            "countdown" -> k.copy(jumpTarget = 4, countdown = 3)
            // Mock (test ride 2026-11-10): the same countdown in the last seconds before the interval ends by itself
            "natural-countdown" -> k.copy(elapsedInStepSec = 297, jumpTarget = k.stepIndex + 1, countdown = 3)
            "natural-countdown-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).let { it.copy(elapsedInStepSec = 297, jumpTarget = it.stepIndex + 1, countdown = 3) }
            // Mock: the last interval's countdown says Finish
            "finish-countdown-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).let { st ->
                val last = st.stepCount - 1
                st.copy(stepIndex = last, elapsedInStepSec = st.plan!!.steps[last].durationSec - 3, countdown = 3)
            }
            "finish-countdown" -> k.let { st ->
                val last = st.stepCount - 1
                st.copy(stepIndex = last, elapsedInStepSec = st.plan!!.steps[last].durationSec - 3, countdown = 3)
            }
            "paused" -> k.copy(workoutPaused = true)
            "zoomed" -> k.copy(chartZoomed = true)
            "target-range" -> k.copy(targetShowsRange = true)
            "lap-np" -> k.copy(lapShowsNp = true, lapNp = 289, targetShowsRange = true)
            "outline" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, tileOutline = true)
            "cadence" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, secondField = SecondField.CADENCE)
            // A workout with heart-rate targets only (as intervals.icu sends one: % of max HR, resolved to bpm)
            "hr-gauge" -> hrWorkout(WattsMode.GAUGE)
            // The Target tile tapped to %: as written, % of max HR ("% HR 82", Next up "2m @ 69% HR")
            "hr-gauge-pct" -> hrWorkout(WattsMode.GAUGE).copy(hrTargetLthr = true)
            "hr-number" -> hrWorkout(WattsMode.BOTH)
            "hr-top-two" -> hrWorkout(WattsMode.GAUGE).copy(topRowRight = TopRow.CADENCE)
            "hr-ready" -> hrWorkout(WattsMode.GAUGE).copy(notStarted = true, stepIndex = 0, elapsedInStepSec = 0)
            // Settings › Top row › Two: heart rate and cadence
            "top-two" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(topRowRight = TopRow.CADENCE)
            // The Karoo has the ride paused: its yellow bar covers the top, the gear comes down
            "erg-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(erg = ErgStatus.HOLDING, ergWatts = 300)
            "erg-number" -> k.copy(erg = ErgStatus.HOLDING, ergWatts = 300)
            "erg-connecting" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(erg = ErgStatus.CONNECTING)
            "erg-lost" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(erg = ErgStatus.LOST)
            "erg-free" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(erg = ErgStatus.FREE)
            "erg-paused" -> k.copy(workoutPaused = true, erg = ErgStatus.HOLDING, ergWatts = 140)
            "erg-top-two" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(topRowRight = TopRow.CADENCE, erg = ErgStatus.HOLDING, ergWatts = 300)
            "erg-off" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(erg = ErgStatus.OFF, ergOffThisRide = true)
            "erg-off-top-two" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(topRowRight = TopRow.CADENCE, erg = ErgStatus.OFF, ergOffThisRide = true)
            "erg-gauge-route" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(navigating = true, erg = ErgStatus.HOLDING, ergWatts = 300)
            "resume-ready" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 3, elapsedInStepSec = 0, resumeFromSec = 133)
            "empty-free" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, powerShown = null, powerSensor = SensorState.NONE, heartRate = null, hrSensor = SensorState.SEARCHING)
            "empty-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(powerShown = null, powerSensor = SensorState.NONE, heartRate = null, hrSensor = SensorState.SEARCHING)
            "empty-number" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN).copy(powerShown = null, powerSensor = SensorState.SEARCHING, heartRate = null, hrSensor = SensorState.NONE, topRowRight = TopRow.TARGET)
            "empty-ready" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0, powerShown = null, powerSensor = SensorState.NONE)
            "gps-gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(gpsSearching = true)
            "gps-free" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, gpsSearching = true)
            "gps-ready" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0, gpsSearching = true)
            "gps-done" -> k.copy(finished = true, stepIndex = 9, summary = WorkoutSummary(3120, 232, 251, 132, 88), gpsSearching = true)
            "ride-paused" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(paused = true)
            "ride-auto-paused" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE).copy(paused = true, autoPaused = true)
            else -> k
        }
        // Layouts are inflated on the main thread, as on the Karoo.
        var out: Bitmap? = null
        val done = CountDownLatch(1)
        // "-route": the field as the Karoo shortens it when a route is loaded (79 px less), drawn at the top
        val fieldH = if (variant.endsWith("-route")) h - ROUTE_PX else h
        Handler(Looper.getMainLooper()).post { out = ExampleScreen.draw(context, s, w, fieldH); done.countDown() }
        done.await()
        if (fieldH == h) return out!!
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { android.graphics.Canvas(it).drawBitmap(out!!, 0f, 0f, null) }
    }
}
