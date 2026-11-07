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

    /** The workout screen's looks and the states the guide explains (Karoo style is the only style). */
    val variants = listOf(
        "ready", "ready-countdown", "karoo", "karoo-number", "karoo-background", "karoo-colours", "karoo-colours-route", "gauge-countdown", "gauge-route", "gauge", "free", "free-first-lap", "free-instant", "free-5s", "free-10s", "free-lap-np", "free-ride", "top-target", "top-cadence", "gauge-top-hr", "gauge-top-cadence", "gauge-top-target", "countdown-gauge", "done", "done-route",
        "choose", "restart", "cancelled", "countdown", "paused", "zoomed", "target-range", "lap-np",
        "outline", "cadence",
    )
    val sizes: List<String>? = listOf("full")   // BetterIntervals is always a full-page field

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val k = ExampleScreen.state(wattsMode = WattsMode.PLAIN)
        val s = when (variant) {
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
            "paused" -> k.copy(workoutPaused = true)
            "zoomed" -> k.copy(chartZoomed = true)
            "target-range" -> k.copy(targetShowsRange = true)
            "lap-np" -> k.copy(lapShowsNp = true, lapNp = 289, targetShowsRange = true)
            "outline" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, tileOutline = true)
            "cadence" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, secondField = SecondField.CADENCE)
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
