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
    private val HR_ZONES = listOf(0..119, 120..139, 140..154, 155..169, 170..220)

    /** The workout screen's looks and the states the guide explains (Karoo style is the only style). */
    val variants = listOf(
        "ready", "karoo", "karoo-number", "karoo-colours", "gauge", "free", "free-first-lap", "done",
        "choose", "restart", "cancelled", "countdown", "paused", "zoomed", "target-range", "lap-np",
        "outline", "cadence",
    )
    val sizes: List<String>? = listOf("full")   // BetterIntervals is always a full-page field

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val k = ExampleScreen.state(wattsMode = WattsMode.PLAIN)
        val s = when (variant) {
            // A workout loaded, waiting at the first interval for START WORKOUT
            "ready" -> ExampleScreen.state(wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0)
            "karoo" -> k
            "karoo-number" -> ExampleScreen.state(wattsMode = WattsMode.TEXT)
            "karoo-colours" -> ExampleScreen.state(wattsMode = WattsMode.BOTH)
            "gauge" -> ExampleScreen.state(wattsMode = WattsMode.GAUGE)
            // Five HR zones from a rider profile, so the HR tile shows the Karoo's zone colour (132: Z2)
            "free" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES)
            "free-first-lap" -> ExampleScreen.freeRide().copy(hrZones = HR_ZONES, lastLapTimeSec = null)
            // The whole workout done: 52:00, averages that fit the showcase ride
            "done" -> k.copy(finished = true, stepIndex = 9, summary = WorkoutSummary(3120, 232, 251, 132, 88))
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
        Handler(Looper.getMainLooper()).post { out = ExampleScreen.draw(context, s, w, h); done.countDown() }
        done.await()
        return out!!
    }
}
