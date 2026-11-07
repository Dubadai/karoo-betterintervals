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
    /**
     * Karoo style everywhere (how BetterIntervals is shown); "classic*" is the other style, for the
     * guide's "it can also look like this". The rest are the states the guide explains.
     */
    val variants = listOf(
        "ready", "karoo", "karoo-number", "karoo-colours", "gauge", "free", "done",
        "choose", "restart", "cancelled", "countdown", "paused", "zoomed", "target-range", "lap-np",
        "outline", "cadence", "classic", "classic-colours", "classic-free",
    )
    val sizes: List<String>? = listOf("full")   // BetterIntervals is always a full-page field

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val k = ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.PLAIN)
        val s = when (variant) {
            // A workout loaded, waiting at the first interval for START WORKOUT
            "ready" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.PLAIN, notStarted = true).copy(stepIndex = 0, elapsedInStepSec = 0)
            "karoo" -> k
            "karoo-number" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.TEXT)
            "karoo-colours" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.BOTH)
            "gauge" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.GAUGE)
            "free" -> ExampleScreen.freeRide(karooStyle = true)
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
            "outline" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.PLAIN, tileOutline = true)
            "cadence" -> ExampleScreen.state(karooStyle = true, wattsMode = WattsMode.PLAIN, secondField = SecondField.CADENCE)
            "classic-colours" -> ExampleScreen.state(karooStyle = false, wattsMode = WattsMode.BOTH)
            "classic-free" -> ExampleScreen.freeRide(karooStyle = false)
            else -> ExampleScreen.state(karooStyle = false, wattsMode = WattsMode.PLAIN)
        }
        // Layouts are inflated on the main thread, as on the Karoo.
        var out: Bitmap? = null
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { out = ExampleScreen.draw(context, s, w, h); done.countDown() }
        done.await()
        return out!!
    }
}
