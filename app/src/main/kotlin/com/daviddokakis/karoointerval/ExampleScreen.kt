package com.daviddokakis.karoointerval

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import io.hammerhead.karooext.models.ViewConfig

/**
 * The workout screen with a made-up workout and live values, as a picture: the Example card on the
 * Settings page and the field gallery (debug) draw it, with the real ScreenRenderer. The numbers are
 * the showcase values every Karoo app picture uses (design-assets/tools/field-gallery/showcase.json).
 */
object ExampleScreen {
    /** 10 min warm-up, 4 × (5 min at 280 W, 3 min easy), cool-down. */
    private val plan = WorkoutPlan(
        id = "example",
        name = "Example",
        steps = listOf(PlanStep(600, 150, 210, ramp = true)) +
            List(4) { listOf(PlanStep(300, 280, 280, rangeLow = 273, rangeHigh = 287), PlanStep(180, 150, 150)) }.flatten() +
            PlanStep(600, 180, 120, ramp = true),
        ftp = 280,
    )

    /** Interval 4 of 10: 280 W (in range), 132 bpm or 88 rpm, lap average 282 W. */
    fun state(
        karooStyle: Boolean,
        wattsMode: WattsMode,
        appearance: Appearance = Appearance.AUTO,
        tileOutline: Boolean = false,
        secondField: SecondField = SecondField.HEART_RATE,
    ) = ScreenState(
        plan = plan,
        stepIndex = 3,
        elapsedInStepSec = 207,
        power = 280,
        powerShown = 280,
        heartRate = 132,
        cadence = 88,
        lapAvg = 282,
        lapNumber = 4,
        wattsMode = wattsMode,
        appearance = appearance,
        karooStyle = karooStyle,
        tileOutline = tileOutline,
        secondField = secondField,
    )

    /** [s] drawn as a [w] × [h] px Karoo field would show it. [context] decides light or dark (Auto). */
    fun draw(context: Context, s: ScreenState, w: Int, h: Int): Bitmap {
        // Boundaries on: how David rides, and how all our mockups show the Karoo.
        val config = ViewConfig(gridSize = 60 to (60 * h / 721).coerceIn(1, 60), viewSize = w to h, textSize = 96, boundariesEnabled = true)
        val view = ScreenRenderer(context, config).render(s, demo = false).apply(context, FrameLayout(context))
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /** A Karoo 3 full-page field, in pixels. */
    const val FULL_W = 478
    const val FULL_H = 721
}
